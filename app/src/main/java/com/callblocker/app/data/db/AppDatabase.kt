package com.callblocker.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.callblocker.app.data.dao.BlacklistDao
import com.callblocker.app.data.dao.CallRecordDao
import com.callblocker.app.data.dao.SimConfigDao
import com.callblocker.app.data.dao.WhitelistDao
import com.callblocker.app.data.entity.BlockedCallRecord
import com.callblocker.app.data.entity.BlockedNumber
import com.callblocker.app.data.entity.SimConfig
import com.callblocker.app.data.entity.WhitelistEntry

@Database(
    entities = [WhitelistEntry::class, BlockedCallRecord::class, SimConfig::class, BlockedNumber::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun whitelistDao(): WhitelistDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun callRecordDao(): CallRecordDao
    abstract fun simConfigDao(): SimConfigDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE whitelist ADD COLUMN simSlot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE blocked_calls ADD COLUMN simSlot INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""CREATE TABLE IF NOT EXISTS sim_config (
                    simSlot INTEGER PRIMARY KEY NOT NULL,
                    displayName TEXT NOT NULL,
                    intervalMinutes INTEGER NOT NULL DEFAULT 15,
                    enabled INTEGER NOT NULL DEFAULT 1
                )""")
                db.execSQL("INSERT OR IGNORE INTO sim_config (simSlot, displayName, intervalMinutes, enabled) VALUES (0, '卡1', 15, 1)")
                db.execSQL("INSERT OR IGNORE INTO sim_config (simSlot, displayName, intervalMinutes, enabled) VALUES (1, '卡2', 15, 1)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS blacklist (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    phoneNumber TEXT NOT NULL,
                    displayName TEXT,
                    source TEXT NOT NULL DEFAULT 'MANUAL',
                    simSlot INTEGER NOT NULL DEFAULT 0
                )""")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_blocker_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
