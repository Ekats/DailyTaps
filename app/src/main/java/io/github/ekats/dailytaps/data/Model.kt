package io.github.ekats.dailytaps.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val MAX_GRID = 7
const val MIN_GRID = 1

/** How a slot (or the board background) is painted. Colors are ARGB ints. */
@Serializable
sealed interface Fill {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: Int) : Fill

    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val start: Int,
        val end: Int,
        val direction: GradientDirection = GradientDirection.TOP_BOTTOM,
    ) : Fill
}

@Serializable
enum class GradientDirection {
    TOP_BOTTOM,
    LEFT_RIGHT,
    TOP_LEFT_BOTTOM_RIGHT,
    BOTTOM_LEFT_TOP_RIGHT,
    RADIAL,
}

/** One visual state of a slot: its name (shown as secondary text), fill and text color. */
@Serializable
data class SlotStyle(
    val name: String,
    val fill: Fill,
    val textColor: Int,
)

enum class SlotType {
    /** Tapping advances to the next state and wraps. Two states is a plain on/off toggle. */
    STATES,

    /** Tapping adds [SlotEntity.counterStep]. Color moves from state 0 to state 1 as the count nears the target. */
    COUNTER,

    /** Tapping asks for a number. State 0 is shown until a value is logged, then state 1. */
    VALUE,
}

enum class TextScale(val factor: Float) {
    SMALL(0.8f),
    MEDIUM(1f),
    LARGE(1.25f),
}

enum class EventSource { WIDGET, APP }

enum class EventKind {
    /** A tap that changed a STATES slot or bumped a COUNTER slot. */
    PRESS,

    /** A number logged into a VALUE slot. */
    VALUE,

    /** A manual correction from the app (set state, adjust counter, reset). Not counted as a tap. */
    ADJUST,
}

object Defaults {
    const val BOARD_BACKGROUND = 0xE61C1B1F.toInt()
    const val TITLE_COLOR = 0xFFE6E1E5.toInt()
    const val OFF_COLOR = 0xFF3A3940.toInt()
    const val OFF_TEXT = 0xFFE6E1E5.toInt()
    const val ON_COLOR = 0xFF2E9E5B.toInt()
    const val ON_TEXT = 0xFFFFFFFF.toInt()

    fun offStyle(name: String = "Off") = SlotStyle(name, Fill.Solid(OFF_COLOR), OFF_TEXT)
    fun onStyle(name: String = "On") = SlotStyle(name, Fill.Solid(ON_COLOR), ON_TEXT)

    fun statesFor(type: SlotType): List<SlotStyle> = when (type) {
        SlotType.STATES -> listOf(offStyle(), onStyle())
        SlotType.COUNTER -> listOf(offStyle("Zero"), onStyle("Target"))
        SlotType.VALUE -> listOf(offStyle("Empty"), onStyle("Logged"))
    }

    /** Swatches offered by the color picker, roughly one per hue plus neutrals. */
    val swatches: List<Int> = listOf(
        0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF5E35B1, 0xFF3949AB, 0xFF1E88E5,
        0xFF039BE5, 0xFF00ACC1, 0xFF00897B, 0xFF2E9E5B, 0xFF7CB342, 0xFFC0CA33,
        0xFFFDD835, 0xFFFFB300, 0xFFFB8C00, 0xFFF4511E, 0xFF6D4C41, 0xFF757575,
        0xFF546E7A, 0xFF3A3940, 0xFF1C1B1F, 0xFF000000, 0xFFFFFFFF, 0x00000000,
    ).map { it.toInt() }
}
