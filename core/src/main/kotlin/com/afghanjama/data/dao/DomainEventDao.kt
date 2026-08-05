package com.afghanjama.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.afghanjama.data.entities.DomainEvent
import kotlinx.coroutines.flow.Flow

/**
 * صندوقِ خروجیِ رویدادها.
 *
 * نوشتن **داخلِ همان تراکنشی** انجام می‌شود که دادهٔ اصلی نوشته
 * می‌شود؛ خواندن و مهر زدن بیرون از آن.
 */
@Dao
interface DomainEventDao {

    @Insert
    suspend fun insert(row: DomainEvent): Long

    /**
     * رویدادهایی که هنوز مصرف نشده‌اند، به ترتیبِ وقوع.
     *
     * ترتیب با `id` است نه با `at`: دو رویداد در یک تراکنش می‌توانند
     * مهرِ زمانِ یکسان بگیرند، و آن‌وقت ترتیبِ پردازش قطعی نیست. `id`
     * همیشه صعودی است.
     *
     * سقفِ ۵۰۰ عمدی است: اگر مصرف‌کننده مدتی نچرخیده باشد نباید یک‌باره
     * ده‌هزار سطر را در حافظه بیاورد.
     */
    @Query("SELECT * FROM domain_events WHERE processedAt IS NULL ORDER BY id ASC LIMIT 500")
    suspend fun pending(): List<DomainEvent>

    @Query("UPDATE domain_events SET processedAt = :at WHERE id IN (:ids)")
    suspend fun markProcessed(ids: List<Long>, at: Long = System.currentTimeMillis())

    /** تاریخچهٔ یک چیز — مثلاً همهٔ اتفاق‌هایی که برای یک سفارش افتاد. */
    @Query(
        "SELECT * FROM domain_events WHERE aggregate = :aggregate " +
            "AND aggregateId = :id ORDER BY id ASC"
    )
    fun observeFor(aggregate: String, id: String): Flow<List<DomainEvent>>

    @Query("SELECT COUNT(*) FROM domain_events WHERE processedAt IS NULL")
    suspend fun pendingCount(): Int

    /**
     * هرسِ رویدادهای **پردازش‌شدهٔ** قدیمی.
     *
     * `processedAt IS NOT NULL` شرطِ حیاتی است: رویدادی که هنوز مصرف
     * نشده هرچقدر هم قدیمی باشد نباید پاک شود — یعنی مصرف‌کننده‌ای
     * مدتی نچرخیده و پاک کردنش داده را از بین می‌برد، نه اینکه جا باز
     * کند.
     */
    @Query("DELETE FROM domain_events WHERE processedAt IS NOT NULL AND at < :before")
    suspend fun prune(before: Long): Int
}
