package com.example.moneymatev2.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Only the local category key changes. IDs, references and sync metadata are copied verbatim. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE `categories_v3` (
                `localId` TEXT NOT NULL, `stableId` TEXT, `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL, `type` TEXT NOT NULL, `iconKey` TEXT NOT NULL,
                `colorHex` TEXT NOT NULL, `isDefault` INTEGER NOT NULL,
                `isArchived` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL,
                `pendingOperation` TEXT NOT NULL, `lastSyncError` TEXT,
                `retryCount` INTEGER NOT NULL, `lastSyncAttemptAt` INTEGER,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                `remoteUpdatedAt` INTEGER, PRIMARY KEY(`userId`, `localId`)
            )
        """.trimIndent())
        connection.execSQL("""
            INSERT INTO `categories_v3` (
                `localId`, `stableId`, `userId`, `name`, `type`, `iconKey`, `colorHex`,
                `isDefault`, `isArchived`, `syncStatus`, `pendingOperation`, `lastSyncError`,
                `retryCount`, `lastSyncAttemptAt`, `createdAt`, `updatedAt`, `remoteUpdatedAt`
            ) SELECT
                `localId`, `stableId`, `userId`, `name`, `type`, `iconKey`, `colorHex`,
                `isDefault`, `isArchived`, `syncStatus`, `pendingOperation`, `lastSyncError`,
                `retryCount`, `lastSyncAttemptAt`, `createdAt`, `updatedAt`, `remoteUpdatedAt`
            FROM `categories`
        """.trimIndent())
        connection.execSQL("DROP TABLE `categories`")
        connection.execSQL("ALTER TABLE `categories_v3` RENAME TO `categories`")
        for (column in listOf("userId", "stableId", "type", "pendingOperation")) {
            connection.execSQL("CREATE INDEX `index_categories_$column` ON `categories` (`$column`)")
        }
    }
}
