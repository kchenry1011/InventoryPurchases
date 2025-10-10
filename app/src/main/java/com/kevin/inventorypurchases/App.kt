package com.kevin.inventorypurchases

import android.app.Application
import androidx.room.Room
import com.kevin.inventorypurchases.data.db.AppDb
import com.kevin.inventorypurchases.data.repo.PurchaseRepository
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add notes with NOT NULL + default so existing rows are valid
        db.execSQL("ALTER TABLE purchases ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}
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
        ).addMigrations(MIGRATION_1_2).build()

        repo = PurchaseRepository(db.purchaseDao(), applicationContext)
    }
}
