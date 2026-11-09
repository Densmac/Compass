package com.densmac.compass.di

import android.hardware.GeomagneticField
import com.densmac.compass.data.DataStoreSettingsRepository
import com.densmac.compass.data.FusedLocationRepository
import com.densmac.compass.data.HapticsController
import com.densmac.compass.data.LocationRepository
import com.densmac.compass.data.OrientationRepository
import com.densmac.compass.data.SensorOrientationRepository
import com.densmac.compass.data.SettingsRepository
import com.densmac.compass.data.SoundController
import com.densmac.compass.data.SoundPoolTicks
import com.densmac.compass.data.VibratorHaptics
import com.densmac.compass.domain.MagneticModel
import com.densmac.compass.domain.WallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun orientation(impl: SensorOrientationRepository): OrientationRepository
    @Binds abstract fun location(impl: FusedLocationRepository): LocationRepository
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun haptics(impl: VibratorHaptics): HapticsController
    @Binds abstract fun sounds(impl: SoundPoolTicks): SoundController
}

@Module
@InstallIn(SingletonComponent::class)
object ProvidersModule {
    @Provides @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    fun wallClock(): WallClock = WallClock(System::currentTimeMillis)

    @Provides
    fun magneticModel(): MagneticModel = MagneticModel { lat, lon, alt, time ->
        GeomagneticField(lat.toFloat(), lon.toFloat(), alt.toFloat(), time).declination
    }
}
