package com.mw.offlineupi.ui.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object ScanPay : Screen("scan_pay")
    object PayMobile : Screen("pay_mobile")
    object UpiId : Screen("upi_id")
    object RequestMoney : Screen("request_money")
    object BalanceCheck : Screen("balance_check")
    object TransactionHistory : Screen("transaction_history")
    object TransactionDetail : Screen("transaction_detail/{transactionId}") {
        fun createRoute(transactionId: Long) = "transaction_detail/$transactionId"
    }
    object Settings : Screen("settings")
}
