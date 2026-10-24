package com.example.moneymatev2.presentation.root

import com.example.moneymatev2.presentation.security.LocalSecurityState

/** Only identifiers are retained while authentication or the app lock is in progress. */
data class PendingReminderNavigation(
    val reminderId: String,
    val ownerUid: String,
    val requestId: Long
) {
    fun isAllowedFor(auth: AuthStartupState, security: LocalSecurityState): Boolean =
        auth is AuthStartupState.LoggedIn && auth.uid == ownerUid &&
            security is LocalSecurityState.Active &&
            security.uid == ownerUid && (!security.hasPin || security.unlocked)

    fun toSavedState(): ArrayList<String> = arrayListOf(reminderId, ownerUid, requestId.toString())

    companion object {
        const val NAV_KEY = "pending_reminder_navigation"

        fun fromSavedState(values: ArrayList<String>?): PendingReminderNavigation? {
            if (values?.size != 3) return null
            val requestId = values[2].toLongOrNull() ?: return null
            if (values[0].isBlank() || values[1].isBlank()) return null
            return PendingReminderNavigation(values[0], values[1], requestId)
        }
    }
}
