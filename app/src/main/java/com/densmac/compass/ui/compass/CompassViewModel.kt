package com.densmac.compass.ui.compass

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.densmac.compass.data.HapticsController
import com.densmac.compass.data.LocationRepository
import com.densmac.compass.data.OrientationRepository
import com.densmac.compass.data.SettingsRepository
import com.densmac.compass.data.SoundController
import com.densmac.compass.di.DefaultDispatcher
import com.densmac.compass.domain.DeclinationResolver
import com.densmac.compass.domain.Detent
import com.densmac.compass.domain.DetentDetector
import com.densmac.compass.domain.GeoFix
import com.densmac.compass.domain.InfoRows
import com.densmac.compass.domain.MagneticModel
import com.densmac.compass.domain.NorthLatch
import com.densmac.compass.domain.NorthPreference
import com.densmac.compass.domain.NorthReference
import com.densmac.compass.domain.OneEuroAngleFilter
import com.densmac.compass.domain.SensorHealth
import com.densmac.compass.domain.SensorWarning
import com.densmac.compass.domain.Settings
import com.densmac.compass.domain.WallClock
import com.densmac.compass.domain.cardinalOf
import com.densmac.compass.domain.infoRows
import com.densmac.compass.domain.normalize360
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** The fast stream: changes on every sample, and is read only by the animation loop. */
@Immutable
data class CompassFrame(
    val hasHeading: Boolean = false,
    val heading: Float = 0f,
    val unstable: Boolean = false,
    val reference: NorthReference = NorthReference.MAGNETIC,
    val warning: SensorWarning = SensorWarning.NONE,
    val info: InfoRows = InfoRows(),
)

/** The slow stream: changes at most about once per displayed degree. */
@Immutable
data class CompassUiState(
    val hasCompass: Boolean = true,
    val hasHeading: Boolean = false,
    val degree: Int = 0,
    val cardinal: String = "N",
    val isNorth: Boolean = false,
    val unstable: Boolean = false,
    val reference: NorthReference = NorthReference.MAGNETIC,
    val warning: SensorWarning = SensorWarning.NONE,
    val info: InfoRows = InfoRows(),
    val locationGranted: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CompassViewModel @Inject constructor(
    private val orientation: OrientationRepository,
    location: LocationRepository,
    settingsRepository: SettingsRepository,
    private val haptics: HapticsController,
    private val sounds: SoundController,
    magneticModel: MagneticModel,
    clock: WallClock,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) : ViewModel(), DialSpinListener {

    private val locationGranted = MutableStateFlow(false)

    // The wheel is spun from the main thread; the sensor pipeline reads these on another.
    @Volatile private var spinning = false
    @Volatile private var audible = true
    @Volatile private var latestSettings = Settings()
    private var spinDetector: DetentDetector? = null
    private var filter = OneEuroAngleFilter()
    private var detector = DetentDetector()
    private val health = SensorHealth()
    private val latch = NorthLatch()
    private val declination = DeclinationResolver(magneticModel, clock)

    // Last-seen values for spotting changes that must silence the haptics.
    private var lastOffset: Float? = null
    private var lastAccuracy: Int? = null
    private var lastHeading = 0f
    private var haveHeading = false

    // Formatting is cached: it only depends on the fix and settings, not on the heading.
    private var infoFix: GeoFix? = null
    private var infoSettings: Settings? = null
    private var info = InfoRows()

    private val fixes = locationGranted.flatMapLatest { granted ->
        if (granted) location.fixes().map<GeoFix, GeoFix?> { it }.onStart { emit(null) } else flowOf(null)
    }

    private val filtered = orientation.samples().onStart {
        // The phone may have turned while nothing was listening: start clean, with no catch-up ticks.
        filter = OneEuroAngleFilter()
        detector = DetentDetector()
        lastOffset = null
        lastAccuracy = null
    }.map { sample ->
        val smoothed = if (sample.bearing.isNaN()) Float.NaN else filter.update(sample.bearing, sample.timestampNs)
        sample to smoothed
    }

    /**
     * The whole pipeline, with haptics inside it: the UI's lifecycle-aware collection is the only
     * thing keeping the sensors and the vibrator alive.
     */
    val frames: StateFlow<CompassFrame> = combine(
        filtered,
        fixes,
        settingsRepository.settings.onStart { emit(Settings()) },
    ) { (sample, smoothed), fix, settings ->
        val useTrue = settings.north == NorthPreference.TRUE && fix != null
        val offset = if (useTrue) declination.resolve(fix!!) else 0f
        val reference = when {
            !useTrue -> NorthReference.MAGNETIC
            declination.isStale(fix!!) -> NorthReference.TRUE_APPROX
            else -> NorthReference.TRUE
        }
        val unstable = smoothed.isNaN()
        if (!unstable) {
            lastHeading = normalize360(smoothed + offset)
            haveHeading = true
        }

        val previousOffset = lastOffset
        val previousAccuracy = lastAccuracy
        val referenceMoved = previousOffset != null && abs(previousOffset - offset) > 0.01f
        val accuracyMoved = previousAccuracy != null && previousAccuracy != sample.accuracy
        // The wheel owns the ticks while it spins; the sensor must not tick underneath it.
        if (referenceMoved || accuracyMoved || spinning) detector.suppress(sample.timestampNs)
        latestSettings = settings
        lastOffset = offset
        lastAccuracy = sample.accuracy

        if (!unstable) {
            // Ticks use the raw bearing: the filter's lag would make them land late.
            detector.update(normalize360(sample.bearing + offset), sample.timestampNs)
                ?.let { announce(it, settings) }
        }

        if (fix !== infoFix || settings !== infoSettings) {
            infoFix = fix
            infoSettings = settings
            info = infoRows(fix, settings, Locale.getDefault())
        }

        CompassFrame(
            hasHeading = haveHeading,
            heading = lastHeading,
            unstable = unstable,
            reference = reference,
            warning = health.update(sample.accuracy, sample.fieldMicroTesla, sample.timestampNs),
            info = info,
        )
    }.flowOn(dispatcher).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompassFrame())

    val uiState: StateFlow<CompassUiState> = combine(frames, locationGranted) { frame, granted ->
        val north = frame.hasHeading && latch.update(frame.heading)
        // Inside the north latch the readout holds 0°N, matching the orange state.
        val degree = if (north) 0 else frame.heading.roundToInt() % 360
        CompassUiState(
            hasCompass = orientation.hasCompass,
            hasHeading = frame.hasHeading,
            degree = degree,
            cardinal = if (north) "N" else cardinalOf(frame.heading),
            isNorth = north,
            unstable = frame.unstable,
            reference = frame.reference,
            warning = frame.warning,
            info = frame.info,
            locationGranted = granted,
        )
    }.distinctUntilChanged().flowOn(dispatcher)
        // No timeout of its own: it would stack on top of the one on frames.
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            // Without a magnetometer no frame ever arrives, so this can't wait for one.
            CompassUiState(hasCompass = orientation.hasCompass),
        )

    /** Sensors linger briefly after the screen stops; the ticks must not. */
    fun setAudible(value: Boolean) {
        audible = value
    }

    private fun announce(detent: Detent, settings: Settings) {
        if (!audible) return
        if (settings.hapticsEnabled) haptics.play(detent, settings.hapticStrength)
        if (settings.soundEnabled) sounds.play(detent)
    }

    override fun onSpinActive(active: Boolean) {
        spinning = active
        spinDetector = if (active) DetentDetector.forWheel() else null
    }

    override fun onDialMoved(headingDeg: Float, frameNs: Long) {
        spinDetector?.update(headingDeg, frameNs)?.let { announce(it, latestSettings) }
    }

    fun onLocationPermission(granted: Boolean) {
        locationGranted.value = granted
    }

    private companion object {
        /** Short, so haptics and sensors go quiet almost as soon as the app leaves the screen. */
        const val STOP_TIMEOUT_MS = 500L
    }
}
