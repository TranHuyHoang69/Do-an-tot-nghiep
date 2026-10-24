package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.remote.sync.AuthSessionVersion
import com.example.moneymatev2.data.remote.sync.PendingUploadRunner
import com.example.moneymatev2.data.remote.sync.SyncSessionGuard
import com.example.moneymatev2.data.remote.sync.SyncTrigger
import com.example.moneymatev2.domain.reminder.ReminderRepeatCalculator
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderAnchorRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReminderRepositoryImpl
    private lateinit var scheduler: ReminderScheduler
    private lateinit var auth: AuthRepository

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenReturn("account-a")
        scheduler = mock(ReminderScheduler::class.java)
        repository = ReminderRepositoryImpl(
            database.reminderDao(), mock(FirebaseFirestore::class.java), mock(SyncTrigger::class.java),
            PendingUploadRunner(database, SyncSessionGuard(auth, AuthSessionVersion())), auth
        )
    }

    @After fun tearDown() { database.close() }

    @Test fun newlyCreatedAnchorSurvivesNextTriggerUpdate() = runBlocking {
        val future = Calendar.getInstance().apply {
            add(Calendar.YEAR, 2)
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 31)
        }.timeInMillis
        val created = repository.createReminder("account-a", "Pay bill", null, future, RepeatRule.MONTHLY)
        assertEquals(future, created.anchorAt)

        val next = ReminderRepeatCalculator().calculateNextTrigger(future, RepeatRule.MONTHLY, future, created.anchorAt)!!
        repository.updateReminderTrigger(created.id, next)
        val stored = database.reminderDao().getReminderByLocalIdOnce(created.id)!!
        assertEquals(next, stored.triggerAt)
        assertEquals(future, stored.anchorAt)
    }

    @Test fun repeatedRescheduleKeepsMonthlyAnchorAndLegacyYearlyEnabled() = runBlocking {
        val anchor = at(2024, 1, 31)
        database.reminderDao().insertReminder(row("monthly", at(2024, 2, 29), RepeatRule.MONTHLY, anchor))
        database.reminderDao().insertReminder(row("yearly", at(2024, 2, 29), RepeatRule.YEARLY, null))
        val reschedule = RescheduleRemindersUseCase(repository, scheduler, ReminderRepeatCalculator(), auth)

        reschedule("account-a")
        val firstMonthly = database.reminderDao().getReminderByLocalIdOnce("monthly")!!
        val firstYearly = database.reminderDao().getReminderByLocalIdOnce("yearly")!!
        assertEquals(anchor, firstMonthly.anchorAt)
        assertTrue(firstMonthly.triggerAt > System.currentTimeMillis())
        assertTrue(firstYearly.triggerAt > System.currentTimeMillis())
        assertTrue(firstYearly.isActive)
        assertEquals(RepeatRule.YEARLY, firstYearly.repeatRule)

        reschedule("account-a")
        val secondMonthly = database.reminderDao().getReminderByLocalIdOnce("monthly")!!
        assertEquals(firstMonthly.triggerAt, secondMonthly.triggerAt)
        assertEquals(anchor, secondMonthly.anchorAt)
        assertNotNull(database.reminderDao().getReminderByLocalIdOnce("yearly"))
    }

    private fun row(id: String, trigger: Long, rule: RepeatRule, anchor: Long?) = ReminderEntity(
        localId = id,
        userId = "account-a",
        title = id,
        note = null,
        triggerAt = trigger,
        repeatRule = rule,
        isActive = true,
        syncStatus = SyncStatus.SYNCED,
        pendingOperation = PendingOperation.NONE,
        createdAt = 1L,
        updatedAt = 2L,
        anchorAt = anchor
    )

    private fun at(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 9, 30)
    }.timeInMillis
}
