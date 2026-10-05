package com.example.moneymatev2.ui.components

import android.graphics.Color.parseColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.presentation.theme.StringResource

private data class ChartSegment(
    val group: GroupedTransaction,
    val color: Color,
    val percentage: Float
    )

@Composable
fun MorphingChartSection(
    chartData: List<GroupedTransaction>,
    totalAmount: Long,
    morphProgress: Float,
    dynamicHeight: Dp
){
    val optimizedChartData = remember(chartData, totalAmount) {
        val total = totalAmount.takeIf { it > 0 } ?: 1L
        chartData.map { group ->
            val parsedColor = try {
                Color(parseColor(group.category.colorHex))
            }catch (e: Exception){
                Color(0xFF4B8361)
            }
            ChartSegment(
                group = group,
                color = parsedColor,
                percentage = group.totalAmount.toFloat() / total.toFloat() * 100f
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(dynamicHeight),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = (morphProgress * 4).dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ){
            if (morphProgress < 0.6f && optimizedChartData.isNotEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer(alpha = 1f - morphProgress * 1.66f)
                ) {
                    Text(
                        text = StringResource(StringRes.total),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Lấy label qua stringResource() NGOÀI remember (hợp lệ, Compose tự cache riêng),
                    // rồi dùng hàm THUẦN (không @Composable) bên trong remember -- không còn vi phạm quy tắc.
                    val millionLabel = stringResource(StringRes.million)
                    val billionLabel = stringResource(StringRes.billion)
                    val thousandLabel = stringResource(StringRes.thousand)
                    val formattedTotal = remember(totalAmount, millionLabel, billionLabel, thousandLabel) {
                        "${CurrencyFormatter.formatCompact(totalAmount, millionLabel, billionLabel, thousandLabel)} đ"
                    }

                    Text(
                        text = formattedTotal,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if(optimizedChartData.isNotEmpty()){
                MorphingCanvas(
                    optimizedChartData,
                    morphProgress
                )
            }
        }
    }

}

@Composable
private fun MorphingCanvas(
    optimizedData: List<ChartSegment>,
    progress: Float
) {
    val guideCircleColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val pieCenter = Offset(width / 2f, height / 2f)
        val targetBarY = height - 24.dp.toPx()
        val barHeight = 14.dp.toPx()
        val basePieRadius = 70.dp.toPx()
        val pieStokeWidth = 32.dp.toPx()
        val barGapPx = 2.dp.toPx()

        val circumference = 2f * Math.PI.toFloat() * basePieRadius
        val gapDegrees = (barGapPx / circumference) * 360f

        if (progress < 1f) {
            drawCircle(
                color = guideCircleColor.copy(alpha = (1f - progress) * 0.25f),
                radius = basePieRadius,
                center = pieCenter,
                style = Stroke(width = pieStokeWidth)
            )
        }

        // Đảm bảo MỌI lát cắt/thanh đều có kích thước tối thiểu để luôn nhìn thấy được,
        // dù % rất nhỏ. Không gian còn lại mới chia theo đúng tỷ lệ % gốc cho mục lớn hơn
        // -> tổng vẫn khớp đủ 360° / full width, không category nào bị "biến mất".
        val fractions = optimizedData.map { it.percentage / 100f }
        val minSweepDegrees = 3f
        val minBarWidthPx = 4.dp.toPx()

        val pieSweeps = allocateWithMinimum(fractions, minSweepDegrees, 360f)
        val barWidths = allocateWithMinimum(fractions, minBarWidthPx, width)

        var cumulativePieAngle = -90f
        var cumulativeBarX = 0f

        optimizedData.forEachIndexed { index, segment ->
            val finalSweep = pieSweeps[index]
            val finalBarWidth = barWidths[index]

            val pieTopLeft = Offset(pieCenter.x - basePieRadius, pieCenter.y - basePieRadius)
            val pieSize = Size(basePieRadius * 2, basePieRadius * 2)
            val barTopLeft = Offset(cumulativeBarX, targetBarY)
            val barSize = Size(finalBarWidth, barHeight)

            val morphTopLeft = Offset(
                x = lerp(pieTopLeft.x, barTopLeft.x, progress),
                y = lerp(pieTopLeft.y, barTopLeft.y, progress)
            )
            val morphSize = Size(
                width = lerp(pieSize.width, barSize.width, progress),
                height = lerp(pieSize.height, barSize.height, progress)
            )
            val currentStrokeWidth = lerp(pieStokeWidth, barHeight, progress)

            if (progress < 0.85f) {
                val drawStart = lerp(cumulativePieAngle, 0f, progress) + gapDegrees / 2f
                // coerceAtLeast(0.5f) -- không bao giờ cho về 0 tuyệt đối, luôn còn 1 vệt mảnh nhìn thấy được.
                val drawSweep = (finalSweep - gapDegrees).coerceAtLeast(0.5f)

                drawArc(
                    color = segment.color,
                    startAngle = drawStart,
                    sweepAngle = drawSweep,
                    useCenter = false,
                    topLeft = morphTopLeft,
                    size = morphSize,
                    style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Butt)
                )
            } else {
                val isFirst = index == 0
                val isLast = index == optimizedData.lastIndex
                val cornerRadius = when {
                    isFirst && isLast -> CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    isFirst -> CornerRadius(6.dp.toPx(), 0f)
                    isLast -> CornerRadius(0f, 6.dp.toPx())
                    else -> CornerRadius.Zero
                }
                val drawBarTopLeft = Offset(barTopLeft.x + barGapPx / 2f, barTopLeft.y)
                val drawBarSize = Size((finalBarWidth - barGapPx).coerceAtLeast(1f), barSize.height)

                drawRoundRect(
                    color = segment.color,
                    topLeft = drawBarTopLeft,
                    size = drawBarSize,
                    cornerRadius = cornerRadius
                )
            }

            cumulativePieAngle += finalSweep
            cumulativeBarX += finalBarWidth
        }
    }
}

/**
 * Chia `total` cho `fractions.size` phần, mỗi phần tối thiểu `minSize`, phần còn lại
 * chia tiếp theo đúng tỷ lệ gốc trong `fractions`. Nếu tổng tối thiểu đã vượt `total`
 * (quá nhiều mục), fallback chia đều, bỏ qua tỷ lệ để tránh tràn.
 */
private fun allocateWithMinimum(fractions: List<Float>, minSize: Float, total: Float): List<Float> {
    val n = fractions.size
    if (n == 0) return emptyList()
    val reserved = minSize * n
    if (reserved >= total) {
        return List(n) { total / n }
    }
    val remaining = total - reserved
    val fractionSum = fractions.sum().takeIf { it > 0f } ?: 1f
    return fractions.map { f -> minSize + remaining * (f / fractionSum) }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)