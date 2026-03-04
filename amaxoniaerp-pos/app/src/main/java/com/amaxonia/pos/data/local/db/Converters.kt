package com.amaxonia.pos.data.local.db

import androidx.room.TypeConverter
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.domain.model.PriceLevel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json: Json = AppJson

    @TypeConverter
    fun pricesToJson(prices: List<PriceLevel>): String {
        return json.encodeToString(ListSerializer(PriceLevel.serializer()), prices)
    }

    @TypeConverter
    fun jsonToPrices(value: String): List<PriceLevel> {
        return runCatching {
            json.decodeFromString(ListSerializer(PriceLevel.serializer()), value)
        }.getOrDefault(emptyList())
    }
}
