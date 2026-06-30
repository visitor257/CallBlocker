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

    companion object {
        // Diagnostic values — populated by resolveSimSlot, used for debugging
        var lastAccountId: String = ""
        var lastSimCount: Int = 0
        var lastActiveSlots: String = ""
    }

    /**
     * Determine which SIM slot received this call.
     * Uses multiple strategies to handle different phone vendors.
     */
    private fun resolveSimSlot(details: Call.Details): Int {
        try {
            val subManager = getSystemService(SubscriptionManager::class.java)
            val activeInfos = subManager?.activeSubscriptionInfoList
            val simCount = activeInfos?.size ?: 0

            val accountHandle = details.accountHandle
            val accountId = accountHandle?.id ?: "null"

            // --- Diagnostic: store raw values in a static field for the service to use ---
            lastAccountId = accountId
            lastSimCount = simCount
            lastActiveSlots = activeInfos?.joinToString(",") { "${it.simSlotIndex}(subId=${it.subscriptionId})" } ?: "null"

            if (activeInfos.isNullOrEmpty()) return 0
            if (activeInfos.size == 1) return activeInfos[0].simSlotIndex

            if (accountHandle != null) {
                // Strategy 1: Parse as int, try matching against simSlotIndex first
                try {
                    val numericId = accountId.toInt()
                    val directMatch = activeInfos.find { it.simSlotIndex == numericId }
                    if (directMatch != null) return directMatch.simSlotIndex
                } catch (_: NumberFormatException) { }

                // Strategy 2: Direct integer parse matching subscriptionId
                try {
                    val subId = accountId.toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: NumberFormatException) { }

                // Strategy 3: Parse suffix after last "-"
                try {
                    val parts = accountId.split("-")
                    val subId = parts.last().toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: Exception) { }

                // Strategy 4: ICCID substring match
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

                // Strategy 6: Partial string match
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

            // Strategy 7: Default voice subscription
            try {
                val voiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (voiceSubId >= 0) {
                    activeInfos.find { it.subscriptionId == voiceSubId }?.let { return it.simSlotIndex }
                }
            } catch (_: Exception) { }

            // Strategy 8: Pick first non-zero slot
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
        val diagLabel = "acc=$lastAccountId|sims=$lastSimCount|slots=$lastActiveSlots"

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
                    repository.recordCall(normalized, name = diagLabel, simSlot = simSlot)
                    return@launch
                }

                // 2. Whitelist check (per-SIM) → allow + record (for interval tracking later)
                if (repository.isWhitelisted(normalized, simSlot)) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, name = diagLabel, simSlot = simSlot)
                    return@launch
                }

                // 3. Interval check (per-SIM) → record + allow
                if (repository.hasBeenCalledRecently(normalized, simSlot, config.intervalMinutes.toLong())) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, name = diagLabel, simSlot = simSlot)
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
                repository.recordCall(normalized, name = diagLabel, simSlot = simSlot)
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }
}
