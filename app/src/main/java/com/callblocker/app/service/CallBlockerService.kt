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
                val normalized = CallBlockerRepository.normalizeNumber(number)
                // Get ALL SIM configs — resolveSimSlot is unreliable on some phones
                val enabledSims = repository.getEnabledSimConfigs()

                if (enabledSims.isEmpty()) {
                    // No SIM with blocking enabled — let the call through
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                // Check blacklist across ALL enabled SIMs
                for (cfg in enabledSims) {
                    if (repository.isBlacklisted(normalized, cfg.simSlot)) {
                        val blockResponse = CallResponse.Builder()
                            .setDisallowCall(true)
                            .setRejectCall(true)
                            .setSilenceCall(true)
                            .setSkipCallLog(false)
                            .build()
                        respondToCall(details, blockResponse)
                        repository.recordBlockedCall(normalized, simSlot = cfg.simSlot)
                        return@launch
                    }
                }

                // Check whitelist across ALL enabled SIMs
                for (cfg in enabledSims) {
                    if (repository.isWhitelisted(normalized, cfg.simSlot)) {
                        respondToCall(details, CallResponse.Builder().build())
                        return@launch
                    }
                }

                // Interval check: use the most conservative (minimum) interval across all SIMs
                val minInterval = enabledSims.minOf { it.intervalMinutes }
                if (repository.hasBeenCalledRecently(normalized, minInterval.toLong())) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                // Default: block the call (record on first enabled SIM)
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .build()
                respondToCall(details, response)
                repository.recordBlockedCall(normalized, simSlot = enabledSims[0].simSlot)
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
