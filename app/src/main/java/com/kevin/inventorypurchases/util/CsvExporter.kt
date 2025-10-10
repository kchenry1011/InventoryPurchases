package com.kevin.inventorypurchases.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.kevin.inventorypurchases.data.db.Purchase
import java.io.File
import java.io.FileWriter

object CsvExporter {
    private const val HEADER = "PhotoUri,PurchaseDate,Description,Price,Quantity,Notes"

    fun writeHeaderIfEmpty(file: File) {
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            file.writeText(HEADER + "\n")
        }
    }

    fun appendAll(file: File, rows: List<Purchase>) {
        FileWriter(file, /* append = */ true).use { fw ->
            for (p in rows) {
                val price = (p.priceCents.toDouble() / 100.0)
                val date = DateFmt.mmDdYyyy(p.purchaseDateEpoch)
                val notesEscaped = csvEscape(p.notes)
                val line = listOf(
                    p.photoUri.orEmpty(),
                    date,
                    Csv.quote(p.description),
                    price.toString(),
                    p.quantity.toString(),
                    notesEscaped
                ).joinToString(",")
                fw.appendLine(line)
            }
        }
    }
    private fun csvEscape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"${s.replace("\"", "\"\"")}\""
        else s
    private object Csv {
        fun quote(s: String): String {
            // Quote only when needed (commas, quotes, or newlines present)
            val needs = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
            if (!needs) return s
            // Double any embedded quotes: " -> ""
            val esc = s.replace("\"", "\"\"")
            // Wrap the whole field in quotes
            return "\"$esc\""
        }
    }
}

object DateFmt {
    fun yyyyMmDd(epochMillis: Long): String {
        val z = java.time.ZoneOffset.UTC
        val d = java.time.Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDate()
        return d.toString()
    }
    fun mmDdYyyy(epoch: Long): String {
        val fmt = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US)
        return fmt.format(java.util.Date(epoch))
    }
}
