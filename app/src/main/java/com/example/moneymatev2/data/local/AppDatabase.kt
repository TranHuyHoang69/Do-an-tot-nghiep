package com.example.moneymatev2.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.moneymatev2.data.local.dao.CategoryDao
import com.example.moneymatev2.data.local.dao.TransactionDao
import com.example.moneymatev2.data.local.dao.UserDao
import com.example.moneymatev2.data.local.entity.CategoryEntity
import com.example.moneymatev2.data.local.entity.TransactionEntity
import com.example.moneymatev2.data.local.entity.UserEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        UserEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase(){
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun userDao(): UserDao

    companion object{
        const val DATABASE_NAME = "moneymate.db"
    }
}

