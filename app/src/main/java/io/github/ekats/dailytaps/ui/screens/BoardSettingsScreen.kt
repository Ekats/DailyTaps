package io.github.ekats.dailytaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ekats.dailytaps.R
import io.github.ekats.dailytaps.data.BoardEntity
import io.github.ekats.dailytaps.data.MAX_GRID
import io.github.ekats.dailytaps.data.MIN_GRID
import io.github.ekats.dailytaps.data.TextScale
import io.github.ekats.dailytaps.repository
import io.github.ekats.dailytaps.ui.components.BoardGrid
import io.github.ekats.dailytaps.ui.components.ColorField
import io.github.ekats.dailytaps.ui.components.FillEditor
import io.github.ekats.dailytaps.ui.components.SectionTitle
import io.github.ekats.dailytaps.ui.components.Stepper
import io.github.ekats.dailytaps.ui.components.SwitchRow
import kotlinx.coroutines.launch

/** Board settings. Every change saves at once, so placed widgets update while you edit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSettingsScreen(boardId: Long, onBack: () -> Unit, onEditSlot: (Long) -> Unit) {
    val repo = LocalContext.current.repository
    val scope = rememberCoroutineScope()
    val data by remember(boardId) { repo.observeBoard(boardId) }.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_board)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val current = data ?: return@Scaffold
        val board = current.board
        // Local text so typing isn't disturbed by the database round trip. Every save carries it,
        // so a quick toggle right after typing can't write back the previous title.
        var title by remember(boardId) { mutableStateOf(board.title) }
        fun save(b: BoardEntity) = scope.launch { repo.updateBoard(b.copy(title = title)) }

        Column(Modifier.fillMaxSize().padding(padding)) {
            // Preview stays visible while the settings scroll underneath it.
            Box(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
            ) {
                BoardGrid(
                    current,
                    Modifier.fillMaxWidth().heightIn(max = 240.dp)
                        .aspectRatio(board.cols.toFloat() / board.rows, matchHeightConstraintsFirst = true),
                    showDisabled = true,
                    onSlotClick = { onEditSlot(it.id) },
                )
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
            ) {
                Text(
                    stringResource(R.string.tap_slot_to_edit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                SectionTitle(stringResource(R.string.section_title))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        save(board.copy(title = it))
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.board_title)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(R.string.show_title), board.showTitle) { save(board.copy(showTitle = it)) }
                if (board.showTitle) {
                    ColorField(stringResource(R.string.title_color), board.titleColor) { save(board.copy(titleColor = it)) }
                }

                SectionTitle(stringResource(R.string.section_layout))
                Stepper(stringResource(R.string.rows), board.rows, MIN_GRID..MAX_GRID) { save(board.copy(rows = it)) }
                Stepper(stringResource(R.string.columns), board.cols, MIN_GRID..MAX_GRID) { save(board.copy(cols = it)) }
                Stepper(stringResource(R.string.spacing), board.spacingDp, 0..16, format = { "$it dp" }) { save(board.copy(spacingDp = it)) }
                Stepper(stringResource(R.string.corner_radius), board.cornerRadiusDp, 0..32, format = { "$it dp" }) {
                    save(board.copy(cornerRadiusDp = it))
                }
                Text(stringResource(R.string.text_size), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val labels = listOf(R.string.small, R.string.medium, R.string.large)
                    TextScale.entries.forEachIndexed { i, scale ->
                        SegmentedButton(
                            selected = board.textScale == scale,
                            onClick = { save(board.copy(textScale = scale)) },
                            shape = SegmentedButtonDefaults.itemShape(i, TextScale.entries.size),
                        ) { Text(stringResource(labels[i])) }
                    }
                }

                SectionTitle(stringResource(R.string.section_background))
                FillEditor(stringResource(R.string.board_background), board.background) { save(board.copy(background = it)) }

                SectionTitle(stringResource(R.string.section_behaviour))
                SwitchRow(
                    stringResource(R.string.reset_daily),
                    board.resetDaily,
                    supporting = stringResource(R.string.reset_daily_help),
                ) { save(board.copy(resetDaily = it)) }
            }
        }
    }
}
