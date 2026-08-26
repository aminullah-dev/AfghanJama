package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.QcRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface QcRecordDao {

    @Insert
    suspend fun insert(record: QcRecord)

    @Query("SELECT * FROM qc_records WHERE orderId = :orderId ORDER BY createdAt DESC")
    fun observeForOrder(orderId: String): Flow<List<QcRecord>>

    @Query("SELECT * FROM qc_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QcRecord>>
}
