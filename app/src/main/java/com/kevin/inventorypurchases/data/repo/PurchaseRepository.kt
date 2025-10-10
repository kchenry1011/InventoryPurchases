package com.kevin.inventorypurchases.data.repo

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.kevin.inventorypurchases.data.db.Purchase
import com.kevin.inventorypurchases.data.db.PurchaseDao
import com.kevin.inventorypurchases.util.CsvExporter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

class PurchaseRepository(
    private val dao: PurchaseDao,
    private val appContext: Context
) {
    suspend fun add(p: Purchase) = dao.insert(p)
    fun streamAll(): Flow<List<Purchase>> = dao.streamAll()
    suspend fun deleteAll() = dao.deleteAll()

    suspend fun exportCsv(nowMillis: Long = System.currentTimeMillis()): Uri = withContext(Dispatchers.IO) {
        val file = File(appContext.cacheDir, "inventory_${nowMillis}.csv")

        // Grab the current list of purchases (no new DAO calls needed)
        val rows: List<Purchase> = streamAll().first()

        // Write header once, then append rows
        CsvExporter.writeHeaderIfEmpty(file)
        CsvExporter.appendAll(file, rows)

        // Return a content:// URI for sharing
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }

    suspend fun deletePurchaseById(id: String) = withContext(Dispatchers.IO) {
        dao.deleteById(id)   // DAO already uses String id
    }

    suspend fun deleteAllPurchases() = withContext(Dispatchers.IO) {
        dao.deleteAll()
    }
}
