package com.example.moneymatev2.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey
import androidx.core.graphics.toColorInt
import com.example.moneymatev2.core.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val groupedItems by viewModel.groupedItems.collectAsState()

    val expenseColor = MaterialTheme.colorScheme.error
    val incomeColor = MaterialTheme.colorScheme.primary
    val themeColor = if (viewModel.selectedType == TransactionType.EXPENSE) expenseColor else incomeColor

    var showDatePicker by remember { mutableStateOf(false) }
    val dateRangePickerState = rememberDateRangePickerState()
    var expandedGroupKey by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val start = dateRangePickerState.selectedStartDateMillis
                    val end = dateRangePickerState.selectedEndDateMillis
                    if (start != null && end != null) {
                        viewModel.setCustomRange(start, end)
                    }
                    showDatePicker = false
                }) {
                    Text(text = stringResource(StringRes.confirm), color = themeColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = stringResource(StringRes.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.weight(1f),
                    title = {
                        Text(
                            text = stringResource(StringRes.select_period),
                            modifier = Modifier.padding(16.dp)
                        )
                    },
                    showModeToggle = false
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Đổi onOpenDrawer -> onBack: app không dùng Navigation Drawer,
                    // History là 1 destination độc lập, cần mũi tên quay lại.
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(StringRes.history_title),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf(
                        TransactionType.EXPENSE to stringResource(StringRes.expense),
                        TransactionType.INCOME to stringResource(StringRes.income)
                    ).forEach { (type, label) ->
                        val isSelected = viewModel.selectedType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) themeColor else Color.Transparent)
                                .clickable { viewModel.onTypeChange(type) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            CompactTimeNavigation(
                currentMode = viewModel.selectedPeriod,
                displayTime = viewModel.getDisplayTime(),
                isNextEnabled = viewModel.isNextEnabled(),
                themeColor = themeColor,
                onModeChange = { viewModel.onPeriodChange(it) },
                onPrevious = { viewModel.moveTimeRange(-1) },
                onNext = { viewModel.moveTimeRange(1) },
                onRangeClick = { showDatePicker = true }
            )

            SummaryRow(
                totalAmount = groupedItems.sumOf { it.totalAmount },
                themeColor = themeColor,
                currentSortType = viewModel.sortType,
                onSortChange = { viewModel.onSortTypeChange(it) }
            )

            if (groupedItems.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text(
                        text = stringResource(StringRes.dont_have_transaction),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    itemsIndexed(
                        items = groupedItems,
                        key = { _, group -> group.category.categoryIdentityKey() ?: group.category.id }
                    ) { _, group ->
                        ExpenseItem(
                            group = group,
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    expandedGroupKey = group.category.categoryIdentityKey() ?: group.category.id
                                }
                        )
                    }
                }
            }
        }
    }

    expandedGroupKey?.let { key ->
        val transactions = remember(key, groupedItems) { viewModel.getTransactionsInGroup(key) }
        TransactionDetailDialog(transactions = transactions, onDismiss = { expandedGroupKey = null })
    }
}

@Composable
fun CompactTimeNavigation(
    currentMode: HomePeriod,
    displayTime: String,
    isNextEnabled: Boolean,
    themeColor: Color,
    onModeChange: (HomePeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRangeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val modes = listOf(
                HomePeriod.DAY to stringResource(StringRes.day),
                HomePeriod.WEEK to stringResource(StringRes.week),
                HomePeriod.MONTH to stringResource(StringRes.month),
                HomePeriod.YEAR to stringResource(StringRes.year)
            )
            modes.forEach { (mode, label) ->
                val isSelected = currentMode == mode
                Text(
                    text = label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onModeChange(mode) }
                        .padding(8.dp),
                    color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
            Text(
                text = stringResource(StringRes.period),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRangeClick() }
                    .padding(8.dp),
                color = if (currentMode == HomePeriod.CUSTOM) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (currentMode == HomePeriod.CUSTOM) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val isCustom = currentMode == HomePeriod.CUSTOM
            IconButton(onClick = onPrevious, enabled = !isCustom) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = if (isCustom) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else themeColor
                )
            }
            Text(
                text = displayTime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            val nextState = isNextEnabled && !isCustom
            IconButton(onClick = onNext, enabled = nextState) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = if (nextState) themeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
fun SummaryRow(
    totalAmount: Long,
    themeColor: Color,
    currentSortType: DetailSortType,
    onSortChange: (DetailSortType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val sortTimeLabel = stringResource(StringRes.sort_by_time)
    val sortAmountLabel = stringResource(StringRes.sort_by_amount)
    val currentLabel = if (currentSortType == DetailSortType.DATE_DESC) sortTimeLabel else sortAmountLabel

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(StringRes.total),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${CurrencyFormatter.formatCompact(totalAmount)} đ",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = themeColor
            )
        }
        Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(StringRes.sort_by)} $currentLabel",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(sortTimeLabel) },
                    onClick = { onSortChange(DetailSortType.DATE_DESC); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text(sortAmountLabel) },
                    onClick = { onSortChange(DetailSortType.AMOUNT_DESC); expanded = false }
                )
            }
        }
    }
}

@Composable
fun ExpenseItem(
    group: GroupedTransaction,
    modifier: Modifier = Modifier
) {
    val categoryColor = remember(group.category.colorHex) {
        runCatching { Color(group.category.colorHex.toColorInt()) }.getOrDefault(Color.Gray)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(categoryColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = group.category.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(
                        text = "${group.transactionCount} ${stringResource(StringRes.transaction)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val sign = if (group.type == TransactionType.EXPENSE) "-" else "+"
            Text(
                text = "$sign ${CurrencyFormatter.formatCompact(group.totalAmount)} đ",
                fontWeight = FontWeight.Bold,
                color = if (group.type == TransactionType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TransactionDetailDialog(
    transactions: List<TransactionWithCategory>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(16.dp)) {
                itemsIndexed(transactions, key = { _, item -> item.transaction.id }) { _, item ->
                    val sdf = remember { SimpleDateFormat("d/M/yyyy", Locale("vi")) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = sdf.format(Date(item.transaction.createdAt)), style = MaterialTheme.typography.bodySmall)
                            if (!item.transaction.note.isNullOrBlank()) {
                                Text(text = item.transaction.note, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val sign = if (item.transaction.type == TransactionType.EXPENSE) "-" else "+"
                        Text(
                            text = "$sign ${CurrencyFormatter.formatCompact(item.transaction.money.amountMinor)} đ",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}