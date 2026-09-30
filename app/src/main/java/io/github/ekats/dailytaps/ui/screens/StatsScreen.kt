package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.TableRows
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardWithSlots
import io.github.ekats.dailytaps.data.ResetMode
import io.github.ekats.dailytaps.data.SlotEntity
import io.github.ekats.dailytaps.data.SlotType
import io.github.ekats.dailytaps.domain.ColorMath
import io.github.ekats.dailytaps.domain.DayValue
import io.github.ekats.dailytaps.domain.Days
import io.github.ekats.dailytaps.domain.ResetSchedule
import io.github.ekats.dailytaps.domain.SlotLogic
import io.github.ekats.dailytaps.domain.Stats
import io.github.ekats.dailytaps.domain.StatsReport
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BarItem
import io.github.ekats.dailytaps.ui.components.CalendarHeatmap
import io.github.ekats.dailytaps.ui.components.ColumnChart
import io.github.ekats.dailytaps.ui.components.HorizontalBars
import io.github.ekats.dailytaps.ui.components.LineChart
import io.github.ekats.dailytaps.ui.components.formatAxis
import io.github.ekats.dailytaps.ui.theme.ChartColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale

private val ranges = listOf(7, 30, 90, 365)

/** Graphs for one board, or all boards when [boardId] is null. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(boardId: Long?, onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    var range by rememberSaveable { mutableIntStateOf(30) }
    var slotId by rememberSaveable { mutableStateOf<Long?>(null) }
    var table by rememberSaveable { mutableStateOf(false) }

    val today = remember { Days.today() }
    val fromDay = today - range + 1
    val boards by remember { repo.observeAllBoards() }.collectAsStateWithLifecycle(emptyList())
    val events by remember(boardId, fromDay) {
        repo.observeEventsSince(boardId, Days.startOfDayMillis(fromDay))
    }.collectAsStateWithLifecycle(emptyList())

    val board = boards.firstOrNull { it.board.id == boardId }
    val slot = board?.visibleSlots?.firstOrNull { it.id == slotId }
    val schedule = board?.board?.let(ResetSchedule::of) ?: ResetSchedule(ResetMode.NEVER)
    val report = remember(events, fromDay, today, slot, schedule) {
        Stats.report(events, fromDay, today, slot, schedule)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (boardId == null) stringResource(R.string.stats_all)
                        else board?.board?.title?.ifBlank { null } ?: stringResource(R.string.stats),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconToggleButton(checked = table, onCheckedChange = { table = it }) {
                        Icon(Icons.Default.TableRows, contentDescription = stringResource(R.string.show_table))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
        ) {
            // Filters: one row above the charts.
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ranges.forEach { d ->
                    FilterChip(selected = range == d, onClick = { range = d }, label = { Text(rangeLabel(d)) })
                }
                if (board != null) SlotFilter(board, slot) { slotId = it }
            }

            if (table) {
                DailyTable(report)
                return@Column
            }

            Tiles(report)

            val boardSlots = boards.flatMap { it.slots }
            val caption = if (slot != null) slotName(slot, "") else null

            ChartCard(stringResource(R.string.chart_taps_per_day), caption) {
                TapsChart(report)
            }

            if (slot?.type == SlotType.COUNTER) {
                ChartCard(stringResource(R.string.chart_counter_per_day), caption) {
                    val unit = stringResource(R.string.count_unit)
                    ColumnChart(
                        values = report.counterDaily.map { it.value },
                        hint = stringResource(R.string.tap_bar_hint),
                        axisLabel = { i -> sparseDayLabel(report.counterDaily, i) },
                        describe = { i -> "${longDate(report.counterDaily[i].epochDay)}: ${formatAxis(report.counterDaily[i].value)} $unit" },
                    )
                }
            }

            if (slot?.type == SlotType.VALUE) {
                ChartCard(stringResource(R.string.chart_values), caption) {
                    if (report.values.isEmpty()) {
                        Empty()
                    } else {
                        LineChart(
                            points = report.values,
                            hint = stringResource(R.string.tap_point_hint),
                            describe = { p -> "${dateTime(p.timestamp)}: ${SlotLogic.formatValue(p.value, slot.valueUnit)}" },
                            axisLabel = { t -> shortDate(Days.of(t)) },
                        )
                    }
                }
            }

            if (range >= 30) {
                val (days, title) = if (slot != null) {
                    report.completion to stringResource(R.string.chart_done_days)
                } else {
                    report.dailyTaps to stringResource(R.string.chart_activity)
                }
                ChartCard(title, caption) {
                    val ramp = ChartColors.heat()
                    CalendarHeatmap(
                        days = days,
                        // Done/not-done has only one "on" step: use the ramp's strongest color.
                        ramp = if (slot != null) listOf(ramp[3]) else ramp,
                        firstDay = WeekFields.of(LocalResources.current.configuration.locales[0]).firstDayOfWeek,
                        hint = stringResource(R.string.tap_day_hint),
                        describe = { d ->
                            if (slot != null) {
                                "${longDate(d.epochDay)}: " + if (d.value > 0) "✓" else "–"
                            } else {
                                "${longDate(d.epochDay)}: " + tapsText(d.value.toInt())
                            }
                        },
                    )
                }
            }

            ChartCard(stringResource(R.string.chart_time_of_day), caption) {
                ColumnChart(
                    values = report.hourly.map { it.toDouble() },
                    height = 140.dp,
                    hint = stringResource(R.string.tap_bar_hint),
                    axisLabel = { h -> if (h % 6 == 0) "%02d".format(h) else null },
                    describe = { h -> "%02d:00–%02d:59: ".format(h, h) + tapsText(report.hourly[h]) },
                )
            }

            if (slot == null) {
                val items = if (boardId == null) {
                    report.perSlot.groupBy { t -> boardSlots.firstOrNull { it.id == t.slotId }?.boardId }
                        .mapNotNull { (bid, list) ->
                            val b = boards.firstOrNull { it.board.id == bid } ?: return@mapNotNull null
                            BarItem(b.board.title.ifBlank { stringResource(R.string.untitled_board) }, list.sumOf { it.taps }.toDouble(), null)
                        }
                        .sortedByDescending { it.value }
                } else {
                    report.perSlot.mapNotNull { t ->
                        val s = boardSlots.firstOrNull { it.id == t.slotId } ?: return@mapNotNull null
                        BarItem(slotName(s, ""), t.taps.toDouble(), Color(ColorMath.primaryColor(s.states.last().fill)))
                    }
                }
                ChartCard(stringResource(if (boardId == null) R.string.chart_per_board else R.string.chart_per_slot), null) {
                    if (items.isEmpty()) Empty() else HorizontalBars(items.take(20))
                }
            }
        }
    }
}

@Composable
private fun SlotFilter(board: BoardWithSlots, slot: SlotEntity?, onPick: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(slot?.let { slotName(it, "") } ?: stringResource(R.string.all_slots)) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.all_slots)) }, onClick = {
                open = false
                onPick(null)
            })
            board.visibleSlots.filter { it.enabled }.forEach { s ->
                DropdownMenuItem(text = { Text(slotName(s, "")) }, onClick = {
                    open = false
                    onPick(s.id)
                })
            }
        }
    }
}

@Composable
private fun TapsChart(report: StatsReport) {
    val daily = report.dailyTaps
    if (daily.size > 90) {
        // A year of daily bars would be thinner than a pixel: group into weeks, newest week last.
        val weeks = daily.reversed().chunked(7).map { it.reversed() }.reversed()
        ColumnChart(
            values = weeks.map { w -> w.sumOf { it.value } },
            hint = stringResource(R.string.tap_bar_hint),
            axisLabel = { i -> if (i % 13 == 0) shortDate(weeks[i].first().epochDay) else null },
            describe = { i ->
                val w = weeks[i]
                "${shortDate(w.first().epochDay)} – ${shortDate(w.last().epochDay)}: " + tapsText(w.sumOf { it.value }.toInt())
            },
        )
    } else {
        ColumnChart(
            values = daily.map { it.value },
            hint = stringResource(R.string.tap_bar_hint),
            axisLabel = { i -> sparseDayLabel(daily, i) },
            describe = { i -> "${longDate(daily[i].epochDay)}: " + tapsText(daily[i].value.toInt()) },
        )
    }
}

/** Label the first and last day plus a few evenly spaced ones in between. */
private fun sparseDayLabel(days: List<DayValue>, i: Int): String? {
    val n = days.size
    val every = when {
        n <= 7 -> 1
        n <= 31 -> 7
        else -> 30
    }
    return if (i == n - 1 || (n - 1 - i) % every == 0) shortDate(days[i].epochDay) else null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tiles(report: StatsReport) {
    val s = report.summary
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(stringResource(R.string.tile_total), "%,d".format(s.totalTaps))
        StatTile(stringResource(R.string.tile_today), "%,d".format(s.todayTaps))
        StatTile(stringResource(R.string.tile_daily_avg), "%.1f".format(s.dailyAverage))
        StatTile(stringResource(R.string.tile_active_days), "%,d".format(s.activeDays))
        StatTile(stringResource(R.string.tile_streak), pluralStringResource(R.plurals.days, s.currentStreak, s.currentStreak))
        StatTile(stringResource(R.string.tile_best_streak), pluralStringResource(R.plurals.days, s.bestStreak, s.bestStreak))
        report.completionRate?.let { StatTile(stringResource(R.string.tile_done_rate), "%.0f %%".format(it * 100)) }
        report.valueSummary?.let { v ->
            val unit = report.slot?.valueUnit.orEmpty()
            StatTile(stringResource(R.string.tile_last), SlotLogic.formatValue(v.last, unit))
            StatTile(stringResource(R.string.tile_average), SlotLogic.formatValue(v.average, unit))
            StatTile(stringResource(R.string.tile_min), SlotLogic.formatValue(v.min, unit))
            StatTile(stringResource(R.string.tile_max), SlotLogic.formatValue(v.max, unit))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.widthIn(min = 104.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ChartCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.padding(top = 8.dp)) { content() }
        }
    }
}

@Composable
private fun Empty() {
    Text(stringResource(R.string.no_data), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The same numbers as the charts, as rows: newest day first. */
@Composable
private fun DailyTable(report: StatsReport) {
    val slot = report.slot
    Column(Modifier.padding(top = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            HeaderCell(stringResource(R.string.col_date), Modifier.weight(1.4f), TextAlign.Start)
            HeaderCell(stringResource(R.string.col_taps), Modifier.weight(1f))
            when (slot?.type) {
                SlotType.COUNTER -> HeaderCell(stringResource(R.string.col_counted), Modifier.weight(1f))
                SlotType.VALUE -> HeaderCell(stringResource(R.string.col_values), Modifier.weight(1.4f))
                SlotType.STATES -> HeaderCell(stringResource(R.string.col_done), Modifier.weight(1f))
                null -> Unit
            }
        }
        HorizontalDivider()
        val valuesByDay = report.values.groupBy { Days.of(it.timestamp) }
        report.dailyTaps.indices.reversed().forEach { i ->
            val day = report.dailyTaps[i]
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(longDate(day.epochDay), Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
                NumberCell(formatAxis(day.value), Modifier.weight(1f))
                when (slot?.type) {
                    SlotType.COUNTER -> NumberCell(formatAxis(report.counterDaily[i].value), Modifier.weight(1f))
                    SlotType.VALUE -> NumberCell(
                        valuesByDay[day.epochDay].orEmpty().joinToString(", ") { SlotLogic.formatValue(it.value) },
                        Modifier.weight(1.4f),
                    )
                    SlotType.STATES -> NumberCell(if ((report.completion.getOrNull(i)?.value ?: 0.0) > 0) "✓" else "–", Modifier.weight(1f))
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier, align: TextAlign = TextAlign.End) {
    Text(text, modifier, style = MaterialTheme.typography.labelLarge, textAlign = align, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NumberCell(text: String, modifier: Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
}

@Composable
private fun rangeLabel(days: Int): String = when (days) {
    365 -> stringResource(R.string.range_year)
    else -> stringResource(R.string.range_days, days)
}

@Composable
private fun tapsText(n: Int): String = pluralStringResource(R.plurals.taps, n, n)

private fun shortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun longDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()))

private fun dateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
