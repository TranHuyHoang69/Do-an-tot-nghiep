package com.example.moneymatev2.presentation.budget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.BudgetModel
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.Money
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.usecase.GetMonthlyBudgetOverviewUseCase
import com.example.moneymatev2.core.util.DefaultCurrency
import com.example.moneymatev2.core.util.TimeRangeCalculator
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.presentation.home.HomePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Model trình bày cho UI — BudgetModel (Domain) không biết tên/màu category, phải join thêm CategoryModel. */
data class BudgetProgress(
    val budget: BudgetModel,
    val categoryName: String,
    val categoryColorHex: String
) {
    val progress: Float
        get() = if (budget.budgetAmount.amountMinor == 0L) 0f
        else budget.spentAmount.amountMinor.toFloat() / budget.budgetAmount.amountMinor.toFloat()

    val isExceeded: Boolean
        get() = budget.spentAmount.amountMinor > budget.budgetAmount.amountMinor

    val remainingAmount: Long
        get() = budget.budgetAmount.amountMinor - budget.spentAmount.amountMinor
}

data class BudgetUiState(
    val isLoading: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList(),
    val categories: List<CategoryModel> = emptyList(), // cho dialog "+" chọn category để đặt ngân sách
    val displayMonth: String = ""
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val getMonthlyBudgetOverviewUseCase: GetMonthlyBudgetOverviewUseCase,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    var anchorDate by mutableStateOf(System.currentTimeMillis())
        private set

    /**
     * Chỉ cho thêm/xóa khi đang xem đúng tháng thực tại — vì BudgetRepository.setBudget/deleteBudget
     * luôn tác động vào tháng thật hiện tại (Calendar.getInstance()), không nhận tháng đang xem trên UI.
     * Xem tháng quá khứ chỉ để đối chiếu lịch sử, không sửa được (đúng nguyên tắc effective-dated).
     */
    val canModifyBudget: Boolean
        get() {
            val now = Calendar.getInstance()
            val viewing = Calendar.getInstance().apply { timeInMillis = anchorDate }
            return now.get(Calendar.YEAR) == viewing.get(Calendar.YEAR) &&
                    now.get(Calendar.MONTH) == viewing.get(Calendar.MONTH)
        }

    private val allCategories: Flow<List<CategoryModel>> =
        authRepository.observeAuthState().flatMapLatest { user ->
            val userId = user?.userId
            if (userId == null) flowOf(emptyList())
            else categoryRepository.getActiveCategoriesByType(userId, TransactionType.EXPENSE)
        }
    private val _anchorDateFlow = MutableStateFlow(anchorDate)

    val uiState: StateFlow<BudgetUiState> =
        combine(
            authRepository.observeAuthState(),
            snapshotFlowOfAnchor(), // xem bên dưới — cầu nối anchorDate (Compose state) sang Flow
            allCategories
        ) { user, anchor, categories -> Triple(user, anchor, categories) }
            .flatMapLatest { (user, anchor, categories) ->
                val userId = user?.userId
                if (userId == null) {
                    flowOf(BudgetUiState(isLoading = false))
                } else {
                    val cal = Calendar.getInstance().apply { timeInMillis = anchor }
                    val month = cal.get(Calendar.MONTH) + 1
                    val year = cal.get(Calendar.YEAR)

                    // Tính periodStart/periodEnd tại đây (tầng Presentation, được phép biết HomePeriod)
                    // rồi truyền xuống UseCase -- đúng theo fix đã chốt trước đó.
                    val (periodStart, periodEnd) = TimeRangeCalculator.getTimeRange(HomePeriod.MONTH, anchor)

                    getMonthlyBudgetOverviewUseCase(userId , month, year, periodStart, periodEnd).map { overview ->
                        val categoryById = categories.associateBy { it.id }
                        val progressList = overview.budgets.mapNotNull { budget ->
                            val category = categoryById[budget.categoryId] ?: return@mapNotNull null
                            BudgetProgress(
                                budget = budget,
                                categoryName = category.name,
                                categoryColorHex = category.colorHex
                            )
                        }
                        BudgetUiState(
                            isLoading = false,
                            budgets = progressList,
                            categories = categories,
                            displayMonth = SimpleDateFormat("'Tháng' M, yyyy", Locale("vi")).format(Date(anchor))
                        )
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    // mutableStateOf (Compose state) không tự phát Flow -> cần cầu nối để dùng combine().
    // Dùng callbackFlow/snapshotFlow thật sẽ cần import androidx.compose.runtime.snapshotFlow,
    // nhưng ViewModel không nên phụ thuộc Compose runtime trực tiếp kiểu đó trong production code
    // thuần -> đơn giản hơn: đổi anchorDate sang MutableStateFlow nội bộ, giữ property ngoài
    // dạng đọc được như cũ.
    private fun snapshotFlowOfAnchor(): Flow<Long> = _anchorDateFlow

    fun movePreviousMonth() {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorDate; add(Calendar.MONTH, -1) }
        anchorDate = cal.timeInMillis
        _anchorDateFlow.value = anchorDate
    }

    fun moveNextMonth() {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorDate; add(Calendar.MONTH, 1) }
        anchorDate = cal.timeInMillis
        _anchorDateFlow.value = anchorDate
    }

    fun saveBudget(category: CategoryModel, amountText: String) {
        val amount = amountText.toLongOrNull() ?: return
        if (!canModifyBudget) return // chặn double-check phía logic, phòng khi UI lỡ không khoá kịp
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            budgetRepository.setBudget(userId, category.id, amount, DefaultCurrency.CODE)
        }
    }

    fun deleteBudget(progress: BudgetProgress) {
        if (!canModifyBudget) return
        viewModelScope.launch {
            val userId = authRepository.getCurrentUserId() ?: return@launch
            budgetRepository.deleteBudget(userId, progress.budget.categoryId)
        }
    }
}