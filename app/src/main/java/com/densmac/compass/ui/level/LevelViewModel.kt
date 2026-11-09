package com.densmac.compass.ui.level

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.compass.data.HapticsController
import com.densmac.compass.data.OrientationRepository
import com.densmac.compass.data.SettingsRepository
import com.densmac.compass.di.DefaultDispatcher
import com.densmac.compass.domain.LevelCalculator
import com.densmac.compass.domain.LevelReading
import com.densmac.compass.domain.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlin.math.roundToInt

@Immutable
data class LevelFrame(val x: Float = 0f, val y: Float = 0f, val reading: LevelReading = LevelReading())

@Immutable
data class LevelUiState(
    val upright: Boolean = false,
    val pitchTenths: Int = 0,
    val rollTenths: Int = 0,
    val isLevel: Boolean = false,
)

@HiltViewModel
class LevelViewModel @Inject constructor(
    orientation: OrientationRepository,
    settingsRepository: SettingsRepository,
    private val haptics: HapticsController,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val calculator = LevelCalculator()
    private var wasLevel = false
    @Volatile private var audible = true
    private var x = 0f
    private var y = 0f
    private var z = 1f
    private var seeded = false

    val frames: StateFlow<LevelFrame> = combine(
        orientation.gravity(),
        settingsRepository.settings.onStart { emit(Settings()) },
    ) { g, settings ->
        // Light smoothing: the accelerometer fallback is noisy, and the bubble shouldn't be.
        val k = if (seeded) SMOOTHING else 1f
        seeded = true
        x += (g.x - x) * k
        y += (g.y - y) * k
        z += (g.z - z) * k
        val reading = calculator.update(x, y, z)
        if (reading.isLevel && !wasLevel && settings.hapticsEnabled && audible) haptics.playLevelLock(settings.hapticStrength)
        wasLevel = reading.isLevel
        LevelFrame(x, y, reading)
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(500), LevelFrame())

    val uiState: StateFlow<LevelUiState> = frames.map {
        LevelUiState(
            upright = it.reading.upright,
            pitchTenths = (it.reading.pitchDeg * 10f).roundToInt(),
            rollTenths = (it.reading.rollDeg * 10f).roundToInt(),
            isLevel = it.reading.isLevel,
        )
    }.distinctUntilChanged().flowOn(dispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), LevelUiState())

    /** Sensors linger briefly after the screen stops; the click must not. */
    fun setAudible(value: Boolean) {
        audible = value
    }

    private companion object {
        const val SMOOTHING = 0.25f
    }
}
