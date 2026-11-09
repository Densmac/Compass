package com.densmac.compass.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

fun interface MagneticModel {
    fun declinationDeg(lat: Double, lon: Double, altitudeM: Double, timeMs: Long): Float
}

fun interface WallClock {
    fun nowMs(): Long
}

/** Caches declination and refreshes it only when the inputs have meaningfully changed. */
class DeclinationResolver(private val model: MagneticModel, private val clock: WallClock) {
    private var cached: Float? = null
    private var lastLat = 0.0
    private var lastLon = 0.0
    private var lastAlt = 0.0
    private var computedAtMs = 0L

    fun resolve(fix: GeoFix): Float {
        val now = clock.nowMs()
        val alt = fix.wgs84AltitudeM ?: 0.0
        val current = cached
        if (current != null &&
            distanceKm(lastLat, lastLon, fix.latitude, fix.longitude) <= 1.0 &&
            abs(alt - lastAlt) <= 500.0 &&
            now - computedAtMs <= DAY_MS
        ) return current
        lastLat = fix.latitude
        lastLon = fix.longitude
        lastAlt = alt
        computedAtMs = now
        return model.declinationDeg(fix.latitude, fix.longitude, alt, now).also { cached = it }
    }

    fun isStale(fix: GeoFix): Boolean = clock.nowMs() - fix.timeMs > STALE_MS

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = Math.toRadians(lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = Math.toRadians(lat2 - lat1)
        return sqrt(x * x + y * y) * 6371.0
    }

    private companion object {
        const val DAY_MS = 86_400_000L
        const val STALE_MS = 6 * 3_600_000L
    }
}
