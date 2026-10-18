package com.example.moneymatev2.presentation.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.moneymatev2.domain.usecase.transaction.UpdateTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

data class TransactionEntryState(
    val loading: Boolean = true,
    val ownerId: String? = null,
    val transaction: TransactionModel? = null,
    val category: CategoryModel? = null,
    val categories: List<CategoryModel> = emptyList(),
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val selectedCategoryId: String? = null,
    val selectedDate: Long = 0L,
    val note: String = "",
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null
)

sealed interface TransactionEntryEvent {
    data object Saved : TransactionEntryEvent
    data object Deleted : TransactionEntryEvent
}

@HiltViewModel
class TransactionEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val auth: AuthRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val update: UpdateTransactionUseCase,
    private val delete: DeleteTransactionUseCase
) : ViewModel() {
    private val id: String = checkNotNull(savedStateHandle["transactionId"])
    private val initialOwner = auth.getCurrentUserId()
    private var initialized = false
    private val _state = MutableStateFlow(TransactionEntryState(ownerId = initialOwner))
    val state: StateFlow<TransactionEntryState> = _state
    private val _events = Channel<TransactionEntryEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            auth.observeAuthState().collectLatest { user ->
                if (initialOwner == null || user?.userId != initialOwner) {
                    initialized = false
                    _state.update { TransactionEntryState(loading = false) }
                    return@collectLatest
                }
                launch {
                    transactions.getTransactionById(initialOwner, id).collect { tx ->
                        _state.update { current ->
                            if (tx == null) current.copy(loading = false, transaction = null)
                            else {
                                val prefill = !initialized
                                initialized = true
                                current.copy(
                                    loading = false, transaction = tx,
                                    type = if (prefill) tx.type else current.type,
                                    amountText = if (prefill) tx.money.amountMinor.toString() else current.amountText,
                                    selectedCategoryId = if (prefill) tx.categoryId else current.selectedCategoryId,
                                    selectedDate = if (prefill) tx.createdAt else current.selectedDate,
                                    note = if (prefill) tx.note.orEmpty() else current.note
                                )
                            }
                        }
                        if (tx != null) _state.update { it.copy(category = categories.getCategory(initialOwner, tx.categoryId)) }
                    }
                }
                launch {
                    _state.map { it.type }.distinctUntilChanged().collectLatest { type ->
                        categories.getActiveCategoriesByType(initialOwner, type).collect { list ->
                            _state.update { it.copy(categories = list) }
                        }
                    }
                }
            }
        }
    }

    private fun ownsSession() = initialOwner != null && auth.getCurrentUserId() == initialOwner && _state.value.ownerId == initialOwner

    fun typeChanged(type: TransactionType) {
        if (ownsSession()) _state.update { it.copy(type = type, selectedCategoryId = null, error = null) }
    }
    fun amountChanged(text: String) {
        if (ownsSession()) _state.update { it.copy(amountText = text.filter(Char::isDigit), error = null) }
    }
    fun noteChanged(text: String) {
        if (ownsSession()) _state.update { it.copy(note = text) }
    }
    fun categoryChanged(category: CategoryModel) {
        if (ownsSession() && category.userId == initialOwner && !category.isArchived && !category.isMissing && category.type == _state.value.type)
            _state.update { it.copy(selectedCategoryId = category.id, error = null) }
    }
    fun categoryResult(userId: String, categoryId: String) {
        if (!ownsSession() || userId != initialOwner) return
        viewModelScope.launch { categories.getCategory(userId, categoryId)?.let(::categoryChanged) }
    }
    fun dateChanged(date: Long) {
        if (!ownsSession()) return
        val old = Calendar.getInstance().apply { timeInMillis = _state.value.selectedDate }
        val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = date }
        old.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH))
        _state.update { it.copy(selectedDate = old.timeInMillis) }
    }
    fun save() {
        val s = _state.value
        if (s.saving || !ownsSession() || s.transaction == null) return
        val amount = s.amountText.toLongOrNull()
        val category = s.categories.firstOrNull { it.id == s.selectedCategoryId }
            ?: s.category?.takeIf { it.id == s.selectedCategoryId && s.type == it.type && !it.isMissing }
        if (amount == null || amount <= 0) { _state.update { it.copy(error = "amount") }; return }
        if (category == null) { _state.update { it.copy(error = "category") }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                update(s.transaction.copy(type = s.type, money = s.transaction.money.copy(amountMinor = amount),
                    categoryId = category.id, note = s.note.ifBlank { null }, createdAt = s.selectedDate))
                if (ownsSession()) _events.send(TransactionEntryEvent.Saved)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (ownsSession()) _state.update { it.copy(error = e.message ?: "save") } }
            finally { _state.update { it.copy(saving = false) } }
        }
    }
    fun delete() {
        val s = _state.value
        if (s.deleting || !ownsSession() || s.transaction == null) return
        _state.update { it.copy(deleting = true, error = null) }
        viewModelScope.launch {
            try {
                delete(s.transaction.userId, s.transaction.id)
                if (ownsSession()) _events.send(TransactionEntryEvent.Deleted)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (ownsSession()) _state.update { it.copy(error = e.message ?: "delete") } }
            finally { _state.update { it.copy(deleting = false) } }
        }
    }
}
