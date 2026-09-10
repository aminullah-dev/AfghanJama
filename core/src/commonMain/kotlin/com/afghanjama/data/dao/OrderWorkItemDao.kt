package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.OrderWorkItem
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderWorkItemDao {

    @Query("SELECT * FROM order_work_items WHERE orderId = :orderId ORDER BY id ASC")
    fun observeForOrder(orderId: String): Flow<List<OrderWorkItem>>

    @Insert
    suspend fun insertAll(items: List<OrderWorkItem>)

    @Query("DELETE FROM order_work_items WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: String)
}
