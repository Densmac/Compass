package com.densmac.compass.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.densmac.compass.R
import com.densmac.compass.domain.ThemeMode

@Immutable
data class CompassColors(
    val Background: Color,
    val DiscRim: Color,
    val Groove: Color,
    val DiscFaceCenter: Color,
    val DiscFaceEdge: Color,
    val Tick: Color,
    val MajorTick: Color,
    val Text: Color,
    val Secondary: Color,
    val Accent: Color,
    val Divider: Color,
    val Bar: Color,
    /** Fine lines drawn over the disc. */
    val Hairline: Color,
)

private val DarkColors = CompassColors(
    Background = Color(0xFF000000),
    DiscRim = Color(0xFF1F2022),
    Groove = Color(0xFF0E0F10),
    DiscFaceCenter = Color(0xFF1C1D1F),
    DiscFaceEdge = Color(0xFF17181A),
    Tick = Color(0xFF55575B),
    MajorTick = Color(0xFFC9CACC),
    Text = Color(0xFFF2F2F2),
    Secondary = Color(0xFF8A8B8E),
    Accent = Color(0xFFFF5A1F),
    Divider = Color(0xFF2A2B2D),
    Bar = Color(0xFF1C1C1E),
    Hairline = Color.White,
)

private val LightColors = CompassColors(
    Background = Color(0xFFF4F4F6),
    DiscRim = Color(0xFFE6E7EA),
    Groove = Color(0xFFCDCFD4),
    DiscFaceCenter = Color(0xFFFDFDFE),
    DiscFaceEdge = Color(0xFFF1F2F4),
    Tick = Color(0xFFB3B5BA),
    MajorTick = Color(0xFF4A4C50),
    Text = Color(0xFF131416),
    Secondary = Color(0xFF75777C),
    Accent = Color(0xFFF04A10),
    Divider = Color(0xFFDADBDF),
    Bar = Color(0xFFFFFFFF),
    Hairline = Color.Black,
)

private val LocalColors = staticCompositionLocalOf { DarkColors }

/** The active colours. Draw code must capture this in composition; it can't read a local. */
val Palette: CompassColors
    @Composable @ReadOnlyComposable get() = LocalColors.current

// Static instances of Inter (opsz 32): Android ignores per-Font variations on one resource file.
val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
)

@Composable
fun CompassTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (mode == ThemeMode.LIGHT) LightColors else DarkColors
    val base = if (mode == ThemeMode.LIGHT) lightColorScheme() else darkColorScheme()
    CompositionLocalProvider(LocalColors provides colors) {
        MaterialTheme(
            colorScheme = base.copy(
                primary = colors.Accent,
                background = colors.Background,
                surface = colors.Bar,
                onSurface = colors.Text,
            ),
            content = content,
        )
    }
}
