package com.kevin.inventorypurchases.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "purchases")
data class Purchase(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val photoUri: String?,
    val description: String,
    val priceCents: Long,
    val quantity: Int,
    val purchaseDateEpoch: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String = "",
    val groupName: String? = null
)
