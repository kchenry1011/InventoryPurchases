package com.kevin.inventorypurchases.util

import android.graphics.*
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ImageCompressor
 *
 * - Input: original photo file (JPEG/PNG).
 * - Output: new JPEG written to outDir with the SAME base filename, preserving EXIF.
 * - Never mutates the source file.
 *
 * Target behavior:
 * - Tries to land near targetBytes (default 180 KB).
 * - If source <= target, short-circuits and copies + re-stamps EXIF (no recompress).
 * - Maintains orientation, datetime, GPS, and your custom EXIF tags.
 */
object ImageCompressor {

    data class Options(
        val targetBytes: Int = 180_000,            // ~180 KB middle of your 100–200 KB range
        val minQuality: Int = 38,                  // guard against mushy results
        val maxQuality: Int = 92,                  // don’t waste bytes above ~92
        val maxLongestSidePx: Int = 2200,          // soft cap for very large sources
        val minLongestSidePx: Int = 800,           // don’t go below this unless absolutely necessary
        val downscaleStep: Float = 0.85f,          // 15% steps if size target not yet met
        val format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG
    )

    /**
     * Compress to target size, preserving EXIF.
     * Returns the compressed file path.
     */
    fun compressToTarget(
        input: File,
        outDir: File,
        opts: Options = Options()
    ): File {
        require(input.exists()) { "Input does not exist: ${input.absolutePath}" }
        if (!outDir.exists()) outDir.mkdirs()

        // Pass-through for non-image or unsupported formats (e.g., HEIC/GIF) – just copy
        val mime = sniffMime(input)
        val isJpegOrPng = mime == "image/jpeg" || mime == "image/png"
        if (!isJpegOrPng) {
            val passthrough = File(outDir, input.name) // keep original name
            input.copyTo(passthrough, overwrite = true)
            // Try to carry EXIF if possible (JPEG/HEIC only; PNG typically lacks EXIF)
            copyExifIfPossible(input, passthrough)
            return passthrough
        }

        // If already small enough, copy and keep EXIF — no generational loss
        if (input.length() <= opts.targetBytes) {
            val small = File(outDir, ensureJpegExtension(input.name))
            input.copyTo(small, overwrite = true)
            // Ensure .jpg and EXIF intact
            if (!small.name.endsWith(".jpg", true) && !small.name.endsWith(".jpeg", true)) {
                val renamed = File(outDir, stripExt(input.name) + ".jpg")
                small.renameTo(renamed)
                return renamed
            }
            return small
        }

        // Decode bounds to pick an initial scale
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(input.absolutePath, bounds)
        var srcW = bounds.outWidth
        var srcH = bounds.outHeight
        require(srcW > 0 && srcH > 0) { "Unable to decode image bounds for ${input.name}" }

        // Respect orientation for proper scaling decisions
        val exif = ExifInterface(input.absolutePath)
        val rotationDegrees = exifRotationDegrees(exif)
        val isRotated = rotationDegrees == 90 || rotationDegrees == 270
        val longest = max(srcW, srcH)
        val shortest = min(srcW, srcH)

        // Initial target longest side (clamped)
        var targetLongest = min(max(longest, opts.minLongestSidePx), opts.maxLongestSidePx)

        // Iteratively: resize -> binary-search quality -> if still too large, step size down
        var qualityLow = opts.minQuality
        var qualityHigh = opts.maxQuality
        var bestBytes: ByteArray? = null
        var bestQuality = -1
        var done = false

        while (!done) {
            val (tW, tH) = scaledSize(srcW, srcH, targetLongest)
            val bmp = decodeScaledBitmap(input, tW, tH, rotationDegrees) ?: break

            // Binary-search quality to hit targetBytes
            qualityLow = opts.minQuality
            qualityHigh = opts.maxQuality
            var localBest: ByteArray? = null
            var localBestQ = -1

            while (qualityLow <= qualityHigh) {
                val mid = (qualityLow + qualityHigh) / 2
                val data = compressBitmap(bmp, mid, opts.format)
                val size = data.size

                if (size > opts.targetBytes) {
                    // too big → lower quality
                    qualityHigh = mid - 1
                } else {
                    // fits → remember and try higher quality to get closer to target
                    localBest = data
                    localBestQ = mid
                    qualityLow = mid + 1
                }
            }

            bmp.recycle()

            if (localBest != null) {
                bestBytes = localBest
                bestQuality = localBestQ
                done = true
            } else {
                // Couldn’t meet target even at minQuality → step down dimensions
                val nextLongest = (targetLongest * opts.downscaleStep).roundToInt()
                if (nextLongest < opts.minLongestSidePx) {
                    // Accept whatever we got at minQuality/next step (last resort): do one more pass at min size
                    val (fW, fH) = scaledSize(srcW, srcH, opts.minLongestSidePx)
                    val finalBmp = decodeScaledBitmap(input, fW, fH, rotationDegrees) ?: break
                    val data = compressBitmap(finalBmp, opts.minQuality, opts.format)
                    finalBmp.recycle()
                    bestBytes = data
                    bestQuality = opts.minQuality
                    done = true
                } else {
                    targetLongest = nextLongest
                }
            }
        }

        // Write out JPEG & copy EXIF
        val outFile = File(outDir, ensureJpegExtension(input.name))
        FileOutputStream(outFile).use { it.write(bestBytes ?: ByteArray(0)) }

        // If we rotated pixels (rotationDegrees != 0), normalize EXIF orientation tag.
        // Otherwise, we can preserve the original tag.
        copyExifAfterCompression(
            source = input,
            dest = outFile,
            normalizeOrientation = (rotationDegrees != 0)
        )

        return outFile
    }

    // ---------- helpers ----------

    private fun sniffMime(f: File): String? {
        // Cheap sniff by header; adequate for JPEG/PNG
        return runCatching {
            FileInputStreamWithAutoClose(f).use { fis ->
                val header = ByteArray(12)
                val read = fis.read(header)
                if (read >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte()) return "image/jpeg"
                if (read >= 8 &&
                    header[0] == 0x89.toByte() && header[1] == 0x50.toByte() && header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte()
                ) return "image/png"
                null
            }
        }.getOrNull()
    }

    private class FileInputStreamWithAutoClose(private val file: File) : java.io.FileInputStream(file) {
        override fun finalize() {
            runCatching { close() }
        }
    }

    private fun stripExt(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(0, dot) else name
    }

    private fun ensureJpegExtension(name: String): String {
        val base = stripExt(name)
        return "$base.jpg"
    }

    private fun exifRotationDegrees(exif: ExifInterface): Int {
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun decodeScaledBitmap(file: File, targetW: Int, targetH: Int, rotation: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, opts)

        // Compute inSampleSize (power-of-two) for memory safety
        val sample = computeInSampleSize(opts.outWidth, opts.outHeight, targetW, targetH)
        val load = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, load) ?: return null

        // Scale to exact target
        val scaled = Bitmap.createScaledBitmap(raw, targetW, targetH, true)
        if (scaled != raw) raw.recycle()

        // Apply rotation if needed
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true)
            if (rotated != scaled) scaled.recycle()
            return rotated
        }
        return scaled
    }

    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        var halfW = srcW / 2
        var halfH = srcH / 2
        while (halfW / inSampleSize >= reqW && halfH / inSampleSize >= reqH) {
            inSampleSize *= 2
        }
        return max(1, inSampleSize)
    }

    private fun scaledSize(srcW: Int, srcH: Int, targetLongest: Int): Pair<Int, Int> {
        val longSide = max(srcW, srcH).toFloat()
        val shortSide = min(srcW, srcH).toFloat()
        val scale = targetLongest / longSide
        val tShort = (shortSide * scale).roundToInt()
        return if (srcW >= srcH) Pair(targetLongest, tShort) else Pair(tShort, targetLongest)
    }

    private fun compressBitmap(bmp: Bitmap, quality: Int, format: Bitmap.CompressFormat): ByteArray {
        val bos = ByteArrayOutputStream()
        bmp.compress(format, quality, bos)
        return bos.toByteArray()
    }

    private fun copyExifIfPossible(from: File, to: File) {
        runCatching {
            val exifFrom = ExifInterface(from.absolutePath)
            val exifTo = ExifInterface(to.absolutePath)
            copyCommonExif(exifFrom, exifTo, preserveOrientation = true)
            exifTo.saveAttributes()
        }
    }

    private fun copyExifAfterCompression(
        source: File,
        dest: File,
        normalizeOrientation: Boolean
    ) {
        runCatching {
            val exifFrom = ExifInterface(source.absolutePath)
            val exifTo = ExifInterface(dest.absolutePath)

            // Copy timestamps, GPS, camera, comments, etc.
            copyCommonExif(
                src = exifFrom,
                dst = exifTo,
                preserveOrientation = !normalizeOrientation // if we rotated pixels, don't preserve tag
            )

            if (normalizeOrientation) {
                // Pixels are upright now; tell everyone not to rotate again.
                exifTo.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString()
                )
            }

            exifTo.saveAttributes()
        }
    }


    private fun copyCommonExif(
        src: ExifInterface,
        dst: ExifInterface,
        preserveOrientation: Boolean
    ) {
        val tags = listOf(
            // Core timestamps
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_OFFSET_TIME,
            // GPS
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            // Camera
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            // Pipeline / custom
            ExifInterface.TAG_USER_COMMENT,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
        )

        for (t in tags) {
            src.getAttribute(t)?.let { dst.setAttribute(t, it) }
        }

        if (preserveOrientation) {
            val ori = src.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ori.toString())
        } else {
            // Since we rotate pixels to “upright”, normalize orientation tag
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        }

        // Carry width/height if present (some readers like it)
        if (Build.VERSION.SDK_INT >= 24) {
            val w = src.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
            val h = src.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
            if (w > 0) dst.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, w.toString())
            if (h > 0) dst.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, h.toString())
        }
    }
}
