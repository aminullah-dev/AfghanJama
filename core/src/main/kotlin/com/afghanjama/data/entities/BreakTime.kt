package com.afghanjama.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * یک وقتِ ثابتِ روزانهٔ کارگاه: چای صبح، نان چاشت، چای عصر.
 *
 * ساعت و دقیقه جدا نگه داشته می‌شوند نه به‌صورت زمانِ مطلق، چون این یک
 * قرارِ هرروزه است نه یک رویدادِ یک‌بار؛ با عوض‌شدنِ روز نباید چیزی
 * دوباره تنظیم شود.
 */
@Entity(tableName = "break_times")
data class BreakTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val hour: Int,                 // ۰ تا ۲۳
    val minute: Int,               // ۰ تا ۵۹
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** «۱۲:۰۵» — همیشه دو رقمی تا ستون‌ها زیرِ هم بمانند. */
    val clock: String
        get() = "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
}
