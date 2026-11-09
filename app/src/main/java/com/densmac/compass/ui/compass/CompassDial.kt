package com.densmac.compass.ui.compass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.rememberTextMeasurer
import com.densmac.compass.domain.northProgress
import com.densmac.compass.domain.shortestDelta
import com.densmac.compass.domain.smoothstep
import com.densmac.compass.ui.theme.Inter
import com.densmac.compass.ui.theme.CompassColors
import com.densmac.compass.ui.theme.Palette
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val TICK_COUNT = 120
private const val TICK_STEP = 360f / TICK_COUNT
private const val HEAT_RANGE = 42f

// Radii as fractions of the disc radius, measured from the reference screens.
private const val RIM_INNER = 0.93f
private const val ARC_R = 0.977f
private const val ARC_WIDTH = 0.024f
private const val DOT_R = 0.968f
private const val DOT_SIZE = 0.0125f
private const val TICK_IN = 0.518f
private const val TICK_OUT = 0.648f
private const val MAJOR_OUT = 0.69f
private const val ZERO_OUT = 0.66f
private const val LABEL_R = 0.81f
private const val LETTER_R = 0.36f
private const val MARKER_R = 0.722f

private val SIN = FloatArray(TICK_COUNT) { sin(Math.toRadians(it * TICK_STEP.toDouble())).toFloat() }
private val COS = FloatArray(TICK_COUNT) { cos(Math.toRadians(it * TICK_STEP.toDouble())).toFloat() }

private fun isMajor(index: Int) = index % 10 == 0

/** The 0° tick stops short, leaving room for the north marker. */
private fun tickOuter(index: Int) = when {
    index == 0 -> ZERO_OUT
    isMajor(index) -> MAJOR_OUT
    else -> TICK_OUT
}

@Composable
fun CompassDial(
    frames: State<CompassFrame>,
    motion: CompassMotion,
    listener: DialSpinListener,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(motion, frames, listener, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { motion.run(frames, listener) }
    }
    // Leaving mid-spin must not leave the sensor ticks silenced.
    DisposableEffect(listener) { onDispose { listener.onSpinActive(false) } }
    Box(modifier.pointerInput(motion) { spinGestures(motion) }) {
        DiscBody()
        DiscMoving(motion)
    }
}

private const val VELOCITY_WINDOW_MS = 100L

private suspend fun PointerInputScope.spinGestures(motion: CompassMotion) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width / 2f
        if ((down.position - centre).getDistance() > radius) return@awaitEachGesture

        motion.grab()
        // Cumulative finger angle over time; the last 100 ms decide the fling's strength.
        val samples = ArrayDeque<Pair<Long, Float>>()
        var travelled = 0f
        var previous = fingerAngle(down.position, centre)
        var wasOutside = (down.position - centre).getDistance() > radius * 0.12f
        samples.addLast(down.uptimeMillis to 0f)
        var lastTime = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                lastTime = change.uptimeMillis
                break
            }
            lastTime = change.uptimeMillis
            val angle = fingerAngle(change.position, centre)
            // Near the centre, tiny movements swing the angle wildly.
            val outside = (change.position - centre).getDistance() > radius * 0.12f
            if (outside && wasOutside) {
                val delta = shortestDelta(previous, angle)
                travelled += delta
                motion.drag(delta)
            }
            wasOutside = outside
            previous = angle
            change.consume()
            samples.addLast(change.uptimeMillis to travelled)
            while (samples.size > 2 && change.uptimeMillis - samples.first().first > VELOCITY_WINDOW_MS) {
                samples.removeFirst()
            }
        }
        val recent = samples.filter { lastTime - it.first <= VELOCITY_WINDOW_MS }
        val span = if (recent.size >= 2) (recent.last().first - recent.first().first) / 1000f else 0f
        val velocity = if (span > 0f) (recent.last().second - recent.first().second) / span else 0f
        motion.release(velocity)
    }
}

private fun fingerAngle(position: Offset, centre: Offset): Float =
    Math.toDegrees(atan2((position.x - centre.x).toDouble(), (centre.y - position.y).toDouble())).toFloat()

/** Never reads motion state, so it is recorded once per size. */
@Composable
private fun DiscBody() {
    val p = Palette
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val r = size.minDimension / 2f
            val face = Brush.radialGradient(
                listOf(p.DiscFaceCenter, p.DiscFaceEdge),
                center = size.center,
                radius = r * RIM_INNER,
            )
            val sheen = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.05f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                start = Offset(size.center.x - r, size.center.y - r),
                end = Offset(size.center.x + r, size.center.y + r),
            )
            onDrawBehind {
                drawCircle(p.DiscRim, r, center)
                drawCircle(sheen, r, center)
                drawCircle(face, r * RIM_INNER, center)
                drawCircle(p.Hairline.copy(alpha = 0.07f), r * (RIM_INNER + 0.011f), center, style = Stroke(r * 0.003f))
                drawCircle(p.Groove, r * RIM_INNER, center, style = Stroke(r * 0.008f))
                drawCircle(p.Hairline.copy(alpha = 0.05f), r * (RIM_INNER - 0.011f), center, style = Stroke(r * 0.003f))
            }
        },
    )
}

/** Everything that moves. All [motion] reads are inside onDrawBehind: draw phase only. */
@Composable
private fun DiscMoving(motion: CompassMotion) {
    val p = Palette
    val measurer = rememberTextMeasurer(cacheSize = 0)
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val r = size.minDimension / 2f
            val c = size.center
            val labelStyle = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                color = p.Text,
                fontSize = (r * 0.082f).toSp(),
            )
            val letterStyle = labelStyle.copy(fontSize = (r * 0.14f).toSp(), fontWeight = FontWeight.Normal)
            val labels = List(12) { i -> measurer.measure((i * 30).toString(), labelStyle) }
            val letters = listOf(
                measurer.measure("N", letterStyle.copy(color = p.Accent)),
                measurer.measure("E", letterStyle),
                measurer.measure("S", letterStyle),
                measurer.measure("W", letterStyle),
            )
            val triangle = Path().apply {
                moveTo(0f, -r * 0.034f)
                lineTo(r * 0.04f, r * 0.03f)
                lineTo(-r * 0.04f, r * 0.03f)
                close()
            }
            val markerStroke = Stroke(r * 0.016f, join = StrokeJoin.Round)
            val markerGlow = Brush.radialGradient(
                listOf(p.Accent.copy(alpha = 0.16f), Color.Transparent),
                center = Offset.Zero,
                radius = r * 0.1f,
            )
            val northGlow = Brush.radialGradient(
                listOf(p.Accent.copy(alpha = 0.42f), p.Accent.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(c.x, c.y - r),
                radius = r * 0.85f,
            )
            val lubberGlow = Brush.radialGradient(
                listOf(p.Accent.copy(alpha = 0.22f), Color.Transparent),
                center = Offset(c.x, c.y - r * 0.64f),
                radius = r * 0.13f,
            )
            val tickWidth = r * 0.0068f
            val ticks = obtainGraphicsLayer().apply {
                record {
                    for (i in 0 until TICK_COUNT) {
                        val major = isMajor(i)
                        val inner = r * TICK_IN
                        val outer = r * tickOuter(i)
                        drawLine(
                            if (major) p.MajorTick else p.Tick,
                            Offset(c.x + inner * SIN[i], c.y - inner * COS[i]),
                            Offset(c.x + outer * SIN[i], c.y - outer * COS[i]),
                            strokeWidth = if (major) tickWidth * 1.25f else tickWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                pivotOffset = c
            }

            onDrawBehind {
                val heading = motion.heading
                val north = northProgress(heading)
                val reveal = motion.reveal

                ticks.rotationZ = -heading
                ticks.alpha = reveal
                drawLayer(ticks)
                if (reveal <= 0f) return@onDrawBehind

                if (north > 0f) drawCircle(northGlow, r, c, alpha = north * reveal)
                drawHeat(p, heading, r, c, tickWidth, reveal)
                val lubber = (1f - north) * reveal
                if (lubber > 0f) {
                    drawCircle(lubberGlow, r * 0.13f, Offset(c.x, c.y - r * 0.64f), alpha = lubber)
                    drawLine(
                        p.Accent.copy(alpha = lubber),
                        Offset(c.x, c.y - r * 0.52f),
                        Offset(c.x, c.y - r * 0.695f),
                        strokeWidth = r * 0.012f,
                        cap = StrokeCap.Round,
                    )
                }
                for (i in labels.indices) drawUpright(labels[i], i * 30f - heading, r * LABEL_R, c, reveal)
                for (i in letters.indices) drawUpright(letters[i], i * 90f - heading, r * LETTER_R, c, reveal)
                drawMarker(p, triangle, markerStroke, markerGlow, heading, r, c, reveal)
                drawRim(p, heading, north, r, c, reveal)
                drawCrosshair(p, r, c)
            }
        },
    )
}

private fun DrawScope.drawUpright(text: TextLayoutResult, angle: Float, radius: Float, c: Offset, alpha: Float) {
    val a = Math.toRadians(angle.toDouble())
    val x = c.x + radius * sin(a).toFloat()
    val y = c.y - radius * cos(a).toFloat()
    drawText(text, topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f), alpha = alpha)
}

/** Repaints the ticks near 12 o'clock warmer and longer, directly over the cached ring. */
private fun DrawScope.drawHeat(p: CompassColors, heading: Float, r: Float, c: Offset, width: Float, alpha: Float) {
    val a = Math.toRadians(heading.toDouble())
    val sinH = sin(a).toFloat()
    val cosH = cos(a).toFloat()
    val first = kotlin.math.floor((heading - HEAT_RANGE) / TICK_STEP).toInt()
    val last = kotlin.math.ceil((heading + HEAT_RANGE) / TICK_STEP).toInt()
    for (k in first..last) {
        val i = k.mod(TICK_COUNT)
        val distance = abs(shortestDelta(heading, i * TICK_STEP))
        if (distance >= HEAT_RANGE) continue
        val heat = smoothstep(0f, 1f, 1f - distance / HEAT_RANGE)
        val major = isMajor(i)
        val base = if (major) p.MajorTick else p.Tick
        val color = lerp(base, p.Accent, if (major) heat * 0.6f else heat)
        val s = SIN[i] * cosH - COS[i] * sinH
        val co = COS[i] * cosH + SIN[i] * sinH
        val inner = r * TICK_IN
        val outer = r * (tickOuter(i) + 0.012f * heat * heat)
        drawLine(
            color.copy(alpha = alpha),
            Offset(c.x + inner * s, c.y - inner * co),
            Offset(c.x + outer * s, c.y - outer * co),
            strokeWidth = (if (major) width * 1.25f else width) * (1f + 0.3f * heat),
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMarker(p: CompassColors, triangle: Path, stroke: Stroke, glow: Brush, heading: Float, r: Float, c: Offset, alpha: Float) {
    val a = Math.toRadians(-heading.toDouble())
    val px = c.x + r * MARKER_R * sin(a).toFloat()
    val py = c.y - r * MARKER_R * cos(a).toFloat()
    drawContext.canvas.save()
    drawContext.transform.translate(px, py)
    drawContext.transform.rotate(-heading, Offset.Zero)
    drawCircle(glow, r * 0.1f, Offset.Zero, alpha = alpha)
    drawPath(triangle, p.Accent, alpha = alpha)
    drawPath(triangle, p.Accent, alpha = alpha, style = stroke)
    drawContext.canvas.restore()
}

private fun DrawScope.drawRim(p: CompassColors, heading: Float, north: Float, r: Float, c: Offset, alpha: Float) {
    val arcRadius = r * ARC_R
    val topLeft = Offset(c.x - arcRadius, c.y - arcRadius)
    val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
    val stroke = Stroke(r * ARC_WIDTH, cap = StrokeCap.Round)
    val toNorth = -shortestDelta(0f, heading)
    if (north < 1f) {
        drawArc(p.Accent, -90f, toNorth, false, topLeft, arcSize, (1f - north) * alpha, stroke)
    }
    if (north > 0f) {
        drawArc(p.Accent, -91.4f, 2.8f, false, topLeft, arcSize, north * alpha, stroke)
    }
    // Rim dots mark E, S and W; north already has the arc's end.
    for (i in 1..3) {
        val a = Math.toRadians((i * 90f - heading).toDouble())
        drawCircle(
            p.Secondary.copy(alpha = alpha),
            r * DOT_SIZE,
            Offset(c.x + r * DOT_R * sin(a).toFloat(), c.y - r * DOT_R * cos(a).toFloat()),
        )
    }
}

private fun DrawScope.drawCrosshair(p: CompassColors, r: Float, c: Offset) {
    val half = r * 0.2f
    val color = p.Hairline.copy(alpha = 0.26f)
    val thin = r * 0.0042f
    drawLine(color, Offset(c.x - half, c.y), Offset(c.x + half, c.y), thin)
    drawLine(color, Offset(c.x, c.y - half), Offset(c.x, c.y + half), thin)
}
