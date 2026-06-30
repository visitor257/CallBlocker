package com.callblocker.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
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

        // ---- 全面诊断 ----
        try {
            val sb = StringBuilder()
            sb.appendLine("=== CALL DETAILS DUMP ===")
            sb.appendLine("number=$number")

            val ah = details.accountHandle
            sb.appendLine("accountHandle=$ah")
            if (ah != null) {
                sb.appendLine("ah.id=${ah.id}")
                sb.appendLine("ah.component=${ah.componentName?.className}")
                sb.appendLine("ah.package=${ah.componentName?.packageName}")
            }
            val props = details.callProperties
            sb.appendLine("callProperties=$props")
            sb.appendLine("callCapabilities=${details.callCapabilities}")
            sb.appendLine("callDirection=${details.callDirection}")
            sb.appendLine("state=${details.state}")
            sb.appendLine("videoState=${details.videoState}")

            try {
                val extras = details.extras
                if (extras != null && !extras.isEmpty) {
                    sb.appendLine("extras bundle:")
                    for (k in extras.keySet().sorted()) {
                        val v = extras.get(k)
                        sb.appendLine("  $k = $v")
                    }
                } else {
                    sb.appendLine("extras=null or empty")
                }
            } catch (ex: Exception) {
                sb.appendLine("extras read error: ${ex.message}")
            }

            try {
                val subManager = getSystemService(SubscriptionManager::class.java)
                val activeList = subManager?.activeSubscriptionInfoList
                sb.appendLine("SubscriptionManager.activeList size=${activeList?.size ?: 0}")
                activeList?.forEachIndexed { i, info ->
                    sb.appendLine("  [$i] slot=${info.simSlotIndex} subId=${info.subscriptionId} carrier=${info.carrierName} number=${info.number}")
                }

                for (slotId in 0..3) {
                    try {
                        val info = subManager?.getActiveSubscriptionInfoForSimSlotIndex(slotId)
                        if (info != null) {
                            sb.appendLine("  getActiveSubInfoForSimSlot($slotId): subId=${info.subscriptionId} carrier=${info.carrierName} number=${info.number}")
                        }
                    } catch (_: Exception) {}
                }

                for (subId in 0..3) {
                    try {
                        val info = subManager?.getActiveSubscriptionInfo(subId)
                        if (info != null && info.subscriptionId != 0) {
                            sb.appendLine("  getActiveSubInfo(subId=$subId): slot=${info.simSlotIndex} carrier=${info.carrierName}")
                        }
                    } catch (_: Exception) {}
                }
            } catch (ex: Exception) {
                sb.appendLine("SubscriptionManager error: ${ex.message}")
            }

            try {
                val tm = getSystemService(TelephonyManager::class.java)
                if (tm != null) {
                    sb.appendLine("TelephonyManager:")
                    sb.appendLine("  line1Number=${tm.line1Number}")
                    sb.appendLine("  simOperator=${tm.simOperator}")
                    sb.appendLine("  simSerialNumber=${tm.simSerialNumber}")
                    sb.appendLine("  activeModemCount=${tm.activeModemCount}")
                    sb.appendLine("  phoneCount=${tm.phoneCount}")

                    for (slotId in 0..3) {
                        try {
                            val tm2 = tm.createForSubscriptionId(slotId)
                            val ln = tm2.line1Number
                            val op = tm2.simOperator
                            if ((ln != null && ln.isNotEmpty()) || (op != null && op.isNotEmpty())) {
                                sb.appendLine("  TM(subId=$slotId): line1=$ln operator=$op")
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (ex: Exception) {
                sb.appendLine("TelephonyManager error: ${ex.message}")
            }

            writeDiag(sb.toString())
        } catch (ex: Exception) {
            writeDiag("ERROR during detailed dump: ${ex.message}")
        }

        // ---- 拦截逻辑 ----
        val simSlot = 0 // 等诊断结果再决定
        scope.launch {
            try {
                val config = repository.getSimConfig(simSlot)
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    writeDiag("Slot $simSlot disabled, allow call")
                    return@launch
                }

                val normalized = CallBlockerRepository.normalizeNumber(number)

                if (repository.isBlacklisted(normalized, simSlot)) {
                    doBlock(details, normalized, simSlot, "BLACKLIST")
                    return@launch
                }
                if (repository.isWhitelisted(normalized, simSlot)) {
                    doAllow(details, normalized, simSlot, "WHITELIST")
                    return@launch
                }
                if (repository.hasBeenCalledRecently(normalized, simSlot, config.intervalMinutes.toLong())) {
                    doAllow(details, normalized, simSlot, "INTERVAL_OK(${config.intervalMinutes}m)")
                    return@launch
                }
                doBlock(details, normalized, simSlot, "DEFAULT")
            } catch (ex: Exception) {
                respondToCall(details, CallResponse.Builder().build())
                writeDiag("ERROR in screening: ${ex.message}")
            }
        }
    }

    private fun doAllow(details: Call.Details, number: String, simSlot: Int, reason: String) {
        respondToCall(details, CallResponse.Builder().build())
        scope.launch {
            repository.recordCall(number, simSlot = simSlot)
        }
        writeDiag("ALLOW|number=$number|slot=$simSlot|reason=$reason")
    }

    private fun doBlock(details: Call.Details, number: String, simSlot: Int, reason: String) {
        val response = CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipCallLog(false)
            .build()
        respondToCall(details, response)
        scope.launch {
            repository.recordCall(number, simSlot = simSlot)
        }
        writeDiag("BLOCK|number=$number|slot=$simSlot|reason=$reason")
    }

    private fun writeDiag(msg: String) {
        try {
            val dir = getExternalFilesDir(null)
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs()
                val logFile = File(dir, "callblocker_diag.txt")
                val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
                logFile.appendText("[$ts] $msg\n")
            }
        } catch (_: Exception) {
            // silently ignore write failures
        }
    }
}
