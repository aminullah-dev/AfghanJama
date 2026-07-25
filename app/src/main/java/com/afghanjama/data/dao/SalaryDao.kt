package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.SalaryPayment
import kotlinx.coroutines.flow.Flow

@Dao
interface SalaryDao {

    @Insert
    suspend fun insert(payment: SalaryPayment): Long

    @Query("SELECT * FROM salary_payments ORDER BY at DESC")
    fun observeAll(): Flow<List<SalaryPayment>>

    @Query("SELECT * FROM salary_payments WHERE employee = :employee ORDER BY at DESC")
    fun observeForEmployee(employee: String): Flow<List<SalaryPayment>>

    @Query("DELETE FROM salary_payments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM salary_payments WHERE id = :id")
    suspend fun getById(id: Long): SalaryPayment?
}
