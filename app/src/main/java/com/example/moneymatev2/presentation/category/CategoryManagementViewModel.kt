package com.example.moneymatev2.presentation.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CategoryManagementViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
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
}