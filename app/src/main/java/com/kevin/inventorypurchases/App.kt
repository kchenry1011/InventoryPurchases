package com.kevin.inventorypurchases

import android.app.Application
import androidx.room.Room
import com.kevin.inventorypurchases.data.db.AppDb
import com.kevin.inventorypurchases.data.repo.PurchaseRepository
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kevin.inventorypurchases.session.GroupSession

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add notes with NOT NULL + default so existing rows are valid
        db.execSQL("ALTER TABLE purchases ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
    }
}
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Add nullable groupName column; allow NULL so no backfill needed
        db.execSQL("ALTER TABLE purchases ADD COLUMN groupName TEXT")
    }
}

class App : Application() {
    lateinit var db: AppDb
        private set

    lateinit var repo: PurchaseRepository
        private set

    lateinit var groupSession: GroupSession
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDb::class.java,
            "inventory.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        repo = PurchaseRepository(db.purchaseDao(), applicationContext)
        groupSession = GroupSession(applicationContext)
    }
}
