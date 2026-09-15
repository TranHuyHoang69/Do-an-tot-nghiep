package com.example.moneymatev2.ui.item


import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource


@Composable
fun rememberCategoryIcon(iconResName: String): Painter{
    val resId = remember(iconResName) { CategoryIconMap.resolve(iconResName) }
    return painterResource(id = resId)
}