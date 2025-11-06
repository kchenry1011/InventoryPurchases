package com.kevin.inventorypurchases.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Purchase::class],
    version = 3,
    exportSchema = true
)
abstract class AppDb : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
}
