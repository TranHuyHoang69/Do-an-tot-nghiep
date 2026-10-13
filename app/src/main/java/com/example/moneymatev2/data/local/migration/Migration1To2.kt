package com.example.moneymatev2.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Adds a new table only. Existing transaction/category/reminder rows remain intact. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `recurring_transactions` (
                `localId` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `categoryLocalId` TEXT NOT NULL,
                `note` TEXT,
                `frequency` TEXT NOT NULL,
                `startAt` INTEGER NOT NULL,
                `timeZoneId` TEXT NOT NULL,
                `nextOccurrenceIndex` INTEGER NOT NULL,
                `nextRunAt` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                `isDeleted` INTEGER NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `pendingOperation` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `remoteUpdatedAt` INTEGER,
                `lastSyncError` TEXT,
                `lastSyncAttemptAt` INTEGER,
                `retryCount` INTEGER NOT NULL,
                PRIMARY KEY(`localId`)
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_userId_isActive_nextRunAt` ON `recurring_transactions` (`userId`, `isActive`, `nextRunAt`)")
    }
}
