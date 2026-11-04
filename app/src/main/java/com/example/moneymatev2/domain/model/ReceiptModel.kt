package com.example.moneymatev2.domain.model

data class OcrTextLine(
    val text: String,
    val top: Int,
    val left: Int = 0,
    val right: Int = 0,
    val bottom: Int = top
)

data class OcrResultModel(val fullText: String, val lines: List<OcrTextLine>)

data class ReceiptModel(
    val merchantName: String?,
    val totalAmount: Long?,
    val transactionDate: Long?,
    val note: String?,
    val suggestedCategoryId: String?
)
