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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Model trình bày cho UI — BudgetModel (Domain) không biết tên/màu category, phải join thêm CategoryModel. */
data class BudgetProgress(
    val userId: String,
    val budget: BudgetModel,
    val categoryName: String,
    val categoryColorHex: String,
    val categoryMissing: Boolean = false
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
    val userId: String? = null,
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

    private val _categoryUnavailable = Channel<String>(Channel.BUFFERED)
    val categoryUnavailable = _categoryUnavailable.receiveAsFlow()

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

    private val _anchorDateFlow = MutableStateFlow(anchorDate)

    val uiState: StateFlow<BudgetUiState> =
        authRepository.observeAuthState().flatMapLatest { user ->
            val userId = user?.userId ?: return@flatMapLatest flowOf(BudgetUiState(isLoading = false))
            snapshotFlowOfAnchor().flatMapLatest { anchor ->
                    val cal = Calendar.getInstance().apply { timeInMillis = anchor }
                    val month = cal.get(Calendar.MONTH) + 1
                    val year = cal.get(Calendar.YEAR)

                    // Tính periodStart/periodEnd tại đây (tầng Presentation, được phép biết HomePeriod)
                    // rồi truyền xuống UseCase -- đúng theo fix đã chốt trước đó.
                    val (periodStart, periodEnd) = TimeRangeCalculator.getTimeRange(HomePeriod.MONTH, anchor)

                    combine(
                        getMonthlyBudgetOverviewUseCase(userId, month, year, periodStart, periodEnd),
                        categoryRepository.getAllCategories(userId)
                    ) { overview, categories ->
                        val categoryById = categories.associateBy { it.id }
                        val progressList = overview.budgets.map { budget ->
                            val category = categoryById[budget.categoryId]
                            BudgetProgress(
                                userId = userId,
                                budget = budget,
                                categoryName = category?.name ?: "Missing category",
                                categoryColorHex = category?.colorHex ?: "#9E9E9E",
                                categoryMissing = category == null
                            )
                        }
                        BudgetUiState(
                            userId = userId,
                            isLoading = false,
                            budgets = progressList,
                            categories = categories.filter { !it.isArchived && it.type == TransactionType.EXPENSE },
                            displayMonth = SimpleDateFormat(" M / yyyy", Locale("vi")).format(Date(anchor))
                        )
                    }
            }.onStart { emit(BudgetUiState()) }
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
            val userId = category.userId
            if (authRepository.getCurrentUserId() != userId || category.isArchived || category.isMissing) return@launch
            try {
                budgetRepository.setBudget(userId, category.id, amount, DefaultCurrency.CODE)
            } catch (error: IllegalArgumentException) {
                _categoryUnavailable.send(userId)
            }
        }
    }

    fun deleteBudget(progress: BudgetProgress) {
        if (!canModifyBudget) return
        viewModelScope.launch {
            val userId = progress.userId
            if (authRepository.getCurrentUserId() != userId) return@launch
            budgetRepository.deleteBudget(userId, progress.budget.categoryId)
        }
    }
}
