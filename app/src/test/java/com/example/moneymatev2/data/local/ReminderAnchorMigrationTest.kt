package com.example.moneymatev2.data.local

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.moneymatev2.data.local.entity.RepeatRule
import com.example.moneymatev2.data.local.migration.MIGRATION_3_4
import com.example.moneymatev2.data.remote.dto.ReminderDto
import com.example.moneymatev2.data.remote.dto.toEntity
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class ReminderAnchorMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun v3RowsKeepTheirDatesAndUnknownOriginalDayAfterMigration() {
        val file = File(temporary.newFolder(), "reminder-v3.db")
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream(
            "com.example.moneymatev2.data.local.AppDatabase/3.json"
        )!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (item in 0 until indices.length()) {
                    database.execSQL(indices.getJSONObject(item).getString("createSql")
                        .replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) database.execSQL(setup.getString(index))
            for ((id, rule, trigger) in listOf(
                Triple("monthly", "MONTHLY", 1803778200000L),
                Triple("yearly", "YEARLY", 1835433000000L)
            )) {
                database.insertOrThrow("reminders", null, ContentValues().apply {
                    put("localId", id)
                    put("userId", "account-a")
                    put("title", id)
                    put("triggerAt", trigger)
                    put("repeatRule", rule)
                    put("isActive", 1)
                    put("syncStatus", "SYNCED")
                    put("pendingOperation", "NONE")
                    put("retryCount", 0)
                    put("createdAt", 1L)
                    put("updatedAt", 2L)
                })
            }
            database.version = 3
        }

        val database = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_3_4).allowMainThreadQueries().build()
        try {
            assertEquals(4, database.openHelper.writableDatabase.version)
            val monthly = kotlinx.coroutines.runBlocking { database.reminderDao().getReminderByLocalIdOnce("monthly")!! }
            val yearly = kotlinx.coroutines.runBlocking { database.reminderDao().getReminderByLocalIdOnce("yearly")!! }
            assertEquals(1803778200000L, monthly.triggerAt)
            assertEquals(1835433000000L, yearly.triggerAt)
            assertEquals(RepeatRule.MONTHLY, monthly.repeatRule)
            assertEquals(RepeatRule.YEARLY, yearly.repeatRule)
            assertEquals(true, yearly.isActive)
            assertNull(monthly.anchorAt)
            assertNull(yearly.anchorAt)
        } finally {
            database.close()
        }
    }

    @Test fun oldCloudDocumentWithoutAnchorStillParsesAsYearly() {
        val row = ReminderDto(triggerAt = 100L, repeatRule = "YEARLY").toEntity(
            "old", "account-a", SyncStatus.SYNCED, PendingOperation.NONE
        )
        assertEquals(RepeatRule.YEARLY, row.repeatRule)
        assertNull(row.anchorAt)
    }
}
