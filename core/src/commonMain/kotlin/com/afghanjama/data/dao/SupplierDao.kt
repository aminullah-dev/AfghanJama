package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.SupplierLedger
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Insert
    suspend fun insert(row: SupplierLedger)

    @Query("SELECT * FROM supplier_ledger ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SupplierLedger>>
}
