package com.mw.offlineupi.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mw.offlineupi.data.local.dao.RecipientDao
import com.mw.offlineupi.data.local.dao.TransactionDao
import com.mw.offlineupi.data.local.dao.UserProfileDao
import com.mw.offlineupi.data.local.entity.RecipientEntity
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.data.local.entity.UserProfile
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        UserProfile::class,
        RecipientEntity::class,
        TransactionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun recipientDao(): RecipientDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun getPassphrase(context: Context): ByteArray {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            val prefs = EncryptedSharedPreferences.create(
                "db_key_prefs",
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val key = prefs.getString("db_passphrase", null)
            if (key != null) return key.toByteArray()
            val newKey = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", newKey).apply()
            return newKey.toByteArray()
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    System.loadLibrary("sqlcipher")
                    val passphrase = getPassphrase(context)
                    val factory = SupportOpenHelperFactory(passphrase)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "offline_pay.db"
                    )
                        .openHelperFactory(factory)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
