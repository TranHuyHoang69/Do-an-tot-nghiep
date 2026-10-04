package com.example.moneymatev2.navigation

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.presentation.home.HomePeriod

sealed class Screen(val route: String){
    object Login: Screen("login")
    object Register: Screen("register")
    object Home: Screen("home")
    object AddTransaction: Screen("add_transaction")
    object History : Screen(
        "history?${HomeNavKeys.SELECTED_PERIOD}={${HomeNavKeys.SELECTED_PERIOD}}" +
                "&${HomeNavKeys.ANCHOR_DATE}={${HomeNavKeys.ANCHOR_DATE}}" +
                "&${HomeNavKeys.SELECTED_TYPE}={${HomeNavKeys.SELECTED_TYPE}}" +
                "&${HomeNavKeys.CUSTOM_END}={${HomeNavKeys.CUSTOM_END}}"
    ) {
        fun createRoute(period: String, anchorDate: Long, type: String, customEnd: Long = -1L) =
            "history?${HomeNavKeys.SELECTED_PERIOD}=$period&${HomeNavKeys.ANCHOR_DATE}=$anchorDate" +
                    "&${HomeNavKeys.SELECTED_TYPE}=$type&${HomeNavKeys.CUSTOM_END}=$customEnd"

        /** Mở độc lập từ icon Home -> luôn bắt đầu tại thời điểm hiện tại, kỳ Ngày, tab Chi tiêu. */
        fun createFreshRoute(): String = createRoute(
            period = HomePeriod.DAY.name,
            anchorDate = System.currentTimeMillis(),
            type = TransactionType.EXPENSE.name,
            customEnd = -1L
        )
    }
    object AddCategory: Screen("add_category")
    object CategoryManagement: Screen("category_management")
    object Budget : Screen("budget")
    object Reminder : Screen("reminder")
    object RecurringTransactions : Screen("recurring_transactions")
    object Profile : Screen("profile")
    object Statistics : Screen("statistics")
    object SecuritySettings : Screen("security_settings")
    object Customization : Screen("customization")
}