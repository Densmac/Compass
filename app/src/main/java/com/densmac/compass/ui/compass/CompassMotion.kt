package com.densmac.compass.ui.compass

import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.densmac.compass.domain.CriticalSpring
import com.densmac.compass.domain.DialSpin
import com.densmac.compass.domain.NorthLatch
import com.densmac.compass.domain.normalize360
import com.densmac.compass.domain.shortestDelta
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

/**
 * Turns the sensor stream into per-frame values. The loop suspends whenever the dial has settled,
 * so a phone resting on a table draws nothing. The state here is read only in draw lambdas, which
 * invalidates drawing and never recomposition.
 */
/** What the wheel tells the outside world while the user is spinning it. */
interface DialSpinListener {
    fun onSpinActive(active: Boolean)
    fun onDialMoved(headingDeg: Float, frameNs: Long)
}

/**
 * Turns the sensor stream and the user's spins into per-frame values. The loop suspends whenever
 * the dial has settled, so a phone resting on a table draws nothing. [heading] and [reveal] are read
 * only in draw lambdas, which invalidates drawing and never recomposition.
 */
@Stable
class CompassMotion {
    var heading by mutableFloatStateOf(0f)
        private set
    var reveal by mutableFloatStateOf(0f)
        private set

    /** What the wheel shows at 12 o'clock, null until the first reading. Changes per whole degree. */
    var displayDegree by mutableStateOf<Int?>(null)
        private set
    var displayNorth by mutableStateOf(false)
        private set

    private var spinning by mutableStateOf(false)
    private var ready = false
    private val latch = NorthLatch()
    private var base = 0f
    private val spin = DialSpin()
    private val headingSpring = CriticalSpring(HEADING_OMEGA)
    private val revealSpring = CriticalSpring(REVEAL_OMEGA)
    private var listener: DialSpinListener? = null

    fun grab() {
        if (!ready) return
        spin.grab()
        if (!spinning) {
            spinning = true
            listener?.onSpinActive(true)
        }
    }

    /** @param fingerDeg clockwise finger travel; the dial's heading runs the other way. */
    fun drag(fingerDeg: Float) = spin.drag(-fingerDeg)

    fun release(fingerDegPerS: Float) = spin.release(-fingerDegPerS)

    suspend fun run(target: State<CompassFrame>, spinListener: DialSpinListener) {
        listener = spinListener
        val first = snapshotFlow { target.value }.first { it.hasHeading }
        base = first.heading
        heading = base
        publishReadout()
        if (!ready) revealSpring.set(-1f)
        ready = true
        try {
            while (true) {
                var last = withFrameNanos { it }
                while (true) {
                    val now = withFrameNanos { it }
                    val dt = ((now - last) / 1e9f).coerceIn(0f, MAX_DT)
                    last = now
                    if (step(target.value, dt, now)) break
                }
                snapshotFlow { spinning to target.value }.first { (active, frame) -> active || hasMoved(frame) }
            }
        } finally {
            // The screen went away: a held or coasting wheel must not resume mid-air.
            spin.cancel()
            if (spinning) {
                spinning = false
                spinListenerOff()
            }
        }
    }

    private fun spinListenerOff() {
        listener?.onSpinActive(false)
    }

    /** @return true once everything has settled. */
    private fun step(frame: CompassFrame, dt: Float, frameNs: Long): Boolean {
        // Offsets are re-based on the moving target each frame; the shortest way, never the long way.
        headingSpring.set(shortestDelta(frame.heading, base))
        headingSpring.step(dt)
        base = normalize360(frame.heading + headingSpring.offset)

        val wasSpinning = spin.isActive
        spin.step(dt)
        heading = normalize360(base + spin.offset)

        if (spin.isActive) {
            listener?.onDialMoved(heading, frameNs)
        } else if (wasSpinning || spinning) {
            spinning = false
            listener?.onSpinActive(false)
        }

        publishReadout()

        revealSpring.step(dt)
        reveal = (1f + revealSpring.offset).coerceIn(0f, 1f)

        val settled = !spin.isActive &&
            abs(headingSpring.offset) < 0.02f && abs(headingSpring.velocity) < 0.5f &&
            abs(revealSpring.offset) < 0.002f
        if (settled) {
            base = frame.heading
            heading = base
            reveal = 1f
            publishReadout()
            headingSpring.reset()
            revealSpring.reset()
        }
        return settled
    }

    /** Readout and dial share this one angle, so "0°N" appears exactly as the marker arrives. */
    private fun publishReadout() {
        val north = latch.update(heading)
        val degree = if (north) 0 else heading.roundToInt() % 360
        if (degree != displayDegree) displayDegree = degree
        if (north != displayNorth) displayNorth = north
    }

    private fun hasMoved(frame: CompassFrame): Boolean =
        abs(shortestDelta(base, frame.heading)) > WAKE_HEADING

    private companion object {
        const val HEADING_OMEGA = 22f
        const val REVEAL_OMEGA = 8f
        const val MAX_DT = 0.05f
        const val WAKE_HEADING = 0.15f
    }
}
