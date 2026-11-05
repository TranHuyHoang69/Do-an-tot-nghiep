package com.example.moneymatev2.presentation.category

import com.example.moneymatev2.R

/** Names for the exact colors offered by CategoryColorPalette. */
internal fun categoryColorNameRes(colorHex: String): Int = when (colorHex.uppercase()) {
    "#F44336" -> R.string.category_color_red
    "#E91E63" -> R.string.category_color_pink
    "#9C27B0" -> R.string.category_color_purple
    "#673AB7" -> R.string.category_color_deep_purple
    "#3F51B5" -> R.string.category_color_indigo
    "#2196F3" -> R.string.category_color_blue
    "#03A9F4" -> R.string.category_color_light_blue
    "#00BCD4" -> R.string.category_color_cyan
    "#009688" -> R.string.category_color_teal
    "#4CAF50" -> R.string.category_color_green
    "#8BC34A" -> R.string.category_color_light_green
    "#CDDC39" -> R.string.category_color_lime
    "#FFC107" -> R.string.category_color_amber
    "#FF9800" -> R.string.category_color_orange
    "#795548" -> R.string.category_color_brown
    "#607D8B" -> R.string.category_color_blue_grey
    else -> R.string.category_color
}
