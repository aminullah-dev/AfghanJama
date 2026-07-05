// app/src/main/java/com/afghanjama/data/dao/OrderDao.kt
package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.afghanjama.data.entities.Order
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface OrderDao {

    /**
     * دریافت تمام سفارشات به ترتیب جدیدترین‌ها.
     * نام جدول حتماً باید 'orders' باشد.
     */
    @Query("SELECT * FROM orders ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Order>>

    /**
     * فیلتر کردن سفارشات بر اساس وضعیت (مانند CUTTING, SEWING, SALES).
     */
    @Query("SELECT * FROM orders WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<Order>>

    /**
     * پیدا کردن یک سفارش خاص بر اساس شناسه منحصر به فرد (UUID).
     */
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getById(id: UUID): Order?

    @Query("SELECT * FROM orders WHERE id = :id")
    fun observeById(id: UUID): Flow<Order?>

    /**
     * درج سفارش جدید یا جایگزینی در صورت تداخل.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(order: Order)

    /**
     * بروزرسانی اطلاعات یک سفارش موجود (مثلاً تغییر وضعیت، نام خیاط، ...).
     */
    @Update
    suspend fun update(order: Order)

    /**
     * حذف یک سفارش از دیتابیس.
     */
    @Delete
    suspend fun delete(order: Order)

    /**
     * متد کمکی برای حذف تمام سفارشات (در صورت نیاز به ریست کردن دیتا).
     */
    @Query("DELETE FROM orders")
    suspend fun deleteAll()
}
