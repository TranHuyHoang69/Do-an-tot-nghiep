package com.example.moneymatev2.presentation.budget

import javax.inject.Inject
import javax.inject.Singleton

/** Keeps the selected month when the drawer creates another Budget destination in this process. */
@Singleton
class BudgetMonthSelectionStore @Inject constructor() {
    private var selection: Pair<String, Long>? = null

    @Synchronized fun anchorFor(userId: String): Long? =
        selection?.takeIf { it.first == userId }?.second

    @Synchronized fun save(userId: String, anchor: Long) {
        selection = userId to anchor
    }
}
