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

    /**
     * کاردکس هم با نامِ تازه بیاید.
     *
     * گردشِ انبار قلم را با **نام و واحد** می‌شناسد، نه با کلیدِ خارجی.
     * پس اگر ردیفِ انبار نامش عوض شود و این‌ها نه، کلِ تاریخچهٔ آن قلم
     * بی‌صاحب می‌ماند: قلمِ تازه بی‌تاریخچه می‌شود و ردیف‌های قدیمی به
     * قلمی اشاره می‌کنند که دیگر نیست. یعنی همان چیزی که کاردکس برای
     * جلوگیری از آن هست.
     */
    @Query(
        "UPDATE stock_movements SET name = :newName, unit = :newUnit " +
            "WHERE name = :oldName AND unit = :oldUnit"
    )
    suspend fun rename(oldName: String, oldUnit: String, newName: String, newUnit: String): Int

    /**
     * همهٔ گردش‌های از [since] به بعد — بدونِ LIMIT، چون برای محاسبهٔ
     * نرخِ مصرف باید کلِ بازه دیده شود نه فقط ۳۰۰ ردیفِ آخر.
     */
    @Query("SELECT * FROM stock_movements WHERE createdAt >= :since ORDER BY createdAt DESC")
    fun observeSince(since: Long): Flow<List<StockMovement>>
}
