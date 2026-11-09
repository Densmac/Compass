package com.densmac.compass.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.densmac.compass.domain.CoordinateFormat
import com.densmac.compass.domain.ElevationUnit
import com.densmac.compass.domain.HapticStrength
import com.densmac.compass.domain.NorthPreference
import com.densmac.compass.domain.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore("settings")

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val north = stringPreferencesKey("north")
        val haptics = booleanPreferencesKey("haptics")
        val sound = booleanPreferencesKey("sound")
        val strength = stringPreferencesKey("haptic_strength")
        val unit = stringPreferencesKey("elevation_unit")
        val coords = stringPreferencesKey("coordinates")
        val keepOn = booleanPreferencesKey("keep_screen_on")
    }

    private inline fun <reified T : Enum<T>> parse(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            theme = parse(p[Keys.theme], d.theme),
            north = parse(p[Keys.north], d.north),
            hapticsEnabled = p[Keys.haptics] ?: d.hapticsEnabled,
            soundEnabled = p[Keys.sound] ?: d.soundEnabled,
            hapticStrength = parse(p[Keys.strength], d.hapticStrength),
            elevationUnit = parse(p[Keys.unit], d.elevationUnit),
            coordinateFormat = parse(p[Keys.coords], d.coordinateFormat),
            keepScreenOn = p[Keys.keepOn] ?: d.keepScreenOn,
        )
    }

    override val settings: Flow<Settings> = context.store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)
        .distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings) {
        context.store.edit { p ->
            val next = transform(read(p))
            p[Keys.theme] = next.theme.name
            p[Keys.north] = next.north.name
            p[Keys.haptics] = next.hapticsEnabled
            p[Keys.sound] = next.soundEnabled
            p[Keys.strength] = next.hapticStrength.name
            p[Keys.unit] = next.elevationUnit.name
            p[Keys.coords] = next.coordinateFormat.name
            p[Keys.keepOn] = next.keepScreenOn
        }
    }
}
