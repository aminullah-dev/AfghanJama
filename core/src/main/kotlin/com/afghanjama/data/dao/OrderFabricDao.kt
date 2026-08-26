package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.OrderFabric
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderFabricDao {

    @Query("SELECT * FROM order_fabrics WHERE orderId = :orderId ORDER BY id ASC")
    fun observeForOrder(orderId: String): Flow<List<OrderFabric>>

    @Query("SELECT * FROM order_fabrics WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun listForOrder(orderId: String): List<OrderFabric>

    @Insert
    suspend fun insertAll(items: List<OrderFabric>)

    @Query("DELETE FROM order_fabrics WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: String)
}
