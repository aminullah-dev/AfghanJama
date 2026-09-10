package com.afghanjama.data.entities

import com.afghanjama.util.nowMillis
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * یک بازهٔ حضورِ کارمند: ورود (checkIn) و خروج (checkOut). تا وقتی خروج
 * ثبت نشده، کارمند «داخل» است. مدت کار = checkOut − checkIn.
 */
@Entity(
    tableName = "attendance",
    indices = [Index(value = ["employee"])]
)
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employee: String,
    val checkIn: Long,
    val checkOut: Long? = null,
    val createdAt: Long = nowMillis()
)
