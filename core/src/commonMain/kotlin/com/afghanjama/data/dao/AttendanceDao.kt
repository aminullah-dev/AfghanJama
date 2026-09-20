package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert
    suspend fun insert(record: AttendanceRecord)

    @Update
    suspend fun update(record: AttendanceRecord)

    @Query("SELECT * FROM attendance ORDER BY checkIn DESC LIMIT 300")
    fun observeRecent(): Flow<List<AttendanceRecord>>

    /** همهٔ بازه‌ها از یک تاریخ به بعد (برای گزارش کارکرد ماهانه). */
    @Query("SELECT * FROM attendance WHERE checkIn >= :since ORDER BY checkIn DESC")
    fun observeSince(since: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE employee = :employee AND checkOut IS NULL ORDER BY checkIn DESC LIMIT 1")
    suspend fun findOpen(employee: String): AttendanceRecord?
}
