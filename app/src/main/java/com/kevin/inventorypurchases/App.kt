package com.kevin.inventorypurchases

import android.app.Application
import androidx.room.Room
import com.kevin.inventorypurchases.data.db.AppDb
import com.kevin.inventorypurchases.data.repo.PurchaseRepository

class App : Application() {
    lateinit var db: AppDb
        private set
    lateinit var repo: PurchaseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDb::class.java,
            "inventory.db"
        ).fallbackToDestructiveMigration().build()

        repo = PurchaseRepository(db.purchaseDao(), applicationContext)
    }
}
