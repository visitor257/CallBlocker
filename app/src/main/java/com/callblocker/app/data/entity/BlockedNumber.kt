package com.callblocker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blacklist")
data class BlockedNumber(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String? = null,
    val source: String = "MANUAL",
    val simSlot: Int = 0
)
