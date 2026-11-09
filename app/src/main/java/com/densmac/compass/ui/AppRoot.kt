package com.densmac.compass.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.densmac.compass.R
import com.densmac.compass.ui.compass.CompassScreen
import com.densmac.compass.ui.level.LevelScreen
import com.densmac.compass.ui.settings.SettingsSheet
import com.densmac.compass.ui.settings.SettingsViewModel
import com.densmac.compass.ui.theme.Inter
import com.densmac.compass.ui.theme.Palette

@Composable
fun AppRoot(settingsVm: SettingsViewModel) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val view = LocalView.current
    DisposableEffect(view, settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize().background(Palette.Background)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Leaving composition stops that tab's collectors, and so its sensors.
            Crossfade(targetState = tab, label = "tab") { current ->
                if (current == 0) CompassScreen() else LevelScreen()
            }
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 16.dp, end = 12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_gear),
                    contentDescription = stringResource(R.string.settings),
                    tint = Palette.Text,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        BottomBar(tab) { tab = it }
    }

    if (showSettings) {
        SettingsSheet(settings, settingsVm::update) { showSettings = false }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Bar).navigationBarsPadding().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Tab(stringResource(R.string.tab_compass), selected == 0, { onSelect(0) }) { CompassGlyph(it) }
        Tab(stringResource(R.string.tab_level), selected == 1, { onSelect(1) }) { LevelGlyph(it) }
    }
}

@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit, glyph: @Composable (Color) -> Unit) {
    val color = if (selected) Palette.Text else Palette.Secondary
    Column(
        Modifier
            .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 36.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        glyph(color)
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CompassGlyph(color: Color) {
    Canvas(Modifier.size(28.dp).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(color, r, c)
        val tip = r * 0.56f
        val side = r * 0.16f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x + tip, c.y - tip)
            lineTo(c.x + side, c.y + side)
            lineTo(c.x - tip, c.y + tip)
            lineTo(c.x - side, c.y - side)
            close()
        }
        drawPath(path, Color.Black, blendMode = BlendMode.Clear)
    }
}

@Composable
private fun LevelGlyph(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val r = size.minDimension / 2f
        val stroke = 1.6.dp.toPx()
        drawCircle(color, r - stroke / 2f, center, style = Stroke(stroke))
        drawLine(color, Offset(0f, center.y), Offset(size.width, center.y), stroke, StrokeCap.Round)
    }
}
