package com.densmac.compass.domain

import kotlin.math.abs
import kotlin.math.min

/** Wraps any angle into [0, 360). */
fun normalize360(deg: Float): Float {
    val r = deg % 360f
    val wrapped = if (r < 0f) r + 360f else r
    return if (wrapped >= 360f) 0f else wrapped
}

/** Signed shortest rotation from [from] to [to], in (-180, 180]. */
fun shortestDelta(from: Float, to: Float): Float {
    val d = normalize360(to - from)
    return if (d > 180f) d - 360f else d
}

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** 1 when pointing at north, 0 once clearly away: drives the pill, glow and lubber together. */
fun northProgress(headingDeg: Float): Float =
    1f - smoothstep(0.5f, 2.5f, abs(shortestDelta(headingDeg, 0f)))

/** 8-point compass name, as shown in the readout. */
fun cardinalOf(deg: Float): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return names[(normalize360(deg + 22.5f) / 45f).toInt() % 8]
}

/** Closest distance to north; handy for tests and latches. */
fun distanceToNorth(deg: Float): Float = min(normalize360(deg), 360f - normalize360(deg))
