package com.example.moneymatev2.presentation.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.domain.model.CategoryModel

@Composable
fun BudgetScreen(
    viewModel: BudgetViewModel = hiltViewModel(),
    onOpenDrawer: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BudgetProgress?>(null) }

    if (showAddDialog) {
        BudgetEditorDialog(
            categories = state.categories,
            onDismiss = { showAddDialog = false },
            onSave = { category, amount ->
                viewModel.saveBudget(category, amount)
                showAddDialog = false
            }
        )
    }

    pendingDelete?.let { progress ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Xóa ngân sách?") }, // TODO: StringResource
            text = {
                Text("Ngân sách ${progress.categoryName} của tháng này sẽ bị xóa.") // TODO: StringResource
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBudget(progress)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Hủy")
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
            if (viewModel.canModifyBudget) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(100.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Thêm ngân sách",
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
                displayMonth = state.displayMonth,
                onPrevious = viewModel::movePreviousMonth,
                onNext = viewModel::moveNextMonth
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                val totalBudget = state.budgets.sumOf { it.budget.budgetAmount.amountMinor }
                val totalSpent = state.budgets.sumOf { it.budget.spentAmount.amountMinor }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        BudgetSummaryCard(
                            totalBudget = totalBudget,
                            totalSpent = totalSpent
                        )
                    }

                    if (state.budgets.isEmpty()) {
                        item { EmptyBudgetState() }
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
                                onDelete = { pendingDelete = progress }
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .height(56.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Ngân sách", // TODO: StringResource
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun MonthSwitcher(
    displayMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
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
                contentDescription = null,
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
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BudgetSummaryCard(
    totalBudget: Long,
    totalSpent: Long
) {
    val remaining = totalBudget - totalSpent
    val isExceeded = remaining < 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Tổng quan tháng", // TODO: StringResource
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            BudgetSummaryRow("Tổng ngân sách", totalBudget, MaterialTheme.colorScheme.primary)
            BudgetSummaryRow("Đã chi", totalSpent, MaterialTheme.colorScheme.error)
            BudgetSummaryRow(
                label = if (isExceeded) "Vượt ngân sách" else "Còn lại",
                amount = kotlin.math.abs(remaining),
                color = if (isExceeded) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
            )
        }
    }
}

@Composable
private fun BudgetSummaryRow(
    label: String,
    amount: Long,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
            text = "${CurrencyFormatter.formatCompact(amount)} đ",
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
    val progressValue = progress.progress.coerceIn(0f, 1f)
    val progressColor = when {
        progress.isExceeded -> MaterialTheme.colorScheme.error
        progress.progress >= 0.8f -> Color(0xFFFF9800)
        else -> Color(0xFF2E7D32)
    }
    val categoryColor = remember(progress.categoryColorHex) {
        runCatching { Color(progress.categoryColorHex.toColorInt()) }.getOrDefault(Color(0xFF4CB080))
    }

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
                        text = progress.categoryName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${CurrencyFormatter.formatCompact(progress.budget.spentAmount.amountMinor)} / " +
                                "${CurrencyFormatter.formatCompact(progress.budget.budgetAmount.amountMinor)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Nút xóa luôn hiện (để biết tính năng có tồn tại), nhưng chỉ bấm được
                // khi đang xem đúng tháng hiện tại -- tránh xóa nhầm vào tháng khác.
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa ngân sách",
                        tint = if (canDelete) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            val remainingText = if (progress.isExceeded) {
                "Vượt ${CurrencyFormatter.formatCompact(kotlin.math.abs(progress.remainingAmount))}"
            } else {
                "Còn lại ${CurrencyFormatter.formatCompact(progress.remainingAmount)}"
            }
            Text(
                text = "${(progress.progress * 100).toInt()}% - $remainingText",
                fontSize = 13.sp,
                color = progressColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun EmptyBudgetState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Chưa có ngân sách nào cho tháng này", // TODO: StringResource
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun BudgetEditorDialog(
    categories: List<CategoryModel>,
    onDismiss: () -> Unit,
    onSave: (CategoryModel, String) -> Unit
) {
    var selectedCategory by remember(categories) { mutableStateOf(categories.firstOrNull()) }
    var amountText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Thêm ngân sách", // TODO: StringResource
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { char -> char.isDigit() } },
                    label = { Text("Số tiền ngân sách") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                if (categories.isEmpty()) {
                    Text(
                        text = "Chưa có danh mục Chi tiêu nào. Tạo danh mục trước khi đặt ngân sách.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                } else {
                    Text(
                        text = "Danh mục",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    LazyColumn(
                        modifier = Modifier.height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories, key = { it.id }) { category ->
                            val isSelected = selectedCategory?.id == category.id
                            CategoryBudgetOption(
                                category = category,
                                isSelected = isSelected,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val category = selectedCategory ?: return@Button
                    onSave(category, amountText)
                },
                enabled = selectedCategory != null && amountText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
private fun CategoryBudgetOption(
    category: CategoryModel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val categoryColor = remember(category.colorHex) {
        runCatching { Color(category.colorHex.toColorInt()) }.getOrDefault(Color(0xFF4CB080))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
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
            text = category.name,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}