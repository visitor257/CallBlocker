package com.callblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.callblocker.app.data.entity.BlockedNumber
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist WHERE simSlot = :simSlot ORDER BY displayName ASC, phoneNumber ASC")
    fun getBySim(simSlot: Int): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blacklist WHERE simSlot = :simSlot ORDER BY displayName ASC, phoneNumber ASC")
    suspend fun getBySimList(simSlot: Int): List<BlockedNumber>

    @Query("SELECT * FROM blacklist ORDER BY displayName ASC, phoneNumber ASC")
    fun getAll(): Flow<List<BlockedNumber>>

    @Query("SELECT * FROM blacklist ORDER BY displayName ASC, phoneNumber ASC")
    suspend fun getAllList(): List<BlockedNumber>

    @Query("SELECT COUNT(*) FROM blacklist WHERE phoneNumber = :number AND simSlot = :simSlot")
    suspend fun countByNumber(number: String, simSlot: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlockedNumber)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<BlockedNumber>)

    @Delete
    suspend fun delete(entry: BlockedNumber)

    @Query("DELETE FROM blacklist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM blacklist WHERE phoneNumber = :number AND simSlot = :simSlot")
    suspend fun deleteByNumber(number: String, simSlot: Int)

    @Query("DELETE FROM blacklist WHERE simSlot = :simSlot")
    suspend fun clearBySim(simSlot: Int)

    @Query("DELETE FROM blacklist")
    suspend fun clearAll()
}
