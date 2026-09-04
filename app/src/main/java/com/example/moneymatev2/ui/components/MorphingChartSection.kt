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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneymatev2.StringRes
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
    morphProgress: Float,
    dynamicHeight: Dp
){
    val optimizedChartData = remember(chartData) {
        val total = chartData.sumOf { it.totalAmount }.takeIf { it > 0 } ?: 1L
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
            if(morphProgress < 0.6f && optimizedChartData.isNotEmpty()){
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer( alpha = 1f - morphProgress * 1.66f)
                ) {
                    Text(
                        text = StringResource(StringRes.total),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val total = remember(chartData) { chartData.sumOf { it.totalAmount }  }
                    Text(
                        text = "${String().format("%,d", total)} đ",
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

        if(progress < 1f){
            drawCircle(
                color = guideCircleColor.copy(alpha = (1f - progress) * 0.25f),
                radius = basePieRadius,
                center = pieCenter,
                style = Stroke(width = pieStokeWidth)
            )
        }

        var currentStartAngle = -90f
        var currentBarX = 0f

        optimizedData.forEachIndexed { index, segment ->
            val sweepAngle = (segment.percentage / 100f) * 360f
            val sectionBarWidth = (segment.percentage / 100f) * width
            val pieTopLeft = Offset(pieCenter.x - basePieRadius, pieCenter.y - basePieRadius)
            val pieSize = Size(basePieRadius * 2, basePieRadius * 2)
            val barTopLeft = Offset(currentBarX, targetBarY)
            val barSize = Size(sectionBarWidth, barHeight)

            val morphTopLeft = Offset(
                x = lerp(pieTopLeft.x, barTopLeft.x, progress),
                y = lerp(pieTopLeft.y, barTopLeft.y, progress)
            )

            val morphSize = Size(
                width = lerp(pieSize.width, barSize.width, progress),
                height = lerp(pieSize.height, barSize.height, progress)
            )

            val currentStrokeWidth = lerp(pieStokeWidth, barHeight, progress)

            if(progress < 0.85f){
                drawArc(
                    color = segment.color,
                    startAngle = lerp(currentStartAngle, 0f, progress),
                    sweepAngle = lerp(sweepAngle, 360f * (segment.percentage / 100f),progress),
                    useCenter = false,
                    topLeft = morphTopLeft,
                    size = morphSize,
                    style = Stroke(width = currentStrokeWidth, cap = StrokeCap.Butt)
                )
            }else{
                val isFirst = index == 0
                val isLast = index == optimizedData.lastIndex
                val cornerRadius = when{
                    isFirst && isLast -> CornerRadius(6.dp.toPx(), 6.dp.toPx())
                    isFirst -> CornerRadius(6.dp.toPx(), 0f)
                    isLast -> CornerRadius(0f, 6.dp.toPx())
                    else -> CornerRadius.Zero
                }

                drawRoundRect(
                    color =  segment.color,
                    topLeft = barTopLeft,
                    size = barSize,
                    cornerRadius = cornerRadius
                )
            }

            currentStartAngle += sweepAngle
            currentBarX += sectionBarWidth
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)