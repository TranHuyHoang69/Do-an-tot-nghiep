
package com.example.moneymatev2.domain.usecase

import android.util.Log
import com.example.moneymatev2.data.remote.sync.SyncSessionGuard
import com.example.moneymatev2.domain.reminder.ReminderScheduler
import com.example.moneymatev2.domain.repository.BudgetRepository
import com.example.moneymatev2.domain.repository.CategoryRepository
import com.example.moneymatev2.domain.repository.CloudPullRepository
import com.example.moneymatev2.domain.repository.RecurringTransactionRepository
import com.example.moneymatev2.domain.repository.ReminderRepository
import com.example.moneymatev2.domain.repository.TransactionRepository
import com.example.moneymatev2.domain.usecase.reminder.RescheduleRemindersUseCase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    private val cloudPullService: CloudPullRepository,
    private val categoryRepository: CategoryRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val reminderRepository: ReminderRepository,
    private val recurringRepository: RecurringTransactionRepository,
    private val reminderScheduler: ReminderScheduler,
    private val rescheduleRemindersUseCase: RescheduleRemindersUseCase,
    private val sessions: SyncSessionGuard
) : SyncOrchestrator {

    // Tránh hai SyncWorker chạy chồng nhau trong cùng process.
    private val syncMutex = Mutex()

    override suspend fun syncAll(userId: String) {
        val session = sessions.capture(userId)

        syncMutex.withLock {
            sessions.check(session)
            Log.i(TAG, "SYNC_START uidHash=${userId.safeHash()}")

            // 1. Ghi lại các Reminder đang bật TRƯỚC khi pull.
            // Những ID này có thể đang có alarm trên thiết bị.
            val activeIdsBefore = reminderRepository
                .getActiveRemindersOnce(userId)
                .map { it.id }
                .toSet()

            // 2. Download + merge dữ liệu từ Firestore.
            // Nếu pull lỗi, không được tiếp tục bước đồng bộ.
            val pullReport = cloudPullService.pull(userId)
            sessions.check(session)
            Log.i(
                TAG,
                "SYNC_PULL_RESULT categories=${pullReport.categories} transactions=${pullReport.transactions} budgets=${pullReport.budgets} reminders=${pullReport.reminders} recurring=${pullReport.recurringTemplates}"
            )

            // 3. Lấy Reminder đang bật SAU khi pull.
            val activeIdsAfter = reminderRepository
                .getActiveRemindersOnce(userId)
                .map { it.id }
                .toSet()

            // 4. Hủy alarm của Reminder vừa bị xóa hoặc tắt.
            val removedOrDisabledIds =
                activeIdsBefore - activeIdsAfter

            removedOrDisabledIds.forEach { reminderId ->
                sessions.check(session)
                reminderScheduler.cancel(reminderId)
            }

            // 5. Đặt lại alarm cho những Reminder đang bật.
            // Bao gồm Reminder mới tải từ cloud và Reminder
            // có ngày/giờ được thay đổi từ thiết bị khác.
            rescheduleRemindersUseCase(userId)
            sessions.check(session)

            // 6. Chỉ seed danh mục sau khi đã pull.
            categoryRepository.seedDefaultCategoriesIfNeeded(userId)
            sessions.check(session)

            // 7. Xử lý các kỳ giao dịch định kỳ đến hạn.
            recurringRepository.postDue(userId)
            sessions.check(session)

            // 8. Upload các thay đổi local lên Firestore.
            Log.i(TAG, "SYNC_UPLOAD_START collection=categories")
            categoryRepository.syncPendingCategories(userId)
            sessions.check(session)
            Log.i(TAG, "SYNC_UPLOAD_RESULT collection=categories result=success")

            // Upload giao dịch thật trước khi upload cursor
            // của mẫu định kỳ.
            Log.i(TAG, "SYNC_UPLOAD_START collection=transactions")
            transactionRepository.syncPendingTransactions(userId)
            sessions.check(session)
            Log.i(TAG, "SYNC_UPLOAD_RESULT collection=transactions result=success")

            Log.i(TAG, "SYNC_UPLOAD_START collection=recurring_transactions")
            recurringRepository.syncPending(userId)
            sessions.check(session)
            Log.i(TAG, "SYNC_UPLOAD_RESULT collection=recurring_transactions result=success")

            Log.i(TAG, "SYNC_UPLOAD_START collection=budgets")
            budgetRepository.syncPendingBudgets(userId)
            sessions.check(session)
            Log.i(TAG, "SYNC_UPLOAD_RESULT collection=budgets result=success")

            Log.i(TAG, "SYNC_UPLOAD_START collection=reminders")
            reminderRepository.syncPendingReminders(userId)
            sessions.check(session)
            Log.i(TAG, "SYNC_UPLOAD_RESULT collection=reminders result=success")
            Log.i(TAG, "SYNC_COMPLETE uidHash=${userId.safeHash()}")
        }
    }

    private fun String.safeHash(): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .take(4)
            .joinToString("") { "%02x".format(it) }

    companion object {
        private const val TAG = "SyncManager"
    }
}
