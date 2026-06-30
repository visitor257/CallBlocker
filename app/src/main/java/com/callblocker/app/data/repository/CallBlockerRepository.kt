package com.callblocker.app.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.callblocker.app.data.dao.BlacklistDao
import com.callblocker.app.data.dao.CallRecordDao
import com.callblocker.app.data.dao.SimConfigDao
import com.callblocker.app.data.dao.WhitelistDao
import com.callblocker.app.data.entity.BlockedCallRecord
import com.callblocker.app.data.entity.BlockedNumber
import com.callblocker.app.data.entity.SimConfig
import com.callblocker.app.data.entity.WhitelistEntry
import kotlinx.coroutines.flow.Flow

class CallBlockerRepository(
    private val whitelistDao: WhitelistDao,
    private val blacklistDao: BlacklistDao,
    private val callRecordDao: CallRecordDao,
    private val simConfigDao: SimConfigDao
) {
    // --- Per-SIM whitelist ---
    fun getWhitelistBySim(simSlot: Int): Flow<List<WhitelistEntry>> = whitelistDao.getBySim(simSlot)

    suspend fun isWhitelisted(number: String, simSlot: Int = 0): Boolean {
        return whitelistDao.countByNumber(normalizeNumber(number), simSlot) > 0
    }

    suspend fun addToWhitelist(number: String, name: String? = null, source: String = "MANUAL", simSlot: Int = 0) {
        val normalized = normalizeNumber(number)
        // Conflict prevention: remove from blacklist first
        blacklistDao.deleteByNumber(normalized, simSlot)
        whitelistDao.insert(WhitelistEntry(
            phoneNumber = normalized,
            displayName = name,
            source = source,
            simSlot = simSlot
        ))
    }

    suspend fun removeFromWhitelist(id: Long) {
        whitelistDao.deleteById(id)
    }

    suspend fun importFromContacts(context: Context, simSlot: Int = 0): Int {
        val existing = whitelistDao.getBySimList(simSlot)
        val existingNumbers = existing.map { normalizeNumber(it.phoneNumber) }.toSet()
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            ),
            null, null, null
        ) ?: return 0

        val toInsert = mutableListOf<WhitelistEntry>()
        while (cursor.moveToNext()) {
            val number = cursor.getString(0)?.trim() ?: continue
            val name = cursor.getString(1)?.trim()
            val normalized = normalizeNumber(number)
            if (normalized.isNotBlank() && normalized !in existingNumbers) {
                toInsert.add(WhitelistEntry(
                    phoneNumber = normalized,
                    displayName = name,
                    source = "CONTACT",
                    simSlot = simSlot
                ))
            }
        }
        cursor.close()
        if (toInsert.isNotEmpty()) whitelistDao.insertAll(toInsert)
        return toInsert.size
    }

    suspend fun copyWhitelist(fromSim: Int, toSim: Int): Int {
        val entries = whitelistDao.getBySimList(fromSim)
        val target = whitelistDao.getBySimList(toSim)
        val targetNumbers = target.map { normalizeNumber(it.phoneNumber) }.toSet()
        val toCopy = entries.filter { normalizeNumber(it.phoneNumber) !in targetNumbers }.map {
            it.copy(id = 0, simSlot = toSim)
        }
        if (toCopy.isNotEmpty()) whitelistDao.insertAll(toCopy)
        return toCopy.size
    }

    // --- Per-SIM blacklist ---
    fun getBlacklistBySim(simSlot: Int): Flow<List<BlockedNumber>> = blacklistDao.getBySim(simSlot)

    suspend fun isBlacklisted(number: String, simSlot: Int = 0): Boolean {
        return blacklistDao.countByNumber(normalizeNumber(number), simSlot) > 0
    }

    suspend fun addToBlacklist(number: String, name: String? = null, source: String = "MANUAL", simSlot: Int = 0) {
        val normalized = normalizeNumber(number)
        // Conflict prevention: remove from whitelist first
        whitelistDao.deleteByNumber(normalized, simSlot)
        blacklistDao.insert(BlockedNumber(
            phoneNumber = normalized,
            displayName = name,
            source = source,
            simSlot = simSlot
        ))
    }

    suspend fun removeFromBlacklist(id: Long) {
        blacklistDao.deleteById(id)
    }

    suspend fun copyBlacklist(fromSim: Int, toSim: Int): Int {
        val entries = blacklistDao.getBySimList(fromSim)
        val target = blacklistDao.getBySimList(toSim)
        val targetNumbers = target.map { normalizeNumber(it.phoneNumber) }.toSet()
        val toCopy = entries.filter { normalizeNumber(it.phoneNumber) !in targetNumbers }.map {
            it.copy(id = 0, simSlot = toSim)
        }
        if (toCopy.isNotEmpty()) blacklistDao.insertAll(toCopy)
        return toCopy.size
    }

    // --- Per-SIM blocked calls ---
    fun getBlockedCallsBySim(simSlot: Int): Flow<List<BlockedCallRecord>> = callRecordDao.getBySim(simSlot)

    suspend fun hasBeenCalledRecently(number: String, simSlot: Int, intervalMinutes: Long): Boolean {
        val since = System.currentTimeMillis() - (intervalMinutes * 60 * 1000)
        return callRecordDao.countByNumberSince(normalizeNumber(number), simSlot, since) > 0
    }

    suspend fun recordCall(number: String, name: String? = null, simSlot: Int = 0) {
        callRecordDao.insert(BlockedCallRecord(
            phoneNumber = normalizeNumber(number),
            displayName = name,
            simSlot = simSlot
        ))
    }

    suspend fun clearBlockedLogs() = callRecordDao.clearAll()
    suspend fun clearBlockedLogsBySim(simSlot: Int) = callRecordDao.clearBySim(simSlot)

    // --- Per-SIM config ---
    val allSimConfigs: Flow<List<SimConfig>> = simConfigDao.getAll()

    suspend fun getEnabledSimConfigs(): List<SimConfig> = simConfigDao.getAllList().filter { it.enabled }

    suspend fun getSimConfig(simSlot: Int): SimConfig {
        return simConfigDao.get(simSlot) ?: SimConfig(simSlot)
    }

    suspend fun saveSimConfig(config: SimConfig) {
        simConfigDao.insert(config)
    }

    suspend fun copySimConfig(fromSim: Int, toSim: Int) {
        val from = getSimConfig(fromSim)
        simConfigDao.insert(from.copy(simSlot = toSim))
    }

    // --- Legacy full lists (for backward compat) ---
    val allWhitelist: Flow<List<WhitelistEntry>> = whitelistDao.getAll()
    val allBlockedCalls: Flow<List<BlockedCallRecord>> = callRecordDao.getAll()
    val allSimConfigsList: Flow<List<SimConfig>> = simConfigDao.getAll()

    companion object {
        fun normalizeNumber(number: String): String {
            val cleaned = number.replace(" ", "").replace("-", "")
                .replace("(", "").replace(")", "")
                .replace(Regex("[^\\d+]+"), "")
            return when {
                cleaned.startsWith("+86") -> cleaned.substring(3)  // +8613800138000 → 13800138000
                cleaned.startsWith("0086") -> cleaned.substring(4) // 008613800138000 → 13800138000
                cleaned.startsWith("00") -> "+" + cleaned.substring(2) // 00852... → +852...
                else -> cleaned // 13800138000 or +85212345678 — keep as is
            }
        }
    }
}
