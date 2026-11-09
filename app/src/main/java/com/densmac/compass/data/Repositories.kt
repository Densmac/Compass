package com.densmac.compass.data

import com.densmac.compass.domain.Detent
import com.densmac.compass.domain.GeoFix
import com.densmac.compass.domain.GravitySample
import com.densmac.compass.domain.HapticStrength
import com.densmac.compass.domain.OrientationSample
import com.densmac.compass.domain.Settings
import kotlinx.coroutines.flow.Flow

/** Flows are cold: the sensor is registered only while collected. */
interface OrientationRepository {
    val hasCompass: Boolean
    fun samples(): Flow<OrientationSample>
    fun gravity(): Flow<GravitySample>
}

interface LocationRepository {
    /** Requires a location permission; completes quietly without one. */
    fun fixes(): Flow<GeoFix>
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

/** Audible clicks: one at every 30° mark, with a soft second note on the cardinals. */
fun interface SoundController {
    fun play(detent: Detent)
}

interface HapticsController {
    fun play(detent: Detent, strength: HapticStrength)
    fun playLevelLock(strength: HapticStrength)
}
