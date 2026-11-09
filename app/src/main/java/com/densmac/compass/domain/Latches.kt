package com.densmac.compass.domain

import kotlin.math.abs

/** North state with hysteresis, so the readout can't flicker at the edge. */
class NorthLatch {
    var isNorth = false
        private set

    fun update(headingDeg: Float): Boolean {
        val distance = distanceToNorth(headingDeg)
        isNorth = if (isNorth) distance <= EXIT else distance < ENTER
        return isNorth
    }

    private companion object {
        const val ENTER = 1.0f
        const val EXIT = 1.5f
    }
}

/** Debounces sensor-quality problems: a warning needs 1.5 s of bad data, and clears at once. */
class SensorHealth {
    private var badSinceNs = -1L
    private var warned = SensorWarning.NONE

    fun update(accuracy: Int, fieldMicroTesla: Float, timestampNs: Long): SensorWarning {
        val interference = fieldMicroTesla > 0f && (fieldMicroTesla < MIN_FIELD || fieldMicroTesla > MAX_FIELD)
        val problem = when {
            interference -> SensorWarning.INTERFERENCE
            accuracy <= ACCURACY_LOW -> SensorWarning.CALIBRATE
            else -> SensorWarning.NONE
        }
        if (problem == SensorWarning.NONE) {
            badSinceNs = -1L
            warned = SensorWarning.NONE
        } else {
            if (badSinceNs < 0) badSinceNs = timestampNs
            if (timestampNs - badSinceNs >= DEBOUNCE_NS) warned = problem
        }
        return warned
    }

    private companion object {
        const val MIN_FIELD = 25f
        const val MAX_FIELD = 65f
        const val ACCURACY_LOW = 1
        const val DEBOUNCE_NS = 1_500_000_000L
    }
}

data class LevelReading(
    val upright: Boolean = false,
    /** Flat: tilt toward the top edge. Upright: unused. */
    val pitchDeg: Float = 0f,
    /** Flat: tilt toward the right edge. Upright: deviation from the nearest level edge. */
    val rollDeg: Float = 0f,
    val isLevel: Boolean = false,
)

/** Pure gravity→level maths, with hysteresis on both the flat/upright mode and the level lock. */
class LevelCalculator {
    private var upright = false
    private var level = false

    fun update(x: Float, y: Float, z: Float): LevelReading {
        upright = if (upright) abs(z) < 0.6f else abs(z) < 0.45f
        val pitch: Float
        val roll: Float
        if (upright) {
            pitch = 0f
            val angle = deg(kotlin.math.atan2(x, y))
            val nearestQuarter = kotlin.math.round(angle / 90f) * 90f
            roll = shortestDelta(nearestQuarter, angle)
        } else {
            val depth = abs(z)
            pitch = deg(kotlin.math.atan2(y, depth))
            roll = deg(kotlin.math.atan2(x, depth))
        }
        val error = maxOf(abs(pitch), abs(roll))
        level = if (level) error <= 0.8f else error < 0.5f
        return LevelReading(upright, pitch, roll, level)
    }

    private fun deg(rad: Float) = Math.toDegrees(rad.toDouble()).toFloat()
}
