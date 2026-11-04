package com.example.moneymatev2.data.ocr

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.OcrResultModel
import com.example.moneymatev2.domain.model.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Selected, unmodified ML Kit lines from the five local photos; private lines are omitted. */
class RealReceiptFixtureTest {
    private val analyzer = ReceiptAnalyzer()
    private val owner = "fixture-owner"
    private val catalog = listOf("spend_food", "spend_shopping", "spend_transport", "spend_health")
        .map { id -> CategoryModel(owner, id, id, id, TransactionType.EXPENSE,
            "ic_category", "#000000", true, false) }

    @Test fun capturedMlKitLinesPreserveGeometryAndExtractSupportedFields() {
        val expected = mapOf(
            "receipt_01.jpg" to Triple(405_000L, "spend_food", "2026-08-20"),
            "receipt_02.jpg" to Triple(15_000L, "spend_shopping", null),
            "receipt_03.jpg" to Triple(35_500L, "spend_shopping", "2026-08-18"),
            "receipt_04.jpg" to Triple(77_000L, "spend_health", "2026-06-17"),
            "receipt_05.jpg" to Triple(46_500L, "spend_shopping", "2026-08-03")
        )
        val fixtures = loadFixtures()
        assertEquals(expected.keys, fixtures.keys)
        fixtures.forEach { (name, lines) ->
            val result = analyzer.analyze(OcrResultModel(lines.joinToString("\n") { it.text }, lines), catalog, owner)
            val (amount, category, date) = expected.getValue(name)
            assertEquals(name, amount, result.totalAmount)
            assertEquals(name, category, result.suggestedCategoryId)
            if (date != null) {
                assertEquals(name, date, result.transactionDate?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                })
            }
        }
    }

    @Test fun pharmacySignalCannotSelectAnotherUsersOrMissingHealthCategory() {
        val lines = loadFixtures().getValue("receipt_04.jpg")
        val ocr = OcrResultModel(lines.joinToString("\n") { it.text }, lines)
        assertEquals(77_000L, analyzer.analyze(ocr, catalog, owner).totalAmount)
        assertNull(analyzer.analyze(ocr, catalog.filterNot { it.stableId == "spend_health" }, owner)
            .suggestedCategoryId)
        assertNull(analyzer.analyze(ocr, catalog.map { it.copy(userId = "another-owner") }, owner)
            .suggestedCategoryId)
    }

    @Test fun allCapturedReceiptsRemainReviewableAndProvisionalBillIsNotMarkedPaid() {
        val validator = ReceiptValidator()
        loadFixtures().forEach { (name, lines) ->
            val ocr = OcrResultModel(lines.joinToString("\n") { it.text }, lines)
            val status = validator.validate(ocr).status
            assertEquals(name, if (name == "receipt_01.jpg") ReceiptValidationStatus.UNCERTAIN
                else ReceiptValidationStatus.VALID, status)
        }
    }

    private fun loadFixtures(): Map<String, List<OcrTextLine>> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/ocr/real_receipts_redacted.tsv"))
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().drop(1).filter(String::isNotBlank).map { row ->
                val fields = row.split('\t', limit = 6)
                require(fields.size == 6)
                fields[0] to OcrTextLine(fields[5], fields[1].toInt(), fields[2].toInt(),
                    fields[3].toInt(), fields[4].toInt())
            }.groupBy({ it.first }, { it.second })
        }
    }
}
