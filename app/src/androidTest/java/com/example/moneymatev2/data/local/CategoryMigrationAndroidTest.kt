package com.example.moneymatev2.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.moneymatev2.data.local.migration.MIGRATION_1_2
import com.example.moneymatev2.data.local.migration.MIGRATION_2_3
import com.example.moneymatev2.data.local.migration.MIGRATION_3_4
import com.example.moneymatev2.data.repository.DefaultCategoryIds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryMigrationAndroidTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java
    )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val legacyId = DefaultCategoryIds.SPEND_FOOD
    private val tables = listOf(
        "budgets", "categories", "recurring_transactions", "reminders", "transactions", "users"
    )
    private val operations = listOf("NONE", "CREATE", "UPDATE", "DELETE")

    @Test fun v2ToV3_preservesRowsReferencesAndMetadata_withRoomValidation() = runBlocking {
        val name = "gate-v2-preservation.db"
        assertNotEquals(AppDatabase.DATABASE_NAME, name)
        val legacy = helper.createDatabase(name, 2)
        populateV2(legacy)
        val before = rows(legacy)
        assertEquals(tables.toSet(), before.keys)
        before.values.forEach { assertEquals(4, it.size) }
        legacy.close()

        val migrated = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)
        assertEquals(before, rows(migrated))
        assertSchemaMatchesExport(migrated, schema(3))
        migrated.query("PRAGMA integrity_check").use {
            assertTrue(it.moveToFirst())
            assertEquals("ok", it.getString(0))
        }
        migrated.close()

        repeat(2) {
            openRoom(name).use { room ->
                assertEquals(before, rows(room.openHelper.writableDatabase))
                val category = room.categoryDao().getCategoryByLocalIdOnce("account-a", legacyId)!!
                assertTrue(category.isArchived)
                assertEquals(legacyId, room.transactionDao().getRowForCloudMerge("transactions-0")!!.categoryLocalId)
                assertEquals(legacyId, room.budgetDao().getRowForCloudMerge("budgets-0")!!.categoryLocalId)
                val recurring = room.recurringTransactionDao().getById("recurring_transactions-0", "account-a")!!
                assertEquals(legacyId, recurring.categoryLocalId)
                assertFalse(recurring.isActive)
                assertEquals(operations.toSet(), room.categoryDao().getAllCategories("account-a")
                    .first().map { row -> row.pendingOperation.name }.toSet())
            }
        }
    }

    @Test fun v2ToV3_allowsSharedLegacyUuidWithoutCrossOwnerOverwrite() = runBlocking {
        val name = "gate-v2-shared-id.db"
        assertNotEquals(AppDatabase.DATABASE_NAME, name)
        helper.createDatabase(name, 2).also { populateV2(it); it.close() }
        helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3).close()

        openRoom(name).use { room ->
            val dao = room.categoryDao()
            val ownerA = dao.getCategoryByLocalIdOnce("account-a", legacyId)!!
            val ownerB = ownerA.copy(userId = "account-b", name = "Independent", isArchived = false)
            dao.insertCategory(ownerB)
            dao.archiveCategory("account-b", legacyId, 1800000000000L)
            assertEquals(ownerA, dao.getCategoryByLocalIdOnce("account-a", legacyId))
            val changedB = dao.getCategoryByLocalIdOnce("account-b", legacyId)!!
            assertEquals("Independent", changedB.name)
            assertEquals("account-b", changedB.userId)
            assertTrue(changedB.isArchived)
            assertEquals("UPDATE", changedB.pendingOperation.name)
            assertEquals(2, room.openHelper.writableDatabase.query(
                "SELECT * FROM categories WHERE localId = ?", arrayOf(legacyId)
            ).use { it.count })
        }
    }

    private fun schema(version: Int): JSONObject = JSONObject(
        instrumentation.context.assets.open("com.example.moneymatev2.data.local.AppDatabase/$version.json")
            .bufferedReader().use { it.readText() }
    ).getJSONObject("database")

    private fun populateV2(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        val entities = schema(2).getJSONArray("entities")
        for (entityIndex in 0 until entities.length()) {
            val entity = entities.getJSONObject(entityIndex)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            repeat(4) { row ->
                val values = ContentValues()
                for (fieldIndex in 0 until fields.length()) {
                    val field = fields.getJSONObject(fieldIndex)
                    val column = field.getString("columnName")
                    when (val value = fixtureValue(table, column, field.getString("affinity"), row)) {
                        null -> values.putNull(column)
                        is Long -> values.put(column, value)
                        else -> values.put(column, value.toString())
                    }
                }
                db.insert(table, SQLiteDatabase.CONFLICT_ABORT, values)
            }
        }
    }

    private fun fixtureValue(table: String, column: String, affinity: String, row: Int): Any? = when (column) {
        "localId" -> if (table == "categories") categoryId(row) else "$table-$row"
        "userId" -> if (table == "users") "account-$row" else "account-a"
        "categoryLocalId" -> if (row == 3) "missing-category" else categoryId(row)
        "stableId" -> if (row == 0) null else "spend_food"
        "type" -> "EXPENSE"
        "currency" -> "VND"
        "syncStatus" -> listOf("SYNCED", "PENDING", "FAILED", "PENDING")[row]
        "pendingOperation" -> operations[row]
        "frequency" -> "MONTHLY"
        "repeatRule" -> "NONE"
        "timeZoneId" -> "Asia/Ho_Chi_Minh"
        "colorHex" -> "#123456"
        "iconKey" -> "ic_food"
        "isArchived" -> if (row == 0) 1L else 0L
        "isActive", "isDeleted" -> 0L
        "isDefault" -> 1L
        "effectiveMonth" -> row + 1L
        "effectiveYear" -> 2026L
        "retryCount" -> row + 2L
        "remoteUpdatedAt", "lastSyncAttemptAt", "lastSyncError", "note", "message", "photoUrl" ->
            if (row == 0) null else if (affinity == "INTEGER") 1700000000123L + row else "$column-$row"
        else -> if (affinity == "INTEGER") 1700000000000L + row else "$column-$row"
    }

    private fun categoryId(row: Int) = if (row == 0) legacyId else "category-$row"

    private fun rows(db: androidx.sqlite.db.SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        tables.associateWith { table ->
            db.query("SELECT * FROM `$table` ORDER BY ${if (table == "users") "userId" else "localId"}").use { cursor ->
                val originalColumns = (0 until cursor.columnCount).filter { cursor.getColumnName(it) != "anchorAt" }
                buildList { while (cursor.moveToNext()) add(originalColumns.map(cursor::getString)) }
            }
        }

    private fun assertSchemaMatchesExport(db: androidx.sqlite.db.SupportSQLiteDatabase, expected: JSONObject) {
        val entities = expected.getJSONArray("entities")
        for (entityIndex in 0 until entities.length()) {
            val entity = entities.getJSONObject(entityIndex)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val expectedPk = entity.getJSONObject("primaryKey").getJSONArray("columnNames")
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                assertEquals("$table column count", fields.length(), cursor.count)
                var index = 0
                while (cursor.moveToNext()) {
                    val field = fields.getJSONObject(index++)
                    assertEquals(field.getString("columnName"), cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    assertEquals(field.getString("affinity"), cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    assertEquals(if (field.optBoolean("notNull")) 1 else 0,
                        cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                    val pk = (0 until expectedPk.length()).firstOrNull { expectedPk.getString(it) == field.getString("columnName") }
                    assertEquals(if (pk == null) 0 else pk + 1, cursor.getInt(cursor.getColumnIndexOrThrow("pk")))
                    val expectedDefault = field.optString("defaultValue").ifEmpty { null }
                    assertEquals(expectedDefault, cursor.getString(cursor.getColumnIndexOrThrow("dflt_value")))
                }
            }
            val indices = entity.optJSONArray("indices") ?: JSONArray()
            val actual = db.query("PRAGMA index_list(`$table`)").use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                        if (!name.startsWith("sqlite_autoindex")) put(name,
                            cursor.getInt(cursor.getColumnIndexOrThrow("unique")))
                    }
                }
            }
            assertEquals("$table index count", indices.length(), actual.size)
            for (index in 0 until indices.length()) {
                val entry = indices.getJSONObject(index)
                val name = entry.getString("name")
                assertEquals(name, if (entry.optBoolean("unique")) 1 else 0, actual[name])
                val expectedColumns = entry.getJSONArray("columnNames")
                val columns = db.query("PRAGMA index_info(`$name`)").use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
                }
                assertEquals((0 until expectedColumns.length()).map(expectedColumns::getString), columns)
            }
        }
    }

    private fun openRoom(name: String): AppDatabase = Room.databaseBuilder(
        instrumentation.targetContext, AppDatabase::class.java, name
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    private inline fun <T> AppDatabase.use(block: (AppDatabase) -> T): T =
        try { block(this) } finally { close() }
}
