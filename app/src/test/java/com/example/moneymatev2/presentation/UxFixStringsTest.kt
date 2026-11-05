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
class UxFixStringsTest {
    @Test fun categoryGoogleAndStatisticsFeedbackHaveVietnameseAndEnglishText() {
        val strings = listOf(
            R.string.category_manage_actions to ("Quản lý danh mục" to "Manage category"),
            R.string.category_default_actions_blocked to ("Không thể chỉnh sửa/xóa danh mục mặc định" to
                "Default categories cannot be edited or deleted."),
            R.string.edit_category_action to ("Chỉnh sửa" to "Edit"),
            R.string.google_sign_in_failed to ("Đăng nhập Google không thành công. Vui lòng thử lại." to
                "Google sign-in failed. Please try again."),
            R.string.statistics_error to ("Không thể tải dữ liệu thống kê." to
                "Unable to load statistics.")
        )
        strings.forEach { (id, expected) ->
            assertEquals(expected.first, text("vi", id))
            assertEquals(expected.second, text("en", id))
        }
    }

    private fun text(language: String, id: Int): String {
        val base = RuntimeEnvironment.getApplication()
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return base.createConfigurationContext(configuration).getString(id)
    }
}
