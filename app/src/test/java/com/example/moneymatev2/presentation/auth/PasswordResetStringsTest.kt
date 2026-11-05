package com.example.moneymatev2.presentation.auth

import android.app.Application
import android.content.res.Configuration
import com.example.moneymatev2.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class PasswordResetStringsTest {
    private fun text(language: String, id: Int): String {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }
        return app.createConfigurationContext(configuration).getString(id)
    }

    @Test fun acceptedMessageIsLocalizedAndDoesNotAssertAccountExistsOrEmailWasDelivered() {
        assertEquals("Vui lòng nhập địa chỉ email hợp lệ.",
            text("vi", R.string.password_reset_invalid_email))
        assertEquals("Please enter a valid email address.",
            text("en", R.string.password_reset_invalid_email))
        val english = text("en", R.string.password_reset_accepted)
        assertEquals("If this email is linked to an account that supports password reset, " +
            "you'll receive instructions by email. Please also check your spam folder.", english)
        assertFalse(english.contains("account exists", ignoreCase = true))
        assertFalse(english.contains("sent successfully", ignoreCase = true))
        assertEquals("Nếu email này được liên kết với tài khoản hỗ trợ đặt lại mật khẩu, " +
            "bạn sẽ nhận được hướng dẫn qua email. Vui lòng kiểm tra cả thư mục Spam.",
            text("vi", R.string.password_reset_accepted))
    }
}
