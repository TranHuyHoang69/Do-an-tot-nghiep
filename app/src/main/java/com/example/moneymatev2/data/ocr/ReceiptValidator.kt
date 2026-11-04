package com.example.moneymatev2.data.ocr

import com.example.moneymatev2.domain.model.OcrResultModel
import java.text.Normalizer
import java.util.Locale

enum class ReceiptValidationStatus { VALID, UNCERTAIN, NOT_RECEIPT, NO_TEXT }

data class ReceiptValidationResult(
    val status: ReceiptValidationStatus,
    val reason: String
)

/** Checks transaction structure independently of the category and merchant name guesses. */
class ReceiptValidator(private val analyzer: ReceiptAnalyzer = ReceiptAnalyzer()) {
    private val paymentLabels = listOf(
        "tong cong", "ong cong", "tong tien", "tong thanh toan", "thanh toan",
        "tienanhtoan", "phai tra", "total", "grand total", "amount due",
        "amount payable", "total payment", "thanh tien"
    )
    private val provisionalLabels = listOf("tam tinh", "subtotal")
    private val businessSignals = listOf(
        "nha hang", "quan an", "oc quan", "sieu thi", "mart", "cua hang",
        "store", "restaurant", "pharmacy", "nha thuoc", "coffee", "cafe",
        "taxi", "cinema", "shop", "benh vien", "hospital",
        "winmart", "grabmart", "grabfood", "highlands", "long chau", "cgv"
    )
    private val transactionSignals = listOf(
        "hoa don", "receipt", "invoice", "ma don hang", "order no",
        "ma giao dich", "transaction id", "ma so thue", "mst",
        "tien khach dua", "cash received", "tien thoi", "change",
        "tien mat", "cash", "the ngan hang", "card payment"
    )
    private val itemSignals = listOf(
        "so luong", "qty", "quantity", "don gia", "unit price",
        "tien hang", "vat", "thue", "discount", "giam gia"
    )
    private val printedPrice = Regex("(?<!\\d)\\d{1,3}[., ]\\d{3}(?:\\s*(?:đ|vnd))?(?!\\d)", RegexOption.IGNORE_CASE)

    fun validate(result: OcrResultModel): ReceiptValidationResult {
        val text = normalize(result.fullText)
        if (text.none(Char::isLetter)) return ReceiptValidationResult(ReceiptValidationStatus.NO_TEXT, "no useful text")

        // Keep ML Kit geometry when the label and amount are separate visual lines.
        val amount = analyzer.findTotal(result)
        val hasPayment = paymentLabels.any { contains(text, it) }
        val hasProvisional = provisionalLabels.any { contains(text, it) }
        val hasBusiness = businessSignals.any { contains(text, it) }
        val hasTransaction = transactionSignals.any { contains(text, it) }
        val hasItems = itemSignals.any { contains(text, it) }
        val contextCount = listOf(hasBusiness, hasTransaction, hasItems).count { it }

        // A subtotal is evidence of a bill, but it does not prove payment occurred.
        if (hasProvisional && !hasPayment) {
            return ReceiptValidationResult(ReceiptValidationStatus.UNCERTAIN, "provisional bill")
        }
        if (hasPayment && amount != null && contextCount > 0) {
            return ReceiptValidationResult(ReceiptValidationStatus.VALID, "payment amount and independent receipt context")
        }
        if (hasPayment || hasProvisional || (hasBusiness && hasTransaction) ||
            (hasBusiness && printedPrice.containsMatchIn(result.fullText))) {
            return ReceiptValidationResult(ReceiptValidationStatus.UNCERTAIN, "incomplete receipt structure")
        }
        return ReceiptValidationResult(ReceiptValidationStatus.NOT_RECEIPT, "no supported receipt structure")
    }

    private fun normalize(raw: String): String = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('đ', 'd')
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun contains(text: String, phrase: String): Boolean =
        Regex("(?<![a-z0-9])${Regex.escape(phrase)}(?![a-z0-9])").containsMatchIn(text)
}
