package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.SewingAssignment
import kotlinx.coroutines.flow.Flow

@Dao
interface SewingAssignmentDao {

    @Query("SELECT * FROM sewing_assignments WHERE orderId = :orderId ORDER BY createdAt ASC")
    fun observeForOrder(orderId: String): Flow<List<SewingAssignment>>

    @Query("SELECT * FROM sewing_assignments WHERE orderId = :orderId")
    suspend fun listForOrder(orderId: String): List<SewingAssignment>

    /** همه تحویل‌های در حال دوخت (برای تب «در حال دوخت»). */
    @Query("SELECT * FROM sewing_assignments WHERE status = 'SEWING' ORDER BY createdAt ASC")
    fun observeInProgress(): Flow<List<SewingAssignment>>

    /** همه تحویل‌ها (برای محاسبهٔ تعداد تحویل‌شدهٔ هر سفارش). */
    @Query("SELECT * FROM sewing_assignments ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SewingAssignment>>

    @Query("SELECT * FROM sewing_assignments WHERE id = :id")
    suspend fun getById(id: Long): SewingAssignment?

    @Insert
    suspend fun insert(assignment: SewingAssignment): Long

    @Update
    suspend fun update(assignment: SewingAssignment)

    @Query("DELETE FROM sewing_assignments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sewing_assignments WHERE orderId = :orderId")
    suspend fun deleteForOrder(orderId: String)
}
