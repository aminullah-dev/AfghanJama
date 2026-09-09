package com.afghanjama.data

import androidx.room.TypeConverter
import com.afghanjama.util.UUID
import com.afghanjama.util.randomUuid
import com.afghanjama.util.uuidOf

class Converters {
    @TypeConverter
    fun uuidToString(v: UUID?): String? = v?.toString()

    @TypeConverter
    fun stringToUuid(v: String?): UUID? = v?.let(::uuidOf)
}
