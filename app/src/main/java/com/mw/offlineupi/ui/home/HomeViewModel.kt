package com.mw.offlineupi.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mw.offlineupi.OfflineUpiApp
import com.mw.offlineupi.data.local.entity.TransactionEntity
import com.mw.offlineupi.data.local.entity.UserProfile
import com.mw.offlineupi.service.UssdManager
import kotlinx.coroutines.flow.Flow

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OfflineUpiApp

    val recentTransactions: Flow<List<TransactionEntity>> =
        app.transactionRepository.getRecentTransactions(5)

    val userProfile: Flow<UserProfile?> =
        app.database.userProfileDao().getProfile()

    fun isAccessibilityEnabled(): Boolean =
        UssdManager.isAccessibilityEnabled(app)
}
