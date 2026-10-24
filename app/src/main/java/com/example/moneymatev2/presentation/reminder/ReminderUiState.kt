package com.example.moneymatev2.presentation.reminder

import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel

sealed interface ReminderUiState {

    data object Loading : ReminderUiState

    data object NotAuthenticated : ReminderUiState

    data class Success(
        val reminders: List<ReminderModel>
    ) : ReminderUiState

    data class Error(
        val message: String
    ) : ReminderUiState
}

enum class ReminderOpenResult { Ready, Missing, Denied, Error }

data class ReminderFormState(
    val editingReminderId: String? = null,
    val title: String = "",
    val note: String = "",
    val selectedDateMillis: Long = 0L,
    val hour: Int = 8,
    val minute: Int = 0,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val isSaving: Boolean = false
) {
    val isEditing: Boolean
        get() = editingReminderId != null
}

sealed interface ReminderEvent {

    data object Saved : ReminderEvent

    data object Deleted : ReminderEvent

    data object InvalidTitle : ReminderEvent

    data object InvalidTime : ReminderEvent

    data object NotAuthenticated : ReminderEvent

    data class ExpiredOneTimeReminder(
        val reminderId: String
    ) : ReminderEvent

    data class Error(
        val kind: ReminderErrorKind
    ) : ReminderEvent
}

enum class ReminderErrorKind { MISSING, SAVE, UPDATE, DELETE }

internal fun snapshotReminderDeleteIds(
    selected: Collection<String>,
    visible: List<ReminderModel>
): List<String> {
    val visibleIds = visible.mapTo(mutableSetOf()) { it.id }
    return selected.distinct().filter { it in visibleIds }
}
