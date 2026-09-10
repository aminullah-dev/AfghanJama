package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.OrderStageLog
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderStageLogDao {

    @Query("SELECT * FROM order_stage_logs WHERE orderId = :orderId ORDER BY at ASC")
    fun observeForOrder(orderId: String): Flow<List<OrderStageLog>>

    @Insert
    suspend fun insert(log: OrderStageLog)
}
