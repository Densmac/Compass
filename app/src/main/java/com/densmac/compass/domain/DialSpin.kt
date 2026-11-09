package com.densmac.compass.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * The wheel the user can grab and fling. [offset] is added to the real heading, so offset 0 is the
 * true reading. Held, it follows the finger. On release the landing is decided up front: whole
 * turns are added until the distance suits the fling's strength, and one ease-out carries the wheel
 * there, so it comes to rest on the true heading with no correction afterwards.
 */
class DialSpin {
    enum class Phase { IDLE, HELD, COASTING, RETURNING }

    var phase = Phase.IDLE
        private set
    var offset = 0f
        private set
    var velocity = 0f
        private set
    private val spring = CriticalSpring(RETURN_OMEGA)

    /** Signed distance of the coast, ending where offset is 0 (mod 360). */
    private var distance = 0f
    private var duration = 0f
    private var elapsed = 0f

    val isActive: Boolean get() = phase != Phase.IDLE

    fun grab() {
        phase = Phase.HELD
        velocity = 0f
    }

    fun drag(deltaDeg: Float) {
        if (phase == Phase.HELD) offset = wrap(offset + deltaDeg)
    }

    fun release(velocityDegPerS: Float) {
        if (phase != Phase.HELD) return
        velocity = velocityDegPerS.coerceIn(-MAX_VELOCITY, MAX_VELOCITY)
        val speed = abs(velocity)
        if (speed >= MIN_FLING && planCoast(speed)) phase = Phase.COASTING else beginReturn()
    }

    /** @return false if no landing ahead can be reached naturally at this speed. */
    private fun planCoast(speed: Float): Boolean {
        val direction = sign(velocity)
        val toHome = normalize360(-offset * direction)
        val idealDuration = MIN_IDEAL_S + (MAX_IDEAL_S - MIN_IDEAL_S) * speed / MAX_VELOCITY
        // Ease-out cubic starts at 3 × distance / duration, which is how speed sets the distance.
        val idealDistance = speed * idealDuration / 3f
        val turns = max(0, ((idealDistance - toHome) / 360f).roundToInt())
        var total = toHome + turns * 360f
        // Almost home already: take a full turn if the fling can carry it, rather than stop dead.
        if (3f * total / speed < MIN_COAST_S && 3f * (total + 360f) / speed <= MAX_COAST_S) total += 360f
        // Duration comes only from speed and distance, so the release velocity carries over exactly.
        val time = 3f * total / speed
        if (time > MAX_COAST_S || total < MIN_DISTANCE) return false
        distance = total * direction
        duration = time
        elapsed = 0f
        return true
    }

    /** Abandons any spin at once, e.g. when the screen goes away. */
    fun cancel() = settle()

    fun step(dt: Float) {
        when (phase) {
            Phase.COASTING -> {
                elapsed += dt
                val left = 1f - (elapsed / duration).coerceAtMost(1f)
                if (left <= 0f) {
                    settle()
                } else {
                    offset = wrap(-distance * left.pow(3))
                    velocity = 3f * distance / duration * left * left
                }
            }
            Phase.RETURNING -> {
                spring.step(dt)
                offset = spring.offset
                velocity = spring.velocity
                if (abs(offset) < 0.05f && abs(velocity) < 1f) settle()
            }
            Phase.HELD, Phase.IDLE -> Unit
        }
    }

    private fun settle() {
        offset = 0f
        velocity = 0f
        phase = Phase.IDLE
    }

    /** A nudge too gentle to carry anywhere: one smooth move straight home, keeping its velocity. */
    private fun beginReturn() {
        offset = wrap(offset)
        spring.launch(offset, velocity)
        phase = Phase.RETURNING
    }

    private fun wrap(deg: Float) = shortestDelta(0f, deg)

    private companion object {
        const val RETURN_OMEGA = 4.5f
        const val MIN_FLING = 40f
        const val MIN_IDEAL_S = 1.6f
        const val MAX_IDEAL_S = 3.2f
        const val MIN_COAST_S = 0.6f
        const val MAX_COAST_S = 4.5f
        const val MIN_DISTANCE = 0.5f

        /** Capped so that even at 30 fps one frame crosses at most one 30° mark, and none go unheard. */
        const val MAX_VELOCITY = 900f
    }
}
