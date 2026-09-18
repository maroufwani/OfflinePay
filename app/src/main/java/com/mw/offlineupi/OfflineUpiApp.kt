package com.mw.offlineupi

import android.app.Application
import com.mw.offlineupi.data.local.AppDatabase
import com.mw.offlineupi.data.preferences.AppPreferences
import com.mw.offlineupi.data.repository.RecipientRepository
import com.mw.offlineupi.data.repository.TransactionRepository
import com.mw.offlineupi.util.CrashLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class OfflineUpiApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var transactionRepository: TransactionRepository
        private set
    lateinit var recipientRepository: RecipientRepository
        private set

    /**
     * Application-lifetime scope. `SupervisorJob` so one failed startup task cannot cancel the
     * others, and never cancelled — it is intended to live as long as the process.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Installed before anything that can fail, so a crash inside the startup path below is
        // still recorded. Sideloaded testers have no adb and release builds are minified, so
        // this file is the only way a stack trace survives the process dying.
        CrashLog.install(this)
        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)
        transactionRepository = TransactionRepository(database.transactionDao())
        recipientRepository = RecipientRepository(database.recipientDao())
        // The encrypted preference flows start empty and are seeded here rather than from field
        // initialisers, so opening the Keystore-backed file never happens on the main thread.
        // Anything reading phoneNumber / lastBalance sees "" for the first few milliseconds after
        // process start, which is the same thing it would see on a genuinely empty install.
        appScope.launch { preferences.loadEncryptedValues() }
    }
}
