package com.mw.offlineupi

import android.app.Application
import com.mw.offlineupi.data.local.AppDatabase
import com.mw.offlineupi.data.preferences.AppPreferences
import com.mw.offlineupi.data.repository.RecipientRepository
import com.mw.offlineupi.data.repository.TransactionRepository

class OfflineUpiApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var transactionRepository: TransactionRepository
        private set
    lateinit var recipientRepository: RecipientRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        preferences = AppPreferences(this)
        transactionRepository = TransactionRepository(database.transactionDao())
        recipientRepository = RecipientRepository(database.recipientDao())
    }
}
