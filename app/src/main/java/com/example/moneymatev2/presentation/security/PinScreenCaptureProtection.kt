package com.example.moneymatev2.presentation.security

import android.content.pm.ApplicationInfo
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/** Protect only the visible PIN UI in release builds. */
@Composable
internal fun PinScreenCaptureProtection() {
    val activity = BiometricAuthHelper.findFragmentActivity(LocalContext.current) ?: return
    if ((activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) return

    DisposableEffect(activity) {
        SecurePinWindows.show(activity.window)
        onDispose { SecurePinWindows.hide(activity.window) }
    }
}

/** Navigation can briefly compose two PIN screens at once. Keep the flag until both leave. */
private object SecurePinWindows {
    private val visibleScreens = WeakHashMap<Window, Int>()

    fun show(window: Window) {
        val count = visibleScreens[window] ?: 0
        if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        visibleScreens[window] = count + 1
    }

    fun hide(window: Window) {
        val count = visibleScreens[window] ?: return
        if (count == 1) {
            visibleScreens.remove(window)
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            visibleScreens[window] = count - 1
        }
    }
}
