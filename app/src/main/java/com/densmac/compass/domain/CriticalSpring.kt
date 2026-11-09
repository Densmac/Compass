package com.densmac.compass.domain

import kotlin.math.exp

/** Exact critically-damped step: stable at any frame time, never overshoots from rest. */
class CriticalSpring(private val omega: Float) {
    var offset = 0f
        private set
    var velocity = 0f
        private set

    fun set(offset: Float) {
        this.offset = offset
    }

    /** Starts from [offset] already moving, so a hand-off from another motion has no kink. */
    fun launch(offset: Float, velocity: Float) {
        this.offset = offset
        this.velocity = velocity
    }

    fun reset() {
        offset = 0f
        velocity = 0f
    }

    /** Advances toward zero offset by [dt] seconds. */
    fun step(dt: Float) {
        val temp = (velocity + omega * offset) * dt
        val e = exp(-omega * dt)
        velocity = (velocity - omega * temp) * e
        offset = (offset + temp) * e
    }
}
