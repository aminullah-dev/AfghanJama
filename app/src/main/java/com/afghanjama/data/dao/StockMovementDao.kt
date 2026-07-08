package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.StockMovement
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {

    @Insert
    suspend fun insert(movement: StockMovement)

    @Query("SELECT * FROM stock_movements ORDER BY createdAt DESC LIMIT 300")
    fun observeRecent(): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE name = :name AND unit = :unit ORDER BY createdAt DESC")
    fun observeForItem(name: String, unit: String): Flow<List<StockMovement>>
}
