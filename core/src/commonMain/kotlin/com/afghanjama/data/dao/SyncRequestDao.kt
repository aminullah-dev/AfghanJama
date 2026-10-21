package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.SyncRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRequestDao {

    @Query("SELECT * FROM sync_requests ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<SyncRequest>>

    @Query("SELECT * FROM sync_requests WHERE status = 'PENDING' ORDER BY createdAt ASC")
    fun observePending(): Flow<List<SyncRequest>>

    @Query("SELECT COUNT(*) FROM sync_requests WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM sync_requests WHERE id = :id")
    suspend fun getById(id: Long): SyncRequest?

    @Insert
    suspend fun insert(row: SyncRequest): Long

    /** همان کارِ گوشی، اگر پیش‌تر رسیده (قراردادِ ۲). */
    @Query("SELECT * FROM sync_requests WHERE clientKey = :key")
    suspend fun getByClientKey(key: String): SyncRequest?

    /** درخواست‌های یک کارگر، تازه‌ترین اول — برای تصویرِ گوشیِ خودش. */
    @Query("SELECT * FROM sync_requests WHERE worker = :worker ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentFor(worker: String, limit: Int): List<SyncRequest>

    @Update
    suspend fun update(row: SyncRequest)
}
