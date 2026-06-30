package com.callblocker.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SubscriptionManager
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

        // Determine SIM slot from subscription info
        var simSlot = 0
        try {
            val subManager = getSystemService(SubscriptionManager::class.java)
            if (subManager != null) {
                val activeInfos = subManager.activeSubscriptionInfoList
                if (!activeInfos.isNullOrEmpty()) {
                    if (activeInfos.size == 1) {
                        // Only one SIM active — use its slot directly
                        simSlot = activeInfos[0].simSlotIndex
                    } else {
                        // Multiple SIMs — try to match by account handle
                        val phoneAccountHandle = details.accountHandle
                        if (phoneAccountHandle != null) {
                            val matched = activeInfos.find { info ->
                                try {
                                    info.subscriptionId.toString() == phoneAccountHandle.id
                                } catch (_: Exception) { false }
                            }
                            if (matched != null) simSlot = matched.simSlotIndex
                        }
                    }
                }
            }
        } catch (_: Exception) { }

        scope.launch {
            try {
                val config = repository.getSimConfig(simSlot)
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                val normalized = CallBlockerRepository.normalizeNumber(number)

                // 黑名单优先：在黑名单中则直接拦截
                if (repository.isBlacklisted(normalized, simSlot)) {
                    val blockResponse = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(true)
                        .setSkipCallLog(false)
                        .build()
                    respondToCall(details, blockResponse)
                    repository.recordBlockedCall(normalized, simSlot = simSlot)
                    return@launch
                }

                if (repository.isWhitelisted(normalized, simSlot)) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                if (repository.hasBeenCalledRecently(normalized, config.intervalMinutes.toLong(), simSlot)) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .build()
                respondToCall(details, response)

                repository.recordBlockedCall(normalized, simSlot = simSlot)
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
