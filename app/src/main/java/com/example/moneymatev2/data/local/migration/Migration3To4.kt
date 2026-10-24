package com.example.moneymatev2.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Old occurrences have no reliable original day, so leave the new anchor null. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `reminders` ADD COLUMN `anchorAt` INTEGER")
    }
}
