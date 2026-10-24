package com.example.moneymatev2.data.local

import android.os.Build
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.data.local.entity.PendingOperation
import com.example.moneymatev2.data.local.entity.SyncStatus
import com.example.moneymatev2.data.local.migration.MIGRATION_1_2
import com.example.moneymatev2.data.local.migration.MIGRATION_2_3
import com.example.moneymatev2.data.local.migration.MIGRATION_3_4
import com.example.moneymatev2.data.repository.DefaultCategoryIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class InstalledV2UpgradeAndroidTest {
    private val tables = listOf(
        "budgets", "categories", "recurring_transactions", "reminders", "transactions", "users"
    )

    @Test fun installedV2Upgrade_preservesOriginalRowsAndRoomReopens() {
        val expected = expectedV2Rows()
        openRoom().use { room ->
            val sql = room.openHelper.writableDatabase
            assertEquals(4, sql.version)
            sql.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("efd12592c9e3ae9c98876dfa73ed58a2", cursor.getString(0))
            }
            val actual = snapshot(sql)
            for (table in tables) {
                assertEquals("$table rows", expected.getJSONArray(table).toString(), actual.getJSONArray(table).toString())
                assertEquals("$table count", 4, actual.getJSONArray(table).length())
            }
            sql.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        }
    }

    @Test fun installedV2Upgrade_acceptsSharedLegacyUuidForTwoOwners() = runBlocking {
        expectedV2Rows()
        openRoom().use { room ->
            val categories = room.categoryDao()
            val legacyId = DefaultCategoryIds.SPEND_FOOD
            val ownerA = categories.getCategoryByLocalIdOnce("account-a", legacyId)
            assertNotNull(ownerA)
            val original = ownerA!!
            assertTrue(original.isArchived)
            if (categories.getCategoryByLocalIdOnce("account-b", legacyId) == null) {
                categories.insertCategory(original.copy(
                    userId = "account-b", name = "Owner B", isArchived = false,
                    syncStatus = SyncStatus.PENDING, pendingOperation = PendingOperation.CREATE,
                    updatedAt = original.updatedAt + 1
                ))
            }
            assertEquals(original, categories.getCategoryByLocalIdOnce("account-a", legacyId))
            val ownerB = categories.getCategoryByLocalIdOnce("account-b", legacyId)
            assertNotNull(ownerB)
            assertEquals("Owner B", ownerB!!.name)
            assertEquals(PendingOperation.CREATE, ownerB.pendingOperation)
            assertEquals(original, categories.getCategoryByLocalIdOnce("account-a", legacyId))
            for (table in listOf("transactions", "budgets", "recurring_transactions")) {
                room.openHelper.writableDatabase.query(
                    "SELECT COUNT(*) FROM `$table` AS item JOIN categories AS category " +
                        "ON category.userId = item.userId AND category.localId = item.categoryLocalId " +
                        "WHERE item.userId = ? AND item.categoryLocalId = ?",
                    arrayOf("account-b", legacyId)
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("$table owner B link", 1, cursor.getInt(0))
                }
            }
        }
    }

    @Test fun restoredV2Snapshot_canUpgradeToV4() {
        val expected = expectedV2Rows()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (name in listOf("gate-restored-v2.db", "gate-host-import-v2.db")) {
            assertTrue(context.getDatabasePath(name).exists())
            openRoom(name).use { room ->
                val sql = room.openHelper.writableDatabase
                assertEquals(4, sql.version)
                val actual = snapshot(sql)
                for (table in tables) {
                    assertEquals("$name $table", expected.getJSONArray(table).toString(), actual.getJSONArray(table).toString())
                }
                sql.query("PRAGMA integrity_check").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("ok", cursor.getString(0))
                }
            }
        }
    }

    @Test fun installedV2Upgrade_screenDataSourcesRemainReadable() = runBlocking {
        expectedV2Rows()
        openRoom().use { room ->
            val transactionsA = room.transactionDao().getAllTransactions("account-a").first()
            assertEquals(3, transactionsA.size)
            assertEquals(1, room.transactionDao().getAllTransactions("account-b").first().size)
            assertEquals(DefaultCategoryIds.SPEND_FOOD,
                transactionsA.single { it.localId == "transactions-0" }.categoryLocalId)

            val categoriesA = room.categoryDao().getAllCategories("account-a").first()
            assertEquals(4, categoriesA.size)
            assertEquals(3, room.categoryDao().getActiveCategories("account-a").first().size)
            assertTrue(categoriesA.single { it.localId == DefaultCategoryIds.SPEND_FOOD }.isArchived)
            assertEquals(2, room.budgetDao().getActiveBudgetsForMonth("account-a", 2026, 10).first().size)
            assertEquals(1, room.budgetDao().getActiveBudgetsForMonth("account-b", 2026, 10).first().size)

            assertNotNull(room.reminderDao().getReminderByLocalIdOnce("reminders-0"))
            assertEquals(0, room.reminderDao().getActiveReminders("account-a").first().size)
            assertEquals(3, room.recurringTransactionDao().observeAll("account-a").first().size)
            assertEquals(1, room.recurringTransactionDao().observeAll("account-b").first().size)
        }
    }

    @Test fun vacuumSnapshot_includesCommittedPendingWriteFromWal() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseGate") == "verify-wal")
        assertTrue(Build.FINGERPRINT.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = System.currentTimeMillis().toString()
        val source = context.getDatabasePath("gate-wal-source-$suffix.db")
        val snapshot = File(context.filesDir, "gate-wal-snapshot-$suffix.db")
        val restored = context.getDatabasePath("gate-wal-restored-$suffix.db")
        assertFalse(source.exists())
        assertFalse(snapshot.exists())
        assertFalse(restored.exists())
        File(context.filesDir, "release-gate-v2-backup.db").copyTo(source)

        val connection = SQLiteDatabase.openDatabase(source.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            assertTrue(connection.enableWriteAheadLogging())
            connection.execSQL("UPDATE categories SET lastSyncError = ? WHERE localId = ?",
                arrayOf("wal-marker", "category-1"))
            assertTrue(File("${source.path}-wal").length() > 0)
            connection.execSQL("VACUUM INTO ?", arrayOf(snapshot.path))
        } finally {
            connection.close()
        }

        assertTrue(snapshot.length() > 0)
        snapshot.copyTo(restored)
        openRoom(restored.name).use { room ->
            assertEquals(4, room.openHelper.writableDatabase.version)
            val category = runBlocking { room.categoryDao().getCategoryByLocalIdOnce("account-a", "category-1") }
            assertNotNull(category)
            assertEquals("wal-marker", category!!.lastSyncError)
            assertEquals(PendingOperation.CREATE, category.pendingOperation)
            room.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
        }
    }

    private fun expectedV2Rows(): JSONObject {
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseGate") == "verify-v3")
        assertTrue(Build.FINGERPRINT.contains("sdk_gphone") || Build.FINGERPRINT.contains("generic"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return JSONObject(File(context.filesDir, "release-gate-v2.json").readText()).getJSONObject("tables")
    }

    private fun snapshot(sql: SupportSQLiteDatabase): JSONObject = JSONObject().also { result ->
        for (table in tables) {
            val rows = JSONArray()
            val filter = if (table == "categories") " WHERE userId = 'account-a'" else ""
            sql.query("SELECT * FROM `$table`$filter ORDER BY ${if (table == "users") "userId" else "localId"}").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONArray()
                    for (index in 0 until cursor.columnCount) {
                        if (cursor.getColumnName(index) == "anchorAt") continue
                        row.put(if (cursor.isNull(index)) JSONObject.NULL else cursor.getString(index))
                    }
                    rows.put(row)
                }
            }
            result.put(table, rows)
        }
    }

    private fun openRoom(name: String = AppDatabase.DATABASE_NAME): AppDatabase {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }
}
