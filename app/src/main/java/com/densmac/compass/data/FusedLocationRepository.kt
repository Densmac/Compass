package com.densmac.compass.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.core.location.LocationCompat
import androidx.core.location.altitude.AltitudeConverterCompat
import com.densmac.compass.domain.GeoFix
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Singleton
class FusedLocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationRepository {

    /** Callbacks must never land on the main thread. */
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "compass-location") }

    @SuppressLint("MissingPermission") // Callers gate on permission; a revoke is caught below.
    override fun fixes(): Flow<GeoFix> = callbackFlow {
        val client = LocationServices.getFusedLocationProviderClient(context)
        // GPS priority: network fixes carry no altitude, and the elevation row needs one.
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L).build()
        val listener = LocationListener { trySend(it) }
        try {
            client.lastLocation.addOnSuccessListener(executor) { last -> last?.let { trySend(it) } }
            client.requestLocationUpdates(request, executor, listener)
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { client.removeLocationUpdates(listener) }
    }.conflate().map(::toFix).flowOn(Dispatchers.IO)

    private fun toFix(location: Location): GeoFix {
        // The geoid lookup reads from disk; this runs on IO. Any failure just means no MSL value.
        val msl = if (location.hasAltitude()) {
            runCatching {
                AltitudeConverterCompat.addMslAltitudeToLocation(context, location)
                if (LocationCompat.hasMslAltitude(location)) LocationCompat.getMslAltitudeMeters(location) else null
            }.getOrNull()
        } else {
            null
        }
        return GeoFix(
            latitude = location.latitude,
            longitude = location.longitude,
            wgs84AltitudeM = if (location.hasAltitude()) location.altitude else null,
            mslAltitudeM = msl,
            timeMs = location.time,
        )
    }
}
