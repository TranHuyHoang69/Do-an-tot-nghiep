package com.example.moneymatev2.data.ocr

import com.example.moneymatev2.domain.model.OcrResultModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptValidatorTest {
    private val validator = ReceiptValidator()

    @Test fun emptyAndNumberOnlyImagesHaveNoUsefulText() {
        assertEquals(ReceiptValidationStatus.NO_TEXT, status(""))
        assertEquals(ReceiptValidationStatus.NO_TEXT, status("123456789"))
    }

    @Test fun booksMessagesAndPhoneNumbersAreNotReceipts() {
        assertEquals(ReceiptValidationStatus.NOT_RECEIPT,
            status("Chapter 3: Personal finance\nSaving money is important."))
        assertEquals(ReceiptValidationStatus.NOT_RECEIPT,
            status("Message: pay me 120000 on 20/09/2026"))
        assertEquals(ReceiptValidationStatus.NOT_RECEIPT,
            status("Call 0912345678\nDate 20/09/2026"))
    }

    @Test fun priceListWithoutPaymentIsNotACompletedReceipt() {
        assertEquals(ReceiptValidationStatus.NOT_RECEIPT,
            status("Bảng giá dịch vụ\nĐơn giá 120.000đ\nĐơn giá 150.000đ"))
    }

    @Test fun paymentAmountAndIndependentContextCanBeValidWithoutDate() {
        assertEquals(ReceiptValidationStatus.VALID,
            status("ACME STORE\nTổng cộng: 120.000đ"))
        assertEquals(ReceiptValidationStatus.VALID,
            status("Receipt\nGrand Total 120000"))
    }

    @Test fun provisionalAndPartialBillsRequireConfirmation() {
        assertEquals(ReceiptValidationStatus.UNCERTAIN,
            status("Hội ốc quán\nTỔNG TẠM TÍNH 405,000d"))
        assertEquals(ReceiptValidationStatus.UNCERTAIN,
            status("Nhà hàng An An\nBánh mì 40.000đ"))
        assertEquals(ReceiptValidationStatus.UNCERTAIN,
            status("TOTAL 120000"))
    }

    @Test fun conflictingPaymentValuesDoNotBecomeValid() {
        assertEquals(ReceiptValidationStatus.UNCERTAIN,
            status("Store\nTotal 120000\nTotal 140000"))
    }

    private fun status(text: String) = validator.validate(OcrResultModel(text, emptyList())).status
}
