package com.example.moneymatev2.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.moneymatev2.data.local.dao.BudgetDao
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.dao.ReminderDao
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.dao.UserDao
import com.example.moneymatev2.data.local.entity.BudgetEntity
import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.ReminderEntity
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.local.entity.UserEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        UserEntity::class,
        BudgetEntity::class,
        ReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase(){
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao
    abstract fun reminderDao(): ReminderDao
    abstract fun budgetDao(): BudgetDao
    companion object{
        const val DATABASE_NAME = "moneymate.db"
    }
}

