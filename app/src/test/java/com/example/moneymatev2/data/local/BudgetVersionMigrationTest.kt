package com.example.moneymatev2.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.example.moneymatev2.data.local.migration.MIGRATION_4_5
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BudgetVersionMigrationTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun v4RowsAndPendingOperationsSurviveUpgradeWithoutInventedRevision() = runBlocking {
        val file = File(temporary.newFolder(), "budget-v4.db")
        val schema = JSONObject(javaClass.classLoader!!.getResourceAsStream(
            "com.example.moneymatev2.data.local.AppDatabase/4.json"
        )!!.bufferedReader().use { it.readText() }).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                sqlite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: JSONArray()
                for (j in 0 until indices.length()) {
                    sqlite.execSQL(indices.getJSONObject(j).getString("createSql")
                        .replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) sqlite.execSQL(setup.getString(i))
            fun budget(id: String, uid: String, amount: Long, archived: Int, pending: String) {
                sqlite.execSQL("""INSERT INTO budgets
                    (localId,userId,categoryLocalId,amountMinor,currency,effectiveMonth,effectiveYear,
                     isArchived,syncStatus,pendingOperation,retryCount,createdAt,updatedAt)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""".trimIndent(), arrayOf<Any?>(
                    id, uid, "food-$id", amount, "VND", 10, 2026, archived,
                    if (pending == "NONE") "SYNCED" else "PENDING", pending, 0, 1L, 2L
                ))
            }
            budget("clean", "A", 0L, 0, "NONE")
            budget("pending", "A", 2_000_000L, 0, "UPDATE")
            budget("tombstone", "A", 1_000_000L, 1, "UPDATE")
            budget("other-uid", "B", 3_000_000L, 0, "NONE")
            sqlite.execSQL("""INSERT INTO categories
                (localId,userId,name,type,iconKey,colorHex,isDefault,isArchived,syncStatus,
                 pendingOperation,retryCount,createdAt,updatedAt)
                 VALUES ('category','A','Food','EXPENSE','food','#000000',0,0,'SYNCED','NONE',0,1,2)""")
            sqlite.execSQL("""INSERT INTO transactions
                (localId,userId,type,amountMinor,currency,categoryLocalId,syncStatus,
                 pendingOperation,isDeleted,createdAt,updatedAt,retryCount)
                 VALUES ('tx','A','EXPENSE',100,'VND','category','SYNCED','NONE',0,1,2,0)""")
            sqlite.execSQL("""INSERT INTO reminders
                (localId,userId,title,triggerAt,repeatRule,isActive,syncStatus,pendingOperation,
                 retryCount,createdAt,updatedAt)
                 VALUES ('reminder','A','Pay',100,'MONTHLY',1,'SYNCED','NONE',0,1,2)""")
            sqlite.execSQL("""INSERT INTO recurring_transactions
                (localId,userId,type,amountMinor,currency,categoryLocalId,frequency,startAt,
                 timeZoneId,nextOccurrenceIndex,nextRunAt,isActive,isDeleted,syncStatus,
                 pendingOperation,createdAt,updatedAt,retryCount)
                 VALUES ('recurring','A','EXPENSE',100,'VND','category','MONTHLY',1,'UTC',0,100,
                         1,0,'SYNCED','NONE',1,2,0)""")
            sqlite.version = 4
        }
        val room = Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java,
            file.absolutePath).addMigrations(MIGRATION_4_5).allowMainThreadQueries().build()
        try {
            assertEquals(5, room.openHelper.writableDatabase.version)
            val clean = room.budgetDao().getRowForCloudMerge("clean")!!
            val pending = room.budgetDao().getRowForCloudMerge("pending")!!
            val tombstone = room.budgetDao().getRowForCloudMerge("tombstone")!!
            assertEquals(0L, clean.amountMinor)
            assertNull(clean.cloudRevision)
            assertNull(clean.pendingUploadId)
            assertEquals(2_000_000L, pending.amountMinor)
            assertNull(pending.baseRevision)
            assertNotNull(pending.pendingUploadId)
            assertTrue(tombstone.isArchived)
            assertNotNull(tombstone.pendingUploadId)
            assertEquals("B", room.budgetDao().getRowForCloudMerge("other-uid")!!.userId)
            assertEquals(2, room.budgetDao().getPendingBudgetsForSync("A").size)
            assertTrue(room.budgetConflictDao().getForBudget("A", "pending").isEmpty())
            val db = room.openHelper.writableDatabase
            for (table in listOf("categories", "transactions", "reminders", "recurring_transactions")) {
                db.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }
        } finally { room.close() }
    }
}
