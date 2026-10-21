package com.example.moneymatev2.presentation

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
class CategoryDeleteStringsTest {
    private fun text(language: String, id: Int): String {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(configuration).getString(id)
    }

    @Test fun deleteFlowHasVietnameseAndEnglishMessages() {
        val expected = listOf(
            R.string.category_delete_title to ("Xóa danh mục" to "Delete category"),
            R.string.category_delete_warning to ("Vui lòng chọn danh mục thay thế cho các giao dịch thuộc danh mục sắp xóa." to
                "Choose a replacement category for transactions in the category you are deleting."),
            R.string.category_delete_confirm_empty to ("Bạn có chắc muốn xóa danh mục này không?" to
                "Are you sure you want to delete this category?"),
            R.string.category_delete_transactions_warning to ("Danh mục này đang có %1\$d giao dịch. Vui lòng chọn danh mục thay thế trước khi xóa." to
                "This category has %1\$d transactions. Select a replacement category before deleting it."),
            R.string.category_move_and_delete to ("Chuyển và xóa" to "Move and delete"),
            R.string.category_delete_changed_need_replacement to ("Danh mục vừa có giao dịch mới. Vui lòng chọn danh mục thay thế trước khi xóa." to
                "A transaction was added to this category. Select a replacement category before deleting it."),
            R.string.category_delete_loading to ("Đang kiểm tra liên kết danh mục…" to "Checking category links…"),
            R.string.category_delete_retry to ("Thử lại" to "Try again"),
            R.string.category_choose_replacement to ("Chọn danh mục thay thế" to "Select a replacement category"),
            R.string.category_no_replacement to ("Không có danh mục khác cùng loại để thay thế. Hãy tạo thêm danh mục trước." to
                "There is no other category of this type. Create one before deleting this category."),
            R.string.category_delete_success to ("Đã xóa danh mục" to "Category deleted"),
            R.string.category_delete_failed to ("Không thể xóa danh mục. Vui lòng thử lại." to
                "Unable to delete category. Please try again."),
            R.string.category_delete_invalid to ("Danh mục đã thay đổi. Vui lòng chọn lại." to
                "The category has changed. Please select it again."),
            R.string.category_delete_in_use to ("Danh mục đang được dùng trong ngân sách hoặc giao dịch định kỳ. Hãy xóa các mục liên quan trước." to
                "This category is used by a budget or recurring transaction. Delete those items first."),
            R.string.category_delete_in_budget to ("Danh mục đang được dùng trong ngân sách. Hãy xử lý ngân sách liên quan trước khi xóa danh mục." to
                "This category is used by a budget. Resolve the linked budget before deleting this category."),
            R.string.category_delete_in_recurring to ("Danh mục đang được dùng trong giao dịch định kỳ. Hãy xử lý giao dịch định kỳ liên quan trước khi xóa danh mục." to
                "This category is used by a recurring transaction. Resolve the linked recurring transaction before deleting this category."),
            R.string.category_delete_in_budget_and_recurring to ("Danh mục đang được dùng trong ngân sách và giao dịch định kỳ. Hãy xử lý các liên kết này trước khi xóa danh mục." to
                "This category is used by a budget and a recurring transaction. Resolve these links before deleting this category."),
            R.string.delete to ("Xóa" to "Delete"),
            R.string.cancel to ("Hủy" to "Cancel"),
            R.string.confirm to ("Xác nhận" to "Confirm")
        )
        expected.forEach { (id, values) ->
            assertEquals(values.first, text("vi", id))
            assertEquals(values.second, text("en", id))
        }
    }
}
