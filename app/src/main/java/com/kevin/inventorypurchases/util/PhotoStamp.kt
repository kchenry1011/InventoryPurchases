package com.kevin.inventorypurchases.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PhotoStamp {

    private val exifDateFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val fileDateFmt = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /**
     * Imports a photo Uri into app storage with a timestamped filename.
     * Ensures EXIF DateTimeOriginal is present; writes it if missing.
     * Returns a content:// Uri via FileProvider for persistence/sharing.
     */
    fun importAndStamp(context: Context, source: Uri): Uri {
        val appId = context.packageName
        val picturesDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "InventoryPurchases")
        picturesDir.mkdirs()

        // Prefer timestamp from source EXIF if present; else now().
        val (stampForFile, stampForExif) = readSourceTimestamp(context, source) ?: run {
            val now = Date()
            fileDateFmt.format(now) to exifDateFmt.format(now)
        }

        val dest = File(picturesDir, "IMG_${stampForFile}.jpg")

        // Copy bytes
        context.contentResolver.openInputStream(source)?.use { ins ->
            FileOutputStream(dest).use { outs ->
                ins.copyTo(outs, 8 * 1024)
            }
        } ?: error("Unable to open input stream for $source")

        // Ensure EXIF timestamps exist
        ensureExif(dest, stampForExif)

        return FileProvider.getUriForFile(context, "$appId.fileprovider", dest)
    }

    private fun readSourceTimestamp(context: Context, src: Uri): Pair<String,String>? {
        return try {
            context.contentResolver.openInputStream(src)?.use { ins ->
                val exif = ExifInterface(ins)
                val dto = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                val sub = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
                if (dto != null) {
                    // Use source’s EXIF for filename; keep colons out of filename
                    val normalized = dto.replace(':','-').replace(' ','_')
                    val filePart = normalized
                        .replace("-", "") // strip colons->dashes already
                        .replace("_", "") // strip space
                        .takeIf { it.length >= 15 } // yyyyMMDDHHmmss at least
                        ?: fileDateFmt.format(Date())
                    // Append subsec into filename if available
                    val name = if (!sub.isNullOrBlank()) "${filePart}_${sub.padStart(3,'0')}" else filePart
                    // Keep original dto for EXIF writebacks
                    name to dto
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureExif(file: File, dto: String) {
        try {
            val exif = ExifInterface(file.absolutePath)
            if (exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).isNullOrBlank()) {
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dto)
            }
            // Subseconds: if we derived now(), we won’t have subsec; that’s fine
            if (exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL).isNullOrBlank()) {
                val tz = TimeZone.getDefault()
                val offsetMillis = tz.getOffset(System.currentTimeMillis())
                val sign = if (offsetMillis >= 0) "+" else "-"
                val totalMinutes = kotlin.math.abs(offsetMillis / 60000)
                val hh = (totalMinutes / 60).toString().padStart(2, '0')
                val mm = (totalMinutes % 60).toString().padStart(2, '0')
                exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, "$sign$hh:$mm")
            }
            exif.saveAttributes()
        } catch (_: Throwable) {
            // Non-fatal: the file copy succeeded; EXIF is best-effort
        }
    }
}
