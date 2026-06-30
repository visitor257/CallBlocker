package com.callblocker.app

import android.app.Application
import com.callblocker.app.data.db.AppDatabase
import com.callblocker.app.data.repository.CallBlockerRepository

class CallBlockerApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: CallBlockerRepository by lazy {
        CallBlockerRepository(
            database.whitelistDao(),
            database.blacklistDao(),
            database.callRecordDao(),
            database.simConfigDao()
        )
    }
    companion object {
        lateinit var instance: CallBlockerApp
            private set
    }
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
