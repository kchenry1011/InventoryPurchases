package com.kevin.inventorypurchases.ui.form

import android.net.Uri

data class FormState(
    val photoUris: List<Uri> = emptyList(),
    val description: String = "",
    val priceText: String = "",
    val quantityText: String = "1",
    val dateMillis: Long = midnightUtc(System.currentTimeMillis()),
    val isSaving: Boolean = false,
    val error: String? = null
)

fun midnightUtc(now: Long): Long {
    val d = java.time.Instant.ofEpochMilli(now)
        .atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
    return d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
}
