package io.github.ekats.dailytaps

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.ekats.dailytaps.ui.screens.BoardScreen
import io.github.ekats.dailytaps.ui.screens.BoardSettingsScreen
import io.github.ekats.dailytaps.ui.screens.HomeScreen
import io.github.ekats.dailytaps.ui.screens.SettingsScreen
import io.github.ekats.dailytaps.ui.screens.SlotEditorScreen
import io.github.ekats.dailytaps.ui.screens.StatsScreen
import io.github.ekats.dailytaps.ui.theme.DailyTapsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
data class BoardRoute(val id: Long)

@Serializable
data class BoardSettingsRoute(val id: Long)

@Serializable
data class SlotRoute(val id: Long)

/** [boardId] of -1 shows every board together. */
@Serializable
data class StatsRoute(val boardId: Long = -1)

@Serializable
object SettingsRoute

class MainActivity : ComponentActivity() {

    /** Board to open from a widget title tap; consumed once navigated. */
    private val pendingBoard = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            DailyTapsTheme {
                val nav = rememberNavController()
                val pending by pendingBoard.collectAsStateWithLifecycle()
                LaunchedEffect(pending) {
                    pending?.let {
                        nav.navigate(BoardRoute(it)) { popUpTo(HomeRoute) }
                        pendingBoard.value = null
                    }
                }
                AppNavHost(nav)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "dailytaps" && data.host == "board") {
            data.lastPathSegment?.toLongOrNull()?.let { pendingBoard.value = it }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(nav, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenBoard = { nav.navigate(BoardRoute(it)) },
                onOpenStats = { nav.navigate(StatsRoute()) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
                onEditBoard = { nav.navigate(BoardSettingsRoute(it)) },
            )
        }
        composable<BoardRoute> { entry ->
            val id = entry.toRoute<BoardRoute>().id
            BoardScreen(
                boardId = id,
                onBack = { nav.popBackStack() },
                onEditBoard = { nav.navigate(BoardSettingsRoute(id)) },
                onEditSlot = { nav.navigate(SlotRoute(it)) },
                onOpenStats = { nav.navigate(StatsRoute(id)) },
            )
        }
        composable<BoardSettingsRoute> { entry ->
            BoardSettingsScreen(
                boardId = entry.toRoute<BoardSettingsRoute>().id,
                onBack = { nav.popBackStack() },
                onEditSlot = { nav.navigate(SlotRoute(it)) },
            )
        }
        composable<SlotRoute> { entry ->
            SlotEditorScreen(slotId = entry.toRoute<SlotRoute>().id, onBack = { nav.popBackStack() })
        }
        composable<StatsRoute> { entry ->
            StatsScreen(boardId = entry.toRoute<StatsRoute>().boardId.takeIf { it >= 0 }, onBack = { nav.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
