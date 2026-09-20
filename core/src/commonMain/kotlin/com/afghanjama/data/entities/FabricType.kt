package com.afghanjama.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fabric_types",
    indices = [Index(value = ["title"], unique = true)]
)
data class FabricType(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,

    /**
     * فصلی که این پارچه برایش مناسب است — تابستان، زمستان، چهارفصل…
     *
     * ویژگیِ خودِ پارچه است نه هر خرید: کتان همیشه تابستانی می‌مانَد و
     * مخمل همیشه زمستانی. پس یک بار در «اطلاعات پایه» داده می‌شود و
     * هنگامِ خرید فقط دیده می‌شود.
     *
     * خالی مجاز است و یعنی «تعیین‌نشده»، پس پارچه‌های ثبت‌شده دست‌نخورده
     * می‌مانند.
     */
    @ColumnInfo(defaultValue = "")
    val season: String = ""
)

/** فصل‌هایی که می‌شود به پارچه نسبت داد. خالی یعنی تعیین‌نشده. */
object FabricSeasons {
    const val NONE = ""
    val ALL = listOf("چهارفصل", "تابستان", "زمستان", "بهار و پاییز")
}

/** واحدهای رایجِ خرید پارچه در بازار. */
object FabricUnits {
    val ALL = listOf("متر", "یارد", "ثوب", "توپ")
}
