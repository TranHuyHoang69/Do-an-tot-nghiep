package com.example.moneymatev2.presentation.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.transaction.CreateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.emptyList

data class AddTransactionFormState(
    val userId: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val note: String = "",
    val selectedCategoryId: String? = null,
    val selectedDate: Long = System.currentTimeMillis(),
    val categories: List<CategoryModel> = emptyList(),
    val isSaving: Boolean = false
)

sealed class AddTransactionEvent {
    object SaveSuccessfully : AddTransactionEvent()
    data class SaveFailed(val error: TransactionError) : AddTransactionEvent()
}

@HiltViewModel
class AddTransactionViewmodel @Inject constructor(
    private val createTransactionUseCase: CreateTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _formState = MutableStateFlow(AddTransactionFormState())
    val formState: StateFlow<AddTransactionFormState> = _formState

    private val _events = Channel<AddTransactionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        obserCategoryByType()
    }

    private fun obserCategoryByType() {
        viewModelScope.launch {
            authRepository.observeAuthState().map { it?.userId }.distinctUntilChanged()
                .flatMapLatest { userId ->
                    _formState.update { AddTransactionFormState(userId = userId) }
                    _formState.map { it.type }.distinctUntilChanged().flatMapLatest { type ->
                        if (userId == null) flowOf(emptyList())
                        else categoryRepository.getActiveCategoriesByType(userId, type)
                    }.map { userId to it }
                }
                .collect { (userId, categories) ->
                    if (userId != _formState.value.userId || userId != authRepository.getCurrentUserId()) return@collect
                    _formState.update { current ->
                        val stillValid = categories.any { it.userId == userId && it.id == current.selectedCategoryId }
                        current.copy(
                            categories = categories,
                            selectedCategoryId = if (stillValid) current.selectedCategoryId else categories.firstOrNull()?.id
                        )
                    }
                }
        }
    }

    private fun ownsForm(userId: String?) = userId != null &&
        userId == _formState.value.userId && userId == authRepository.getCurrentUserId()

    fun onTypeChange(type: TransactionType, userId: String?) {
        if (!ownsForm(userId)) return
        _formState.update { it.copy(type = type, selectedCategoryId = null) }
    }

    fun onAmountChange(text: String, userId: String?) {
        if (!ownsForm(userId)) return
        val digitsOnly = text.filter { it.isDigit() }
        _formState.update { it.copy(amountText = digitsOnly) }
    }

    fun onNoteChange(text: String, userId: String?) {
        if (!ownsForm(userId)) return
        _formState.update { it.copy(note = text) }
    }

    fun onCategorySelected(category: CategoryModel) {
        if (category.userId != authRepository.getCurrentUserId() || category.userId != _formState.value.userId) return
        if (category.isArchived || category.isMissing) return
        _formState.update { it.copy(type = category.type, selectedCategoryId = category.id) }
    }

    fun onCategoryResult(userId: String, categoryId: String) {
        if (userId != authRepository.getCurrentUserId()) return
        viewModelScope.launch {
            categoryRepository.getCategory(userId, categoryId)?.let(::onCategorySelected)
        }
    }

    fun onDateChange(dateMillis: Long, userId: String?) {
        if (!ownsForm(userId)) return
        _formState.update { it.copy(selectedDate = dateMillis) }
    }

    fun save(expectedUserId: String?) {
        val state = _formState.value
        if (expectedUserId == null || expectedUserId != state.userId || expectedUserId != authRepository.getCurrentUserId()) return
        val amountMinor = state.amountText.toLongOrNull() ?: 0L

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }

            val result = createTransactionUseCase(
                type = state.type,
                amountMinor = amountMinor,
                category = state.categories.firstOrNull { it.id == state.selectedCategoryId },
                note = state.note,
                date = state.selectedDate
            )

            if (_formState.value.userId != expectedUserId || authRepository.getCurrentUserId() != expectedUserId) return@launch

            _formState.update { it.copy(isSaving = false) }

            when (result) {
                is AppResult.Success -> {
                    _formState.value = AddTransactionFormState(userId = expectedUserId, categories = state.categories)
                    _events.send(AddTransactionEvent.SaveSuccessfully)
                }

                is AppResult.Failure -> {
                    _events.send(AddTransactionEvent.SaveFailed(result.error as TransactionError))
                }

                else -> {}
            }
        }
    }
}
