package com.example.moneymatev2.navigation

import com.example.moneymatev2.data.local.entity.TransactionType

sealed class Screen(val route: String){
    object Login: Screen("login")
    object Register: Screen("register")
    object Home: Screen("home")
    object AddTransaction: Screen("add_transaction")
    object History : Screen(
        "history/{${HomeNavKeys.SELECTED_PERIOD}}/{${HomeNavKeys.ANCHOR_DATE}}/{${HomeNavKeys.SELECTED_TYPE}}" +
                "?${HomeNavKeys.CUSTOM_END}={${HomeNavKeys.CUSTOM_END}}"
    ) {
        fun createRoute(period: String, anchorDate: Long, type: TransactionType, customEnd: Long = -1L) =
            "history/$period/$anchorDate/$type?${HomeNavKeys.CUSTOM_END}=$customEnd"
    }
}