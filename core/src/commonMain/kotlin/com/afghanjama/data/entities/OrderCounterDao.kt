package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.afghanjama.data.entities.OrderCounter

@Dao
interface OrderCounterDao {
    @Query("SELECT * FROM OrderCounter WHERE id=1 LIMIT 1")
    suspend fun get(): OrderCounter?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(counter: OrderCounter)
}
