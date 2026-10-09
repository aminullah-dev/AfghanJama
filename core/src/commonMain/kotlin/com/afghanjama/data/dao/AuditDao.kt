package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.AuditLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Insert
    suspend fun insert(row: AuditLog)

    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT 400")
    fun observeRecent(): Flow<List<AuditLog>>

    /** زودترین ردیفِ یک کار — برای لنگرِ دورهٔ آزمایشیِ لایسنس در خودِ دفتر. */
    @Query("SELECT MIN(at) FROM audit_log WHERE action = :action")
    suspend fun firstAt(action: String): Long?
}
