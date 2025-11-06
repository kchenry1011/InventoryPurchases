package com.kevin.inventorypurchases.data.repo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
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
import com.kevin.inventorypurchases.util.ImageCompressor
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

// 3) Copy photos (compressed) + build per-row filename list
            val filenamesById: Map<String, List<String>> = rows.associate { p ->
                val uris = parsePhotoList(p.photoUri)
                val names = uris.mapIndexedNotNull { idx, u ->
                    // Keep your filename logic
                    val baseName = exportedNameFor(u, p.id, idx)

                    // Decide the target folder: photos/<group>/ or photos/
                    val targetBaseDir = p.groupName
                        ?.takeIf { it.isNotBlank() }
                        ?.let { File(photosDir, sanitizeFolder(it)).apply { mkdirs() } }
                        ?: photosDir

                    // Ensure uniqueness in the target folder (before we write the compressed copy)
                    val uniqueBaseName = ensureUniqueName(targetBaseDir, baseName)

                    // 3a) Stage URI → temp file with the chosen name (so ImageCompressor preserves base)
                    val tempStaging = File(appContext.cacheDir, uniqueBaseName)
                    val stagedOk = copyUri(u, tempStaging)
                    if (!stagedOk) {
                        tempStaging.delete()
                        null
                    } else {
                        // 3b) Compress to target dir (~100–200 KB), preserving EXIF
                        val compressed: File = ImageCompressor.compressToTarget(
                            input = tempStaging,
                            outDir = targetBaseDir,
                            opts = ImageCompressor.Options(
                                targetBytes = 180_000,   // ~100–200 KB
                                maxLongestSidePx = 2000, // tune to taste (1600–2200 is a good range)
                                minLongestSidePx = 900,
                                minQuality = 40          // guardrail for sharpness
                            )
                        )

                        // Clean up the temp staging file
                        tempStaging.delete()

                        // Return the relative path that actually exists on disk (note: util may normalize extension to .jpg)
                        if (p.groupName.isNullOrBlank()) compressed.name
                        else "${sanitizeFolder(p.groupName!!)}/${compressed.name}"
                    }
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
    private fun sanitizeFolder(name: String): String {
        // allow letters, digits, spaces, dashes, underscores; collapse others to _
        val cleaned = name.trim().replace(Regex("[^A-Za-z0-9 _-]"), "_")
        return cleaned.ifEmpty { "group" }
    }
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

    data class CacheClearResult(
        val bytesFreed: Long,
        val entriesRemoved: Int
    )

    suspend fun clearExportCache(): CacheClearResult = withContext(Dispatchers.IO) {
        val cache = appContext.cacheDir
        val targets = cache.listFiles()?.filter { f ->
            // Only delete our stuff; originals live elsewhere
            val n = f.name.lowercase()
            n.startsWith("export_") ||            // export_<timestamp>/ working dir
                    n.startsWith("inventory_") && n.endsWith(".zip") || // generated zips
                    n.startsWith("img_stage_") ||         // per-image staging files
                    n == "export_images"                  // image staging dir (older impl)
        }.orEmpty()

        var freed = 0L
        var removed = 0

        fun sizeOf(file: File): Long {
            if (!file.exists()) return 0
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        }

        targets.forEach { f ->
            val sz = sizeOf(f)
            if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (!f.exists()) {
                freed += sz
                removed += 1
            }
        }
        CacheClearResult(bytesFreed = freed, entriesRemoved = removed)
    }

    // PurchaseRepository.kt
    data class WipeResult(
        val recordsDeleted: Int,
        val photosDeleted: Int,
        val photosFailed: Int
    )

    suspend fun wipeAllPurchasesAndPhotos(): WipeResult = withContext(Dispatchers.IO) {
        val cr = appContext.contentResolver
        var del = 0
        var fail = 0

        // 1) Delete all MediaStore images under Pictures/InventoryPurchases (Q+)
        runCatching {
            val images = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val sel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                // Pre-Q doesn’t have RELATIVE_PATH; we’ll also try name prefix below as a fallback.
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            }
            val args = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("Pictures/InventoryPurchases%")
            } else {
                arrayOf("inv_%")
            }
            val n = cr.delete(images, sel, args)
            if (n > 0) del += n
        }.onFailure { /* keep going; we’ll sweep by names below too */ }

        // 2) Safety sweep: delete any inv_*.jpg we can resolve in MediaStore (covers secondary volumes)
        runCatching {
            val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                listOf(
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                )
            else listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

            volumes.forEach { base ->
                cr.query(
                    base,
                    arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME),
                    "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                    arrayOf("inv_%"),
                    null
                )?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (c.moveToNext()) {
                        val id = c.getLong(idIdx)
                        val target = ContentUris.withAppendedId(base, id)
                        val n = cr.delete(target, null, null)
                        if (n > 0) del += n else fail++
                    }
                }
            }
        }

        // 3) Delete preview captures living in cache (photo_*.jpg)
        appContext.cacheDir
            .listFiles { f -> f.isFile && f.name.startsWith("photo_") && f.name.endsWith(".jpg") }
            ?.forEach { f -> if (f.delete()) del++ else fail++ }

        // 4) Clear DB last (we’re wiping *all* records)
        val records = streamAll().first().size
        dao.deleteAll()

        // 5) Tidy our export caches (optional)
        runCatching { clearExportCache() }

        WipeResult(recordsDeleted = records, photosDeleted = del, photosFailed = fail)
    }

}

