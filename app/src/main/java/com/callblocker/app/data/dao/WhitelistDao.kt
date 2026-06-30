package com.callblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.callblocker.app.data.entity.WhitelistEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistDao {
    @Query("SELECT * FROM whitelist WHERE simSlot = :simSlot ORDER BY displayName ASC, phoneNumber ASC")
    fun getBySim(simSlot: Int): Flow<List<WhitelistEntry>>

    @Query("SELECT * FROM whitelist WHERE simSlot = :simSlot ORDER BY displayName ASC, phoneNumber ASC")
    suspend fun getBySimList(simSlot: Int): List<WhitelistEntry>

    @Query("SELECT * FROM whitelist ORDER BY displayName ASC, phoneNumber ASC")
    fun getAll(): Flow<List<WhitelistEntry>>

    @Query("SELECT * FROM whitelist ORDER BY displayName ASC, phoneNumber ASC")
    suspend fun getAllList(): List<WhitelistEntry>

    @Query("SELECT COUNT(*) FROM whitelist WHERE phoneNumber = :number AND simSlot = :simSlot")
    suspend fun countByNumber(number: String, simSlot: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WhitelistEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WhitelistEntry>)

    @Delete
    suspend fun delete(entry: WhitelistEntry)

    @Query("DELETE FROM whitelist WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM whitelist WHERE phoneNumber = :number AND simSlot = :simSlot")
    suspend fun deleteByNumber(number: String, simSlot: Int)

    @Query("DELETE FROM whitelist WHERE simSlot = :simSlot")
    suspend fun clearBySim(simSlot: Int)

    @Query("DELETE FROM whitelist")
    suspend fun clearAll()
}
