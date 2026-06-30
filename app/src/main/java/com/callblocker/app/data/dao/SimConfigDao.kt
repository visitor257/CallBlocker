package com.callblocker.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.callblocker.app.data.entity.SimConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface SimConfigDao {
    @Query("SELECT * FROM sim_config ORDER BY simSlot ASC")
    fun getAll(): Flow<List<SimConfig>>

    @Query("SELECT * FROM sim_config WHERE simSlot = :simSlot")
    suspend fun get(simSlot: Int): SimConfig?

    @Query("SELECT * FROM sim_config ORDER BY simSlot ASC")
    suspend fun getAllList(): List<SimConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: SimConfig)

    @Query("DELETE FROM sim_config")
    suspend fun clearAll()

    @Query("DELETE FROM sim_config WHERE simSlot = :simSlot")
    suspend fun delete(simSlot: Int)
}
