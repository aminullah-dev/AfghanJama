package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.CustomerInstallment
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerInstallmentDao {

    @Query("SELECT * FROM customer_installments ORDER BY dueDate ASC, id ASC")
    fun observeAll(): Flow<List<CustomerInstallment>>

    @Query(
        "SELECT * FROM customer_installments WHERE customerName = :name " +
            "ORDER BY dueDate ASC, id ASC"
    )
    fun observeFor(name: String): Flow<List<CustomerInstallment>>

    @Insert
    suspend fun insert(row: CustomerInstallment)

    @Delete
    suspend fun delete(row: CustomerInstallment)
}
