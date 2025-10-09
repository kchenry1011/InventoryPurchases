package com.kevin.inventorypurchases.util

import android.os.Build
import androidx.annotation.RequiresApi
import com.kevin.inventorypurchases.data.db.Purchase
import java.io.File
import java.io.FileWriter

object CsvExporter {
    private const val HEADER = "PhotoUri,Description,Price,Quantity,PurchaseDate"

    fun writeHeaderIfEmpty(file: File) {
        if (!file.exists() || file.length() == 0L) {
            file.parentFile?.mkdirs()
            file.writeText(HEADER + "\n")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun appendAll(file: File, rows: List<Purchase>) {
        FileWriter(file, /* append = */ true).use { fw ->
            for (p in rows) {
                val price = (p.priceCents.toDouble() / 100.0)
                val date = DateFmt.yyyyMmDd(p.purchaseDateEpoch)
                val line = listOf(
                    p.photoUri.orEmpty(),
                    Csv.quote(p.description),
                    price.toString(),
                    p.quantity.toString(),
                    date
                ).joinToString(",")
                fw.appendLine(line)
            }
        }
    }

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
    @RequiresApi(Build.VERSION_CODES.O)
    fun yyyyMmDd(epochMillis: Long): String {
        val z = java.time.ZoneOffset.UTC
        val d = java.time.Instant.ofEpochMilli(epochMillis).atZone(z).toLocalDate()
        return d.toString()
    }
}
