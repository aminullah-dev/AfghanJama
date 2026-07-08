package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.CuttingRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface CuttingRecordDao {

    @Insert
    suspend fun insert(record: CuttingRecord)

    @Query("SELECT * FROM cutting_records WHERE orderId = :orderId ORDER BY createdAt DESC")
    fun observeForOrder(orderId: String): Flow<List<CuttingRecord>>
}
