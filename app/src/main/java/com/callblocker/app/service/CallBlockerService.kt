package com.callblocker.app.service

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
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

    /** Read each SIM's phone number via TelephonyManager (needs READ_PHONE_STATE) */
    private val simPhoneNumbers: List<String> by lazy {
        try {
            val tm = getSystemService(TelephonyManager::class.java) ?: return@lazy emptyList()
            val count = tm.activeModemCount.coerceAtMost(4)
            (0 until count).mapNotNull { slotId ->
                try {
                    val tm2 = tm.createForSubscriptionId(slotId)
                    val num = tm2.line1Number?.trim()
                    if (num.isNullOrEmpty()) null else num
                } catch (_: Exception) { null }
            }
        } catch (_: Exception) { emptyList() }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as CallBlockerApp).repository
        writeDiag("--- Service start ---")
        writeDiag("READ_PHONE_STATE=${hasPermission(Manifest.permission.READ_PHONE_STATE)}")
        val nums = simPhoneNumbers
        if (nums.isNotEmpty()) {
            writeDiag("SIM numbers: ${nums.joinToString(", ")}")
        }
    }

    private fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun getSimSlot(number: String): Int {
        // Strategy 1: Call.Details extras (failed on this phone)

        // Strategy 2: SubscriptionManager
        try {
            val subManager = getSystemService(SubscriptionManager::class.java)
            val activeList = subManager?.activeSubscriptionInfoList
            if (!activeList.isNullOrEmpty()) {
                // Try direct slot queries
                for (slotId in 0 until activeList.size) {
                    val info = subManager.getActiveSubscriptionInfoForSimSlotIndex(slotId)
                    if (info != null) {
                        writeDiag("Slot detect via SubMgr: slot=$slotId subId=${info.subscriptionId}")
                    }
                }
                if (activeList.size == 1) return activeList[0].simSlotIndex
                // If we get here with multiple active subs, we still don't know which one
                // But we can try to find by carrier name or number if available
                return 0
            }
        } catch (_: Exception) {}

        // Strategy 3: Check if READ_PHONE_STATE is available and try TelephonyManager
        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            try {
                val tm = getSystemService(TelephonyManager::class.java)
                if (tm != null) {
                    val count = tm.activeModemCount
                    writeDiag("TelephonyManager.activeModemCount=$count")
                    for (slotId in 0 until count.coerceAtMost(4)) {
                        try {
                            val tm2 = tm.createForSubscriptionId(slotId)
                            writeDiag("  TM(subId=$slotId): op=${tm2.simOperator} line1=${tm2.line1Number}")
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        // Strategy 4: try static SubscriptionManager methods
        try {
            val defaultData = SubscriptionManager.getDefaultDataSubscriptionId()
            val defaultVoice = SubscriptionManager.getDefaultVoiceSubscriptionId()
            val defaultSms = SubscriptionManager.getDefaultSmsSubscriptionId()
            writeDiag("Default subIds: data=$defaultData voice=$defaultVoice sms=$defaultSms")
        } catch (_: Exception) {}

        return 0 // fallback
    }

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart?.trim() ?: run {
            respondToCall(details, CallResponse.Builder().build())
            return
        }

        // ---- 诊断 ----
        try {
            val sb = StringBuilder()
            sb.appendLine("=== CALL DETAILS ===")
            sb.appendLine("number=$number")
            val ah = details.accountHandle
            sb.appendLine("accountHandle=$ah")
            if (ah != null) {
                sb.appendLine("ah.id=${ah.id}")
                sb.appendLine("ah.component=${ah.componentName?.className}")
                sb.appendLine("ah.package=${ah.componentName?.packageName}")
            }
            sb.appendLine("callProperties=${details.callProperties}")
            sb.appendLine("callCapabilities=${details.callCapabilities}")
            sb.appendLine("callDirection=${details.callDirection}")
            try { sb.appendLine("state=${details.state}") } catch (_: Exception) {}
            try { sb.appendLine("videoState=${details.videoState}") } catch (_: Exception) {}
            val extras = details.extras
            if (extras != null && !extras.isEmpty) {
                sb.appendLine("extras:")
                for (k in extras.keySet().sorted()) {
                    sb.appendLine("  $k = ${extras.get(k)}")
                }
            } else {
                sb.appendLine("extras=null")
            }
            writeDiag(sb.toString())
        } catch (_: Exception) {}

        val simSlot = getSimSlot(number)
        writeDiag("Resolved slot=$simSlot for number=$number")

        scope.launch {
            try {
                val config = repository.getSimConfig(simSlot)
                if (!config.enabled) {
                    respondToCall(details, CallResponse.Builder().build())
                    writeDiag("Slot $simSlot disabled, allow")
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
                writeDiag("ERROR: ${ex.message}")
            }
        }
    }

    private fun doAllow(details: Call.Details, number: String, simSlot: Int, reason: String) {
        respondToCall(details, CallResponse.Builder().build())
        scope.launch { repository.recordCall(number, simSlot = simSlot) }
        writeDiag("ALLOW|number=$number|slot=$simSlot|reason=$reason")
    }

    private fun doBlock(details: Call.Details, number: String, simSlot: Int, reason: String) {
        val response = CallResponse.Builder()
            .setDisallowCall(true).setRejectCall(true)
            .setSilenceCall(true).setSkipCallLog(false).build()
        respondToCall(details, response)
        scope.launch { repository.recordCall(number, simSlot = simSlot) }
        writeDiag("BLOCK|number=$number|slot=$simSlot|reason=$reason")
    }

    private fun writeDiag(msg: String) {
        try {
            val dir = getExternalFilesDir(null) ?: return
            if (!dir.exists()) dir.mkdirs()
            val logFile = File(dir, "callblocker_diag.txt")
            val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logFile.appendText("[$ts] $msg\n")
        } catch (_: Exception) {}
    }
}
