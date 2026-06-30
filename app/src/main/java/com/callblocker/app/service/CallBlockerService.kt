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

    /**
     * Accurately determine which SIM slot received this call.
     * Uses multiple strategies to handle different phone vendors.
     */
    private fun resolveSimSlot(details: Call.Details): Int {
        try {
            val subManager = getSystemService(SubscriptionManager::class.java) ?: return 0
            val activeInfos = subManager.activeSubscriptionInfoList
            if (activeInfos.isNullOrEmpty()) return 0
            if (activeInfos.size == 1) return activeInfos[0].simSlotIndex

            val accountHandle = details.accountHandle
            if (accountHandle != null) {
                val accountId = accountHandle.id

                // Strategy 1: Direct integer parse (works on Google Pixel, stock Android)
                try {
                    val subId = accountId.toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: NumberFormatException) { }

                // Strategy 2: Parse suffix after last "-" (common on Samsung/小米)
                // e.g. "com.android.phone/.TelephonyConnectionService-1"
                try {
                    val parts = accountId.split("-")
                    val subId = parts.last().toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: Exception) { }

                // Strategy 3: ICCID substring match (Samsung alternative)
                for (info in activeInfos) {
                    try {
                        val iccid = info.iccId ?: continue
                        if (accountId.contains(iccid)) return info.simSlotIndex
                    } catch (_: Exception) { }
                }

                // Strategy 4: Original approach — direct string compare
                val matched = activeInfos.find { info ->
                    try { info.subscriptionId.toString() == accountId } catch (_: Exception) { false }
                }
                if (matched != null) return matched.simSlotIndex
            }

            // Strategy 5: Use default voice subscription as hint
            try {
                val voiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (voiceSubId >= 0) {
                    activeInfos.find { it.subscriptionId == voiceSubId }?.let { return it.simSlotIndex }
                }
            } catch (_: Exception) { }

            // Strategy 6: Pick first non-zero slot (often the incoming call slot)
            // Try to find a slot different from 0 as a guess for the second SIM
            activeInfos.find { it.simSlotIndex != 0 }?.let { return it.simSlotIndex }

        } catch (_: Exception) { }
        return 0
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as CallBlockerApp).repository
    }

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart?.trim() ?: run {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        // Determine SIM slot with multi-strategy fallback
        val simSlot = resolveSimSlot(details)

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
