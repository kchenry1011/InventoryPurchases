package com.kevin.inventorypurchases.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Purchase::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
}
