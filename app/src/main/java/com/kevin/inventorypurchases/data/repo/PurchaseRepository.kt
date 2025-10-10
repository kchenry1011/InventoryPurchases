package com.kevin.inventorypurchases.data.repo

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.kevin.inventorypurchases.data.db.Purchase
import com.kevin.inventorypurchases.data.db.PurchaseDao
import com.kevin.inventorypurchases.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PurchaseRepository(
    private val dao: PurchaseDao,
    private val appContext: Context
) {
    suspend fun add(p: Purchase) = dao.insert(p)
    fun streamAll(): Flow<List<Purchase>> = dao.streamAll()
    suspend fun deleteAll() = dao.deleteAll()
    suspend fun deletePurchaseById(id: String) = withContext(Dispatchers.IO) { dao.deleteById(id) }
    suspend fun deleteAllPurchases() = withContext(Dispatchers.IO) { dao.deleteAll() }


    suspend fun exportCsvAndPhotosZip(nowMillis: Long = System.currentTimeMillis()): Uri =
        withContext(Dispatchers.IO) {
// 1) Load all rows
            val rows: List<Purchase> = streamAll().first()

// 2) Prep export dirs/files
            val exportDir = File(appContext.cacheDir, "export_$nowMillis").apply { mkdirs() }
            val photosDir = File(exportDir, "photos").apply { mkdirs() }
            val csvFile = File(exportDir, "inventory_$nowMillis.csv")

// 3) Copy photos + build per-row filename list
            val filenamesById: Map<String, List<String>> = rows.associate { p ->
                val uris = parsePhotoList(p.photoUri)
                val names = uris.mapIndexedNotNull { idx, u ->
                    val ext = guessExtension(u) ?: ".jpg"
                    val name = "p_${p.id}__${idx + 1}$ext"
                    val dest = File(photosDir, name)
                    if (copyUri(u, dest)) name else null
                }
                p.id to names
            }

// 4) Write CSV using pretty formats + our filenames
            CsvExporter.writeCsvWithPhotoFilenames(
                csvFile = csvFile,
                rows = rows,
                photoFilenamesFor = { p -> filenamesById[p.id].orEmpty().joinToString(";") }
            )

// 5) Zip the export folder
            val zipFile = File(appContext.cacheDir, "inventory_export_$nowMillis.zip")
            zipDirectory(exportDir, zipFile)

// 6) Return shareable URI
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                zipFile
            )
        }

// ---- helpers ----

    private fun parsePhotoList(stored: String?): List<Uri> {
        if (stored.isNullOrBlank()) return emptyList()
        val s = stored.trim()
// We stored List<Uri>.toString() like "[content://..., content://...]"
        val parts = if (s.startsWith("[") && s.endsWith("]")) {
            s.substring(1, s.length - 1)
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else listOf(s)
        return parts.mapNotNull { runCatching { Uri.parse(it) }.getOrNull() }
    }

    private fun copyUri(src: Uri, dest: File): Boolean {
        return try {
            appContext.contentResolver.openInputStream(src)?.use { ins ->
                FileOutputStream(dest).use { out ->
                    ins.copyTo(out, 8 * 1024); out.flush()
                }
            } ?: return false
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun guessExtension(uri: Uri): String? {
        val type = appContext.contentResolver.getType(uri) ?: return null
        return when (type.lowercase()) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> null
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            fun addAll(base: File, f: File) {
                if (f.isDirectory) {
                    f.listFiles()?.forEach { addAll(base, it) }
                } else {
                    val entryName = f.relativeTo(base).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(entryName))
                    f.inputStream().use { it.copyTo(zos, 8 * 1024) }
                    zos.closeEntry()
                }
            }
            addAll(sourceDir, sourceDir)
        }
    }
}
