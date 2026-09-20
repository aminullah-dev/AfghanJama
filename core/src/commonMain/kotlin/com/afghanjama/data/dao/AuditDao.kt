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
}
