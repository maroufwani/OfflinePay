package com.mw.offlineupi.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mw.offlineupi.ui.balance.BalanceCheckScreen
import com.mw.offlineupi.ui.history.TransactionDetailScreen
import com.mw.offlineupi.ui.history.TransactionHistoryScreen
import com.mw.offlineupi.ui.home.HomeScreen
import com.mw.offlineupi.ui.onboarding.OnboardingScreen
import com.mw.offlineupi.ui.paymobile.PayMobileScreen
import com.mw.offlineupi.ui.requestmoney.RequestMoneyScreen
import com.mw.offlineupi.ui.scanpay.ScanPayScreen
import com.mw.offlineupi.ui.settings.SettingsScreen
import com.mw.offlineupi.ui.upiid.UpiIdScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onOnboardingComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) }
            )
        }
        composable(Screen.ScanPay.route) {
            ScanPayScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.PayMobile.route) {
            PayMobileScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.UpiId.route) {
            UpiIdScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.RequestMoney.route) {
            RequestMoneyScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.BalanceCheck.route) {
            BalanceCheckScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.TransactionHistory.route) {
            TransactionHistoryScreen(
                onBack = { navController.popBackStack() },
                onTransactionClick = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                }
            )
        }
        composable(
            Screen.TransactionDetail.route,
            arguments = listOf(navArgument("transactionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: 0L
            TransactionDetailScreen(
                transactionId = transactionId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
