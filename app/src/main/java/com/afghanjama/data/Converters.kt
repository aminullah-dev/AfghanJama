// app/src/main/java/com/afghanjama/data/Converters.kt
package com.afghanjama.data

import androidx.room.TypeConverter
import java.util.UUID

class Converters {
    @TypeConverter
    fun uuidToString(v: UUID?): String? = v?.toString()

    @TypeConverter
    fun stringToUuid(v: String?): UUID? = v?.let(UUID::fromString)
}
