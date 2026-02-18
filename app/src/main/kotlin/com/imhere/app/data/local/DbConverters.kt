package com.imhere.app.data.local

import androidx.room.TypeConverter
import com.imhere.core.model.StopReason

class DbConverters {
    @TypeConverter
    fun toStopReason(raw: String?): StopReason? = raw?.let(StopReason::valueOf)

    @TypeConverter
    fun fromStopReason(reason: StopReason?): String? = reason?.name
}
