package com.example.moneymatev2.presentation.budget

import android.app.Application
import android.content.res.Configuration
import com.example.moneymatev2.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BudgetUiStringsTest {
    private fun resources(language: String) = RuntimeEnvironment.getApplication()
        .createConfigurationContext(Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).resources

    @Test fun deleteMessageNamesBudgetAndSaysTransactionsRemainInBothLanguages() {
        assertEquals("Xóa ngân sách “Ăn uống”? Các giao dịch đã ghi nhận vẫn được giữ lại.",
            resources("vi").getString(R.string.budget_confirm_delete, "Ăn uống"))
        assertEquals("Delete the budget for “Food”? Existing transactions will be kept.",
            resources("en").getString(R.string.budget_confirm_delete, "Food"))
    }

    @Test fun englishConflictNoticeUsesSingularAndPluralWithoutClaimingSyncSuccess() {
        val en = resources("en")
        assertEquals("1 budget sync conflict needs attention. The copy on this device has been kept.",
            en.getQuantityString(R.plurals.budget_sync_conflict_notice, 1, 1))
        assertEquals("2 budget sync conflicts need attention. The copy on this device has been kept.",
            en.getQuantityString(R.plurals.budget_sync_conflict_notice, 2, 2))
        val vi = resources("vi")
        assertEquals("Có 2 trường hợp xung đột ngân sách. Bản lưu trên thiết bị này được giữ lại.",
            vi.getQuantityString(R.plurals.budget_sync_conflict_notice, 2, 2))
    }
}
