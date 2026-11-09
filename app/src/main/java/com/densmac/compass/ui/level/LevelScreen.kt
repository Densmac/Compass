package com.densmac.compass.ui.level

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.densmac.compass.R
import com.densmac.compass.domain.CriticalSpring
import com.densmac.compass.ui.theme.Inter
import com.densmac.compass.ui.theme.Palette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.flow.first

private class LevelMotion {
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set
    private val sx = CriticalSpring(16f)
    private val sy = CriticalSpring(16f)

    suspend fun run(target: State<LevelFrame>) {
        while (true) {
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                val f = target.value
                sx.set(x - f.x); sx.step(dt); x = f.x + sx.offset
                sy.set(y - f.y); sy.step(dt); y = f.y + sy.offset
                if (abs(sx.offset) < 0.0008f && abs(sy.offset) < 0.0008f) {
                    x = f.x; y = f.y; sx.reset(); sy.reset()
                    break
                }
            }
            snapshotFlow { target.value }.first { abs(it.x - x) > 0.003f || abs(it.y - y) > 0.003f }
        }
    }
}

@Composable
fun LevelScreen(viewModel: LevelViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val frames = viewModel.frames.collectAsStateWithLifecycle()
    val motion = remember { LevelMotion() }
    LifecycleResumeEffect(Unit) {
        viewModel.setAudible(true)
        onPauseOrDispose { viewModel.setAudible(false) }
    }
    LaunchedEffect(motion, frames) { motion.run(frames) }

    val color by animateColorAsState(if (state.isLevel) Palette.Accent else Palette.Text, tween(150), label = "level")

    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val w = maxWidth
        val h = maxHeight
        val side = min(w, 480.dp)
        val dial = min(min(w * 0.825f, h * 0.48f), 520.dp)
        val density = LocalDensity.current
        val big = with(density) { (side * 0.0955f).toSp() }
        val body = with(density) { (side * 0.048f).toSp() }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            Spacer(Modifier.weight(0.45f))
            val shown = if (state.upright) abs(state.rollTenths) else maxOf(abs(state.pitchTenths), abs(state.rollTenths))
            Text(
                String.format(Locale.getDefault(), "%.1f°", shown / 10f),
                color = color,
                fontFamily = Inter,
                fontWeight = FontWeight.Bold,
                fontSize = big,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.weight(1f))
            LevelDial(motion, frames, Modifier.size(dial))
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth().padding(horizontal = side * 0.11f)) {
                ValueRow(stringResource(R.string.pitch), state.pitchTenths, state.upright, body)
                HorizontalDivider(color = Palette.Divider, thickness = 0.5.dp)
                ValueRow(stringResource(R.string.roll), state.rollTenths, false, body)
            }
            Spacer(Modifier.weight(0.6f))
        }
    }
}

@Composable
private fun ValueRow(label: String, tenths: Int, hidden: Boolean, size: androidx.compose.ui.unit.TextUnit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Palette.Secondary, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = size)
        Text(
            if (hidden) "—" else String.format(Locale.getDefault(), "%.1f°", tenths / 10f),
            color = Palette.Text,
            fontFamily = Inter,
            fontWeight = FontWeight.Bold,
            fontSize = size,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
    }
}

@Composable
private fun LevelDial(motion: LevelMotion, frames: State<LevelFrame>, modifier: Modifier) {
    val p = Palette
    Spacer(
        modifier.drawWithCache {
            val r = size.minDimension / 2f
            val c = size.center
            val face = Brush.radialGradient(listOf(p.DiscFaceCenter, p.DiscFaceEdge), c, r * 0.93f)
            onDrawBehind {
                drawCircle(p.DiscRim, r, c)
                drawCircle(face, r * 0.93f, c)
                val thin = r * 0.0042f
                val guide = p.Hairline.copy(alpha = 0.26f)
                drawCircle(guide, r * 0.14f, c, style = Stroke(thin))
                drawCircle(guide.copy(alpha = 0.12f), r * 0.5f, c, style = Stroke(thin))
                drawLine(guide, Offset(c.x - r * 0.3f, c.y), Offset(c.x + r * 0.3f, c.y), thin)
                drawLine(guide, Offset(c.x, c.y - r * 0.3f), Offset(c.x, c.y + r * 0.3f), thin)

                val frame = frames.value
                val x = motion.x
                val y = motion.y
                val error = sqrt(x * x + y * y)
                val accent = lerp(p.Accent, p.Text, ((error - 0.01f) / 0.03f).coerceIn(0f, 1f))
                if (frame.reading.upright) {
                    val angle = Math.toDegrees(atan2(x, y).toDouble()).toFloat()
                    val quarter = kotlin.math.round(angle / 90f) * 90f
                    rotate(-(angle - quarter), c) {
                        drawLine(accent, Offset(c.x - r * 0.72f, c.y), Offset(c.x + r * 0.72f, c.y), r * 0.012f)
                    }
                } else {
                    var dx = x * r * 2f
                    var dy = -y * r * 2f
                    val limit = r * 0.72f
                    val d = sqrt(dx * dx + dy * dy)
                    if (d > limit) { dx *= limit / d; dy *= limit / d }
                    val bubble = Offset(c.x + dx, c.y + dy)
                    drawCircle(accent.copy(alpha = 0.16f), r * 0.14f, bubble)
                    drawCircle(accent, r * 0.14f, bubble, style = Stroke(r * 0.008f))
                }
            }
        },
    )
}
