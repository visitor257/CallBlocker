package com.callblocker.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SubscriptionManager
import com.callblocker.app.CallBlockerApp
import com.callblocker.app.data.repository.CallBlockerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallBlockerService : CallScreeningService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: CallBlockerRepository

    companion object {
        var lastAccountId: String = ""
        var lastSimCount: Int = 0
        var lastActiveSlots: String = ""
    }

    /**
     * Determine which SIM slot received this call.
     * Uses multiple strategies to handle different phone vendors.
     */
    private fun resolveSimSlot(details: Call.Details, number: String): Int {
        try {
            val subManager = getSystemService(SubscriptionManager::class.java)
            val activeInfos = subManager?.activeSubscriptionInfoList
            val simCount = activeInfos?.size ?: 0

            val accountHandle = details.accountHandle
            val accountId = accountHandle?.id ?: "null"

            lastAccountId = accountId
            lastSimCount = simCount
            lastActiveSlots = activeInfos?.joinToString(",") { "${it.simSlotIndex}(subId=${it.subscriptionId})" } ?: "null"

            writeDiag("SlotDetect|number=$number|accountId=$accountId|activeSims=$simCount|slots=$lastActiveSlots")

            if (activeInfos.isNullOrEmpty()) return 0
            if (activeInfos.size == 1) return activeInfos[0].simSlotIndex

            if (accountHandle != null) {
                try {
                    val numericId = accountId.toInt()
                    val directMatch = activeInfos.find { it.simSlotIndex == numericId }
                    if (directMatch != null) return directMatch.simSlotIndex
                } catch (_: NumberFormatException) { }

                try {
                    val subId = accountId.toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: NumberFormatException) { }

                try {
                    val parts = accountId.split("-")
                    val subId = parts.last().toInt()
                    activeInfos.find { it.subscriptionId == subId }?.let { return it.simSlotIndex }
                } catch (_: Exception) { }

                for (info in activeInfos) {
                    try {
                        val iccid = info.iccId ?: continue
                        if (accountId.contains(iccid)) return info.simSlotIndex
                    } catch (_: Exception) { }
                }

                val matched = activeInfos.find { info ->
                    try { info.subscriptionId.toString() == accountId } catch (_: Exception) { false }
                }
                if (matched != null) return matched.simSlotIndex

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

            try {
                val voiceSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
                if (voiceSubId >= 0) {
                    activeInfos.find { it.subscriptionId == voiceSubId }?.let { return it.simSlotIndex }
                }
            } catch (_: Exception) { }

            activeInfos.find { it.simSlotIndex != 0 }?.let { return it.simSlotIndex }

        } catch (_: Exception) { }
        return 0
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as CallBlockerApp).repository
        writeDiag("--- Service start ---")
    }

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart?.trim() ?: run {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        val simSlot = resolveSimSlot(details, number)
        writeDiag("Resolved|number=$number|slot=$simSlot")

        scope.launch {
            try {
                val config = repository.getSimConfig(simSlot)
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    return@launch
                }

                val normalized = CallBlockerRepository.normalizeNumber(number)

                if (repository.isBlacklisted(normalized, simSlot)) {
                    val blockResponse = CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSilenceCall(true)
                        .setSkipCallLog(false)
                        .build()
                    respondToCall(details, blockResponse)
                    repository.recordCall(normalized, simSlot = simSlot)
                    writeDiag("Action|number=$normalized|slot=$simSlot|action=BLOCK_LIST")
                    return@launch
                }

                if (repository.isWhitelisted(normalized, simSlot)) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, simSlot = simSlot)
                    writeDiag("Action|number=$normalized|slot=$simSlot|action=ALLOW_LIST")
                    return@launch
                }

                if (repository.hasBeenCalledRecently(normalized, simSlot, config.intervalMinutes.toLong())) {
                    respondToCall(details, CallResponse.Builder().build())
                    repository.recordCall(normalized, simSlot = simSlot)
                    writeDiag("Action|number=$normalized|slot=$simSlot|action=INTERVAL_OK(${config.intervalMinutes}m)")
                    return@launch
                }

                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .build()
                respondToCall(details, response)
                repository.recordCall(normalized, simSlot = simSlot)
                writeDiag("Action|number=$normalized|slot=$simSlot|action=BLOCK_DEFAULT")
            } catch (_: Exception) {
                respondToCall(details, CallResponse.Builder().build())
            }
        }
    }

    private fun writeDiag(msg: String) {
        try {
            val dir = File("/sdcard/0/")
            if (!dir.exists()) dir.mkdirs()
            val logFile = File(dir, "callblocker_diag.txt")
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logFile.appendText("[$ts] $msg\n")
        } catch (_: Exception) {
            // silently ignore write failures
        }
    }
}
