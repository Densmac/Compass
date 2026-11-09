package com.densmac.compass.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * One-Euro adaptive low-pass on the unit circle: heavy smoothing at rest, little lag while turning,
 * and no discontinuity at 359→0.
 */
class OneEuroAngleFilter(
    private val minCutoffHz: Float = 1.2f,
    private val beta: Float = 0.04f,
    private val dCutoffHz: Float = 1f,
) {
    private var s = 0f
    private var c = 0f
    private var lastRaw = 0f
    private var speed = 0f
    private var lastNs = 0L
    private var ready = false

    fun update(deg: Float, timestampNs: Long): Float {
        val rad = Math.toRadians(deg.toDouble())
        val sn = sin(rad).toFloat()
        val cs = cos(rad).toFloat()
        if (!ready) {
            s = sn; c = cs; lastRaw = deg; lastNs = timestampNs; ready = true
            return normalize360(deg)
        }
        val dt = ((timestampNs - lastNs) / 1e9f).coerceIn(1e-3f, 0.5f)
        lastNs = timestampNs
        val rawSpeed = shortestDelta(lastRaw, deg) / dt
        lastRaw = deg
        speed += alpha(dCutoffHz, dt) * (rawSpeed - speed)
        val a = alpha(minCutoffHz + beta * abs(speed), dt)
        s += a * (sn - s)
        c += a * (cs - c)
        return normalize360(Math.toDegrees(atan2(s.toDouble(), c.toDouble())).toFloat())
    }

    private fun alpha(cutoffHz: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoffHz)
        return 1f / (1f + tau / dt)
    }
}
