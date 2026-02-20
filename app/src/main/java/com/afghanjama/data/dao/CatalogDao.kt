package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.GarmentDesign
import com.afghanjama.data.entities.WorkCost
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {

    @Query("SELECT * FROM WorkCost ORDER BY title ASC")
    fun observeWorkCosts(): Flow<List<WorkCost>>

    @Query("SELECT * FROM GarmentDesign ORDER BY title ASC")
    fun observeDesigns(): Flow<List<GarmentDesign>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkCosts(items: List<WorkCost>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDesigns(items: List<GarmentDesign>)

    // ✅ ویرایش خرج کار
    @Update
    suspend fun updateWorkCost(item: WorkCost)

    // ✅ حذف خرج کار
    @Query("DELETE FROM WorkCost WHERE id = :id")
    suspend fun deleteWorkCostById(id: Long)
}
