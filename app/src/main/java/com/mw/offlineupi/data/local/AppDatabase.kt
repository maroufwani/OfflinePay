package com.mw.offlineupi.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.mw.offlineupi.data.preferences.SecurePrefs
import com.mw.offlineupi.data.local.dao.RecipientDao
import com.mw.offlineupi.data.local.dao.TransactionDao
import com.mw.offlineupi.data.local.dao.UserProfileDao
import com.mw.offlineupi.data.local.entity.RecipientEntity
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.data.local.entity.UserProfile
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

@Database(
    entities = [
        UserProfile::class,
        RecipientEntity::class,
        TransactionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun recipientDao(): RecipientDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Explicit migrations, applied in order by Room.
         *
         * Empty at version 1 — the schema JSON in `app/schemas` is the baseline. Every future
         * `version` bump MUST add a [androidx.room.migration.Migration] here. There is
         * deliberately no `fallbackToDestructiveMigration()`: on a shipped beta that would
         * silently delete users' transaction history rather than failing loudly in review.
         */
        private val MIGRATIONS: Array<Migration> = emptyArray()

        private const val PREFS_NAME = "db_key_prefs"
        private const val KEY_PASSPHRASE = "db_passphrase"
        private const val KEY_LENGTH_BYTES = 32

        /**
         * Returns the database passphrase as a raw 32-byte array.
         *
         * On first run, 32 cryptographically random bytes are generated via
         * [SecureRandom], Base64-encoded, and stored in EncryptedSharedPreferences
         * (AES256-GCM, protected by the Android Keystore master key).
         *
         * The raw bytes are returned directly to SQLCipher so that the key material
         * never passes through the JVM string pool. SQLCipher zeroes the array itself
         * once the database is opened — see the note in [getInstance] for why the caller
         * must not do it.
         */
        private fun getPassphrase(context: Context): ByteArray {
            val prefs = SecurePrefs.openStandard(context, PREFS_NAME)
            val stored = prefs.getString(KEY_PASSPHRASE, null)
            if (stored != null) {
                // Migration: legacy keys were stored as raw UUID strings (e.g. "550e8400-e29b-…").
                // Detect by the presence of hyphens, which are not valid Base64 characters.
                if (stored.contains('-')) {
                    // Old format — derive bytes the same way the original code did, so the
                    // existing encrypted database remains openable.  Re-store as Base64 so
                    // future reads take the new path.
                    val keyBytes = stored.toByteArray()
                    prefs.edit()
                        .putString(KEY_PASSPHRASE, Base64.encodeToString(keyBytes, Base64.NO_WRAP))
                        .apply()
                    return keyBytes
                }
                return Base64.decode(stored, Base64.NO_WRAP)
            }
            // Generate 256 bits of cryptographically random key material
            val keyBytes = ByteArray(KEY_LENGTH_BYTES)
            SecureRandom().nextBytes(keyBytes)
            prefs.edit().putString(KEY_PASSPHRASE, Base64.encodeToString(keyBytes, Base64.NO_WRAP)).apply()
            return keyBytes
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    System.loadLibrary("sqlcipher")
                    // NOTE: do NOT zero this array here.
                    //
                    // Room.build() does not open the database — SQLite open is deferred to the
                    // first query, and SupportOpenHelperFactory holds the array *by reference*.
                    // Zeroing it in a finally block therefore risks keying the database with 32
                    // zero bytes (silently, since a DB created and read with a zero key works
                    // perfectly). sqlcipher-android defaults clearPassphrase = true and zeroes
                    // the array itself once the database is actually opened, so the manual wipe
                    // was either redundant or destructive, never useful.
                    val passphrase = getPassphrase(context)
                    val factory = SupportOpenHelperFactory(passphrase)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "offline_pay.db"
                    )
                        .openHelperFactory(factory)
                        .addMigrations(*MIGRATIONS)
                        .build()
                        .also { INSTANCE = it }
                }
            }
        }
    }
}
