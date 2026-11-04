package com.example.moneymatev2.data.ocr

import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.domain.model.CategoryModel
import com.example.moneymatev2.domain.model.OcrResultModel
import com.example.moneymatev2.domain.model.OcrTextLine
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone

class ReceiptAnalyzerTest {
    private val analyzer = ReceiptAnalyzer()

    @Test fun totalPrefersPaymentOverItemsAndCashGiven() {
        assertEquals(120_000L, analyzer.findTotal(listOf(
            "Cà phê 40.000đ", "Bánh 80.000đ", "Tổng cộng: 120.000đ", "Tiền khách đưa: 200.000đ"
        )))
        assertEquals(120_000L, analyzer.findTotal(listOf("TOTAL", "120000")))
    }

    @Test fun amountRejectsPhoneDecimalsOverflowAndAmbiguousValues() {
        assertNull(analyzer.findTotal(listOf("SĐT: 0912345678", "Mã hóa đơn 123456")))
        assertNull(analyzer.parseAmount("120.50"))
        assertNull(analyzer.parseAmount("999999999999999999999999"))
        assertNull(analyzer.findTotal(listOf("Cà phê 40.000đ", "Bánh 80.000đ")))
        assertEquals(120_000L, analyzer.parseAmount("120,000 VND"))
        assertEquals(120_000L, analyzer.parseAmount("120 000"))
    }

    @Test fun datesAreValidatedInLocalTimezone() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
            val date = analyzer.findDate("Ngày: 20/09/2026 14:30")!!
            assertEquals("2026-09-20", Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate().toString())
            assertNotNull(analyzer.findDate("20-9-26"))
            assertNull(analyzer.findDate("31/02/2026"))
            assertNull(analyzer.findDate("Receipt without date"))
        } finally { TimeZone.setDefault(original) }
    }

    @Test fun categoryUsesOnlyExistingOwnedExpenseStableIds() {
        val food = category("a", "spend_food")
        val otherOwner = category("b", "spend_transport")
        assertEquals(food.id, analyzer.suggestCategory("Nhà hàng", listOf(food, otherOwner), "a"))
        assertNull(analyzer.suggestCategory("Taxi", listOf(food, otherOwner), "a"))
        assertNull(analyzer.suggestCategory("Restaurant", listOf(food.copy(isArchived = true)), "a"))
        assertEquals(food.id, analyzer.suggestCategory("Restaurant", listOf(food), "a"))
    }

    @Test fun totalLabelsWorkInVietnameseAndEnglish() {
        listOf(
            "TỔNG CỘNG: 120.000đ", "TỔNG TIỀN 120.000 VNĐ",
            "THANH TOÁN 120,000", "GRAND TOTAL 120000",
            "Số tiền phải trả: 120 000 VND", "Amount Payable: 120000",
            "T0NG C0NG 120000"
        ).forEach { assertEquals(it, 120_000L, analyzer.findTotal(listOf(it))) }
    }

    @Test fun groupedAmountsAndZeroDecimalsAreParsedWithoutGuessingNonzeroFractions() {
        listOf("120.000đ", "120,000đ", "120 000 VND", "120000",
            "120.000 VNĐ", "120,000 VND", "120.000,00", "120,000.00")
            .forEach { assertEquals(it, 120_000L, analyzer.parseAmount(it)) }
        assertNull(analyzer.parseAmount("120.000,50"))
        assertNull(analyzer.parseAmount("120,00,000"))
        assertNull(analyzer.parseAmount("99999999999999999999999"))
    }

    @Test fun subtotalIsFallbackButPaymentBeatsItAndCashGiven() {
        assertEquals(120_000L, analyzer.findTotal(listOf(
            "Đơn giá 80.000đ", "Tạm tính 140.000đ", "Giảm giá 20.000đ",
            "Tổng thanh toán: 120.000đ", "Tiền khách đưa 200.000đ", "Tiền thối 80.000đ"
        )))
        assertEquals(120_000L, analyzer.findTotal(listOf(
            "Thành tiền: 80.000đ", "Subtotal: 120.000đ",
            "Cash Received: 200.000đ", "Change: 80.000đ"
        )))
        assertEquals(120_000L, analyzer.findTotal(listOf(
            "Tổng giá trị 140.000đ", "VAT: 10.000đ", "Phải trả: 120.000đ"
        )))
    }

    @Test fun labelCanBeAboveAmountOrSeparatedAcrossSameVisualRow() {
        assertEquals(120_000L, analyzer.findTotal(listOf("Tổng thanh toán", "120.000")))
        val layout = OcrResultModel("Tổng cộng 120000", listOf(
            OcrTextLine("120000", top = 100, left = 300, right = 390, bottom = 123),
            OcrTextLine("Ngày", top = 102, left = 200, right = 250, bottom = 123),
            OcrTextLine("TỔNG CỘNG", top = 105, left = 10, right = 150, bottom = 126)
        ))
        assertEquals(120_000L, analyzer.analyze(layout, emptyList(), "a").totalAmount)
    }

    @Test fun identifiersDatesQuantitiesAndAmbiguousItemPricesDoNotBecomeTotal() {
        assertNull(analyzer.findTotal(listOf(
            "Ngày 20/09/2026", "SĐT 0912345678", "Mã giao dịch 123456789",
            "Số lượng 2", "Cà phê 40.000đ", "Bánh mì 80.000đ"
        )))
        assertNull(analyzer.findTotal(listOf("Tổng cộng 120.000đ 140.000đ")))
        assertNull(analyzer.findTotal(listOf("Tổng cộng -120.000đ")))
        assertNull(analyzer.findTotal(listOf("Tiền khách đưa: 200.000đ", "Tiền trả lại: 80.000đ")))
        assertEquals(120_000L, analyzer.findTotal(listOf("Tổng cộng 120.000,00 VNĐ")))
    }

    @Test fun merchantAndItemsGuideCategoriesWithoutUsingAmount() {
        val food = category("a", "spend_food")
        val shopping = category("a", "spend_shopping")
        val transport = category("a", "spend_transport")
        val categories = listOf(food, shopping, transport)
        val market = analyzer.analyze(OcrResultModel("WinMart\nCoffee 40.000đ", emptyList()), categories, "a")
        assertEquals(shopping.id, market.suggestedCategoryId)
        assertNull(market.totalAmount)
        assertEquals(food.id, analyzer.suggestCategory("Trà sữa / Milk tea", categories, "a"))
        assertEquals(food.id, analyzer.suggestCategory("Bánh mì và phở", categories, "a"))
        assertEquals(transport.id, analyzer.suggestCategory("GrabCar", categories, "a"))
        assertEquals(food.id, analyzer.suggestCategory("GrabFood", categories, "a"))
        assertEquals(shopping.id, analyzer.suggestCategory("GrabMart", categories, "a"))
        assertEquals(transport.id, analyzer.suggestCategory("Vé xe", categories, "a"))
        assertEquals(shopping.id, analyzer.suggestCategory("Grocery", categories, "a"))
    }

    @Test fun healthRequiresRealOwnedCategoryAndAmbiguousCluesReturnNull() {
        val food = category("a", "spend_food")
        val transport = category("a", "spend_transport")
        val health = category("a", "custom_health").copy(stableId = null, name = "Health")
        assertNull(analyzer.suggestCategory("Nhà thuốc Pharmacy", listOf(health), "a"))
        assertNull(analyzer.suggestCategory("Nhà thuốc", listOf(food, transport), "a"))
        assertNull(analyzer.suggestCategory("Coffee Taxi", listOf(food, transport), "a"))
        assertNull(analyzer.suggestCategory("Business Smart", listOf(food, transport), "a"))
        assertNull(analyzer.suggestCategory("Grab", listOf(food, transport), "a"))
        assertNull(analyzer.suggestCategory("Phô mai", listOf(food, transport), "a"))
        assertNull(analyzer.suggestCategory("Coffee", listOf(food.copy(userId = "b")), "a"))
        assertNull(analyzer.suggestCategory("Coffee", listOf(food.copy(type = TransactionType.INCOME)), "a"))
    }

    @Test fun categorySignalsCoverSpecializedBusinessesAndServices() {
        val catalog = listOf("spend_food", "spend_shopping", "spend_health", "spend_transport",
            "spend_gift", "spend_entertainment", "spend_other", "income_salary")
            .map { category("a", it).copy(type = if (it == "income_salary")
                TransactionType.INCOME else TransactionType.EXPENSE) }
        val examples = mapOf(
            "Highlands Coffee\nCà phê sữa" to "spend_food",
            "GrabFood giao đồ ăn" to "spend_food",
            "WinMart\nCà phê\nBánh mì" to "spend_shopping",
            "GrabMart" to "spend_shopping",
            "NHÀ THUỐC LONG CHÂU\nVitamin C" to "spend_health",
            "GrabCar" to "spend_transport",
            "GrabBike" to "spend_transport",
            "Trạm xăng\nXăng RON95" to "spend_transport",
            "CGV\nVé xem phim" to "spend_entertainment",
            "Vé karaoke" to "spend_entertainment",
            "Cửa hàng quà tặng\nGift wrapping" to "spend_gift"
        )
        examples.forEach { (text, stableId) ->
            assertEquals(text, category("a", stableId).id,
                analyzer.suggestCategory(text, catalog, "a"))
        }
    }

    @Test fun categoryClassificationAvoidsWeakAndConflictingEvidence() {
        val catalog = listOf("spend_food", "spend_shopping", "spend_health", "spend_transport",
            "spend_gift", "spend_entertainment", "spend_other", "income_salary")
            .map { category("a", it) }
        listOf("Hóa đơn bán lẻ", "Phô mai", "Gift card", "Grab", "Áo thun game",
            "Nhà hàng và nhà thuốc", "Coffee Taxi").forEach { text ->
            assertNull(text, analyzer.suggestCategory(text, catalog, "a"))
        }
        assertEquals(category("a", "spend_shopping").id,
            analyzer.suggestCategory("Cửa hàng quần áo\nÁo thun", catalog, "a"))
        assertNull(analyzer.suggestCategory("Salary\nLương tháng", catalog, "a"))
    }

    @Test fun stableIdMappingRequiresUniqueActiveOwnedDefaultExpense() {
        val health = category("a", "spend_health").copy(id = "local-health")
        val personal = health.copy(id = "personal-health", stableId = null,
            name = "Sức khỏe", isDefault = false)
        val text = "Nhà thuốc Long Châu"
        assertEquals("local-health", analyzer.suggestCategory(text, listOf(personal, health), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(personal), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health.copy(userId = "b")), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health.copy(isArchived = true)), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health.copy(isMissing = true)), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health.copy(type = TransactionType.INCOME)), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health.copy(isDefault = false)), "a"))
        assertNull(analyzer.suggestCategory(text, listOf(health, health.copy(id = "duplicate")), "a"))
    }

    @Test fun partialResultsKeepRecognizedFieldsIndependent() {
        val food = category("a", "spend_food")
        val withoutCategory = analyzer.analyze(
            OcrResultModel("Unknown Store\nTổng cộng: 120000\nNgày: 20/09/2026", emptyList()),
            listOf(food), "a"
        )
        assertEquals(120_000L, withoutCategory.totalAmount)
        assertNull(withoutCategory.suggestedCategoryId)
        assertNotNull(withoutCategory.transactionDate)
        assertEquals("Unknown Store", withoutCategory.note)

        val withoutAmount = analyzer.analyze(
            OcrResultModel("Nhà hàng An An\nNgày: 20/09/2026", emptyList()), listOf(food), "a"
        )
        assertNull(withoutAmount.totalAmount)
        assertEquals(food.id, withoutAmount.suggestedCategoryId)
        assertNotNull(withoutAmount.transactionDate)
        assertEquals("Nhà hàng An An", withoutAmount.note)
    }

    private fun category(owner: String, stableId: String) = CategoryModel(
        owner, "$owner-$stableId", stableId, stableId, TransactionType.EXPENSE,
        "ic_food", "#123456", true, false
    )
}
