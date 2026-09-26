package io.github.ekats.dailytaps.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1C6B45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA7F2C2),
    onPrimaryContainer = Color(0xFF002111),
    secondary = Color(0xFF4F6354),
    tertiary = Color(0xFF3B6470),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BD5A7),
    onPrimary = Color(0xFF003920),
    primaryContainer = Color(0xFF005231),
    onPrimaryContainer = Color(0xFFA7F2C2),
    secondary = Color(0xFFB6CCBA),
    tertiary = Color(0xFFA3CDDB),
)

@Composable
fun DailyTapsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/**
 * Chart colors. Heatmap cells use one blue ramp (light to dark = few to many taps), with its own
 * steps for dark mode rather than an inverted copy.
 */
object ChartColors {
    val heatLight = listOf(Color(0xFFCDE2FB), Color(0xFF86B6EF), Color(0xFF3987E5), Color(0xFF1C5CAB), Color(0xFF0D366B))
    val heatDark = listOf(Color(0xFF184F95), Color(0xFF2A6FC2), Color(0xFF3987E5), Color(0xFF86B6EF), Color(0xFFCDE2FB))

    @Composable
    fun heat(): List<Color> = if (isSystemInDarkTheme()) heatDark else heatLight
}
