package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.FabricStock
import kotlinx.coroutines.flow.Flow

@Dao
interface FabricStockDao {

    @Query("SELECT * FROM fabric_stock ORDER BY fabricType ASC, fabricColor ASC")
    fun observeAll(): Flow<List<FabricStock>>

    @Query(
        """
        SELECT * FROM fabric_stock
        WHERE fabricType = :type AND fabricColor = :color AND fabricUnit = :unit
        LIMIT 1
        """
    )
    suspend fun find(type: String, color: String, unit: String): FabricStock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stock: FabricStock)

    @Query("DELETE FROM fabric_stock WHERE id = :id")
    suspend fun deleteById(id: Long)
}
