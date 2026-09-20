package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.CustomerPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerPaymentDao {

    @Query("SELECT * FROM customer_payments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CustomerPayment>>

    @Insert
    suspend fun insert(payment: CustomerPayment)
}
