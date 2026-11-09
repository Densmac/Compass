package com.densmac.compass.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

data class InfoRows(
    val latitudeLabel: String = "Latitude",
    val latitude: String = "—",
    val longitudeLabel: String = "Longitude",
    val longitude: String = "—",
    val elevation: String = "—",
)

const val NO_VALUE = "—"

fun formatDms(valueDeg: Double): String {
    val total = (abs(valueDeg) * 3600.0).roundToLong()
    val d = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "$d°${m.toString().padStart(2, '0')}′${s.toString().padStart(2, '0')}″"
}

fun formatCoordinate(valueDeg: Double, format: CoordinateFormat, locale: Locale): String =
    when (format) {
        CoordinateFormat.DMS -> formatDms(valueDeg)
        CoordinateFormat.DECIMAL -> String.format(locale, "%.5f°", abs(valueDeg))
    }

fun formatElevation(fix: GeoFix, unit: ElevationUnit, locale: Locale): String {
    val msl = fix.mslAltitudeM
    val metres = msl ?: fix.wgs84AltitudeM ?: return NO_VALUE
    val prefix = if (msl == null) "≈" else ""
    return when (unit) {
        ElevationUnit.METRES -> prefix + String.format(locale, "%,.1f m", metres)
        ElevationUnit.FEET -> prefix + String.format(locale, "%,.0f ft", metres * 3.28084)
    }
}

fun infoRows(fix: GeoFix?, settings: Settings, locale: Locale): InfoRows {
    if (fix == null) return InfoRows()
    return InfoRows(
        latitudeLabel = if (fix.latitude >= 0) "North Latitude" else "South Latitude",
        latitude = formatCoordinate(fix.latitude, settings.coordinateFormat, locale),
        longitudeLabel = if (fix.longitude >= 0) "East Longitude" else "West Longitude",
        longitude = formatCoordinate(fix.longitude, settings.coordinateFormat, locale),
        elevation = formatElevation(fix, settings.elevationUnit, locale),
    )
}
