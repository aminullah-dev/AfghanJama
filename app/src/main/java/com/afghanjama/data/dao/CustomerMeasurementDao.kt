package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.CustomerMeasurement
import kotlinx.coroutines.flow.Flow

/**
 * یک اندازه همراهِ نامِ صاحبش. سفارش‌ها مشتری را با نام می‌شناسند نه با
 * شناسه، پس برای رساندنِ اندازه به برش و دوخت همین شکل لازم است.
 */
data class NamedMeasurement(
    val customerName: String,
    val label: String,
    val value: String
)

@Dao
interface CustomerMeasurementDao {

    @Query("SELECT * FROM customer_measurements WHERE customerId = :customerId ORDER BY id ASC")
    fun observeForCustomer(customerId: Long): Flow<List<CustomerMeasurement>>

    /**
     * همهٔ اندازه‌ها با نامِ مشتری. حجمش کوچک است (چند ده مشتری × چند
     * اندازه) پس یک‌جا خوانده می‌شود و صفحه‌های برش و دوخت بدونِ
     * کوئریِ جداگانه برای هر سطر از آن می‌خوانند.
     */
    @Query(
        "SELECT p.name AS customerName, m.label AS label, m.value AS value " +
            "FROM customer_measurements m " +
            "INNER JOIN parties p ON p.id = m.customerId " +
            "WHERE p.type = 'CUSTOMER' AND TRIM(m.label) <> '' " +
            "ORDER BY p.name ASC, m.id ASC"
    )
    fun observeAllNamed(): Flow<List<NamedMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: CustomerMeasurement)

    @Query("DELETE FROM customer_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
