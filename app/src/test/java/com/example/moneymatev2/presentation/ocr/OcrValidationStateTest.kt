package com.example.moneymatev2.presentation.ocr

import com.example.moneymatev2.data.ocr.ReceiptValidationStatus
import com.example.moneymatev2.domain.model.ReceiptModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrValidationStateTest {
    private val extracted = ReceiptModel("Store", 120_000L, 1L, "Store", "local-food")

    @Test fun rejectedAndEmptyImagesNeverExposeExtractedFields() {
        listOf(ReceiptValidationStatus.NOT_RECEIPT, ReceiptValidationStatus.NO_TEXT).forEach { status ->
            val state = validatedOcrState(status, extracted, "owner")
            assertNull(state.receipt)
            assertEquals("owner", state.ownerId)
            assertEquals(if (status == ReceiptValidationStatus.NO_TEXT) OcrPhase.NO_TEXT
                else OcrPhase.NOT_RECEIPT, state.phase)
        }
    }

    @Test fun uncertainDataRemainsAvailableForExplicitReviewAndValidPartialDataIsKept() {
        val uncertain = validatedOcrState(ReceiptValidationStatus.UNCERTAIN, extracted, "owner")
        assertEquals(OcrPhase.UNCERTAIN, uncertain.phase)
        assertEquals(extracted, uncertain.receipt)

        val partial = extracted.copy(totalAmount = null)
        val valid = validatedOcrState(ReceiptValidationStatus.VALID, partial, "owner")
        assertEquals(OcrPhase.INCOMPLETE, valid.phase)
        assertEquals(partial, valid.receipt)
    }
}
