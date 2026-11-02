package com.example.moneymatev2.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Keep unknown legacy bases null: guessing a cloud revision could authorize a stale overwrite. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `budgets` ADD COLUMN `cloudRevision` INTEGER")
        connection.execSQL("ALTER TABLE `budgets` ADD COLUMN `baseRevision` INTEGER")
        connection.execSQL("ALTER TABLE `budgets` ADD COLUMN `pendingUploadId` TEXT")
        connection.execSQL("ALTER TABLE `budgets` ADD COLUMN `pendingRecreate` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("""
            UPDATE `budgets` SET `pendingUploadId` = lower(hex(randomblob(16)))
            WHERE `pendingOperation` != 'NONE'
        """.trimIndent())
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `budget_conflicts` (
                `conflictId` TEXT NOT NULL PRIMARY KEY,
                `userId` TEXT NOT NULL,
                `budgetLocalId` TEXT NOT NULL,
                `categoryLocalId` TEXT NOT NULL,
                `effectiveMonth` INTEGER NOT NULL,
                `effectiveYear` INTEGER NOT NULL,
                `amountMinor` INTEGER NOT NULL,
                `currency` TEXT NOT NULL,
                `isArchived` INTEGER NOT NULL,
                `expectedRevision` INTEGER,
                `actualRevision` INTEGER,
                `reason` TEXT NOT NULL,
                `capturedAt` INTEGER NOT NULL
            )
        """.trimIndent())
        connection.execSQL("CREATE INDEX `index_budget_conflicts_userId` ON `budget_conflicts` (`userId`)")
    }
}
