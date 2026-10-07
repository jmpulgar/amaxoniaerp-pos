package com.amaxonia.erp.data.local.db

import androidx.room.TypeConverter
import com.amaxonia.erp.data.remote.AppJson
import com.amaxonia.erp.domain.model.PriceLevel
import kotlinx.serialization.encodeToString

class Converters {
    @TypeConverter
    fun fromPriceLevelList(value: List<PriceLevel>): String =
        AppJson.encodeToString(value)

    @TypeConverter
    fun toPriceLevelList(value: String): List<PriceLevel> =
        runCatching {
            AppJson.decodeFromString<List<PriceLevel>>(value)
        }.getOrDefault(emptyList())
}
