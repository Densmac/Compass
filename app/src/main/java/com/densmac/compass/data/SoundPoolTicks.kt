package com.densmac.compass.data

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.densmac.compass.R
import com.densmac.compass.domain.Detent
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundPoolTicks @Inject constructor(
    @ApplicationContext context: Context,
) : SoundController {

    /** Loading reads resources and play() is a native call: both stay off the main thread. */
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "compass-sound") }
    private var pool: SoundPool? = null
    private var major = 0
    private var cardinal = 0
    private val loaded = ConcurrentHashMap.newKeySet<Int>()

    init {
        executor.execute {
            val attributes = AudioAttributes.Builder()
                // Sonification follows the system volume and goes quiet in silent mode.
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val created = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build()
            created.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded += id }
            major = created.load(context, R.raw.tick_major, 1)
            cardinal = created.load(context, R.raw.tick_cardinal, 1)
            pool = created
        }
    }

    override fun play(detent: Detent) {
        executor.execute {
            val sound = when (detent) {
                Detent.MAJOR -> major
                Detent.CARDINAL, Detent.NORTH -> cardinal
                Detent.MINOR, Detent.MID -> return@execute
            }
            if (sound in loaded) pool?.play(sound, 1f, 1f, 1, 0, 1f)
        }
    }
}
