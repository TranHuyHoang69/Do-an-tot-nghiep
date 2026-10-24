package com.example.moneymatev2

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import com.example.moneymatev2.presentation.root.AppRootViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var localSecurityManager: LocalSecurityManager
    private val rootViewModel: AppRootViewModel by viewModels()
    private var notificationNavigationReady by mutableStateOf(false)

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
        if (savedInstanceState != null) {
            intent?.removeExtra(ReminderNotificationManager.EXTRA_REMINDER_ID)
            intent?.removeExtra(ReminderNotificationManager.EXTRA_OWNER_UID)
        }

        setContent {

            AppRoot(rootViewModel = rootViewModel, notificationNavigationReady = notificationNavigationReady)
        }
    }

    override fun onStart() {
        super.onStart()
        localSecurityManager.onForeground()
        notificationNavigationReady = true
        handleNotificationIntent(intent)
    }

    override fun onStop() {
        notificationNavigationReady = false
        localSecurityManager.onBackground()
        super.onStop()
    }

    override fun onNewIntent(
        intent: Intent
    ) {
        super.onNewIntent(
            intent
        )
        setIntent(
            intent
        )
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
            handleNotificationIntent(intent)
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val reminderId = intent.getStringExtra(ReminderNotificationManager.EXTRA_REMINDER_ID)
        val ownerUid = intent.getStringExtra(ReminderNotificationManager.EXTRA_OWNER_UID)
        // SavedStateHandle keeps accepted requests; Activity extras must not replay on recreation.
        intent.removeExtra(ReminderNotificationManager.EXTRA_REMINDER_ID)
        intent.removeExtra(ReminderNotificationManager.EXTRA_OWNER_UID)
        if (!reminderId.isNullOrBlank() && !ownerUid.isNullOrBlank()) {
            rootViewModel.offerReminderNavigation(reminderId, ownerUid)
        }
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
