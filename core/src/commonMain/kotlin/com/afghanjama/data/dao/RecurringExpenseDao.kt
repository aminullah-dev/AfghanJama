package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.afghanjama.data.entities.RecurringExpense
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringExpenseDao {

    @Query("SELECT * FROM recurring_expenses ORDER BY dayOfMonth ASC, id ASC")
    fun observeAll(): Flow<List<RecurringExpense>>

    @Query("SELECT * FROM recurring_expenses WHERE id = :id")
    suspend fun byId(id: Long): RecurringExpense?

    /**
     * هزینه‌هایی که این ماه هنوز ثبت نشده‌اند.
     *
     * فیلتر در SQL است نه در کاتلین، چون همین یک پرس‌وجو هم به هشدار
     * جواب می‌دهد هم به دکمهٔ «همه را ثبت کن» — و اگر دو جا نوشته
     * می‌شد، روزی یکی‌شان عوض می‌شد و آن یکی نه.
     */
    @Query(
        "SELECT * FROM recurring_expenses " +
            "WHERE enabled = 1 AND lastPostedYm < :ym " +
            "ORDER BY dayOfMonth ASC, id ASC"
    )
    suspend fun dueFor(ym: Int): List<RecurringExpense>

    @Upsert
    suspend fun upsert(row: RecurringExpense): Long

    @Query("UPDATE recurring_expenses SET lastPostedYm = :ym WHERE id = :id")
    suspend fun markPosted(id: Long, ym: Int)

    @Delete
    suspend fun delete(row: RecurringExpense)
}
