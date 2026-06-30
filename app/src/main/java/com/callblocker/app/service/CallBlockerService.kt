package com.callblocker.app.service

import android.Manifest
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
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

    /** SubscriptionManager-based SIM info (reliable with READ_PHONE_STATE) */
    private data class SimInfo(val slotIndex: Int, val subId: Int, val carrier: String?, val number: String?)
    private val activeSims: List<SimInfo> by lazy {
        try {
            val subMgr = getSystemService(SubscriptionManager::class.java) ?: return@lazy emptyList()
            subMgr.activeSubscriptionInfoList?.mapNotNull { info ->
                try {
                    SimInfo(
                        slotIndex = info.simSlotIndex,
                        subId = info.subscriptionId,
                        carrier = info.carrierName?.toString(),
                        number = info.number?.trim()
                    )
                } catch (_: Exception) { null }
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    override fun onCreate() {
        super.onCreate()
        repository = (application as CallBlockerApp).repository
        writeDiag("--- Service start ---")
        writeDiag("READ_PHONE_STATE=${hasPermission(Manifest.permission.READ_PHONE_STATE)}")

        // Dump all SIM info from SubscriptionManager (now with permission)
        val sims = activeSims
        if (sims.isNotEmpty()) {
            writeDiag("Active SIMs (from SubMgr):")
            sims.forEach { s ->
                writeDiag("  slot=${s.slotIndex} subId=${s.subId} carrier=${s.carrier} number=${s.number}")
            }
        } else {
            writeDiag("Active SIMs: none from SubMgr")
        }

        // Try TelecomManager phone accounts
        try {
            val tm = getSystemService(TelecomManager::class.java)
            if (tm != null) {
                val accounts = tm.callCapablePhoneAccounts
                if (accounts.isNotEmpty()) {
                    writeDiag("TelecomManager callCapablePhoneAccounts:")
                    accounts.forEach { acc ->
                        val pa = tm.getPhoneAccount(acc)
                        writeDiag("  cmp=${acc.componentName?.className} id=${acc.id}")
                    }
                } else {
                    writeDiag("TelecomManager accounts: empty")
                }
            } else {
                writeDiag("TelecomManager: null")
            }
        } catch (ex: Exception) {
            writeDiag("TelecomManager error: ${ex.message}")
        }

        // Try TelephonyManager SIM numbers
        try {
            val tm = getSystemService(TelephonyManager::class.java)
            if (tm != null) {
                writeDiag("TelephonyManager.phoneCount=${tm.phoneCount}")
                writeDiag("TelephonyManager.activeModemCount=${tm.activeModemCount}")
                // simCount not available on minSdk 24
                for (slotId in 0 until 4) {
                    try {
                        val tm2 = tm.createForSubscriptionId(slotId)
                        writeDiag("  TM(subId=$slotId): line1=${tm2.line1Number} op=${tm2.simOperator}")
                    } catch (_: Exception) {}
                }
            }
        } catch (ex: Exception) {
            writeDiag("TelephonyManager error: ${ex.message}")
        }

        // static default subIds
        try {
            writeDiag("Default subIds: data=${SubscriptionManager.getDefaultDataSubscriptionId()} voice=${SubscriptionManager.getDefaultVoiceSubscriptionId()} sms=${SubscriptionManager.getDefaultSmsSubscriptionId()}")
        } catch (_: Exception) {}
    }

    private fun hasPermission(perm: String): Boolean {
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
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

        // Always fallback to slot 0 since we can't detect incoming SIM on this phone
        val simSlot = 0
        writeDiag("Using slot=$simSlot for number=$number (detection unsupported)")

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
