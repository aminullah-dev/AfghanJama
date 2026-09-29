package com.afghanjama.data.dao

import com.afghanjama.util.nowMillis
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
/** خروجیِ [DomainEventDao.observeLatestAt] — شناسه و آخرین زمان. */
data class EventStamp(val id: String, val at: Long)

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
    suspend fun markProcessed(ids: List<Long>, at: Long = nowMillis())

    /** تاریخچهٔ یک چیز — مثلاً همهٔ اتفاق‌هایی که برای یک سفارش افتاد. */
    @Query(
        "SELECT * FROM domain_events WHERE aggregate = :aggregate " +
            "AND aggregateId = :id ORDER BY id ASC"
    )
    fun observeFor(aggregate: String, id: String): Flow<List<DomainEvent>>

    /**
     * آخرین باری که هر موجودیت، رویدادی از نوعِ [type] گرفته.
     *
     * **چرا جمعی و نه یکی‌یکی.** صفِ تحویل ده‌ها ردیف دارد و پرسیدنِ
     * «این یکی خبر داده شده؟» برای هر ردیف یعنی ده‌ها پرس‌وجو. اینجا
     * یک پرس‌وجو نقشهٔ کاملِ «شناسه ← آخرین زمان» را می‌دهد و صفحه از
     * روی همان می‌خواند.
     *
     * ایندکسِ `(aggregate, aggregateId)` همین را پوشش می‌دهد.
     */
    @Query(
        """
        SELECT aggregateId AS id, MAX(at) AS at
        FROM domain_events
        WHERE type = :type AND aggregate = :aggregate
        GROUP BY aggregateId
        """
    )
    fun observeLatestAt(type: String, aggregate: String): Flow<List<EventStamp>>

    /**
     * آخرین رویدادِ نوعِ [type] برای هر موجودیت — **خودِ سطر**، نه فقط
     * زمانش.
     *
     * [observeLatestAt] برای «خبر داده شد یا نه» کافی بود. پیگیریِ طلب
     * بیشتر می‌خواهد: قول داد یا جواب نداد، تا کِی، و بدهی در آن لحظه —
     * که همه در `payload`اند. یک پرس‌وجو برای کلِ فهرست، مثلِ همان.
     */
    @Query(
        """
        SELECT * FROM domain_events WHERE id IN (
            SELECT MAX(id) FROM domain_events
            WHERE type = :type AND aggregate = :aggregate
            GROUP BY aggregateId
        )
        """
    )
    fun observeLatestOf(type: String, aggregate: String): Flow<List<DomainEvent>>

    /**
     * رویدادهای یک موجودیت را به نامِ تازه‌اش می‌برد — وقتی کلیدش نام
     * است و نام عوض شد. بی این، قولی که مشتری داده با عوض شدنِ املای
     * نامش گم می‌شد.
     */
    @Query(
        "UPDATE domain_events SET aggregateId = :to " +
            "WHERE aggregate = :aggregate AND aggregateId = :from"
    )
    suspend fun moveAggregate(aggregate: String, from: String, to: String): Int

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

    /**
     * آیا این صندوقِ خروجی **تا به حال** مصرف‌کننده‌ای داشته؟
     *
     * شرطِ `processedAt IS NOT NULL` در [prune] برای محافظت از
     * مصرف‌کننده است. ولی اگر هیچ مصرف‌کننده‌ای وجود نداشته باشد،
     * `processedAt` هرگز پر نمی‌شود و آن شرط یعنی **هیچ سطری هرگز
     * پاک نمی‌شود** — یعنی نگهبانی که همیشه هیچ می‌کند.
     */
    @Query("SELECT COUNT(*) FROM domain_events WHERE processedAt IS NOT NULL")
    suspend fun processedCount(): Int

    /**
     * هرس فقط بر اساسِ سن.
     *
     * این را تنها وقتی صدا بزنید که ثابت شده باشد هیچ مصرف‌کننده‌ای
     * وجود ندارد ([processedCount] برابرِ صفر). با وجودِ مصرف‌کننده،
     * [prune] درست است نه این.
     */
    @Query("DELETE FROM domain_events WHERE at < :before")
    suspend fun pruneByAge(before: Long): Int
}
