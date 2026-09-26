package io.github.ekats.dailytaps.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

val DataJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class Converters {
    private val stylesSerializer = ListSerializer(SlotStyle.serializer())

    @TypeConverter
    fun fillToString(fill: Fill): String = DataJson.encodeToString(Fill.serializer(), fill)

    @TypeConverter
    fun stringToFill(value: String): Fill = DataJson.decodeFromString(Fill.serializer(), value)

    @TypeConverter
    fun stylesToString(styles: List<SlotStyle>): String = DataJson.encodeToString(stylesSerializer, styles)

    @TypeConverter
    fun stringToStyles(value: String): List<SlotStyle> = DataJson.decodeFromString(stylesSerializer, value)
}
