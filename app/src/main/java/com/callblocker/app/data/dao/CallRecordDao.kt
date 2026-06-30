package com.callblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.callblocker.app.data.entity.BlockedCallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CallRecordDao {
    @Query("SELECT * FROM blocked_calls WHERE simSlot = :simSlot ORDER BY timestamp DESC")
    fun getBySim(simSlot: Int): Flow<List<BlockedCallRecord>>

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BlockedCallRecord>>

    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BlockedCallRecord>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE phoneNumber = :number AND simSlot = :simSlot AND timestamp > :since")
    suspend fun countByNumberSince(number: String, simSlot: Int, since: Long): Int

    @Insert
    suspend fun insert(record: BlockedCallRecord)

    @Query("DELETE FROM blocked_calls WHERE simSlot = :simSlot")
    suspend fun clearBySim(simSlot: Int)

    @Query("DELETE FROM blocked_calls")
    suspend fun clearAll()

    @Query("DELETE FROM blocked_calls WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM blocked_calls WHERE simSlot = :simSlot AND timestamp < :before")
    suspend fun deleteOlderThanBySim(simSlot: Int, before: Long)
}
