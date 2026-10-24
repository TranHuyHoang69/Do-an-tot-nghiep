package com.example.moneymatev2.presentation.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.moneymatev2.StringRes
import com.example.moneymatev2.presentation.theme.StringResource

@Composable
internal fun PinDots(
    filled: Int,
    count: Int,
    size: Dp,
    color: Color,
    spacing: Dp,
    outlined: Boolean
) {
    Row(horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val isFilled = index < filled
            val empty = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            Box(Modifier.size(size).clip(CircleShape)
                .background(if (isFilled) color else empty)
                .then(if (outlined) Modifier.border(1.dp,
                    if (isFilled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    CircleShape) else Modifier))
        }
    }
}

@Composable
internal fun PinKeypad(
    keySize: Dp,
    horizontalGap: Dp,
    enabled: Boolean,
    createStyle: Boolean,
    showBiometric: Boolean = false,
    onBiometric: () -> Unit = {},
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val backspace = StringResource(StringRes.security_backspace_desc)
    val biometric = StringResource(StringRes.security_bio_icon_desc)
    val buttonColor = if (createStyle) MaterialTheme.colorScheme.surfaceContainerHighest
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val keyWidth = minOf(120.dp, (maxWidth - horizontalGap * 2).coerceAtLeast(0.dp) / 3)
        val keyHeight = minOf(keySize, keyWidth)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap)) {
                    row.forEach { digit ->
                        PinNumber(digit.toString(), keyWidth, keyHeight, buttonColor, enabled, onDigit)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(keyWidth, keyHeight), contentAlignment = Alignment.Center) {
                    if (showBiometric) IconButton(onClick = onBiometric,
                        enabled = enabled, modifier = Modifier.size(keyWidth, keyHeight)) {
                        Icon(Icons.Default.Fingerprint, biometric,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    }
                }
                PinNumber("0", keyWidth, keyHeight, buttonColor, enabled, onDigit)
                Box(Modifier.size(keyWidth, keyHeight), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onBackspace, enabled = enabled,
                        modifier = Modifier.size(keyWidth, keyHeight)) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, backspace,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PinNumber(
    digit: String,
    width: Dp,
    height: Dp,
    background: Color,
    enabled: Boolean,
    onDigit: (String) -> Unit
) {
    Box(Modifier.size(width, height).clip(RoundedCornerShape(height / 2)).background(background)
        .clickable(enabled = enabled) { onDigit(digit) }, contentAlignment = Alignment.Center) {
        Text(digit, fontSize = 30.sp,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}
