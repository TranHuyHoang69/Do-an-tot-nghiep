package com.example.moneymatev2.presentation.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.RecurringFrequency
import com.example.moneymatev2.domain.model.RecurringTransactionModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.RecurringTransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface RecurringEvent {
    data object Saved : RecurringEvent
    data object Deleted : RecurringEvent
    data class Error(val message: String) : RecurringEvent
}

data class RecurringUiState(
    val userId: String? = null,
    val loading: Boolean = true,
    val signedIn: Boolean = false,
    val templates: List<RecurringTransactionModel> = emptyList(),
    val categories: List<CategoryModel> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class RecurringTransactionViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val recurringRepository: RecurringTransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    val uiState: StateFlow<RecurringUiState> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(RecurringUiState(loading = false))
            } else {
                combine(
                    recurringRepository.observe(user.userId),
                    categoryRepository.getAllCategories(user.userId)
                ) { templates, categories ->
                    RecurringUiState(
                        userId = user.userId,
                        loading = false,
                        signedIn = true,
                        templates = templates,
                        categories = categories
                    )
                }.onStart { emit(RecurringUiState()) }
                    .catch { emit(RecurringUiState(loading = false, signedIn = true, error = it.message)) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringUiState())

    private val _events = Channel<RecurringEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        // Opening the screen performs immediate local catch-up. The periodic SyncWorker
        // does the same in background through SyncManager when this screen is not open.
        viewModelScope.launch {
            authRepository.observeAuthState()
                .map { it?.userId }
                .distinctUntilChanged()
                .collect { userId ->
                    if (!userId.isNullOrBlank()) {
                        try { recurringRepository.postDue(userId) }
                        catch (e: Exception) { _events.send(RecurringEvent.Error(e.message ?: "Unable to process due transactions")) }
                    }
                }
        }
    }


    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    /** Validation is repeated inside the repository to protect non-UI callers. */
    fun save(
        ownerId: String?,
        existing: RecurringTransactionModel?,
        type: TransactionType,
        amountText: String,
        categoryId: String,
        note: String,
        frequency: RecurringFrequency,
        startAt: Long,
        timeZoneId: String,
        active: Boolean
    ) {
        if (_isSaving.value) return
        viewModelScope.launch {
            val userId = ownerId
            if (userId == null || authRepository.getCurrentUserId() != userId ||
                (existing != null && existing.userId != userId)) return@launch
            val amount = amountText.toLongOrNull()
            if (userId.isNullOrBlank() || amount == null || amount <= 0L || categoryId.isBlank()) {
                _events.send(RecurringEvent.Error("Vui lòng nhập số tiền và chọn danh mục hợp lệ"))
                return@launch
            }
            _isSaving.value = true
            try {
                val now = System.currentTimeMillis()
                recurringRepository.save(userId, RecurringTransactionModel(
                    id = existing?.id.orEmpty(),
                    userId = userId,
                    type = type,
                    amountMinor = amount,
                    currency = "VND",
                    categoryId = categoryId,
                    note = note.takeIf { it.isNotBlank() },
                    frequency = frequency,
                    startAt = startAt,
                    timeZoneId = timeZoneId,
                    nextOccurrenceIndex = existing?.nextOccurrenceIndex ?: 0L,
                    nextRunAt = existing?.nextRunAt ?: startAt,
                    isActive = active,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now
                ))
                _events.send(RecurringEvent.Saved)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(RecurringEvent.Error(e.message ?: "Không thể lưu giao dịch định kỳ"))
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun toggle(item: RecurringTransactionModel, active: Boolean) {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUserId() ?: return@launch
                if (uid != item.userId) return@launch
                recurringRepository.setActive(uid, item.id, active)
            } catch (e: Exception) {
                _events.send(RecurringEvent.Error(e.message ?: "Không thể thay đổi trạng thái"))
            }
        }
    }

    fun delete(item: RecurringTransactionModel) {
        viewModelScope.launch {
            try {
                val uid = authRepository.getCurrentUserId() ?: return@launch
                if (uid != item.userId) return@launch
                recurringRepository.delete(uid, item.id)
                _events.send(RecurringEvent.Deleted)
            } catch (e: Exception) {
                _events.send(RecurringEvent.Error(e.message ?: "Không thể xóa"))
            }
        }
    }
}
