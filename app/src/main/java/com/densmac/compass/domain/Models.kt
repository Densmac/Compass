package com.densmac.compass.domain

enum class NorthPreference { TRUE, MAGNETIC }
enum class ThemeMode { DARK, LIGHT }
enum class HapticStrength { SUBTLE, NORMAL }
enum class ElevationUnit { METRES, FEET }
enum class CoordinateFormat { DMS, DECIMAL }

data class Settings(
    val theme: ThemeMode = ThemeMode.DARK,
    val north: NorthPreference = NorthPreference.TRUE,
    val hapticsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticStrength: HapticStrength = HapticStrength.NORMAL,
    val elevationUnit: ElevationUnit = ElevationUnit.METRES,
    val coordinateFormat: CoordinateFormat = CoordinateFormat.DMS,
    val keepScreenOn: Boolean = false,
)

/** One orientation reading. [bearing] is magnetic, or NaN while the pose has no usable direction. */
data class OrientationSample(
    val bearing: Float,
    val accuracy: Int,
    val fieldMicroTesla: Float,
    val timestampNs: Long,
)

/** Unit gravity direction ("up") in screen coordinates. */
data class GravitySample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

data class GeoFix(
    val latitude: Double,
    val longitude: Double,
    /** WGS84 ellipsoid height; what GeomagneticField expects. */
    val wgs84AltitudeM: Double?,
    val mslAltitudeM: Double?,
    val timeMs: Long,
)

enum class NorthReference { TRUE, TRUE_APPROX, MAGNETIC }
enum class SensorWarning { NONE, CALIBRATE, INTERFERENCE }
