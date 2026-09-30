package com.example.moneymatev2.ui.item

import com.example.moneymatev2.R

object CategoryIconMap{
    private val icons: Map<String, Int> = mapOf(
        // Icon mặc định
        "ic_food" to R.drawable.ic_food,
        "ic_shop" to R.drawable.ic_shop,
        "ic_car" to R.drawable.ic_car,
        "ic_money" to R.drawable.ic_money,

        // Nhóm Tài chính
        "ic_cat_finance_calculator" to R.drawable.ic_cat_finance_calculator,
        "ic_cat_finance_creditcard" to R.drawable.ic_cat_finance_creditcard,
        "ic_cat_finance_money" to R.drawable.ic_cat_finance_money,
        "ic_cat_finance_moneybag" to R.drawable.ic_cat_finance_moneybag,
        "ic_cat_finance_receipt" to R.drawable.ic_cat_finance_receipt,
        "ic_cat_finance_wallet" to R.drawable.ic_cat_finance_wallet,

        // Nhóm Ăn uống
        "ic_cat_food_cake" to R.drawable.ic_cat_food_cake,
        "ic_cat_food_coffee" to R.drawable.ic_cat_food_coffee,
        "ic_cat_food_food" to R.drawable.ic_cat_food_food,
        "ic_cat_food_restaurant" to R.drawable.ic_cat_food_restaurant,
        "ic_cat_food_water" to R.drawable.ic_cat_food_water,
        "ic_cat_food_wine" to R.drawable.ic_cat_food_wine,

        // Nhóm Sức khỏe
        "ic_cat_health_eyeglassess" to R.drawable.ic_cat_health_eyeglassess,
        "ic_cat_health_health" to R.drawable.ic_cat_health_health,
        "ic_cat_health_heart" to R.drawable.ic_cat_health_heart,
        "ic_cat_health_injury" to R.drawable.ic_cat_health_injury,
        "ic_cat_health_pill" to R.drawable.ic_cat_health_pill,

        // Nhóm Mua sắm
        "ic_cat_shop_camera" to R.drawable.ic_cat_shop_camera,
        "ic_cat_shop_cart" to R.drawable.ic_cat_shop_cart,
        "ic_cat_shop_chair" to R.drawable.ic_cat_shop_chair,
        "ic_cat_shop_game" to R.drawable.ic_cat_shop_game,
        "ic_cat_shop_gift" to R.drawable.ic_cat_shop_gift,
        "ic_cat_shop_laptop" to R.drawable.ic_cat_shop_laptop,
        "ic_cat_shop_phone" to R.drawable.ic_cat_shop_phone,
        "ic_cat_shop_shoppingbag" to R.drawable.ic_cat_shop_shoppingbag,
        "ic_cat_shop_watch" to R.drawable.ic_cat_shop_watch,

        // Nhóm Di chuyển
        "ic_cat_transport_boat" to R.drawable.ic_cat_transport_boat,
        "ic_cat_transport_bus" to R.drawable.ic_cat_transport_bus,
        "ic_cat_transport_car" to R.drawable.ic_cat_transport_car,
        "ic_cat_transport_gas" to R.drawable.ic_cat_transport_gas,
        "ic_cat_transport_motorbike" to R.drawable.ic_cat_transport_motorbike,
        "ic_cat_transport_parking" to R.drawable.ic_cat_transport_parking,
        "ic_cat_transport_pedalbike" to R.drawable.ic_cat_transport_pedalbike,
        "ic_cat_transport_plane" to R.drawable.ic_cat_transport_plane,
        "ic_cat_transport_taxi" to R.drawable.ic_cat_transport_taxi,
        "ic_cat_transport_train" to R.drawable.ic_cat_transport_train
    )

    fun resolve(iconKey: String): Int = icons[iconKey] ?: android.R.drawable.ic_menu_help

    fun allKeys(): List<String> = icons.keys.toList()

    fun groupedKeys(): Map<String, List<String>> {
        val groups = linkedMapOf<String, MutableList<String>>()

        icons.keys.filter { it.startsWith("ic_cat_") }.forEach { key ->
            val groupKey = key.removePrefix("ic_cat_").substringBefore("_")
            val label = when(groupKey){
                "finance" -> "Tài chính"
                "food" -> "Ăn uống"
                "health" -> "Sức khỏe"
                "shop" -> "Mua sắm"
                "transport" -> "Di chuyển"
                else -> "Khác"
            }
            groups.getOrPut(label) { mutableListOf() }.add(key)
        }
        return groups
    }
}