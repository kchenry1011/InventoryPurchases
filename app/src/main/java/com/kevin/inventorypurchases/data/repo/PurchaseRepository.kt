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
import java.io.File

class PurchaseRepository(
    private val dao: PurchaseDao,
    private val appContext: Context
) {
    suspend fun add(p: Purchase) = dao.insert(p)
    fun streamAll(): Flow<List<Purchase>> = dao.streamAll()
    suspend fun deleteAll() = dao.deleteAll()

    @RequiresApi(Build.VERSION_CODES.O)
    fun exportCsv(nowMillis: Long = System.currentTimeMillis()): Uri {
        val file = File(appContext.cacheDir, "inventory_${nowMillis}.csv")
        CsvExporter.writeHeaderIfEmpty(file)
        // In a later step we will pass real DB rows here.
        CsvExporter.appendAll(file, emptyList())
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
    }
}
