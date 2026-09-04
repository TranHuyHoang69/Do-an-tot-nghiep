package com.example.moneymatev2.core.util

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.GroupedTransaction
import com.example.moneymatev2.domain.model.TransactionWithCategory
import com.example.moneymatev2.domain.model.categoryIdentityKey

/**
 * Gộp giao dịch theo category, trên source đã được lọc sẵn theo khoảng thời gian.
 * Giao dịch có category == null (đang chờ sync) bị loại tạm — tự xuất hiện lại
 * khi sync xong. Category tự tạo trùng tên/màu nhưng khác localId sẽ KHÔNG
 * gộp chung (giới hạn đã biết, chấp nhận).
 */
fun List<TransactionWithCategory>.groupByCategory(type: TransactionType): List<GroupedTransaction>{
    return this
        .filter { it.transaction.type == type && it.category != null }
        .groupBy { it.category!!.categoryIdentityKey() }
        .map { (_, group) ->
            GroupedTransaction(
                category = group.first().category!!,
                totalAmount =  group.sumOf { it.transaction.money.amountMinor },
                transactionCount = group.size,
                type = type
            )
        }
        .sortedByDescending { it.totalAmount }
}