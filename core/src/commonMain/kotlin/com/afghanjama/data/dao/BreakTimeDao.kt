package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.BreakTime
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakTimeDao {

    @Query("SELECT * FROM break_times ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<BreakTime>>

    @Query("SELECT * FROM break_times WHERE enabled = 1 ORDER BY hour ASC, minute ASC")
    suspend fun listEnabled(): List<BreakTime>

    @Query("SELECT * FROM break_times WHERE id = :id")
    suspend fun getById(id: Long): BreakTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: BreakTime): Long

    @Update
    suspend fun update(row: BreakTime)

    @Delete
    suspend fun delete(row: BreakTime)

    @Query("SELECT COUNT(*) FROM break_times")
    suspend fun count(): Int

    /** چند نفر همین حالا داخلِ کارگاه‌اند (ورود زده و خروج نزده). */
    @Query("SELECT COUNT(*) FROM attendance WHERE checkOut IS NULL")
    suspend fun insideCount(): Int
}
