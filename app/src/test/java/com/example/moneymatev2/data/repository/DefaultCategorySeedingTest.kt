package com.example.moneymatev2.data.repository

import android.app.Application
import androidx.room.Room
import com.example.moneymatev2.data.local.AppDatabase
import com.example.moneymatev2.data.local.CategoryStableId
import com.example.moneymatev2.core.util.CategoryColorPalette
import com.example.moneymatev2.data.local.entity.*
import com.example.moneymatev2.data.remote.sync.*
import com.example.moneymatev2.domain.repository.AuthRepository
import com.example.moneymatev2.ui.item.CategoryIconMap
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DefaultCategorySeedingTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: CategoryRepositoryImpl
    private lateinit var trigger: SyncTrigger
    private lateinit var auth: AuthRepository
    private var currentUid = "account-a"

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        auth = mock(AuthRepository::class.java)
        `when`(auth.getCurrentUserId()).thenAnswer { currentUid }
        trigger = mock(SyncTrigger::class.java)
        repository = CategoryRepositoryImpl(db.categoryDao(), mock(FirebaseFirestore::class.java),
            trigger, PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion())), db)
    }

    @After fun tearDown() { db.close() }

    @Test fun newAccountGetsSevenExpenseOneIncomeAndRepeatedSeedIsIdempotent() = runBlocking {
        repository.seedDefaultCategoriesIfNeeded(currentUid)
        val first = db.categoryDao().getAllCategoriesOnce(currentUid)
        assertEquals(8, first.size)
        assertEquals(7, first.count { it.type == TransactionType.EXPENSE })
        assertEquals(1, first.count { it.type == TransactionType.INCOME })
        assertEquals(setOf(CategoryStableId.SPEND_FOOD, CategoryStableId.SPEND_SHOPPING,
            CategoryStableId.SPEND_HEALTH, CategoryStableId.SPEND_TRANSPORT,
            CategoryStableId.SPEND_GIFT, CategoryStableId.SPEND_ENTERTAINMENT,
            CategoryStableId.SPEND_OTHER, CategoryStableId.INCOME_SALARY), first.map { it.stableId }.toSet())
        assertTrue(first.all { it.isDefault && !it.isArchived && it.userId == currentUid &&
            it.pendingOperation == PendingOperation.CREATE &&
            it.localId == DefaultCategoryIds.forUser(currentUid, it.stableId!!) })
        assertTrue(first.all { it.iconKey in CategoryIconMap.allKeys() &&
            it.colorHex in CategoryColorPalette.colors })
        assertEquals(7, repository.getActiveCategoriesByType(currentUid, TransactionType.EXPENSE).first().size)
        assertEquals(1, repository.getActiveCategoriesByType(currentUid, TransactionType.INCOME).first().size)
        assertEquals(0, countRows("transactions"))
        assertEquals(0, countRows("budgets"))
        assertEquals(0, countRows("recurring_transactions"))

        repository.seedDefaultCategoriesIfNeeded(currentUid)
        assertEquals(first, db.categoryDao().getAllCategoriesOnce(currentUid))
        verify(trigger, times(1)).requestImmediateSync()
        repository = CategoryRepositoryImpl(db.categoryDao(), mock(FirebaseFirestore::class.java),
            trigger, PendingUploadRunner(db, SyncSessionGuard(auth, AuthSessionVersion())), db)
        repository.seedDefaultCategoriesIfNeeded(currentUid)
        assertEquals(first, db.categoryDao().getAllCategoriesOnce(currentUid))

        currentUid = "account-b"
        repository.seedDefaultCategoriesIfNeeded(currentUid)
        val second = db.categoryDao().getAllCategoriesOnce(currentUid)
        assertEquals(8, second.size)
        assertTrue(first.map { it.localId }.intersect(second.map { it.localId }.toSet()).isEmpty())
        assertEquals(first, db.categoryDao().getAllCategoriesOnce("account-a"))
    }

    @Test fun existingDefaultsKeepIdsReferencesCustomRowsAndArchivedTombstone() = runBlocking {
        val uid = currentUid
        val food = category(uid, "legacy-food", CategoryStableId.SPEND_FOOD, "ic_food")
        val shopping = category(uid, "legacy-shop", CategoryStableId.SPEND_SHOPPING, "ic_shop")
        val transport = category(uid, DefaultCategoryIds.SPEND_TRANSPORT, null, "ic_car")
            .copy(name = "Di chuyển", colorHex = "#123456")
        val salary = category(uid, "legacy-salary", CategoryStableId.INCOME_SALARY,
            "ic_money", TransactionType.INCOME)
        val healthTombstone = category(uid, "deleted-health", CategoryStableId.SPEND_HEALTH,
            "ic_cat_health_health").copy(isArchived = true, pendingOperation = PendingOperation.UPDATE)
        val customHealth = category(uid, "user-health", null, "ic_cat_health_pill")
            .copy(name = "Sức khỏe", isDefault = false, pendingOperation = PendingOperation.CREATE)
        listOf(food, shopping, transport, salary, healthTombstone, customHealth)
            .forEach { db.categoryDao().insertCategory(it) }
        val transaction = TransactionEntity("old-transaction", uid, TransactionType.EXPENSE, 500,
            "VND", transport.localId, null, SyncStatus.SYNCED, PendingOperation.NONE,
            createdAt = 1, updatedAt = 1)
        val budget = BudgetEntity("old-budget", uid, food.localId, 10_000, "VND", 11, 2026,
            syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
            createdAt = 1, updatedAt = 1)
        val recurring = RecurringTransactionEntity("old-recurring", uid, TransactionType.EXPENSE,
            1_000, "VND", shopping.localId, null, "MONTHLY", 1, "Asia/Ho_Chi_Minh",
            0, 2, true, createdAt = 1, updatedAt = 1)
        db.transactionDao().insertTransaction(transaction)
        db.budgetDao().insertBudget(budget)
        db.recurringTransactionDao().insert(recurring)

        repository.seedDefaultCategoriesIfNeeded(uid)
        repository.seedDefaultCategoriesIfNeeded(uid)
        val rows = db.categoryDao().getAllCategoriesOnce(uid)
        assertEquals(1, rows.count { it.stableId == CategoryStableId.SPEND_TRANSPORT })
        assertEquals(transport.localId, rows.single { it.stableId == CategoryStableId.SPEND_TRANSPORT }.localId)
        assertEquals("Di chuyển", rows.single { it.stableId == CategoryStableId.SPEND_TRANSPORT }.name)
        assertEquals("#123456", rows.single { it.stableId == CategoryStableId.SPEND_TRANSPORT }.colorHex)
        assertEquals(food, db.categoryDao().getCategoryByLocalIdOnce(uid, food.localId))
        assertEquals(shopping, db.categoryDao().getCategoryByLocalIdOnce(uid, shopping.localId))
        assertEquals(salary, db.categoryDao().getCategoryByLocalIdOnce(uid, salary.localId))
        assertEquals(healthTombstone, db.categoryDao().getCategoryByLocalIdOnce(uid, healthTombstone.localId))
        assertEquals(customHealth, db.categoryDao().getCategoryByLocalIdOnce(uid, customHealth.localId))
        assertEquals(1, rows.count { it.stableId == CategoryStableId.SPEND_HEALTH })
        assertEquals(6, rows.count { it.isDefault && !it.isArchived && it.type == TransactionType.EXPENSE })
        assertEquals(transaction, db.transactionDao().getRowForCloudMerge(transaction.localId))
        assertEquals(budget, db.budgetDao().getRowForCloudMerge(budget.localId))
        assertEquals(recurring, db.recurringTransactionDao().getById(recurring.localId, uid))
    }

    @Test fun pendingAndLegacyArchivedRowsNeverGetDuplicatedOrRestored() = runBlocking {
        val uid = currentUid
        val pendingGift = category(uid, "remote-gift", CategoryStableId.SPEND_GIFT,
            "ic_cat_shop_gift").copy(pendingOperation = PendingOperation.CREATE)
        val archivedOther = category(uid, "old-other", null, "ic_cat_finance_receipt")
            .copy(isArchived = true, pendingOperation = PendingOperation.UPDATE)
        val deletingEntertainment = category(uid, "deleted-entertainment", null,
            "ic_cat_shop_game").copy(isArchived = true, pendingOperation = PendingOperation.DELETE)
        db.categoryDao().insertCategory(pendingGift)
        db.categoryDao().insertCategory(archivedOther)
        db.categoryDao().insertCategory(deletingEntertainment)
        repository.seedDefaultCategoriesIfNeeded(uid)
        assertEquals(pendingGift, db.categoryDao().getCategoryByLocalIdOnce(uid, pendingGift.localId))
        val stillArchived = db.categoryDao().getCategoryByLocalIdOnce(uid, archivedOther.localId)!!
        assertTrue(stillArchived.isArchived)
        assertEquals(archivedOther.localId, stillArchived.localId)
        assertEquals(CategoryStableId.SPEND_OTHER, stillArchived.stableId)
        assertEquals(deletingEntertainment, db.categoryDao().getCategoryByLocalIdOnce(uid,
            deletingEntertainment.localId))
        assertEquals(1, db.categoryDao().getAllCategoriesOnce(uid)
            .count { it.stableId == CategoryStableId.SPEND_OTHER && it.isArchived })
        assertEquals(1, db.categoryDao().getAllCategoriesOnce(uid)
            .count { it.stableId == CategoryStableId.SPEND_GIFT })
        assertEquals(0, db.categoryDao().getAllCategoriesOnce(uid)
            .count { it.stableId == CategoryStableId.SPEND_ENTERTAINMENT })
    }

    @Test fun cloudPulledDefaultKeepsRemoteDocumentIdOnRepeatedSeeds() = runBlocking {
        val uid = currentUid
        val firestore = mock(FirebaseFirestore::class.java, RETURNS_DEEP_STUBS)
        val document = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
        `when`(document.id).thenReturn("remote-health-id")
        `when`(document.reference.path).thenReturn("users/$uid/categories/remote-health-id")
        `when`(document.data).thenReturn(mapOf("name" to "Health", "stableId" to CategoryStableId.SPEND_HEALTH,
            "type" to "EXPENSE", "iconKey" to "ic_cat_health_health", "isDefault" to true,
            "isArchived" to false, "updatedAt" to 200L))
        val archivedGift = mock(DocumentSnapshot::class.java, RETURNS_DEEP_STUBS)
        `when`(archivedGift.id).thenReturn("remote-archived-gift")
        `when`(archivedGift.reference.path).thenReturn("users/$uid/categories/remote-archived-gift")
        `when`(archivedGift.data).thenReturn(mapOf("name" to "Gifts", "stableId" to CategoryStableId.SPEND_GIFT,
            "type" to "EXPENSE", "iconKey" to "ic_cat_shop_gift", "isDefault" to true,
            "isArchived" to true, "updatedAt" to 201L))
        for (collection in listOf("categories", "transactions", "budgets", "reminders", "recurring_transactions")) {
            val snapshot = mock(QuerySnapshot::class.java)
            `when`(snapshot.documents).thenReturn(if (collection == "categories")
                listOf(document, archivedGift) else emptyList())
            `when`(firestore.collection("users").document(uid).collection(collection).get(Source.SERVER))
                .thenReturn(Tasks.forResult(snapshot))
        }
        val guard = SyncSessionGuard(auth, AuthSessionVersion())
        val pull = CloudPullService(firestore, auth, db, db.categoryDao(), db.transactionDao(),
            db.budgetDao(), db.reminderDao(), db.recurringTransactionDao(), guard)
        pull.pull(uid)
        repository.seedDefaultCategoriesIfNeeded(uid)
        pull.pull(uid)
        repository.seedDefaultCategoriesIfNeeded(uid)
        val health = db.categoryDao().getAllCategoriesOnce(uid)
            .filter { it.stableId == CategoryStableId.SPEND_HEALTH }
        assertEquals(1, health.size)
        assertEquals("remote-health-id", health.single().localId)
        assertEquals(8, db.categoryDao().getAllCategoriesOnce(uid).size)
        val gift = db.categoryDao().getAllCategoriesOnce(uid)
            .single { it.stableId == CategoryStableId.SPEND_GIFT }
        assertEquals("remote-archived-gift", gift.localId)
        assertTrue(gift.isArchived)
    }

    @Test fun userCreatedCategoryWithSameNameIsKeptSeparate() = runBlocking {
        val personal = category(currentUid, "personal-health", "custom-123",
            "ic_cat_health_pill").copy(name = "Sức khỏe", isDefault = false)
        db.categoryDao().insertCategory(personal)
        repository.seedDefaultCategoriesIfNeeded(currentUid)
        val rows = db.categoryDao().getAllCategoriesOnce(currentUid)
        assertEquals(personal, rows.single { it.localId == personal.localId })
        assertEquals(1, rows.count { it.stableId == CategoryStableId.SPEND_HEALTH && it.isDefault })
        assertEquals(9, rows.size)
    }

    private fun category(owner: String, id: String, stableId: String?, icon: String,
                         type: TransactionType = TransactionType.EXPENSE) = CategoryEntity(
        id, stableId, owner, stableId ?: id, type, icon, "#9E9E9E", true,
        syncStatus = SyncStatus.SYNCED, pendingOperation = PendingOperation.NONE,
        createdAt = 1, updatedAt = 1
    )

    private fun countRows(table: String): Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
}
