package com.example.moneymatev2.presentation.reminder

import androidx.lifecycle.ViewModel
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.domain.model.ReminderModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.usecase.reminder.CreateReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.DeleteReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.ToggleReminderUseCase
import com.example.moneymatev2.domain.usecase.reminder.UpdateReminderUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val reminderRepository: ReminderRepository,
    private val createReminderUseCase: CreateReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val toggleReminderUseCase: ToggleReminderUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase
) : ViewModel() {

    private val currentUserId: StateFlow<String?> =
        authRepository.observeAuthState()
            .map { user ->
                user?.userId
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    val uiState: StateFlow<ReminderUiState> =
        authRepository.observeAuthState()
            .flatMapLatest { user ->

                if (user == null) {
                    flowOf(
                        ReminderUiState.NotAuthenticated
                    )
                } else {
                    reminderRepository
                        .getReminders(user.userId)
                        .map<List<ReminderModel>, ReminderUiState> { reminders ->
                            ReminderUiState.Success(
                                reminders = reminders
                            )
                        }
                        .onStart {
                            emit(ReminderUiState.Loading)
                        }
                        .catch { e ->
                            emit(
                                ReminderUiState.Error(
                                    message =
                                        e.message
                                            ?: "Unable to load reminders"
                                )
                            )
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(
                    5_000
                ),
                initialValue = ReminderUiState.Loading
            )

    private val _formState =
        MutableStateFlow(
            createDefaultFormState()
        )

    val formState: StateFlow<ReminderFormState> =
        _formState

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting

    private val _events =
        Channel<ReminderEvent>(
            Channel.BUFFERED
        )

    val events =
        _events.receiveAsFlow()

    fun onTitleChange(
        value: String
    ) {
        _formState.update {
            it.copy(
                title = value
            )
        }
    }

    fun onNoteChange(
        value: String
    ) {
        _formState.update {
            it.copy(
                note = value
            )
        }
    }

    fun onDateChange(
        dateMillis: Long
    ) {
        _formState.update {
            it.copy(
                selectedDateMillis = dateMillis
            )
        }
    }

    fun onTimeChange(
        hour: Int,
        minute: Int
    ) {
        _formState.update {
            it.copy(
                hour = hour,
                minute = minute
            )
        }
    }

    fun onRepeatChange(
        repeatRule: RepeatRule
    ) {
        _formState.update {
            it.copy(
                repeatRule = repeatRule
            )
        }
    }

    fun startCreate() {
        _formState.value =
            createDefaultFormState()
    }

    /** Load before showing the editor; repository also rejects foreign and pending-delete rows. */
    suspend fun prepareNotificationEdit(reminderId: String, ownerUid: String): ReminderOpenResult {
        if (ownerUid.isBlank() || authRepository.getCurrentUserId() != ownerUid) {
            return ReminderOpenResult.Denied
        }
        val reminder = try {
            reminderRepository.getReminder(reminderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ReminderOpenResult.Error
        } ?: return ReminderOpenResult.Missing
        currentCoroutineContext().ensureActive()
        if (authRepository.getCurrentUserId() != ownerUid || reminder.id != reminderId) {
            return ReminderOpenResult.Denied
        }
        val calendar = Calendar.getInstance().apply { timeInMillis = reminder.triggerAt }
        _formState.value = ReminderFormState(
            editingReminderId = reminder.id,
            title = reminder.title,
            note = reminder.note.orEmpty(),
            selectedDateMillis = reminder.triggerAt,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            repeatRule = reminder.repeatRule,
            isSaving = false
        )
        return ReminderOpenResult.Ready
    }

    fun startEdit(
        reminderId: String
    ) {
        viewModelScope.launch {

            val reminder =
                reminderRepository
                    .getReminder(reminderId)
                    ?: run {
                        _events.send(
                            ReminderEvent.Error(ReminderErrorKind.MISSING)
                        )
                        return@launch
                    }

            val calendar =
                Calendar.getInstance().apply {
                    timeInMillis =
                        reminder.triggerAt
                }

            _formState.value =
                ReminderFormState(
                    editingReminderId =
                        reminder.id,
                    title =
                        reminder.title,
                    note =
                        reminder.note.orEmpty(),
                    selectedDateMillis =
                        reminder.triggerAt,
                    hour =
                        calendar.get(
                            Calendar.HOUR_OF_DAY
                        ),
                    minute =
                        calendar.get(
                            Calendar.MINUTE
                        ),
                    repeatRule =
                        reminder.repeatRule,
                    isSaving = false
                )
        }
    }

    fun saveReminder() {
        val state = _formState.value

        if (state.title.isBlank()) {
            sendEvent(
                ReminderEvent.InvalidTitle
            )
            return
        }

        val triggerAt =
            buildTriggerAt(state)

        if (
            state.editingReminderId == null &&
            triggerAt <=
            System.currentTimeMillis()
        ) {
            sendEvent(
                ReminderEvent.InvalidTime
            )
            return
        }

        val userId =
            currentUserId.value

        if (userId == null) {
            sendEvent(
                ReminderEvent.NotAuthenticated
            )
            return
        }

        viewModelScope.launch {
            _formState.update {
                it.copy(
                    isSaving = true
                )
            }

            try {
                if (
                    state.editingReminderId ==
                    null
                ) {
                    createReminderUseCase(
                        userId = userId,
                        title =
                            state.title,
                        note =
                            state.note,
                        triggerAt =
                            triggerAt,
                        repeatRule =
                            state.repeatRule
                    )
                } else {
                    val existing =
                        reminderRepository
                            .getReminder(
                                state.editingReminderId
                            )
                            ?: throw IllegalStateException(
                                "Reminder not found"
                            )

                    updateReminderUseCase(
                        existing.copy(
                            title =
                                state.title,
                            note =
                                state.note,
                            triggerAt =
                                triggerAt,
                            anchorAt = if (
                                triggerAt / 60_000L == existing.triggerAt / 60_000L &&
                                    state.repeatRule == existing.repeatRule
                            ) existing.anchorAt else triggerAt,
                            repeatRule =
                                state.repeatRule
                        )
                    )
                }

                _events.send(
                    ReminderEvent.Saved
                )

                _formState.value =
                    createDefaultFormState()

            } catch (
                e: IllegalArgumentException
            ) {
                _events.send(
                    ReminderEvent.InvalidTime
                )

            } catch (
                e: Exception
            ) {
                if (e is CancellationException) throw e
                Log.e("ReminderViewModel", "Unable to save reminder", e)
                _events.send(ReminderEvent.Error(ReminderErrorKind.SAVE))

            } finally {
                _formState.update {
                    it.copy(
                        isSaving = false
                    )
                }
            }
        }
    }

    fun toggleReminder(
        reminderId: String,
        isActive: Boolean
    ) {
        viewModelScope.launch {
            try {
                toggleReminderUseCase(
                    reminderId = reminderId,
                    isActive = isActive
                )

            } catch (
                e: IllegalStateException
            ) {
                _events.send(
                    ReminderEvent
                        .ExpiredOneTimeReminder(
                            reminderId =
                                reminderId
                        )
                )

            } catch (
                e: Exception
            ) {
                if (e is CancellationException) throw e
                Log.e("ReminderViewModel", "Unable to update reminder", e)
                _events.send(ReminderEvent.Error(ReminderErrorKind.UPDATE))
            }
        }
    }

    fun deleteReminder(
        reminderId: String
    ) {
        viewModelScope.launch {
            try {
                deleteReminderUseCase(
                    reminderId = reminderId
                )

                _events.send(
                    ReminderEvent.Deleted
                )

            } catch (
                e: Exception
            ) {
                if (e is CancellationException) throw e
                Log.e("ReminderViewModel", "Unable to delete reminder", e)
                _events.send(ReminderEvent.Error(ReminderErrorKind.DELETE))
            }
        }
    }

    fun deleteReminders(
        reminderIds: Collection<String>
    ): Boolean {
        val ids = reminderIds.distinct()
        if (ids.isEmpty() || !_isDeleting.compareAndSet(expect = false, update = true)) return false

        viewModelScope.launch {
            try {
                ids.forEach { reminderId ->
                    deleteReminderUseCase(
                        reminderId = reminderId
                    )
                }

                /*
                 * Chỉ gửi 1 event sau khi toàn bộ
                 * reminder được xử lý xong.
                 *
                 * Tránh việc xóa 5 reminder
                 * lại hiện 5 Snackbar.
                 */
                _events.send(
                    ReminderEvent.Deleted
                )

            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("ReminderViewModel", "Unable to delete reminders", e)
                _events.send(ReminderEvent.Error(ReminderErrorKind.DELETE))
            } finally {
                _isDeleting.value = false
            }
        }
        return true
    }

    private fun buildTriggerAt(
        state: ReminderFormState
    ): Long {

        val selectedDate =
            Calendar.getInstance().apply {
                timeInMillis =
                    state.selectedDateMillis
            }

        return Calendar
            .getInstance()
            .apply {
                set(
                    Calendar.YEAR,
                    selectedDate.get(
                        Calendar.YEAR
                    )
                )

                set(
                    Calendar.MONTH,
                    selectedDate.get(
                        Calendar.MONTH
                    )
                )

                set(
                    Calendar.DAY_OF_MONTH,
                    selectedDate.get(
                        Calendar.DAY_OF_MONTH
                    )
                )

                set(
                    Calendar.HOUR_OF_DAY,
                    state.hour
                )

                set(
                    Calendar.MINUTE,
                    state.minute
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }
            .timeInMillis
    }

    private fun createDefaultFormState():
            ReminderFormState {

        val calendar =
            Calendar.getInstance().apply {
                add(
                    Calendar.HOUR_OF_DAY,
                    1
                )

                set(
                    Calendar.SECOND,
                    0
                )

                set(
                    Calendar.MILLISECOND,
                    0
                )
            }

        return ReminderFormState(
            selectedDateMillis =
                calendar.timeInMillis,
            hour =
                calendar.get(
                    Calendar.HOUR_OF_DAY
                ),
            minute =
                calendar.get(
                    Calendar.MINUTE
                ),
            repeatRule =
                RepeatRule.NONE
        )
    }

    private fun sendEvent(
        event: ReminderEvent
    ) {
        viewModelScope.launch {
            _events.send(event)
        }
    }
}
