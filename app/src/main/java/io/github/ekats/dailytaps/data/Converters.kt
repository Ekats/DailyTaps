package io.github.ekats.dailytaps.data

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

val DataJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Room's KSP processor runs before the serialization plugin generates `X.serializer()`, so this
 * class must only use the reified `encodeToString`/`decodeFromString` entry points.
 */
class Converters {
    @TypeConverter
    fun fillToString(fill: Fill): String = DataJson.encodeToString<Fill>(fill)

    @TypeConverter
    fun stringToFill(value: String): Fill = DataJson.decodeFromString<Fill>(value)

    @TypeConverter
    fun stylesToString(styles: List<SlotStyle>): String = DataJson.encodeToString<List<SlotStyle>>(styles)

    @TypeConverter
    fun stringToStyles(value: String): List<SlotStyle> = DataJson.decodeFromString<List<SlotStyle>>(value)
}
