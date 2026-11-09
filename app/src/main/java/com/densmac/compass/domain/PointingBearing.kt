package com.densmac.compass.domain

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Bearing of the direction the user is pointing the phone, from a device→world rotation matrix
 * (row-major 3×3; rows are East, North, Up).
 *
 * The pointing vector is screen-up + rear-camera (−Z), projected on the horizontal plane. Flat, the
 * screen-up term dominates; upright, the camera term does. With the top tilted away from the user
 * both share one horizontal bearing, so there is no mode switch and no jump.
 *
 * [displayRotation] is Surface.ROTATION_* (0..3). ROTATION_90 means the device was turned
 * counter-clockwise, so its right edge (+X) is now screen-up.
 *
 * @return bearing in [0, 360), or NaN when the pose has no usable horizontal direction.
 */
fun pointingBearing(r: FloatArray, displayRotation: Int): Float {
    val upE: Float
    val upN: Float
    when (displayRotation) {
        1 -> { upE = r[0]; upN = r[3] }
        2 -> { upE = -r[1]; upN = -r[4] }
        3 -> { upE = -r[0]; upN = -r[3] }
        else -> { upE = r[1]; upN = r[4] }
    }
    val e = upE - r[2]
    val n = upN - r[5]
    if (sqrt(e * e + n * n) < MIN_HORIZONTAL) return Float.NaN
    return normalize360(Math.toDegrees(atan2(e.toDouble(), n.toDouble())).toFloat())
}

private const val MIN_HORIZONTAL = 0.3f

/** X component of a device-frame vector in screen coordinates (right = +). */
fun screenX(x: Float, y: Float, displayRotation: Int): Float = when (displayRotation) {
    1 -> -y
    2 -> -x
    3 -> y
    else -> x
}

/** Y component of a device-frame vector in screen coordinates (up = +). */
fun screenY(x: Float, y: Float, displayRotation: Int): Float = when (displayRotation) {
    1 -> x
    2 -> -y
    3 -> -x
    else -> y
}
