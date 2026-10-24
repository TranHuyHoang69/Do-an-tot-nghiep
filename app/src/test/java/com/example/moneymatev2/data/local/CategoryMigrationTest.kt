package com.example.moneymatev2.data.local

import android.app.Application
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import com.example.moneymatev2.data.local.migration.MIGRATION_1_2
import com.example.moneymatev2.data.local.migration.MIGRATION_2_3
import com.example.moneymatev2.data.local.migration.MIGRATION_3_4
import com.example.moneymatev2.core.util.groupByCategory
import com.example.moneymatev2.data.local.entity.TransactionType
import com.example.moneymatev2.data.remote.sync.*
import com.example.moneymatev2.data.repository.CategoryRepositoryImpl
import com.example.moneymatev2.data.repository.DefaultCategoryIds
import com.example.moneymatev2.data.repository.TransactionRepositoryImpl
import com.example.moneymatev2.domain.model.UserModel
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.domain.usecase.transaction.GetTransactionWithCategoryUseCase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class CategoryMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val schema by lazy {
        val resource = javaClass.classLoader!!.getResourceAsStream(
            "com.example.moneymatev2.data.local.AppDatabase/2.json"
        ) ?: error("Frozen Room v2 schema missing")
        JSONObject(resource.bufferedReader().use { it.readText() }).getJSONObject("database")
    }
    private val operations = listOf("NONE", "CREATE", "UPDATE", "DELETE")

    @Test fun migrateV2_preservesEveryValueAndReference_andReopens() = runBlocking {
        val file = createLegacyDatabase(2)
        val before = snapshot(file)
        assertEquals(6, before.size)
        before.values.forEach { assertEquals(4, it.size) }

        open(file).use { database ->
            assertEquals(4, database.openHelper.writableDatabase.version)
            database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("ok", cursor.getString(0))
            }
            assertEquals(before, snapshot(database))
            val archived = database.categoryDao().getCategoryByLocalIdOnce("account-a", "category-0")!!
            assertTrue(archived.isArchived)
            assertEquals("category-0", database.transactionDao().getRowForCloudMerge("transactions-0")!!.categoryLocalId)
            assertEquals("category-0", database.budgetDao().getRowForCloudMerge("budgets-0")!!.categoryLocalId)
            assertEquals("category-0", database.recurringTransactionDao().getById("recurring_transactions-0", "account-a")!!.categoryLocalId)
            assertFalse(database.recurringTransactionDao().getById("recurring_transactions-0", "account-a")!!.isActive)
            assertEquals(operations.toSet(), database.categoryDao().getAllCategories("account-a").first()
                .map { it.pendingOperation.name }.toSet())
            val auth = mock(AuthRepository::class.java)
            `when`(auth.getCurrentUserId()).thenReturn("account-a")
            `when`(auth.observeAuthState()).thenReturn(flowOf(UserModel("account-a", null, null, null)))
            val runner = PendingUploadRunner(database, SyncSessionGuard(auth, AuthSessionVersion()))
            val firestore = mock(FirebaseFirestore::class.java)
            val trigger = mock(SyncTrigger::class.java)
            val categories = CategoryRepositoryImpl(database.categoryDao(), firestore, trigger, runner, database)
            val transactions = TransactionRepositoryImpl(database.transactionDao(), database.categoryDao(), firestore, trigger, runner)
            val history = GetTransactionWithCategoryUseCase(transactions, categories, auth)().first()
            assertEquals(4, history.size)
            assertTrue(history.single { it.transaction.id == "transactions-0" }.category!!.isArchived)
            val orphan = history.single { it.transaction.id == "transactions-3" }
            assertTrue(orphan.category!!.isMissing)
            assertEquals("missing-category", orphan.transaction.categoryId)
            assertEquals(4, history.groupByCategory(TransactionType.EXPENSE).sumOf { it.transactionCount })
            assertFalse(categories.getActiveCategories("account-a").first().any { it.id == "category-0" })
            verifyNoInteractions(firestore)
        }
        repeat(3) { open(file).use { database -> assertEquals(before, snapshot(database)) } }
    }

    @Test fun migratedDatabase_scopesAllCategoryMutationsToOwner() = runBlocking {
        val file = createLegacyDatabase(2)
        open(file).use { database ->
            val dao = database.categoryDao()
            val original = dao.getCategoryByLocalIdOnce("account-a", "category-1")!!
            val other = original.copy(userId = "account-b", name = "other owner")
            dao.insertCategory(other)
            dao.updateCategory(original.copy(name = "edited"))
            dao.archiveCategory("account-a", original.localId, 900L)
            dao.markSyncFailedIfUnchanged(original.localId, "account-a", 900L, "offline", 901L)
            assertEquals(other, dao.getCategoryByLocalIdOnce("account-b", original.localId))
            dao.markSyncedIfUnchanged(original.localId, "account-a", 900L, 902L)
            assertEquals(other, dao.getCategoryByLocalId("account-b", original.localId).first())
            assertEquals("edited", dao.getCategoryByLocalIdOnce("account-a", original.localId)!!.name)
            assertTrue(dao.getCategoryByLocalIdOnce("account-a", original.localId)!!.isArchived)
        }
        open(file).use { database ->
            assertEquals(4, database.categoryDao().getAllCategories("account-a").first().size)
            assertEquals(1, database.categoryDao().getAllCategories("account-b").first().size)
        }
    }

    @Test fun migrateV1ThroughV2_preservesOriginalTables() {
        val file = createLegacyDatabase(1)
        val before = snapshot(file)
        assertEquals(5, before.size)
        open(file).use { database ->
            val after = snapshot(database)
            assertEquals(4, database.openHelper.writableDatabase.version)
            before.forEach { (table, rows) -> assertEquals(table, rows, after[table]) }
            assertTrue(after.getValue("recurring_transactions").isEmpty())
        }
    }

    @Test fun migrationFailure_rollsBackSchemaAndData() {
        val file = createLegacyDatabase(2)
        val before = snapshot(file)
        val failing = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                MIGRATION_2_3.migrate(connection)
                error("Injected failure after category copy")
            }
        }
        val database = open(file, failing)
        try {
            database.openHelper.writableDatabase
            fail("Migration must fail")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("Injected failure"))
        } finally {
            database.close()
        }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { legacy ->
            assertEquals(2, legacy.version)
            legacy.rawQuery("PRAGMA table_info(categories)", null).use { cursor ->
                var keyColumns = 0
                while (cursor.moveToNext()) if (cursor.getInt(cursor.getColumnIndexOrThrow("pk")) > 0) keyColumns++
                assertEquals(1, keyColumns)
            }
        }
        assertEquals(before, snapshot(file))
        open(file).use { migrated -> assertEquals(before, snapshot(migrated)) }
    }

    @Test fun migratedDatabase_cloudPullAcceptsSharedLegacyUuidWithoutRollingBack() = runBlocking {
        val file = createLegacyDatabase(2)
        open(file).use { database ->
            val dao = database.categoryDao()
            val original = dao.getCategoryByLocalIdOnce("account-a", "category-1")!!
                .copy(localId = DefaultCategoryIds.SPEND_FOOD)
            dao.insertCategory(original)
            val auth = mock(AuthRepository::class.java)
            `when`(auth.getCurrentUserId()).thenReturn("account-b")
            val firestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)
            val document = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
            `when`(document.id).thenReturn(original.localId)
            `when`(document.reference.path).thenReturn("users/account-b/categories/${original.localId}")
            `when`(document.data).thenReturn(mapOf("name" to "Owner B", "stableId" to "spend_food",
                "type" to "EXPENSE", "updatedAt" to 200L))
            for (collection in listOf("categories", "transactions", "budgets", "reminders", "recurring_transactions")) {
                val snapshot = mock(QuerySnapshot::class.java)
                `when`(snapshot.documents).thenReturn(if (collection == "categories") listOf(document) else emptyList())
                `when`(firestore.collection("users").document("account-b").collection(collection).get(Source.SERVER))
                    .thenReturn(Tasks.forResult(snapshot))
            }
            val service = CloudPullService(firestore, auth, database, dao, database.transactionDao(),
                database.budgetDao(), database.reminderDao(), database.recurringTransactionDao(),
                SyncSessionGuard(auth, AuthSessionVersion()))
            repeat(2) { service.pull("account-b") }
            assertEquals(original, dao.getCategoryByLocalIdOnce("account-a", original.localId))
            assertEquals("Owner B", dao.getCategoryByLocalIdOnce("account-b", original.localId)!!.name)
            assertEquals(1, dao.getAllCategories("account-b").first().size)
        }
    }

    private fun open(file: File, migration: Migration = MIGRATION_2_3): AppDatabase =
        Room.databaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java, file.absolutePath)
            .addMigrations(MIGRATION_1_2, migration, MIGRATION_3_4).allowMainThreadQueries().build()

    private inline fun <Result> AppDatabase.use(block: (AppDatabase) -> Result): Result =
        try { block(this) } finally { close() }

    private fun createLegacyDatabase(version: Int): File {
        val file = File(temporary.newFolder(), "migration-test.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                if (version == 1 && table == "recurring_transactions") continue
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (indexNumber in 0 until indices.length()) {
                    database.execSQL(indices.getJSONObject(indexNumber).getString("createSql")
                        .replace("\${TABLE_NAME}", table))
                }
                repeat(4) { rowIndex ->
                    val values = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    for (fieldIndex in 0 until fields.length()) {
                        val field = fields.getJSONObject(fieldIndex)
                        val column = field.getString("columnName")
                        val value = fixtureValue(table, column, field.getString("affinity"), rowIndex)
                        when (value) {
                            null -> values.putNull(column)
                            is Long -> values.put(column, value)
                            else -> values.put(column, value.toString())
                        }
                    }
                    database.insertOrThrow(table, null, values)
                }
            }
            if (version == 2) {
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) database.execSQL(setup.getString(index))
            }
            database.version = version
        }
        return file
    }

    private fun fixtureValue(table: String, column: String, affinity: String, row: Int): Any? = when (column) {
        "localId" -> if (table == "categories") "category-$row" else "$table-$row"
        "userId" -> if (table == "users") "account-$row" else "account-a"
        "categoryLocalId" -> if (row == 3) "missing-category" else "category-$row"
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

    private fun snapshot(file: File): Map<String, List<List<String?>>> =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            snapshot { sql -> database.rawQuery(sql, null) }
        }

    private fun snapshot(database: AppDatabase): Map<String, List<List<String?>>> =
        snapshot { sql -> database.openHelper.writableDatabase.query(sql) }

    private fun snapshot(query: (String) -> Cursor): Map<String, List<List<String?>>> {
        val tables = query("SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
            "('categories','transactions','budgets','recurring_transactions','reminders','users') ORDER BY name").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        return tables.associateWith { table ->
            query("SELECT * FROM `$table` ORDER BY ${if (table == "users") "userId" else "localId"}").use { cursor ->
                val originalColumns = (0 until cursor.columnCount).filter { cursor.getColumnName(it) != "anchorAt" }
                buildList { while (cursor.moveToNext()) add(originalColumns.map { cursor.getString(it) }) }
            }
        }
    }
}
