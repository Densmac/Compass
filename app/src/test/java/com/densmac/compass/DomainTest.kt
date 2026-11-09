package com.densmac.compass

import com.densmac.compass.domain.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class DomainTest {
    private val delta = 0.5f

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)

    /** Device→world for a phone lying flat with its top edge at [bearing]. */
    private fun flat(bearing: Double): FloatArray {
        val a = Math.toRadians(bearing)
        val s = Math.sin(a).toFloat()
        val c = Math.cos(a).toFloat()
        return floatArrayOf(c, s, 0f, -s, c, 0f, 0f, 0f, 1f)
    }

    /** Upright, screen facing the user, rear camera toward [bearing]. */
    private fun upright(bearing: Double): FloatArray {
        val a = Math.toRadians(bearing)
        val s = Math.sin(a).toFloat()
        val c = Math.cos(a).toFloat()
        // X → right of view; Y → up; Z → toward the user (−view direction).
        return floatArrayOf(c, 0f, -s, -s, 0f, -c, 0f, 1f, 0f)
    }

    @Test fun wrapAndShortest() {
        assertEquals(350f, normalize360(-10f), 1e-3f)
        assertEquals(2f, shortestDelta(359f, 1f), 1e-3f)
        assertEquals(-2f, shortestDelta(1f, 359f), 1e-3f)
    }

    @Test fun cardinalsMatchReferences() {
        assertEquals("SW", cardinalOf(242f))
        assertEquals("NW", cardinalOf(310f))
        assertEquals("N", cardinalOf(0f))
        assertEquals("N", cardinalOf(359f))
    }

    @Test fun bearingFlat() {
        assertEquals(0f, pointingBearing(identity(), 0), delta)
        for (b in listOf(45.0, 90.0, 180.0, 270.0)) assertEquals(b.toFloat(), pointingBearing(flat(b), 0), delta)
    }

    @Test fun bearingUprightEqualsCameraDirection() {
        for (b in listOf(0.0, 90.0, 200.0)) {
            val got = pointingBearing(upright(b), 0)
            assertEquals(0f, shortestDelta(b.toFloat(), got), delta)
        }
    }

    @Test fun bearingContinuousThroughTilt() {
        // Pitch a north-facing phone from flat to upright: bearing must stay 0 all the way.
        for (p in 0..90 step 5) {
            val a = Math.toRadians(p.toDouble())
            val s = Math.sin(a).toFloat()
            val c = Math.cos(a).toFloat()
            val r = floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)
            assertEquals(0f, shortestDelta(0f, pointingBearing(r, 0)), delta)
        }
    }

    @Test fun bearingForDisplayRotations() {
        // ROTATION_90: device turned CCW, so with the matrix for "device top at 0°" screen-up (+X) is east.
        assertEquals(90f, pointingBearing(identity(), 1), delta)
        assertEquals(180f, pointingBearing(identity(), 2), delta)
        assertEquals(270f, pointingBearing(identity(), 3), delta)
    }

    @Test fun flatBearingForEveryDisplayRotation() {
        for (b in listOf(0.0, 37.0, 200.0)) for (rotation in 0..3) {
            val expected = normalize360(b.toFloat() + 90f * rotation)
            assertEquals(0f, shortestDelta(expected, pointingBearing(flat(b), rotation)), delta)
        }
    }

    @Test fun uprightBearingIsCameraForEveryDisplayRotation() {
        for (b in listOf(0.0, 37.0, 200.0)) {
            val a = Math.toRadians(b)
            val s = Math.sin(a).toFloat()
            val c = Math.cos(a).toFloat()
            // Rear camera toward b; the screen-up edge of each rotation points at the sky.
            val poses = mapOf(
                0 to upright(b),
                1 to floatArrayOf(0f, -c, -s, 0f, s, -c, 1f, 0f, 0f),
                2 to floatArrayOf(-c, 0f, -s, s, 0f, -c, 0f, -1f, 0f),
                3 to floatArrayOf(0f, c, -s, 0f, -s, -c, -1f, 0f, 0f),
            )
            for ((rotation, r) in poses) {
                assertEquals(0f, shortestDelta(b.toFloat(), pointingBearing(r, rotation)), delta)
            }
        }
    }

    @Test fun degeneratePoseHasNoBearing() {
        // Top tipped back toward the user: screen-up and camera cancel horizontally.
        val a = Math.toRadians(135.0)
        val s = Math.sin(a).toFloat()
        val c = Math.cos(a).toFloat()
        assertTrue(pointingBearing(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c), 0).isNaN())
    }

    @Test fun filterHandlesWrapAndConverges() {
        val f = OneEuroAngleFilter()
        var t = 0L
        f.update(359f, t)
        var out = 0f
        repeat(300) { t += 20_000_000; out = f.update(1f, t) }
        assertEquals(0f, shortestDelta(1f, out), 0.1f)
    }

    @Test fun filterStaysBetweenAcrossWrap() {
        val f = OneEuroAngleFilter()
        f.update(359f, 0)
        val out = f.update(1f, 20_000_000)
        assertTrue(out > 358f || out < 2f)
    }

    private fun sweep(d: DetentDetector, values: List<Float>, stepNs: Long = 100_000_000L): List<Detent?> {
        var t = 0L
        return values.map { v -> t += stepNs; d.update(v, t) }
    }

    @Test fun firesOncePerBoundary() {
        val events = sweep(DetentDetector(), listOf(0.9f, 1.5f, 3.5f, 4f, 5f, 6.5f))
        assertEquals(listOf(null, null, Detent.MINOR, null, null, Detent.MINOR), events)
    }

    @Test fun hoverOnBoundaryDoesNotChatter() {
        val events = sweep(DetentDetector(), listOf(8f, 9.1f, 8.9f, 9.1f, 8.9f, 9.1f))
        assertEquals(1, events.count { it != null })
    }

    @Test fun refiresAfterMovingBackPastRearm() {
        val events = sweep(DetentDetector(), listOf(8f, 9.1f, 8.2f, 9.1f))
        assertEquals(2, events.count { it != null })
    }

    @Test fun tiersAndWrapIntoNorth() {
        assertEquals(Detent.MID, sweep(DetentDetector(), listOf(14f, 15.5f)).last())
        assertEquals(Detent.MAJOR, sweep(DetentDetector(), listOf(29f, 30.5f)).last())
        assertEquals(Detent.CARDINAL, sweep(DetentDetector(), listOf(89f, 90.5f)).last())
        assertEquals(Detent.NORTH, sweep(DetentDetector(), listOf(359f, 0.5f)).last())
    }

    @Test fun manyCrossingsPlayOnlyHighestPriority() {
        assertEquals(Detent.CARDINAL, sweep(DetentDetector(), listOf(85f, 95f)).last())
    }

    @Test fun fastSpinDropsMinors() {
        val d = DetentDetector()
        var h = 0f
        var t = 0L
        val events = mutableListOf<Detent?>()
        repeat(100) { h += 5f; t += 20_000_000; events += d.update(normalize360(h), t) } // 250°/s
        assertFalse(events.drop(30).contains(Detent.MINOR))
        assertFalse(events.drop(30).contains(Detent.MID))
    }

    @Test fun suppressionAndJumps() {
        val d = DetentDetector()
        d.update(1f, 0)
        d.suppress(1)
        assertNull(d.update(3.5f, 100_000_000))
        val j = DetentDetector()
        j.update(0.5f, 0)
        assertNull(j.update(60.5f, 100_000_000))
    }

    @Test fun minimumGapIsEnforced() {
        val d = DetentDetector()
        d.update(1f, 0)
        assertNotNull(d.update(3.5f, 100_000_000))
        assertNull(d.update(6.5f, 110_000_000))
    }

    @Test fun wheelKeepsThirtyDegreeMarksAtTopSpeed() {
        for ((fps, atLeast) in listOf(30 to 19, 60 to 19, 120 to 19)) {
            val d = DetentDetector.forWheel()
            val stepNs = 1_000_000_000L / fps
            val stepDeg = 900f / fps
            var h = 0.5f
            var t = 0L
            d.update(h, t)
            var marks = 0
            repeat(fps * 2 / 3) {
                h += stepDeg
                t += stepNs
                val e = d.update(normalize360(h), t)
                if (e == Detent.MAJOR || e == Detent.CARDINAL || e == Detent.NORTH) marks++
            }
            assertTrue("$fps fps gave $marks", marks >= atLeast)
        }
    }

    @Test fun springSettlesWithoutOvershoot() {
        val s = CriticalSpring(22f)
        s.set(100f)
        var min = 100f
        repeat(120) { s.step(1 / 60f); min = minOf(min, s.offset) }
        assertTrue(min > -0.01f)
        assertEquals(0f, s.offset, 0.05f)
    }

    /** Flings from [start] and returns the total angle turned on the way home. */
    private fun travel(start: Float, fling: Float): Float {
        val s = DialSpin()
        s.grab()
        s.drag(start)
        s.release(fling)
        var total = 0f
        var steps = 0
        while (s.isActive && steps < 2000) { s.step(1 / 60f); total += abs(s.velocity) / 60f; steps++ }
        assertFalse(s.isActive)
        assertEquals(0f, s.offset, 0f)
        return total
    }

    @Test fun harderFlingsTurnFurther() {
        assertTrue(travel(0f, 1000f) > travel(0f, 300f) + 180f)
    }

    @Test fun flingLandsHomeWithoutEverReversing() {
        for (start in listOf(50f, -120f, 179f)) for (fling in listOf(250f, -250f, 700f, -1000f)) {
            val s = DialSpin()
            s.grab()
            s.drag(start)
            s.release(fling)
            assertEquals(DialSpin.Phase.COASTING, s.phase)
            var steps = 0
            var lastSpeed = Float.MAX_VALUE
            while (s.isActive && steps < 2000) {
                s.step(1 / 60f)
                if (s.isActive) {
                    assertTrue("reversed", s.velocity * fling > 0f)
                    assertTrue("sped up", abs(s.velocity) <= lastSpeed)
                    lastSpeed = abs(s.velocity)
                }
                steps++
            }
            assertFalse(s.isActive)
            assertEquals(0f, s.offset, 0f)
        }
    }

    @Test fun flingJustPastHomeKeepsItsDirection() {
        val s = DialSpin()
        s.grab()
        s.drag(-10f)
        s.release(200f)
        assertEquals(DialSpin.Phase.COASTING, s.phase)
        s.step(1e-4f)
        assertEquals(200f, s.velocity, 1f)
        var steps = 0
        while (s.isActive && steps < 2000) {
            s.step(1 / 60f)
            if (s.isActive) assertTrue(s.velocity > 0f)
            steps++
        }
        assertFalse(s.isActive)
    }

    @Test fun gentleNudgeGoesStraightHome() {
        travel(40f, 10f)
        travel(170f, 60f)
    }

    @Test fun spinAlwaysSettlesHomeAndGoesIdle() {
        val s = DialSpin()
        s.grab()
        s.drag(50f)
        assertEquals(50f, s.offset, 1e-3f)
        s.release(800f)
        var steps = 0
        while (s.isActive && steps < 2000) { s.step(1 / 60f); steps++ }
        assertFalse(s.isActive)
        assertEquals(0f, s.offset, 0f)
    }

    @Test fun grabStopsACoast() {
        val s = DialSpin()
        s.grab()
        s.release(800f)
        s.step(1 / 60f)
        s.grab()
        val held = s.offset
        s.step(0.5f)
        assertEquals(held, s.offset, 0f)
    }

    @Test fun flingFrameStaysUnderJumpLimit() {
        val s = DialSpin()
        s.grab()
        s.release(100_000f)
        assertTrue(abs(s.velocity) / 60f < 20f)
    }

    @Test fun dmsCarriesRounding() {
        assertEquals("12°30′00″", formatDms(-12.5))
        assertEquals("2°00′00″", formatDms(1.99999999))
        assertEquals("0°01′00″", formatDms(0.0166665))
    }

    @Test fun rowsUseHemisphereLabels() {
        val fix = GeoFix(-12.5, 45.25, 260.0, 250.04, 0)
        val rows = infoRows(fix, Settings(), Locale.US)
        assertEquals("South Latitude", rows.latitudeLabel)
        assertEquals("East Longitude", rows.longitudeLabel)
        assertEquals("250.0 m", rows.elevation)
        assertEquals("West Longitude", infoRows(fix.copy(longitude = -3.0), Settings(), Locale.US).longitudeLabel)
    }

    @Test fun ellipsoidElevationIsMarked() {
        val fix = GeoFix(0.0, 0.0, 100.0, null, 0)
        assertEquals("≈100.0 m", formatElevation(fix, ElevationUnit.METRES, Locale.US))
        assertEquals(NO_VALUE, formatElevation(fix.copy(wgs84AltitudeM = null), ElevationUnit.METRES, Locale.US))
    }

    @Test fun declinationRefreshRules() {
        var now = 1_000L
        var calls = 0
        val r = DeclinationResolver({ _, _, _, _ -> calls++; 4f }, { now })
        val fix = GeoFix(10.0, 10.0, 0.0, null, 0)
        r.resolve(fix); r.resolve(fix.copy(latitude = 10.001))
        assertEquals(1, calls)
        r.resolve(fix.copy(latitude = 10.1))
        assertEquals(2, calls)
        r.resolve(fix.copy(latitude = 10.1, wgs84AltitudeM = 900.0))
        assertEquals(3, calls)
        now += 90_000_000L
        r.resolve(fix.copy(latitude = 10.1, wgs84AltitudeM = 900.0))
        assertEquals(4, calls)
    }

    @Test fun northLatchHasHysteresis() {
        val l = NorthLatch()
        assertFalse(l.update(2f))
        assertTrue(l.update(0.5f))
        assertTrue(l.update(359f - 0.2f + 0.5f))
        assertTrue(l.update(1.4f))
        assertFalse(l.update(1.6f))
    }

    @Test fun healthDebouncesAndClears() {
        val h = SensorHealth()
        assertEquals(SensorWarning.NONE, h.update(1, 45f, 0))
        assertEquals(SensorWarning.CALIBRATE, h.update(1, 45f, 1_600_000_000))
        assertEquals(SensorWarning.NONE, h.update(3, 45f, 1_700_000_000))
        h.update(3, 90f, 2_000_000_000)
        assertEquals(SensorWarning.INTERFERENCE, h.update(3, 90f, 3_600_000_000))
    }

    @Test fun levelFlatUprightAndLock() {
        val c = LevelCalculator()
        val flat = c.update(0f, 0f, 1f)
        assertFalse(flat.upright)
        assertTrue(flat.isLevel)
        val tilted = c.update(0.1f, 0f, 0.995f)
        assertFalse(tilted.isLevel)
        val up = c.update(0f, 1f, 0f)
        assertTrue(up.upright)
        assertTrue(up.isLevel)
    }
}
