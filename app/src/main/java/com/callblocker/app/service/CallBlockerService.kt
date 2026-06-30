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
     * Determine which SIM slot received this call.
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

                // Strategy 1: Parse as int, try matching against simSlotIndex first
                try {
                    val numericId = accountId.toInt()
                    // If it's a small number (0 or 1), treat as simSlotIndex directly
                    val directMatch = activeInfos.find { it.simSlotIndex == numericId }
                    if (directMatch != null) return directMatch.simSlotIndex
                } catch (_: NumberFormatException) { }

                // Strategy 2: Direct integer parse matching subscriptionId
                // (after simSlotIndex check, in case subscriptionId also happens to match)
                try {
                    val subId = accountId.toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: NumberFormatException) { }

                // Strategy 3: Parse suffix after last "-" (common on Samsung/小米)
                try {
                    val parts = accountId.split("-")
                    val subId = parts.last().toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: Exception) { }

                // Strategy 4: ICCID substring match (Samsung alternative)
                for (info in activeInfos) {
                    try {
                        val iccid = info.iccId ?: continue
                        if (accountId.contains(iccid)) return info.simSlotIndex
                    } catch (_: Exception) { }
                }

                // Strategy 5: Direct string compare
                val matched = activeInfos.find { info ->
                    try { info.subscriptionId.toString() == accountId } catch (_: Exception) { false }
                }
                if (matched != null) return matched.simSlotIndex

                // Strategy 6: Try matching by concatenated account ID patterns
                // Some phones format as "P0S1", "S1D1", "sub0", "SIM_1" etc.
                for (info in activeInfos) {
                    try {
                        val slotStr = info.simSlotIndex.toString()
                        val subStr = info.subscriptionId.toString()
                        if (accountId.contains(slotStr) || accountId.contains(subStr)) {
                            return info.simSlotIndex
                        }
                    } catch (_: Exception) { }
                }
            }

            // Strategy 7: Use default voice subscription as hint
            try {
                val voiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (voiceSubId >= 0) {
                    activeInfos.find { it.subscriptionId == voiceSubId }?.let { return it.simSlotIndex }
                }
            } catch (_: Exception) { }

            // Strategy 8: Pick first non-zero slot as fallback for dual-SIM
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

        val simSlot = resolveSimSlot(details)

        scope.launch {
            try {
                val config = repository.getSimConfig(simSlot)
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                val normalized = CallBlockerRepository.normalizeNumber(number)

                // 1. Blacklist check (per-SIM) → block + record
                if (repository.isBlacklisted(normalized, simSlot)) {
                    val blockResponse = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(true)
                        .setSkipCallLog(false)
                        .build()
                    respondToCall(details, blockResponse)
                    repository.recordCall(normalized, simSlot = simSlot)
                    return@launch
                }

                // 2. Whitelist check (per-SIM) → allow + record (for interval tracking later)
                if (repository.isWhitelisted(normalized, simSlot)) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, simSlot = simSlot)
                    return@launch
                }

                // 3. Interval check (per-SIM) → record + allow
                // "距离上次来电时间" — every call resets the timer
                if (repository.hasBeenCalledRecently(normalized, simSlot, config.intervalMinutes.toLong())) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, simSlot = simSlot)
                    return@launch
                }

                // 4. Default: block + record
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .build()
                respondToCall(details, response)
                repository.recordCall(normalized, simSlot = simSlot)
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
