package com.kevin.inventorypurchases.util

import com.kevin.inventorypurchases.data.db.Purchase
import java.io.File
import java.io.FileWriter
import java.util.Locale

object CsvExporter {
    private val header = listOf(
        "Id",
        "Description",
        "Price",              // $12.34
        "Quantity",
        "PurchaseDate",       // MM/dd/yyyy
        "Notes",
        "PhotoFileNames"      // semicolon-separated
    )

    fun writeCsvWithPhotoFilenames(
        csvFile: File,
        rows: List<Purchase>,
        photoFilenamesFor: (Purchase) -> String
    ) {
        FileWriter(csvFile, false).use { w ->
            w.appendLine(header.joinToString(","))
            rows.forEach { p ->
                val priceDollars = String.format(Locale.US, "$%.2f", p.priceCents / 100.0)
                val dateStr = p.purchaseDateEpoch.let { DateFmt.mmDdYyyy(it) }
                val cols = listOf(
                    escape(p.id),
                    escape(p.description),
                    escape(priceDollars),
                    p.quantity.toString(),
                    escape(dateStr),
                    escape(p.notes),
                    escape(photoFilenamesFor(p))
                )
                val line = cols.joinToString(",")
                // android.util.Log.d("CsvExporter", "CSV: $line")
                w.appendLine(line)
            }
            w.flush()
        }
    }

    private fun escape(s: String?): String {
        val v = s ?: ""
        return if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r')) {
            "\"${v.replace("\"", "\"\"")}\""
        } else v
    }
}
