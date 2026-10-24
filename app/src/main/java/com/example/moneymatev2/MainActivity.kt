package com.example.moneymatev2

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.example.moneymatev2.presentation.security.LocalSecurityManager
import com.example.moneymatev2.presentation.security.LocalSecurityState
import com.example.moneymatev2.data.reminder.ReminderNotificationManager
import com.example.moneymatev2.presentation.root.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var localSecurityManager: LocalSecurityManager

    /*
     * Counter thay vì Boolean.
     *
     * Ví dụ:
     *
     * notification 1 -> 0 → 1
     * notification 2 -> 1 → 2
     *
     * Nhờ vậy LaunchedEffect trong AppRoot
     * chạy lại với mỗi lần tap notification.
     */
    private var reminderNavigationRequest
            by mutableStateOf(0L)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        hideNavigationBar()
        lifecycleScope.launch {
            localSecurityManager.state.collect { state ->
                val protected = (state as? LocalSecurityState.Active)?.hasPin == true
                if (protected) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }

        /*
         * Trường hợp app chưa chạy.
         */
        handleNotificationIntent(
            intent
        )

        setContent {

            AppRoot(
                reminderNavigationRequest =
                    reminderNavigationRequest
            )
        }
    }

    override fun onStart() {
        super.onStart()
        localSecurityManager.onForeground()
    }

    override fun onStop() {
        localSecurityManager.onBackground()
        super.onStop()
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )

        /*
         * Cập nhật Intent hiện tại của Activity.
         */
        setIntent(
            intent
        )

        /*
         * Trường hợp MoneyMate đang mở/background
         * rồi user tap notification.
         */
        handleNotificationIntent(
            intent
        )
    }

    private fun handleNotificationIntent(
        intent: Intent?
    ) {
        val reminderId =
            intent
                ?.getStringExtra(
                    ReminderNotificationManager
                        .EXTRA_REMINDER_ID
                )
                ?: return

        /*
         * Hiện tại reminderId chỉ xác nhận đây
         * đúng là Intent đến từ Reminder.
         *
         * Ta chưa mở editor cụ thể theo id.
         */
        if (reminderId.isBlank()) {
            return
        }

        /*
         * Trigger Compose điều hướng.
         */
        reminderNavigationRequest++

        /*
         * Consume extra.
         *
         * Nếu Activity bị recreate do đổi theme,
         * locale..., Intent cũ sẽ không khiến
         * app tự navigate lại Reminder.
         */
        intent.removeExtra(
            ReminderNotificationManager
                .EXTRA_REMINDER_ID
        )
    }

    private fun hideNavigationBar() {
        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView
        )

        controller.hide(
            WindowInsetsCompat.Type.navigationBars()
        )

        controller.systemBarsBehavior =
            WindowInsetsControllerCompat
                .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
