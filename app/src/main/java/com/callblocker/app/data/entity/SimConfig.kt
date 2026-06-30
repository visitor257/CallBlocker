package com.callblocker.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sim_config")
data class SimConfig(
    @PrimaryKey val simSlot: Int,
    val displayName: String = "卡${simSlot + 1}",
    val intervalMinutes: Int = 15,
    val enabled: Boolean = true
)
