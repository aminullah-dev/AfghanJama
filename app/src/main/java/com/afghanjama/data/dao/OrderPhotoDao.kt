package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.OrderPhoto
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderPhotoDao {

    @Query("SELECT * FROM order_photos WHERE orderId = :orderId ORDER BY createdAt ASC")
    fun observeForOrder(orderId: String): Flow<List<OrderPhoto>>

    @Query("SELECT * FROM order_photos ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OrderPhoto>>

    @Insert
    suspend fun insert(row: OrderPhoto): Long

    @Delete
    suspend fun delete(row: OrderPhoto)

    @Query("DELETE FROM order_photos WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: String)
}
