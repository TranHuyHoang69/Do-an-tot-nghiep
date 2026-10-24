package com.example.moneymatev2.presentation.reminder

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeleteDialogAndroidTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cancelDoesNotConfirm() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cancel = context.getString(R.string.cancel)
        val title = context.getString(R.string.delete_reminder_title)
        var confirms = 0
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) ReminderDeleteConfirmationDialog(
                    count = 2, inProgress = false,
                    onDismiss = { visible = false }, onConfirm = { confirms++ }
                )
            }
        }
        compose.onNodeWithText(cancel).performClick()
        compose.onNodeWithText(title).assertDoesNotExist()
        assertEquals(0, confirms)
    }

    @Test fun confirmationIsDisabledDuringDeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val delete = context.getString(R.string.delete)
        var confirms = 0
        compose.setContent {
            var inProgress by remember { mutableStateOf(false) }
            MaterialTheme {
                ReminderDeleteConfirmationDialog(
                    count = 1, inProgress = inProgress,
                    onDismiss = {}, onConfirm = { confirms++; inProgress = true }
                )
            }
        }
        compose.onNodeWithText(delete).performClick()
        compose.onNodeWithText(delete).assertIsNotEnabled()
        assertEquals(1, confirms)
    }
}
