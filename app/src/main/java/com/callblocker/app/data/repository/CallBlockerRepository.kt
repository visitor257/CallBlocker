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
    // === Whitelist (global, always simSlot=0) ===

    val allWhitelist: Flow<List<WhitelistEntry>> = whitelistDao.getAll()

    suspend fun isWhitelisted(number: String): Boolean {
        return whitelistDao.countByNumber(normalizeNumber(number), 0) > 0
    }

    suspend fun addToWhitelist(number: String, name: String? = null, source: String = "MANUAL") {
        val normalized = normalizeNumber(number)
        // Conflict prevention: remove from blacklist first
        blacklistDao.deleteByNumber(normalized, 0)
        whitelistDao.insert(WhitelistEntry(
            phoneNumber = normalized, displayName = name, source = source, simSlot = 0
        ))
    }

    suspend fun removeFromWhitelist(id: Long) = whitelistDao.deleteById(id)

    suspend fun importFromContacts(context: Context): Int {
        val existing = whitelistDao.getAllList()
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
                    phoneNumber = normalized, displayName = name, source = "CONTACT", simSlot = 0
                ))
            }
        }
        cursor.close()
        if (toInsert.isNotEmpty()) whitelistDao.insertAll(toInsert)
        return toInsert.size
    }

    // === Blacklist (global, always simSlot=0) ===

    val allBlacklist: Flow<List<BlockedNumber>> = blacklistDao.getAll()

    suspend fun isBlacklisted(number: String): Boolean {
        return blacklistDao.countByNumber(normalizeNumber(number), 0) > 0
    }

    suspend fun addToBlacklist(number: String, name: String? = null, source: String = "MANUAL") {
        val normalized = normalizeNumber(number)
        // Conflict prevention: remove from whitelist first
        whitelistDao.deleteByNumber(normalized, 0)
        blacklistDao.insert(BlockedNumber(
            phoneNumber = normalized, displayName = name, source = source, simSlot = 0
        ))
    }

    suspend fun removeFromBlacklist(id: Long) = blacklistDao.deleteById(id)

    // === Call records (global, always simSlot=0) ===

    val allBlockedCalls: Flow<List<BlockedCallRecord>> = callRecordDao.getAll()

    suspend fun hasBeenCalledRecently(number: String, intervalMinutes: Long): Boolean {
        val since = System.currentTimeMillis() - (intervalMinutes * 60 * 1000)
        return callRecordDao.countByNumberSinceAnySlot(normalizeNumber(number), since) > 0
    }

    suspend fun recordCall(number: String, name: String? = null) {
        callRecordDao.insert(BlockedCallRecord(
            phoneNumber = normalizeNumber(number), displayName = name, simSlot = 0
        ))
    }

    suspend fun clearBlockedLogs() = callRecordDao.clearAll()

    // === Config (single global config, simSlot=0) ===

    val globalConfig: Flow<List<SimConfig>> = simConfigDao.getAll()

    suspend fun getConfig(): SimConfig {
        return simConfigDao.get(0) ?: SimConfig(0)
    }

    suspend fun saveConfig(config: SimConfig) {
        simConfigDao.insert(config)
    }

    companion object {
        fun normalizeNumber(number: String): String {
            val cleaned = number.replace(" ", "").replace("-", "")
                .replace("(", "").replace(")", "")
                .replace(Regex("[^\\d+]+"), "")
            return when {
                cleaned.startsWith("+86") -> cleaned.substring(3)
                cleaned.startsWith("0086") -> cleaned.substring(4)
                cleaned.startsWith("00") -> "+" + cleaned.substring(2)
                else -> cleaned
            }
        }
    }
}
