package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Insert
    suspend fun insert(doc: Document): Long

    @Query("UPDATE documents SET number = :number WHERE id = :id")
    suspend fun setNumber(id: Long, number: String)

    @Query("SELECT * FROM documents ORDER BY at DESC")
    fun observeAll(): Flow<List<Document>>
}
