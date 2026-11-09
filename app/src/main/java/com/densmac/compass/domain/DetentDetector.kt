package com.densmac.compass.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/** Haptic events, lowest priority first. */
enum class Detent { MINOR, MID, MAJOR, CARDINAL, NORTH }

/**
 * Turns the final heading into graduation "clicks". Works on a continuously unwrapped heading, so a
 * crossing is always real physical motion. Pure and clock-free: time comes from sensor timestamps.
 */
class DetentDetector(
    private val minGapNs: Long = SENSOR_MIN_GAP_NS,
    private val jumpDeg: Double = SENSOR_JUMP_DEG,
) {
    private var heading = Double.NaN
    private var lastNs = 0L
    private var speed = 0.0
    private var lastFired: Int? = null
    private var rearmed = true
    private var lastEventNs = Long.MIN_VALUE
    private var suppressUntilNs = Long.MIN_VALUE

    /** Silences events for a moment, e.g. while a reference change swings the dial. */
    fun suppress(timestampNs: Long) {
        suppressUntilNs = max(suppressUntilNs, timestampNs + SUPPRESS_NS)
    }

    fun update(wrappedDeg: Float, timestampNs: Long): Detent? {
        val prev = heading
        if (prev.isNaN()) {
            heading = wrappedDeg.toDouble()
            lastNs = timestampNs
            return null
        }
        // Nearest 360k image of the sample: unwrapped without accumulating error.
        val next = wrappedDeg + 360.0 * round((prev - wrappedDeg) / 360.0)
        val delta = next - prev
        val dt = (timestampNs - lastNs) / 1e9
        if (dt > 0) {
            speed += (abs(delta) / dt - speed) * min(1.0, dt / SPEED_TAU_S)
            lastNs = timestampNs
        }
        heading = next
        if (abs(delta) > jumpDeg) suppress(timestampNs)
        lastFired?.let { if (abs(next - it * STEP) >= REARM_DEG) rearmed = true }
        if (delta == 0.0 || timestampNs < suppressUntilNs) return null

        val lo: Int
        val hi: Int
        if (delta > 0) {
            lo = floor(prev / STEP).toInt() + 1
            hi = floor(next / STEP).toInt()
        } else {
            lo = ceil(next / STEP).toInt()
            hi = ceil(prev / STEP).toInt() - 1
        }
        var bestIndex: Int? = null
        var best: Detent? = null
        for (k in lo..hi) {
            if (k == lastFired && !rearmed) continue
            val tier = tierOf(k)
            if (!allowed(tier)) continue
            if (best == null || tier > best) { best = tier; bestIndex = k }
        }
        if (best == null) return null
        if (lastEventNs != Long.MIN_VALUE && timestampNs - lastEventNs < minGapNs) return null
        lastFired = bestIndex
        rearmed = false
        lastEventNs = timestampNs
        return best
    }

    private fun allowed(tier: Detent): Boolean = when (tier) {
        Detent.MINOR -> speed < MINOR_MAX_SPEED
        Detent.MID -> speed < MID_MAX_SPEED
        else -> true
    }

    private fun tierOf(index: Int): Detent {
        val deg = (index * STEP.toInt()).mod(360)
        return when {
            deg == 0 -> Detent.NORTH
            deg % 90 == 0 -> Detent.CARDINAL
            deg % 30 == 0 -> Detent.MAJOR
            deg % 15 == 0 -> Detent.MID
            else -> Detent.MINOR
        }
    }

    companion object {
        private const val SENSOR_MIN_GAP_NS = 40_000_000L
        private const val SENSOR_JUMP_DEG = 20.0

        /**
         * For the flung wheel: its motion is real however large a frame's step, and at top speed
         * 30° marks arrive about 33 ms apart on frames 8–16 ms apart, so none may be dropped.
         */
        fun forWheel() = DetentDetector(minGapNs = 10_000_000L, jumpDeg = Double.POSITIVE_INFINITY)

        private const val STEP = 3.0
        private const val REARM_DEG = 0.6
        private const val SPEED_TAU_S = 0.12
        private const val MINOR_MAX_SPEED = 60.0
        private const val MID_MAX_SPEED = 180.0
        private const val SUPPRESS_NS = 400_000_000L
    }
}
