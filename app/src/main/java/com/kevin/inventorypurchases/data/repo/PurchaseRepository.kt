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
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Locale
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
                    val name = exportedNameFor(u, p.id, idx)
                    val dest = File(photosDir, ensureUniqueName(photosDir, name))
                    if (copyUri(u, dest)) dest.name else null
                }
                p.id to names
            }

            // 4) Write CSV using pretty formats + our filenames
            CsvExporter.writeCsvWithPhotoFilenames(
                csvFile = csvFile,
                rows = rows,
                photoFilenamesFor = { p -> filenamesById[p.id].orEmpty().joinToString(";") }
            )

            // 4.5) NEW: Write location.txt into exportDir (once)
            val location = com.kevin.inventorypurchases.util.LocationHelper.getBestEffortLocation(appContext)
            val locTxt = com.kevin.inventorypurchases.util.LocationHelper.toLocationTxt(location)
            File(exportDir, "location.txt").writeText(locTxt, Charsets.UTF_8)

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
private val exifFileDateFmt by lazy { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US) }

    private fun exportedNameFor(uri: Uri, purchaseId: String, index: Int): String {
        // 1) Try to use the original display name
        originalDisplayName(uri)?.let { dn ->
            val clean = dn.trim().takeIf { it.isNotEmpty() }
            if (!clean.isNullOrEmpty()) return clean
        }

        // 2) Try to derive from EXIF DateTimeOriginal
        exifTimestampName(uri)?.let { return it }

        // 3) Fallback to the legacy pattern
        val ext = guessExtension(uri) ?: ".jpg"
        return "p_${purchaseId}__${index + 1}$ext"
    }

    private fun originalDisplayName(uri: Uri): String? {
        return try {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun exifTimestampName(uri: Uri): String? {
        return try {
            // Open as stream for EXIF read
            appContext.contentResolver.openInputStream(uri)?.use { ins ->
                val exif = ExifInterface(ins)
                val dto = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
                // dto is "yyyy:MM:dd HH:mm:ss"
                // Build filename-friendly part "yyyyMMdd_HHmmss"
                val yyyy = dto.substring(0, 4)
                val MM   = dto.substring(5, 7)
                val dd   = dto.substring(8, 10)
                val HH   = dto.substring(11, 13)
                val mm   = dto.substring(14, 16)
                val ss   = dto.substring(17, 19)
                val base = "${yyyy}${MM}${dd}_${HH}${mm}${ss}"

                val sub = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
                val ext = (guessExtension(uri) ?: ".jpg")
                if (!sub.isNullOrBlank()) "IMG_${base}_${sub.padStart(3, '0')}$ext"
                else "IMG_${base}$ext"
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureUniqueName(dir: File, name: String): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var candidate = name
        var i = 1
        while (File(dir, candidate).exists()) {
            candidate = "${base}_$i$ext"
            i++
        }
        return candidate
    }

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
    private fun ZipOutputStream.putTextEntry(name: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
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
