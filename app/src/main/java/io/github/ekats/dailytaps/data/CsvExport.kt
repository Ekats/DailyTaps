package io.github.ekats.dailytaps.data

import java.io.Writer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Writes every recorded event as CSV (RFC 4180 quoting, ISO 8601 local times). */
object CsvExport {
    private val header = listOf(
        "time", "board", "slot", "row", "col", "type", "kind", "source", "state", "delta", "count", "value", "unit",
    )

    fun write(out: Writer, boards: List<BoardWithSlots>, events: List<TapEventEntity>, zone: ZoneId = ZoneId.systemDefault()) {
        val slots = boards.flatMap { it.slots }.associateBy { it.id }
        val titles = boards.associate { it.board.id to it.board.title }
        val fmt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
        out.appendLine(header.joinToString(","))
        events.forEach { e ->
            val s = slots[e.slotId]
            val row = listOf(
                Instant.ofEpochMilli(e.timestamp).atZone(zone).format(fmt),
                titles[e.boardId].orEmpty(),
                s?.label.orEmpty(),
                s?.let { (it.row + 1).toString() }.orEmpty(),
                s?.let { (it.col + 1).toString() }.orEmpty(),
                s?.type?.name.orEmpty(),
                e.kind.name,
                e.source.name,
                s?.states?.getOrNull(e.stateIndex)?.name ?: e.stateIndex.toString(),
                e.delta.toString(),
                e.count.toString(),
                e.value?.toString().orEmpty(),
                s?.valueUnit.orEmpty(),
            )
            out.appendLine(row.joinToString(",", transform = ::quote))
        }
        out.flush()
    }

    fun quote(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
