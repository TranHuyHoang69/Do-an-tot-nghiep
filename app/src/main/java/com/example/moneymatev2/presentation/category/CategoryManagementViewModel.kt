package com.example.moneymatev2.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.AppResult
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.TransactionError
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.category.DeleteCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CategoryDeletePreview {
    data object Idle : CategoryDeletePreview
    data class Loading(val userId: String, val categoryId: String) : CategoryDeletePreview
    data class Ready(
        val userId: String,
        val categoryId: String,
        val usage: CategoryRepository.DeletionUsage
    ) : CategoryDeletePreview
    data class Failed(val userId: String, val categoryId: String, val error: TransactionError) : CategoryDeletePreview
}

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val deleteCategoryUseCase: DeleteCategoryUseCase
) : ViewModel() {

    // observeAuthState() phản ứng đúng khi đổi user, khắc phục hạn chế đã nêu
    // trước đó ở GetTransactionWithCategoryUseCase (lấy userId 1 lần, không cập nhật).
    private val allCategories: Flow<List<CategoryModel>> =
        authRepository.observeAuthState().flatMapLatest { user ->
            val userId = user?.userId
            if (userId == null) flowOf(emptyList())
            else categoryRepository.getActiveCategories(userId)
        }

    val expenseCategories: StateFlow<List<CategoryModel>> =
        allCategories.map { list -> list.filter { it.type == TransactionType.EXPENSE } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val incomeCategories: StateFlow<List<CategoryModel>> =
        allCategories.map { list -> list.filter { it.type == TransactionType.INCOME } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Thêm vào class CategoryManagementViewModel (đã có allCategories, expenseCategories, incomeCategories):

    private val _deleteEvents = Channel<DeleteCategoryEvent>(Channel.BUFFERED)
    val deleteEvents = _deleteEvents.receiveAsFlow()
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting
    private val _deletePreview = MutableStateFlow<CategoryDeletePreview>(CategoryDeletePreview.Idle)
    val deletePreview: StateFlow<CategoryDeletePreview> = _deletePreview

    fun inspectCategoryDeletion(category: CategoryModel) {
        val loading = CategoryDeletePreview.Loading(category.userId, category.id)
        _deletePreview.value = loading
        viewModelScope.launch {
            try {
                if (authRepository.getCurrentUserId() != category.userId) {
                    if (_deletePreview.value == loading) _deletePreview.value =
                        CategoryDeletePreview.Failed(category.userId, category.id, TransactionError.NotAuthenticated)
                    return@launch
                }
                val usage = categoryRepository.getDeletionUsage(category.userId, category.id)
                if (_deletePreview.value == loading) {
                    _deletePreview.value = if (authRepository.getCurrentUserId() == category.userId)
                        CategoryDeletePreview.Ready(category.userId, category.id, usage)
                    else CategoryDeletePreview.Failed(category.userId, category.id, TransactionError.NotAuthenticated)
                }
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                if (_deletePreview.value == loading) _deletePreview.value =
                    CategoryDeletePreview.Failed(category.userId, category.id, TransactionError.NotAuthenticated)
            } catch (e: IllegalArgumentException) {
                if (_deletePreview.value == loading) _deletePreview.value =
                    CategoryDeletePreview.Failed(category.userId, category.id, TransactionError.CategoryNotSelected)
            } catch (e: Exception) {
                if (_deletePreview.value == loading) _deletePreview.value =
                    CategoryDeletePreview.Failed(category.userId, category.id, TransactionError.UnknownError(e.message.orEmpty()))
            }
        }
    }

    fun cancelCategoryDeletion() {
        if (!_isDeleting.value) _deletePreview.value = CategoryDeletePreview.Idle
    }

    fun deleteCategory(category: CategoryModel, replacement: CategoryModel? = null) {
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                val userId = category.userId
                if (authRepository.getCurrentUserId() != userId ||
                    (replacement != null && replacement.userId != userId)) {
                    _deleteEvents.send(DeleteCategoryEvent.Failed(TransactionError.NotAuthenticated))
                    return@launch
                }
                when (val result = deleteCategoryUseCase(userId, category.id, replacement?.id)) {
                    is AppResult.Success -> {
                        _deletePreview.value = CategoryDeletePreview.Idle
                        _deleteEvents.send(DeleteCategoryEvent.Success)
                    }
                    is AppResult.Failure -> _deleteEvents.send(DeleteCategoryEvent.Failed(result.error as TransactionError))
                    AppResult.Loading -> Unit
                }
            } catch (e: CancellationException) {
                if (!currentCoroutineContext().isActive) throw e
                _deleteEvents.send(DeleteCategoryEvent.Failed(TransactionError.NotAuthenticated))
            } catch (e: Exception) {
                _deleteEvents.send(DeleteCategoryEvent.Failed(TransactionError.UnknownError(e.message.orEmpty())))
            } finally {
                _isDeleting.value = false
            }
        }
    }

    /** Danh sách category có thể chọn làm thay thế: cùng loại, không phải chính nó, chưa bị archive. */
    fun getReplacementOptions(categoryToDelete: CategoryModel): List<CategoryModel> {
        val source = if (categoryToDelete.type == TransactionType.EXPENSE) expenseCategories.value else incomeCategories.value
        return source.filter { it.userId == categoryToDelete.userId && it.id != categoryToDelete.id }
    }

    sealed class DeleteCategoryEvent {
        object Success : DeleteCategoryEvent()
        data class Failed(val error: com.example.moneymatev2.domain.model.TransactionError) : DeleteCategoryEvent()
    }
}
