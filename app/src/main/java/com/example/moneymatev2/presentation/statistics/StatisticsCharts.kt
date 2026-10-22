package com.example.moneymatev2.presentation.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CategoryColorPalette
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.domain.model.CategoryModel
import kotlin.math.max

internal val incomeGreen = Color(0xFF4FA57C)
internal val expenseRed = Color(0xFFFF5F6D)

internal fun categoryColor(category: CategoryModel): Color = runCatching {
    Color(android.graphics.Color.parseColor(category.colorHex))
}.getOrElse {
    Color(android.graphics.Color.parseColor(CategoryColorPalette.colors[(category.userId + category.id).hashCode().and(Int.MAX_VALUE) % CategoryColorPalette.colors.size]))
}

@Composable
internal fun MoneyText(amount: Long, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Text("${CurrencyFormatter.formatFull(amount)} đ", modifier = modifier, color = color, fontWeight = FontWeight.Bold)
}

@Composable
internal fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LegendDot(incomeGreen); Text(stringResource(StringRes.statistics_income_short), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(18.dp)); LegendDot(expenseRed); Text(stringResource(StringRes.statistics_expense_short),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun LegendDot(color: Color) {
    Canvas(Modifier.size(10.dp)) { drawCircle(color) }
    Spacer(Modifier.width(5.dp))
}

@Composable
internal fun BarChart(buckets: List<StatisticsBucket>, modifier: Modifier = Modifier) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1L, buckets.maxOfOrNull { max(it.income, it.expense) } ?: 1L)
    val million = stringResource(StringRes.million)
    val billion = stringResource(StringRes.billion)
    val thousand = stringResource(StringRes.thousand)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(158.dp)) {
            Column(Modifier.width(42.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                for (i in 4 downTo 0) Text(CurrencyFormatter.formatCompact((maxValue.toDouble() * i / 4).toLong(), million, billion, thousand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val slot = size.width / buckets.size.coerceAtLeast(1)
                for (i in 0..4) {
                    val y = size.height * i / 4f
                    drawLine(guide, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
                buckets.forEachIndexed { index, bucket ->
                    val width = (slot * .22f).coerceAtMost(12.dp.toPx())
                    val center = slot * (index + .5f)
                    listOf(bucket.income to incomeGreen, bucket.expense to expenseRed).forEachIndexed { side, pair ->
                        val height = (pair.first.toDouble() / maxValue * size.height).toFloat().coerceAtLeast(0f)
                        drawRoundRect(pair.second, Offset(center + (side - 1) * (width + 1.dp.toPx()), size.height - height), Size(width, height), CornerRadius(3.dp.toPx()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 42.dp)) {
            buckets.forEachIndexed { index, bucket -> Text(if (buckets.size > 6 && index % 2 == 1) "" else bucket.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(12.dp)); ChartLegend()
    }
}

@Composable
internal fun DonutChart(categories: List<StatisticsCategory>, total: Long, modifier: Modifier = Modifier, compactTotal: Boolean = false) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * .19f
            val inset = stroke / 2
            var angle = -90f
            categories.forEach { group ->
                val sweep = if (total == 0L) 0f else (group.total.toDouble() / total * 360.0).toFloat()
                drawArc(categoryColor(group.category), angle, sweep, false,
                    topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                angle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${if (compactTotal) CurrencyFormatter.formatCompact(total) else CurrencyFormatter.formatFull(total)} đ",
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Text(stringResource(StringRes.statistics_expense), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LineChart(months: List<StatisticsBucket>, modifier: Modifier = Modifier) {
    val guide = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1L, months.maxOfOrNull { max(it.income, it.expense) } ?: 1L)
    val million = stringResource(StringRes.million)
    val billion = stringResource(StringRes.billion)
    val thousand = stringResource(StringRes.thousand)
    Column(modifier) {
        Row(Modifier.fillMaxWidth().height(188.dp)) {
            Column(Modifier.width(46.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                for (i in 4 downTo 0) Text(CurrencyFormatter.formatCompact((maxValue.toDouble() * i / 4).toLong(), million, billion, thousand), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                for (i in 0..4) drawLine(guide, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1.dp.toPx())
                fun plot(values: List<Long>, color: Color) {
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / 11f
                        val y = size.height - (value.toDouble() / maxValue * size.height).toFloat()
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    values.forEachIndexed { index, value -> drawCircle(color, 3.dp.toPx(), Offset(size.width * index / 11f, size.height - (value.toDouble() / maxValue * size.height).toFloat())) }
                }
                plot(months.map { it.income }, incomeGreen); plot(months.map { it.expense }, expenseRed)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 46.dp)) {
            months.forEach { Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.height(12.dp)); ChartLegend()
    }
}
