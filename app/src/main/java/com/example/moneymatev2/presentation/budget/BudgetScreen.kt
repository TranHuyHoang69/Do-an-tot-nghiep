package com.example.moneymatev2.presentation.budget

import com.example.moneymatev2.ui.item.categoryDisplayName

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.R
import com.example.moneymatev2.StringRes
import java.math.BigInteger
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.presentation.theme.StringResource
import java.util.Locale

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val editor by viewModel.editor.collectAsState()
    val deletion by viewModel.deletion.collectAsState()
    val conflictNotice by viewModel.conflictNotice.collectAsState()
    val contentLoading = state.isLoading ||
        (state.userId != null && state.periodAnchor != viewModel.anchorDate)
    val budget_delete = StringResource(StringRes.budget_delete)
    val delete = StringResource(StringRes.delete)
    val cancel = StringResource(StringRes.cancel)

    LaunchedEffect(editor?.requestId, editor?.status) {
        if (editor?.status == BudgetOperationStatus.Success) viewModel.dismissEditor(editor?.requestId)
    }
    LaunchedEffect(deletion?.requestId, deletion?.status) {
        if (deletion?.status == BudgetOperationStatus.Success) viewModel.dismissDelete(deletion?.requestId)
    }

    editor?.takeIf { it.userId == state.userId }?.let { form ->
        BudgetEditorDialog(
            editor = form,
            categories = state.categories,
            onDismiss = { viewModel.dismissEditor() },
            onAmountChange = viewModel::changeEditorAmount,
            onCategorySelected = viewModel::selectEditorCategory,
            onSave = viewModel::saveBudget
        )
    }

    deletion?.takeIf { it.target.userId == state.userId }?.let { operation ->
        val progress = operation.target
        val deleting = operation.status == BudgetOperationStatus.Working
        AlertDialog(
            onDismissRequest = { viewModel.dismissDelete() },
            title = { Text(budget_delete) },
            text = {
                val name = budgetCategoryName(progress)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.budget_confirm_delete, name))
                    if (operation.status is BudgetOperationStatus.Error) {
                        Text(stringResource(R.string.budget_delete_failed), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::deleteBudget,
                    enabled = !deleting && operation.status != BudgetOperationStatus.Success,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (deleting) stringResource(R.string.budget_deleting) else delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDelete() }, enabled = !deleting) {
                    Text(cancel)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Chỉ cho thêm ngân sách khi đang xem đúng tháng thực tại (canModifyBudget) --
            // setBudget() luôn tác động vào tháng thật hiện tại, không phải tháng đang xem trên UI.
            if (viewModel.canModifyBudget && !contentLoading && !state.hasLoadError &&
                state.categories.isNotEmpty() && state.budgets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = viewModel::openEditor,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_budget),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BudgetHeader(onOpenDrawer = onOpenDrawer)

            MonthSwitcher(
                anchorDate = viewModel.anchorDate,
                onPrevious = viewModel::movePreviousMonth,
                onNext = viewModel::moveNextMonth
            )

            if (contentLoading) {
                val loadingLabel = stringResource(R.string.budget_loading)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { stateDescription = loadingLabel },
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (state.hasLoadError) {
                BudgetLoadError(onRetry = viewModel::retryLoad)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
//                    if (conflictNotice.count > 0 && conflictNotice.userId == state.userId) {
//                        item {
//                            Text(
//                                text = stringResource(R.string.budget_change_not_synced),
//                                color = MaterialTheme.colorScheme.onSurfaceVariant,
//                                style = MaterialTheme.typography.bodySmall
//                            )
//                        }
//                    }
                    item {
                        BudgetSummaryCard(
                            totalBudget = state.totalBudgetMinor,
                            totalSpent = state.totalSpentMinor,
                            currency = state.currency
                        )
                    }

                    if (state.budgets.isEmpty()) {
                        item {
                            EmptyBudgetState(
                                canAdd = viewModel.canModifyBudget && state.categories.isNotEmpty(),
                                hasCategories = state.categories.isNotEmpty(),
                                onAdd = viewModel::openEditor
                            )
                        }
                    } else {
                        items(
                            items = state.budgets,
                            key = { it.budget.id }
                        ) { progress ->
                            BudgetProgressCard(
                                progress = progress,
                                // Nút xóa cũng bị khoá cùng điều kiện với FAB -- không xóa được
                                // ngân sách của tháng quá khứ (deleteBudget() luôn nhắm vào tháng
                                // thật hiện tại, xóa khi xem quá khứ sẽ xóa SAI tháng).
                                canDelete = viewModel.canModifyBudget,
                                onDelete = { viewModel.openDelete(progress) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun BudgetHeader(onOpenDrawer: () -> Unit) {
    val budget = StringResource(StringRes.drawer_budget)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(R.string.budget_open_menu),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = budget,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun MonthSwitcher(
    anchorDate: Long,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val displayMonth = BudgetMonthLabel.format(anchorDate, locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Lưu ý: không giới hạn "Sau" ở đây như isNextEnabled() của Home/History --
        // Budget CHO PHÉP xem tháng tương lai (để biết mình đã lên kế hoạch ngân sách
        // chưa), chỉ giới hạn THÊM/XÓA mới cần đúng tháng hiện tại. Nếu muốn giới hạn
        // cả việc xem tương lai, cần thêm điều kiện enabled tương tự Home/History.
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = stringResource(R.string.budget_previous_month),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = displayMonth,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.budget_next_month),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    totalBudget: BigInteger?,
    totalSpent: BigInteger?,
    currency: String
) {
    val remaining = if (totalBudget != null && totalSpent != null) totalBudget.subtract(totalSpent) else null
    val isExceeded = remaining != null && remaining < BigInteger.ZERO
    val monthlyOverview = StringResource(StringRes.monthly_overview)
    val total_budget = StringResource(StringRes.total_budget)
    val budget_dachi = StringResource(StringRes.budget_dachi)
    val budget_overpaid = StringResource(StringRes.budget_overpaid)
    val budget_remaining = StringResource(StringRes.budget_remaining)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = monthlyOverview,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            BudgetSummaryRow(total_budget, totalBudget, currency, MaterialTheme.colorScheme.primary)
            BudgetSummaryRow(budget_dachi, totalSpent, currency, MaterialTheme.colorScheme.error)
            BudgetSummaryRow(
                label = if (isExceeded) budget_overpaid else budget_remaining,
                amount = remaining?.abs(),
                currency = currency,
                color = if (isExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BudgetSummaryRow(
    label: String,
    amount: BigInteger?,
    currency: String,
    color: Color
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            text = amount?.let { BudgetMoneyFormatter.formatWithCurrency(it, currency, locale) }
                ?: stringResource(R.string.budget_mixed_currency),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun BudgetProgressCard(
    progress: BudgetProgress,
    canDelete: Boolean,
    onDelete: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()
    val currency = progress.budget.budgetAmount.currency
    val percentText = progress.percentText(locale)
    val progressValue = progress.progress.coerceIn(0f, 1f)
    val progressColor = when {
        progress.isExceeded -> MaterialTheme.colorScheme.error
        progress.progress >= 0.8f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val categoryColor = remember(progress.categoryColorHex) {
        runCatching { Color(progress.categoryColorHex.toColorInt()) }.getOrNull()
    } ?: MaterialTheme.colorScheme.outline
    val overpaid = StringResource(StringRes.overpaid)
    val left = StringResource(StringRes.left)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(42.dp)
                        .background(categoryColor, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budgetCategoryName(progress),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${BudgetMoneyFormatter.format(progress.spentMinor, locale)} / " +
                            BudgetMoneyFormatter.formatWithCurrency(progress.limitMinor, currency, locale),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (progress.hasMixedCurrency) {
                        progress.budget.spentByCurrency.filterKeys { it != progress.budget.budgetAmount.currency }
                            .forEach { (currency, amount) ->
                                Text(BudgetMoneyFormatter.formatWithCurrency(amount, currency, locale),
                                    fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                    }
                }
                // Nút xóa luôn hiện (để biết tính năng có tồn tại), nhưng chỉ bấm được
                // khi đang xem đúng tháng hiện tại -- tránh xóa nhầm vào tháng khác.
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.budget_delete_category, budgetCategoryName(progress)),
                        tint = if (canDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!progress.hasMixedCurrency) {
                val progressDescription = stringResource(
                    R.string.budget_progress_description,
                    budgetCategoryName(progress),
                    if (progress.hasValidLimit) percentText else stringResource(R.string.budget_invalid_limit)
                )
                LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                        .semantics { contentDescription = progressDescription },
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val remainingText = if (progress.isExceeded) {
                "$overpaid ${BudgetMoneyFormatter.formatWithCurrency(progress.remainingAmount.abs(), currency, locale)}"
            } else {
                "$left ${BudgetMoneyFormatter.formatWithCurrency(progress.remainingAmount, currency, locale)}"
            }
            Text(
                text = if (progress.hasMixedCurrency) {
                    stringResource(R.string.budget_mixed_currency)
                } else if (!progress.hasValidLimit) {
                    val invalid = stringResource(R.string.budget_invalid_limit)
                    if (progress.spentMinor > BigInteger.ZERO) "$invalid · $remainingText" else invalid
                } else {
                    val warning = if (progress.isExceeded) " · ${stringResource(R.string.budget_over_budget)}" else ""
                    "$percentText - $remainingText$warning"
                },
                fontSize = 13.sp,
                color = if (progress.hasMixedCurrency) MaterialTheme.colorScheme.error else progressColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun EmptyBudgetState(canAdd: Boolean, hasCategories: Boolean, onAdd: () -> Unit) {
    val no_budget_yet = StringResource(StringRes.no_budget_yet)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = no_budget_yet,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(if (hasCategories) R.string.budget_empty_hint else R.string.budget_slg),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        if (canAdd) Button(onClick = onAdd) { Text(stringResource(R.string.add_budget)) }
    }
}

@Composable
internal fun BudgetLoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.budget_load_failed),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.budget_retry)) }
    }
}

@Composable
private fun BudgetEditorDialog(
    editor: BudgetEditorState,
    categories: List<CategoryModel>,
    onDismiss: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onSave: () -> Unit
) {
    val saving = editor.status == BudgetOperationStatus.Working
    val add_budget = StringResource(StringRes.add_budget)
    val amount_budget = StringResource(StringRes.amount_budget)
    val budget_slg = StringResource(StringRes.budget_slg)
    val save = StringResource(StringRes.save)
    val cancel = StringResource(StringRes.cancel)
    val error = (editor.status as? BudgetOperationStatus.Error)?.reason
    val maxContentHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    AlertDialog(
        modifier = Modifier.imePadding(),
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = add_budget,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = maxContentHeight),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    enabled = !saving,
                    label = { Text(amount_budget) },
                    isError = error != null && error != BudgetOperationError.CATEGORY_UNAVAILABLE,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                }

                if (error != null) {
                    val message = when (error) {
                        BudgetOperationError.INVALID_AMOUNT -> R.string.budget_amount_invalid
                        BudgetOperationError.NON_POSITIVE_AMOUNT -> R.string.budget_limit_positive
                        BudgetOperationError.AMOUNT_OUT_OF_RANGE -> R.string.budget_amount_out_of_range
                        BudgetOperationError.CATEGORY_UNAVAILABLE -> R.string.category_selection_unavailable
                        else -> R.string.budget_save_failed
                    }
                    item {
                        Text(stringResource(message), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }

                if (categories.isEmpty()) {
                    item {
                    Text(
                        text = budget_slg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                    }
                } else {
                    item {
                        Text(
                            text = stringResource(R.string.budget_category_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        val isSelected = editor.categoryId == category.id
                        CategoryBudgetOption(
                            category = category,
                            isSelected = isSelected,
                            enabled = !saving,
                            onClick = { onCategorySelected(category.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = editor.categoryId != null &&
                    !saving && editor.status != BudgetOperationStatus.Success,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (saving) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (saving) stringResource(R.string.budget_saving) else save)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(cancel)
            }
        }
    )
}

@Composable
private fun CategoryBudgetOption(
    category: CategoryModel,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = remember(category.colorHex) {
        runCatching { Color(category.colorHex.toColorInt()) }.getOrNull()
    } ?: MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .selectable(selected = isSelected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(categoryColor, RoundedCornerShape(100.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = categoryDisplayName(category),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun budgetCategoryName(progress: BudgetProgress): String = when {
    progress.categoryMissing -> stringResource(R.string.category_missing_reference)
    progress.category != null -> categoryDisplayName(progress.category)
    else -> progress.categoryName
}
