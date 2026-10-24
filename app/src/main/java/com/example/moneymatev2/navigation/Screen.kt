package com.example.moneymatev2.navigation

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.presentation.home.HomePeriod

sealed class Screen(val route: String){
    object Login: Screen("login")
    object Register: Screen("register")
    object Home: Screen("home")
    object AddTransaction: Screen("add_transaction")
    object TransactionDetail: Screen("transaction_detail/{transactionId}") {
        fun createRoute(id: String) = "transaction_detail/$id"
    }
    object EditTransaction: Screen("edit_transaction/{transactionId}") {
        fun createRoute(id: String) = "edit_transaction/$id"
    }
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
    object CategoryManagement : Screen("category_management?pick={pick}") {
        fun pickRoute(): String = "category_management?pick=true"
        fun manageRoute(): String = "category_management?pick=false"
    }
    object Budget : Screen("budget")
    object Reminder : Screen("reminder")
    object RecurringTransactions : Screen("recurring_transactions")
    object Profile : Screen("profile")
    object Statistics : Screen("statistics")
    object StatisticsMonth : Screen("statistics_month/{start}/{end}") {
        fun createRoute(start: Long, end: Long) = "statistics_month/$start/$end"
    }
    object StatisticsBreakdown : Screen("statistics_breakdown/{start}/{end}") {
        fun createRoute(start: Long, end: Long) = "statistics_breakdown/$start/$end"
    }
    object StatisticsTrends : Screen("statistics_trends")
    object StatisticsCategory : Screen("statistics_category/{start}/{end}/{categoryId}") {
        fun createRoute(start: Long, end: Long, categoryId: String) =
            "statistics_category/$start/$end/${android.net.Uri.encode(categoryId)}"
    }
    object SecuritySettings : Screen("security_settings")
    object CreatePin : Screen("security_create_pin")
    object ChangePin : Screen("security_change_pin")
    object DeletePin : Screen("security_delete_pin")
    object Customization : Screen("customization")
}
