package com.densmac.compass.data

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibrationEffect.Composition
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import com.densmac.compass.domain.Detent
import com.densmac.compass.domain.HapticStrength
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/** The window to fall back on for system haptics; set by the activity while it exists. */
@Singleton
class HapticViewHolder @Inject constructor() {
    @Volatile var view: View? = null
}

@Singleton
class VibratorHaptics @Inject constructor(
    @ApplicationContext context: Context,
    private val viewHolder: HapticViewHolder,
) : HapticsController {

    private val vibrator: Vibrator =
        context.getSystemService(VibratorManager::class.java).defaultVibrator

    /** Vibrator calls are binder calls: one background thread, in order. */
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "compass-haptics") }

    private val effects: Map<HapticStrength, Map<Detent, VibrationEffect?>> by lazy {
        HapticStrength.entries.associateWith { strength ->
            Detent.entries.associateWith { build(it, if (strength == HapticStrength.SUBTLE) 0.6f else 1f) }
        }
    }

    /** @return null when the motor lacks a primitive; a composition with one may play nothing. */
    private fun build(detent: Detent, s: Float): VibrationEffect? {
        val primary = when (detent) {
            Detent.MINOR -> Composition.PRIMITIVE_LOW_TICK to 0.35f
            Detent.MID -> Composition.PRIMITIVE_TICK to 0.5f
            Detent.MAJOR -> Composition.PRIMITIVE_TICK to 0.75f
            Detent.CARDINAL -> Composition.PRIMITIVE_CLICK to 0.7f
            Detent.NORTH -> Composition.PRIMITIVE_CLICK to 1f
        }
        val needed = if (detent == Detent.NORTH) {
            intArrayOf(primary.first, Composition.PRIMITIVE_TICK)
        } else {
            intArrayOf(primary.first)
        }
        if (!vibrator.areAllPrimitivesSupported(*needed)) return null
        val composition = VibrationEffect.startComposition().addPrimitive(primary.first, primary.second * s)
        if (detent == Detent.NORTH) composition.addPrimitive(Composition.PRIMITIVE_TICK, 0.4f * s, 40)
        return composition.compose()
    }

    /**
     * Phones whose HAL reports no primitives (OnePlus among them) still route View haptics through
     * the OEM's tuned effects, which feel far better than a raw vibrator pulse.
     */
    private fun systemFeedback(detent: Detent): Int = when (detent) {
        Detent.MINOR ->
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
            else HapticFeedbackConstants.TEXT_HANDLE_MOVE
        Detent.MID -> HapticFeedbackConstants.CLOCK_TICK
        Detent.MAJOR ->
            if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_TICK
            else HapticFeedbackConstants.CLOCK_TICK
        Detent.CARDINAL -> HapticFeedbackConstants.VIRTUAL_KEY
        Detent.NORTH -> HapticFeedbackConstants.CONFIRM
    }

    override fun play(detent: Detent, strength: HapticStrength) {
        executor.execute {
            val effect = effects.getValue(strength).getValue(detent)
            if (effect != null) {
                vibrate(effect)
            } else {
                // Views belong to the main thread: this avoids racing the window's detach.
                viewHolder.view?.let { view ->
                    view.post { if (view.isAttachedToWindow) view.performHapticFeedback(systemFeedback(detent)) }
                }
            }
        }
    }

    override fun playLevelLock(strength: HapticStrength) = play(Detent.NORTH, strength)

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // USAGE_TOUCH honours the system's touch-feedback setting.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            vibrator.vibrate(effect)
        }
    }
}
