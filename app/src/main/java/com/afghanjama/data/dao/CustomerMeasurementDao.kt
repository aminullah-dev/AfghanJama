package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.CustomerMeasurement
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerMeasurementDao {

    @Query("SELECT * FROM customer_measurements WHERE customerId = :customerId ORDER BY id ASC")
    fun observeForCustomer(customerId: Long): Flow<List<CustomerMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CustomerMeasurement)

    @Query("DELETE FROM customer_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
