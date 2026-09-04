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
            _formState.map { it.type }
                .distinctUntilChanged()
                .flatMapLatest { type ->
                    val userId = authRepository.getCurrentUserId()
                    if (userId == null) flowOf(emptyList())
                    else categoryRepository.getActiveCategoriesByType(userId, type)
                }
                .collect { categories ->
                    _formState.update { current ->
                        val stillValid = categories.any { it.id == current.selectedCategoryId }
                        current.copy(
                            categories = categories,
                            selectedCategoryId = if (stillValid) current.selectedCategoryId else categories.firstOrNull()?.id
                        )
                    }
                }
        }
    }

    fun onTypeChange(type: TransactionType) {
        _formState.update { it.copy(type = type, selectedCategoryId = null) }
    }

    fun onAmountChange(text: String) {
        val digitsOnly = text.filter { it.isDigit() }
        _formState.update { it.copy(amountText = digitsOnly) }
    }

    fun onNoteChange(text: String) {
        _formState.update { it.copy(note = text) }
    }

    fun onCategorySelected(categoryId: String) {
        _formState.update { it.copy(selectedCategoryId = categoryId) }
    }

    fun onDateChange(dateMillis: Long) {
        _formState.update { it.copy(selectedDate = dateMillis) }
    }

    fun save() {
        val state = _formState.value
        val amountMinor = state.amountText.toLongOrNull() ?: 0L

        viewModelScope.launch {
            _formState.update { it.copy(isSaving = true) }

            val result = createTransactionUseCase(
                type = state.type,
                amountMinor = amountMinor,
                categoryId = state.selectedCategoryId,
                note = state.note,
                date = state.selectedDate
            )

            _formState.update { it.copy(isSaving = false) }

            when (result) {
                is AppResult.Success -> {
                    _formState.value = AddTransactionFormState()
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

private fun MutableStateFlow<AddTransactionFormState>.update(
    transform: (AddTransactionFormState) -> AddTransactionFormState
) {
    value = transform(value)
}
