package com.example.moneymatev2.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.core.util.CurrencyFormatter
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.presentation.theme.StringResource
import com.example.moneymatev2.ui.item.rememberCategoryIcon

@Composable
fun ErrorSection(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(50.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun TimeNavigationHeader(
    selectedPeriod: HomePeriod,
    displayTime: String,
    themeColor: Color,
    isNextEnabled: Boolean,
    onPeriodChange: (HomePeriod) -> Unit,
    onCustomRangeClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val visibleModes = listOf(HomePeriod.DAY, HomePeriod.WEEK, HomePeriod.MONTH, HomePeriod.YEAR)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            visibleModes.forEach { mode ->
                val isSelected = selectedPeriod == mode
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onPeriodChange(mode) }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = when (mode) {
                            HomePeriod.DAY -> StringResource(StringRes.day)
                            HomePeriod.WEEK -> StringResource(StringRes.week)
                            HomePeriod.MONTH -> StringResource(StringRes.month)
                            HomePeriod.YEAR -> StringResource(StringRes.year)
                            HomePeriod.PERIOD -> ""
                            HomePeriod.CUSTOM -> StringResource(StringRes.period)
                        },
                        color = if (isSelected) themeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp, 2.dp)
                                .background(themeColor, CircleShape)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onPrevious
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous",
                    tint = themeColor
                )
            }

            Text(
                text = displayTime,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            IconButton(
                onClick = onNext,
                enabled = isNextEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next",
                    tint = if (isNextEnabled) themeColor else themeColor.copy(0.3f)
                )
            }
        }
    }
}


@Composable
fun TransactionListItem(
    group: GroupedTransaction,
    currencyUnit: String,
    onClick: () -> Unit
) {
    val categoryIcon = rememberCategoryIcon(group.category.iconKey)
    val defaultColor = MaterialTheme.colorScheme.primary
    val categoryColor = remember(group.category.colorHex){
        runCatching { Color(group.category.colorHex.toColorInt()) }
            .getOrDefault(defaultColor)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(categoryColor.copy(0.15f)),
                    contentAlignment = Alignment.Center
                ){
                    Icon(
                        painter = categoryIcon,
                        contentDescription = group.category.name,
                        tint =  categoryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = group.category.name,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "${group.transactionCount} ${StringResource(StringRes.transaction)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val sign = if (group.type == TransactionType.EXPENSE) "-" else "+"
            Text(
                text = "$sign ${CurrencyFormatter.formatCompact( group.totalAmount)} $currencyUnit",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyStateCollection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "info",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
            modifier = Modifier.size(48.dp)
        )

        Text(
            text = StringResource(StringRes.dont_have_transaction),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Composable
fun LoadingUI(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(50.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = color)
    }
}