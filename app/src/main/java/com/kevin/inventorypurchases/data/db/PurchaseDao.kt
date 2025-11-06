package com.kevin.inventorypurchases.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert suspend fun insert(p: Purchase)
    @Query("SELECT * FROM purchases ORDER BY purchaseDateEpoch DESC, createdAt DESC")
    fun streamAll(): Flow<List<Purchase>>
    @Query("DELETE FROM purchases")
    suspend fun deleteAll()
    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deleteById(id: String)
    // PurchaseDao.kt
}
