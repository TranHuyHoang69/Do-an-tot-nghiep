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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.math.BigInteger
import javax.inject.Inject

/** Model trình bày cho UI — BudgetModel (Domain) không biết tên/màu category, phải join thêm CategoryModel. */
data class BudgetProgress(
    val userId: String,
    val budget: BudgetModel,
    val categoryName: String,
    val categoryColorHex: String,
    val categoryMissing: Boolean = false
) {
    val limitMinor: BigInteger get() = BigInteger.valueOf(budget.budgetAmount.amountMinor)
    val spentMinor: BigInteger get() = budget.spentMinor
    val hasMixedCurrency: Boolean get() = budget.spentByCurrency.keys.any { it != budget.budgetAmount.currency }
    val hasValidLimit: Boolean get() = limitMinor > BigInteger.ZERO
    val progress: Float
        get() = BudgetMath.progress(spentMinor, limitMinor)

    val percentText: String get() = BudgetMath.percentText(spentMinor, limitMinor)
    fun percentText(locale: Locale): String = BudgetMath.percentText(spentMinor, limitMinor, locale)

    val isExceeded: Boolean
        get() = spentMinor > limitMinor

    val remainingAmount: BigInteger
        get() = limitMinor.subtract(spentMinor)
}

data class BudgetUiState(
    val userId: String? = null,
    val periodAnchor: Long? = null,
    val isLoading: Boolean = true,
    val budgets: List<BudgetProgress> = emptyList(),
    val totalBudgetMinor: BigInteger? = BigInteger.ZERO,
    val totalSpentMinor: BigInteger? = BigInteger.ZERO,
    val currency: String = DefaultCurrency.CODE,
    val categories: List<CategoryModel> = emptyList(), // cho dialog "+" chọn category để đặt ngân sách
    val hasLoadError: Boolean = false
)

enum class BudgetOperationError { INVALID_AMOUNT, NON_POSITIVE_AMOUNT, AMOUNT_OUT_OF_RANGE, CATEGORY_UNAVAILABLE, SAVE_FAILED, DELETE_FAILED }

sealed interface BudgetOperationStatus {
    data object Idle : BudgetOperationStatus
    data object Working : BudgetOperationStatus
    data object Success : BudgetOperationStatus
    data class Error(val reason: BudgetOperationError) : BudgetOperationStatus
}

data class BudgetEditorState(
    val requestId: Long,
    val userId: String,
    val categoryId: String?,
    val amountText: String = "",
    val status: BudgetOperationStatus = BudgetOperationStatus.Idle
)

data class BudgetDeleteState(
    val requestId: Long,
    val target: BudgetProgress,
    val status: BudgetOperationStatus = BudgetOperationStatus.Idle
)

data class BudgetConflictNotice(val userId: String? = null, val count: Int = 0)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val getMonthlyBudgetOverviewUseCase: GetMonthlyBudgetOverviewUseCase,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
    private val authRepository: AuthRepository,
    private val monthSelectionStore: BudgetMonthSelectionStore = BudgetMonthSelectionStore()
) : ViewModel() {

    private var nextRequestId = 0L
    private val _editor = MutableStateFlow<BudgetEditorState?>(null)
    val editor: StateFlow<BudgetEditorState?> = _editor.asStateFlow()
    private val _deletion = MutableStateFlow<BudgetDeleteState?>(null)
    val deletion: StateFlow<BudgetDeleteState?> = _deletion.asStateFlow()

    val conflictNotice: StateFlow<BudgetConflictNotice> = authRepository.observeAuthState()
        .flatMapLatest { user ->
            val userId = user?.userId
            if (userId == null) flowOf(BudgetConflictNotice())
            else budgetRepository.observeConflictCount(userId)
                .map { BudgetConflictNotice(userId, it) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetConflictNotice())

    private var anchorOwner = authRepository.getCurrentUserId()
    var anchorDate by mutableStateOf(anchorOwner?.let(monthSelectionStore::anchorFor) ?: System.currentTimeMillis())
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
    private val _reload = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            authRepository.observeAuthState().collect { user ->
                val owner = user?.userId
                if (_editor.value?.userId != owner) _editor.value = null
                if (_deletion.value?.target?.userId != owner) _deletion.value = null
                if (owner != anchorOwner) {
                    anchorOwner = owner
                    anchorDate = owner?.let(monthSelectionStore::anchorFor) ?: System.currentTimeMillis()
                    _anchorDateFlow.value = anchorDate
                }
            }
        }
    }

    val uiState: StateFlow<BudgetUiState> =
        authRepository.observeAuthState().flatMapLatest { user ->
            val userId = user?.userId ?: return@flatMapLatest flowOf(BudgetUiState(isLoading = false))
            combine(snapshotFlowOfAnchor(), _reload) { anchor, _ -> anchor }.flatMapLatest { anchor ->
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
                            periodAnchor = anchor,
                            isLoading = false,
                            budgets = progressList,
                            totalBudgetMinor = overview.totalBudgetMinor,
                            totalSpentMinor = overview.totalSpentMinor,
                            currency = overview.currency,
                            categories = categories.filter { !it.isArchived && it.type == TransactionType.EXPENSE }
                        )
                    }.catch { error ->
                        if (error is CancellationException) throw error
                        emit(BudgetUiState(userId = userId, periodAnchor = anchor,
                            isLoading = false, hasLoadError = true))
                    }.onStart { emit(BudgetUiState(userId = userId, periodAnchor = anchor)) }
            }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    // mutableStateOf (Compose state) không tự phát Flow -> cần cầu nối để dùng combine().
    // Dùng callbackFlow/snapshotFlow thật sẽ cần import androidx.compose.runtime.snapshotFlow,
    // nhưng ViewModel không nên phụ thuộc Compose runtime trực tiếp kiểu đó trong production code
    // thuần -> đơn giản hơn: đổi anchorDate sang MutableStateFlow nội bộ, giữ property ngoài
    // dạng đọc được như cũ.
    private fun snapshotFlowOfAnchor(): Flow<Long> = _anchorDateFlow

    fun retryLoad() { _reload.value++ }

    fun movePreviousMonth() {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorDate; add(Calendar.MONTH, -1) }
        anchorDate = cal.timeInMillis
        _anchorDateFlow.value = anchorDate
        anchorOwner?.takeIf { it == authRepository.getCurrentUserId() }?.let {
            monthSelectionStore.save(it, anchorDate)
        }
    }

    fun moveNextMonth() {
        val cal = Calendar.getInstance().apply { timeInMillis = anchorDate; add(Calendar.MONTH, 1) }
        anchorDate = cal.timeInMillis
        _anchorDateFlow.value = anchorDate
        anchorOwner?.takeIf { it == authRepository.getCurrentUserId() }?.let {
            monthSelectionStore.save(it, anchorDate)
        }
    }

    fun openEditor() {
        val owner = uiState.value.userId ?: return
        if (authRepository.getCurrentUserId() != owner || !canModifyBudget) return
        if (_editor.value?.status == BudgetOperationStatus.Working) return
        _editor.value = BudgetEditorState(++nextRequestId, owner, uiState.value.categories.firstOrNull()?.id)
    }

    fun selectEditorCategory(categoryId: String) {
        val current = _editor.value ?: return
        if (current.status == BudgetOperationStatus.Working) return
        if (uiState.value.categories.none { it.id == categoryId && it.userId == current.userId }) return
        _editor.value = current.copy(categoryId = categoryId, status = BudgetOperationStatus.Idle)
    }

    fun changeEditorAmount(text: String) {
        val current = _editor.value ?: return
        if (current.status == BudgetOperationStatus.Working) return
        _editor.value = current.copy(amountText = text, status = BudgetOperationStatus.Idle)
    }

    fun dismissEditor(requestId: Long? = null) {
        val current = _editor.value ?: return
        if ((requestId == null || current.requestId == requestId) && current.status != BudgetOperationStatus.Working) {
            _editor.value = null
        }
    }

    fun saveBudget() {
        val current = _editor.value ?: return
        if (current.status == BudgetOperationStatus.Working || current.status == BudgetOperationStatus.Success) return
        val amount = when (val parsed = BudgetLimitParser.parse(current.amountText)) {
            is BudgetLimitParser.Result.Valid -> parsed.amount
            BudgetLimitParser.Result.NonPositive -> {
                _editor.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.NON_POSITIVE_AMOUNT))
                return
            }
            BudgetLimitParser.Result.OutOfRange -> {
                _editor.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.AMOUNT_OUT_OF_RANGE))
                return
            }
            else -> {
                _editor.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.INVALID_AMOUNT))
                return
            }
        }
        val category = uiState.value.categories.firstOrNull {
            it.id == current.categoryId && it.userId == current.userId && !it.isArchived && !it.isMissing
        }
        if (category == null || !canModifyBudget || authRepository.getCurrentUserId() != current.userId) {
            _editor.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.CATEGORY_UNAVAILABLE))
            return
        }
        _editor.value = current.copy(status = BudgetOperationStatus.Working)
        viewModelScope.launch {
            try {
                if (authRepository.getCurrentUserId() != current.userId) throw IllegalStateException("Account changed")
                budgetRepository.setBudget(current.userId, category.id, amount, DefaultCurrency.CODE)
                if (authRepository.getCurrentUserId() == current.userId && _editor.value?.requestId == current.requestId) {
                    _editor.value = _editor.value?.copy(status = BudgetOperationStatus.Success)
                }
            } catch (error: CancellationException) {
                updateEditorError(current.requestId, BudgetOperationError.SAVE_FAILED)
                throw error
            } catch (error: IllegalArgumentException) {
                updateEditorError(current.requestId, BudgetOperationError.CATEGORY_UNAVAILABLE)
            } catch (error: Exception) {
                updateEditorError(current.requestId, BudgetOperationError.SAVE_FAILED)
            }
        }
    }

    private fun updateEditorError(requestId: Long, reason: BudgetOperationError) {
        val current = _editor.value ?: return
        if (current.requestId == requestId && authRepository.getCurrentUserId() == current.userId) {
            _editor.value = current.copy(status = BudgetOperationStatus.Error(reason))
        }
    }

    fun openDelete(progress: BudgetProgress) {
        if (!canModifyBudget || authRepository.getCurrentUserId() != progress.userId ||
            uiState.value.budgets.none { it.budget.id == progress.budget.id && it.userId == progress.userId }) return
        if (_deletion.value?.status == BudgetOperationStatus.Working) return
        _deletion.value = BudgetDeleteState(++nextRequestId, progress)
    }

    fun dismissDelete(requestId: Long? = null) {
        val current = _deletion.value ?: return
        if ((requestId == null || current.requestId == requestId) && current.status != BudgetOperationStatus.Working) {
            _deletion.value = null
        }
    }

    fun deleteBudget() {
        val current = _deletion.value ?: return
        if (current.status == BudgetOperationStatus.Working || current.status == BudgetOperationStatus.Success) return
        val target = current.target
        if (!canModifyBudget || authRepository.getCurrentUserId() != target.userId ||
            uiState.value.budgets.none { it.budget.id == target.budget.id && it.userId == target.userId }) {
            _deletion.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.DELETE_FAILED))
            return
        }
        _deletion.value = current.copy(status = BudgetOperationStatus.Working)
        viewModelScope.launch {
            try {
                if (authRepository.getCurrentUserId() != target.userId) throw IllegalStateException("Account changed")
                budgetRepository.deleteBudget(target.userId, target.budget.categoryId, target.budget.id)
                if (authRepository.getCurrentUserId() == target.userId && _deletion.value?.requestId == current.requestId) {
                    _deletion.value = _deletion.value?.copy(status = BudgetOperationStatus.Success)
                }
            } catch (error: CancellationException) {
                updateDeleteError(current.requestId)
                throw error
            } catch (error: Exception) {
                updateDeleteError(current.requestId)
            }
        }
    }

    private fun updateDeleteError(requestId: Long) {
        val current = _deletion.value ?: return
        if (current.requestId == requestId && authRepository.getCurrentUserId() == current.target.userId) {
            _deletion.value = current.copy(status = BudgetOperationStatus.Error(BudgetOperationError.DELETE_FAILED))
        }
    }
}
