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

    fun deleteCategory(categoryId: String, replacementCategoryId: String) {
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            val result = deleteCategoryUseCase(userId, categoryId, replacementCategoryId)
            when (result) {
                is AppResult.Success -> _deleteEvents.send(DeleteCategoryEvent.Success)
                is AppResult.Failure -> _deleteEvents.send(
                    DeleteCategoryEvent.Failed(result.error as TransactionError) // thêm "as TransactionError"
                )
                else -> {}
            }
        }
    }

    /** Danh sách category có thể chọn làm thay thế: cùng loại, không phải chính nó, chưa bị archive. */
    fun getReplacementOptions(categoryToDelete: CategoryModel): List<CategoryModel> {
        val source = if (categoryToDelete.type == TransactionType.EXPENSE) expenseCategories.value else incomeCategories.value
        return source.filter { it.id != categoryToDelete.id }
    }

    sealed class DeleteCategoryEvent {
        object Success : DeleteCategoryEvent()
        data class Failed(val error: com.example.moneymatev2.domain.model.TransactionError) : DeleteCategoryEvent()
    }
}