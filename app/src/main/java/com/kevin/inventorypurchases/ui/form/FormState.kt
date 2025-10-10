package com.kevin.inventorypurchases.ui.form

import android.net.Uri

data class FormState(
    val photoUris: List<Uri> = emptyList(),
    val description: String = "",
    val priceText: String = "",
    val quantityText: String = "1",
    val dateMillis: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val notes: String = "",
    val error: String? = null
)
