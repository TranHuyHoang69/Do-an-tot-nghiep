package com.example.moneymatev2.presentation.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.ui.item.rememberCategoryIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun StatisticsScreen(
    onOpenDrawer: () -> Unit,
    onBreakdown: (Long, Long) -> Unit,
    onTrends: () -> Unit,
    onCategory: (Long, Long, String) -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val range by viewModel.range.collectAsState()
    val period by viewModel.period.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    if (sheet) StatisticsTimeRangeSheet(period, range, onDismiss = { sheet = false }, onApply = { choice, custom ->
        viewModel.apply(choice, custom); sheet = false
    })
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) { Icon(Icons.Default.Menu, stringResource(StringRes.profile_menu), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                Spacer(Modifier.width(10.dp))
                Text(stringResource(StringRes.statistics_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Surface(onClick = { sheet = true }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.width(12.dp))
                        Text(StatisticsDates.label(range), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(18.dp)); Icon(Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            when (val ready = state) {
                StatisticsState.Loading -> item { Box(Modifier.fillMaxWidth().height(200.dp), Alignment.Center) { CircularProgressIndicator() } }
                StatisticsState.Error -> item { Text(stringResource(StringRes.statistics_error), color = MaterialTheme.colorScheme.error) }
                is StatisticsState.Ready -> {
                    val snapshot = ready.snapshot
                    item { SummaryCard(snapshot) }
                    item {
                        StatisticsSectionCard(stringResource(StringRes.statistics_chart), onTrends) {
                            if (snapshot.income == 0L && snapshot.expense == 0L) EmptyStatistics() else BarChart(snapshot.buckets)
                        }
                    }
                    item {
                        StatisticsSectionCard(stringResource(StringRes.statistics_categories), { onBreakdown(range.start, range.endExclusive) }) {
                            if (snapshot.expense == 0L) EmptyStatistics() else OverviewCategories(snapshot) { id -> onCategory(range.start, range.endExclusive, id) }
                        }
                    }
                    item {
                        TextButton(onClick = onTrends, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(StringRes.statistics_trends)); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(snapshot: StatisticsSnapshot) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SummaryLine(Icons.Default.NorthEast, stringResource(StringRes.statistics_income), snapshot.income, incomeGreen)
            SummaryLine(Icons.Default.SouthEast, stringResource(StringRes.statistics_expense), snapshot.expense, expenseRed)
            SummaryLine(Icons.Default.AccountBalanceWallet, stringResource(StringRes.statistics_net), snapshot.net, MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, amount: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(color.copy(alpha = .14f), RoundedCornerShape(14.dp)), Alignment.Center) {
            Icon(icon, null, tint = color)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${CurrencyFormatter.formatFull(amount)} đ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun StatisticsSectionCard(title: String, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, title, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
        }
    }
}

@Composable
private fun OverviewCategories(snapshot: StatisticsSnapshot, onCategory: (String) -> Unit) {
    val top = snapshot.categories.take(5)
    val rest = snapshot.categories.drop(5)
    val shown = if (rest.isEmpty()) top else top + StatisticsCategory(
        rest.first().category.copy(id = "__other__", colorHex = "#AEB9C8"),
        rest.fold(0L) { acc, c -> Math.addExact(acc, c.total) }, rest.flatMap { it.transactions }
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            DonutChart(shown, snapshot.expense, Modifier.weight(.48f), compactTotal = true)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(.52f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { group ->
                    val isOther = group.category.id == "__other__"
                    Row(Modifier.fillMaxWidth().then(if (isOther) Modifier else Modifier.clickable { onCategory(group.category.id) }), verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(categoryColor(group.category))
                        Text(if (isOther) stringResource(StringRes.statistics_other) else categoryName(group.category),
                            Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(StringRes.statistics_percent, percentage(group.total, snapshot.expense)),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
    }
}

@Composable
private fun categoryName(category: CategoryModel): String = when {
    category.isMissing -> stringResource(com.example.moneymatev2.R.string.category_missing_reference)
    category.stableId == "spend_food" -> stringResource(StringRes.statistics_default_food)
    category.stableId == "spend_transport" -> stringResource(StringRes.statistics_default_transport)
    category.stableId == "spend_shopping" -> stringResource(StringRes.statistics_default_shopping)
    category.stableId == "income_salary" -> stringResource(StringRes.statistics_default_salary)
    else -> category.name
}

private fun percentage(value: Long, total: Long): Int = if (total <= 0L) 0 else (value.toDouble() / total * 100).roundToInt()

@Composable
private fun EmptyStatistics() {
    Box(Modifier.fillMaxWidth().height(150.dp), Alignment.Center) { Text(stringResource(StringRes.statistics_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatisticsTimeRangeSheet(selected: StatisticsPeriod, appliedRange: StatisticsRange, onDismiss: () -> Unit, onApply: (StatisticsPeriod, StatisticsRange?) -> Unit) {
    var draft by remember { mutableStateOf(selected) }
    var custom by remember { mutableStateOf<StatisticsRange?>(if (selected == StatisticsPeriod.CUSTOM) appliedRange else null) }
    var picker by remember { mutableStateOf(false) }
    val dateState = rememberDateRangePickerState()
    if (picker) DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
        TextButton(onClick = {
            val start = dateState.selectedStartDateMillis
            val end = dateState.selectedEndDateMillis
            if (start != null && end != null) {
                custom = runCatching { StatisticsDates.custom(start, end) }.getOrNull()
                if (custom != null) picker = false
            }
        }, enabled = dateState.selectedStartDateMillis != null && dateState.selectedEndDateMillis != null) { Text(stringResource(StringRes.confirm)) }
    }, dismissButton = { TextButton(onClick = { picker = false }) { Text(stringResource(StringRes.cancel)) } }) {
        DateRangePicker(state = dateState, modifier = Modifier.heightIn(max = 500.dp), showModeToggle = false)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 26.dp)) {
            Text(stringResource(StringRes.statistics_select_range), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            val options = listOf(
                StatisticsPeriod.THIS_MONTH to StringRes.statistics_this_month,
                StatisticsPeriod.LAST_MONTH to StringRes.statistics_last_month,
                StatisticsPeriod.LAST_THREE_MONTHS to StringRes.statistics_three_months,
                StatisticsPeriod.THIS_YEAR to StringRes.statistics_this_year,
                StatisticsPeriod.CUSTOM to StringRes.statistics_custom
            )
            options.forEach { (period, label) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { draft = period; if (period == StatisticsPeriod.CUSTOM) picker = true }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), Alignment.Center) { Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(label), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (period == StatisticsPeriod.CUSTOM && custom != null) Text(StatisticsDates.label(requireNotNull(custom)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = draft == period, onClick = { draft = period; if (period == StatisticsPeriod.CUSTOM) picker = true })
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = { onApply(draft, custom) }, enabled = draft != StatisticsPeriod.CUSTOM || custom != null, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(26.dp)) { Text(stringResource(StringRes.statistics_apply)) }
        }
    }
}

@Composable
private fun SimpleHeader(title: String, subtitle: String?, onBack: () -> Unit, category: CategoryModel? = null) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, end = 20.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(StringRes.back),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(10.dp))
            if (category != null) {
                Box(Modifier.size(40.dp).background(categoryColor(category).copy(alpha = .15f), CircleShape), Alignment.Center) {
                    Icon(painter = rememberCategoryIcon(category.iconKey), contentDescription = null,
                        tint = categoryColor(category), modifier = Modifier.size(23.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .8f))
            }
        }
    }
}

@Composable
fun CategoryBreakdownScreen(onBack: () -> Unit, onCategory: (Long, Long, String) -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val range by viewModel.range.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SimpleHeader(stringResource(StringRes.statistics_categories), StatisticsDates.label(range), onBack)
        when (val ready = state) {
            StatisticsState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            StatisticsState.Error -> EmptyStatistics()
            is StatisticsState.Ready -> {
                val snap = ready.snapshot
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        if (snap.expense > 0) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            DonutChart(snap.categories, snap.expense, Modifier.fillMaxWidth().padding(horizontal = 75.dp, vertical = 16.dp))
                        } else EmptyStatistics()
                    }
                    items(snap.categories, key = { it.category.userId + ":" + it.category.id }) { group ->
                        Surface(onClick = { onCategory(range.start, range.endExclusive, group.category.id) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                LegendDot(categoryColor(group.category))
                                Text(categoryName(group.category), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(StringRes.statistics_percent, percentage(group.total, snap.expense)), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(12.dp))
                                Text("${CurrencyFormatter.formatFull(group.total)} đ", style = MaterialTheme.typography.bodySmall)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthlyTrendsScreen(onBack: () -> Unit, onMonth: (Long, Long) -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val year by viewModel.year.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SimpleHeader(stringResource(StringRes.statistics_trends), null, onBack)
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.changeYear(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(StringRes.statistics_previous_year),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Text(year.toString(), Modifier.padding(horizontal = 22.dp), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { viewModel.changeYear(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(StringRes.statistics_next_year),
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        when (val ready = state) {
            StatisticsState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            StatisticsState.Error -> EmptyStatistics()
            is StatisticsState.Ready -> {
                val months = ready.monthly
                LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    item {
                        if (months.all { it.income == 0L && it.expense == 0L }) EmptyStatistics()
                        else Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            LineChart(months, Modifier.fillMaxWidth().padding(16.dp))
                        }
                    }
                    items(months.reversed(), key = { it.label }) { month ->
                        val index = month.label.toInt()
                        val maximum = max(1L, months.maxOf { max(it.income, it.expense) })
                        Surface(onClick = { val r = StatisticsDates.month(year, index); onMonth(r.start, r.endExclusive) }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(StringRes.statistics_month, index), Modifier.width(75.dp), style = MaterialTheme.typography.bodySmall)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.fillMaxWidth((month.income.toDouble() / maximum).toFloat().coerceIn(0f, 1f)).height(8.dp).background(incomeGreen, RoundedCornerShape(4.dp)))
                                    Box(Modifier.fillMaxWidth((month.expense.toDouble() / maximum).toFloat().coerceIn(0f, 1f)).height(8.dp).background(expenseRed, RoundedCornerShape(4.dp)))
                                }
                                Spacer(Modifier.width(9.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("${CurrencyFormatter.formatFull(month.income)} đ", style = MaterialTheme.typography.labelSmall)
                                    Text("${CurrencyFormatter.formatFull(month.expense)} đ", style = MaterialTheme.typography.labelSmall, color = expenseRed)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryTransactionsScreen(categoryId: String, onBack: () -> Unit, onTransaction: (String) -> Unit, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val range by viewModel.range.collectAsState()
    val group = (state as? StatisticsState.Ready)?.snapshot?.categories?.find { it.category.id == categoryId }
    val expense = (state as? StatisticsState.Ready)?.snapshot?.expense ?: 0L
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SimpleHeader(group?.let { categoryName(it.category) } ?: stringResource(StringRes.statistics_categories), StatisticsDates.label(range), onBack, group?.category)
        when (state) {
            StatisticsState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            StatisticsState.Error -> EmptyStatistics()
            is StatisticsState.Ready -> if (group == null) EmptyStatistics() else {
                LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Surface(shape = RoundedCornerShape(22.dp), color = categoryColor(group.category).copy(alpha = .12f)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(painter = rememberCategoryIcon(group.category.iconKey), contentDescription = categoryName(group.category), tint = categoryColor(group.category), modifier = Modifier.size(32.dp))
                                Spacer(Modifier.width(15.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(StringRes.statistics_expense), style = MaterialTheme.typography.bodySmall)
                                    Text("${CurrencyFormatter.formatFull(group.total)} đ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = categoryColor(group.category))
                                }
                                Text(stringResource(StringRes.statistics_share, percentage(group.total, expense)), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item { Text(stringResource(StringRes.statistics_transactions), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 12.dp)) }
                    items(group.transactions, key = { it.transaction.id }) { item ->
                        Surface(onClick = { onTransaction(item.transaction.id) }, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(38.dp).background(categoryColor(group.category).copy(alpha = .15f), CircleShape), Alignment.Center) {
                                    Icon(painter = rememberCategoryIcon(group.category.iconKey), contentDescription = null, tint = categoryColor(group.category), modifier = Modifier.size(21.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.transaction.note?.takeIf { it.isNotBlank() } ?: categoryName(group.category), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(item.transaction.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("- ${CurrencyFormatter.formatFull(item.transaction.money.amountMinor)} đ", color = expenseRed, style = MaterialTheme.typography.bodySmall)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
