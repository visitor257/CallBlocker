package com.callblocker.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.data.repository.CallBlockerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallBlockerService : CallScreeningService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: CallBlockerRepository

    override fun onCreate() {
        super.onCreate()
        repository = (application as CallBlockerApp).repository
    }

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart?.trim() ?: run {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        scope.launch {
            try {
                val config = repository.getConfig()
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                val normalized = CallBlockerRepository.normalizeNumber(number)

                if (repository.isBlacklisted(normalized)) {
                    doBlock(details, normalized, "BLACKLIST")
                    return@launch
                }
                if (repository.isWhitelisted(normalized)) {
                    doAllow(details, normalized, "WHITELIST")
                    return@launch
                }
                if (repository.hasBeenCalledRecently(normalized, config.intervalMinutes.toLong())) {
                    doAllow(details, normalized, "INTERVAL_OK(${config.intervalMinutes}m)")
                    return@launch
                }
                doBlock(details, normalized, "DEFAULT")
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }

    private fun doAllow(details: Call.Details, number: String, reason: String) {
        respondToCall(details, CallResponse.Builder().build())
        scope.launch { repository.recordCall(number) }
    }

    private fun doBlock(details: Call.Details, number: String, reason: String) {
        val response = CallResponse.Builder()
            .setDisallowCall(true).setRejectCall(true)
            .setSilenceCall(true).setSkipCallLog(false).build()
        respondToCall(details, response)
        scope.launch { repository.recordCall(number) }
    }
}
