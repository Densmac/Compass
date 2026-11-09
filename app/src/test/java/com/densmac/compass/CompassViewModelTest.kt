package com.densmac.compass

import app.cash.turbine.test
import com.densmac.compass.data.HapticsController
import com.densmac.compass.data.LocationRepository
import com.densmac.compass.data.OrientationRepository
import com.densmac.compass.data.SettingsRepository
import com.densmac.compass.data.SoundController
import com.densmac.compass.domain.*
import com.densmac.compass.ui.compass.CompassViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompassViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val samples = MutableSharedFlow<OrientationSample>(extraBufferCapacity = 64)
    private val settingsFlow = MutableStateFlow(Settings())
    private val fixFlow = MutableSharedFlow<GeoFix>(replay = 1)
    private val played = mutableListOf<Detent>()

    private val orientation = object : OrientationRepository {
        override val hasCompass = true
        override fun samples(): Flow<OrientationSample> = samples
        override fun gravity(): Flow<GravitySample> = emptyFlow()
    }
    private val location = object : LocationRepository {
        override fun fixes(): Flow<GeoFix> = fixFlow
    }
    private val settings = object : SettingsRepository {
        override val settings: Flow<Settings> = settingsFlow
        override suspend fun update(transform: (Settings) -> Settings) { settingsFlow.value = transform(settingsFlow.value) }
    }
    private val haptics = object : HapticsController {
        override fun play(detent: Detent, strength: HapticStrength) { played += detent }
        override fun playLevelLock(strength: HapticStrength) = Unit
    }

    private val heard = mutableListOf<Detent>()
    private val sounds = SoundController { heard += it }

    private fun vm() = CompassViewModel(orientation, location, settings, haptics, sounds, { _, _, _, _ -> 10f }, { 1_000L }, dispatcher)
    private fun sample(b: Float, t: Long) = OrientationSample(b, 3, 45f, t)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun readoutFollowsHeading() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            skipItems(1)
            samples.emit(sample(242f, 1))
            val s = expectMostRecentItem()
            assertEquals(242, s.degree)
            assertEquals("SW", s.cardinal)
            assertEquals(NorthReference.MAGNETIC, s.reference)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun trueNorthAddsDeclinationOnceFixArrives() = runTest(dispatcher) {
        val vm = vm()
        vm.onLocationPermission(true)
        vm.uiState.test {
            samples.emit(sample(100f, 1))
            fixFlow.emit(GeoFix(0.0, 0.0, 0.0, 0.0, 1_000L))
            samples.emit(sample(100f, 20_000_000))
            val s = expectMostRecentItem()
            assertEquals(110, s.degree)
            assertEquals(NorthReference.TRUE, s.reference)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun readoutHoldsZeroInsideNorthLatch() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            samples.emit(sample(0.8f, 1))
            val s = expectMostRecentItem()
            assertTrue(s.isNorth)
            assertEquals(0, s.degree)
            assertEquals("N", s.cardinal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun sensorsStopWhenUiStopsCollecting() = runTest(dispatcher) {
        val vm = vm()
        assertEquals(0, samples.subscriptionCount.value)
        val job = launch { vm.uiState.collect { } }
        runCurrent()
        assertEquals(1, samples.subscriptionCount.value)
        job.cancel()
        advanceTimeBy(600)
        runCurrent()
        assertEquals(0, samples.subscriptionCount.value)
    }

    @Test fun hapticsRespectSetting() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.frames.collect { } }
        // The filter eases toward each value, so hold it long enough to cross the graduation.
        var t = 0L
        suspend fun hold(value: Float) = repeat(100) { t += 20_000_000; samples.emit(sample(value, t)) }
        hold(14f)
        hold(15.5f)
        assertEquals(listOf(Detent.MID), played)
        assertEquals(listOf(Detent.MID), heard)
        settingsFlow.value = Settings(hapticsEnabled = false, soundEnabled = false)
        hold(18.5f)
        assertEquals(listOf(Detent.MID), played)
        job.cancel()
    }

    @Test fun noCatchUpTicksAfterRestart() = runTest(dispatcher) {
        val vm = vm()
        var job = launch { vm.frames.collect { } }
        var t = 0L
        repeat(50) { t += 20_000_000; samples.emit(sample(10f, t)) }
        job.cancel()
        advanceTimeBy(600)
        runCurrent()
        played.clear()
        job = launch { vm.frames.collect { } }
        runCurrent()
        // The phone turned 170° while stopped.
        repeat(50) { t += 20_000_000; samples.emit(sample(180f, t)) }
        assertTrue(played.isEmpty())
        job.cancel()
    }

    @Test fun spinTicksReplaceSensorTicks() = runTest(dispatcher) {
        val vm = vm()
        val job = launch { vm.frames.collect { } }
        var t = 0L
        repeat(50) { t += 20_000_000; samples.emit(sample(14f, t)) }
        vm.onSpinActive(true)
        repeat(50) { t += 20_000_000; samples.emit(sample(15.5f, t)) }
        assertTrue(played.isEmpty())
        vm.onDialMoved(29f, 1_000_000_000L)
        vm.onDialMoved(30.5f, 1_100_000_000L)
        assertEquals(listOf(Detent.MAJOR), played)
        assertEquals(listOf(Detent.MAJOR), heard)
        vm.onSpinActive(false)
        vm.onDialMoved(32f, 1_200_000_000L)
        assertEquals(1, played.size)
        job.cancel()
    }

    @Test fun noMagnetometerIsKnownWithoutAnySample() = runTest(dispatcher) {
        val none = object : OrientationRepository {
            override val hasCompass = false
            override fun samples(): Flow<OrientationSample> = emptyFlow()
            override fun gravity(): Flow<GravitySample> = emptyFlow()
        }
        val vm = CompassViewModel(none, location, settings, haptics, sounds, { _, _, _, _ -> 0f }, { 0L }, dispatcher)
        assertFalse(vm.uiState.value.hasCompass)
    }

    @Test fun unstablePoseHoldsHeading() = runTest(dispatcher) {
        val vm = vm()
        vm.uiState.test {
            samples.emit(sample(50f, 1))
            samples.emit(sample(Float.NaN, 20_000_000))
            val s = expectMostRecentItem()
            assertEquals(50, s.degree)
            assertTrue(s.unstable)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
