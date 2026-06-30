package com.callblocker.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_calls")
data class BlockedCallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,
    val displayName: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val simSlot: Int = 0
)
