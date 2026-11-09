package com.densmac.compass.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.view.Display
import com.densmac.compass.domain.GravitySample
import com.densmac.compass.domain.OrientationSample
import com.densmac.compass.domain.pointingBearing
import com.densmac.compass.domain.screenX
import com.densmac.compass.domain.screenY
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

@Singleton
class SensorOrientationRepository @Inject constructor(
    @ApplicationContext context: Context,
) : OrientationRepository {

    private val sensors = context.getSystemService(SensorManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)

    private val handler: Handler by lazy {
        Handler(HandlerThread("compass-sensors").apply { start() }.looper)
    }

    override val hasCompass: Boolean
        get() = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null && listOf(
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
            Sensor.TYPE_ACCELEROMETER,
        ).any { sensors.getDefaultSensor(it) != null }

    /** Tracks Display.rotation off the main thread for as long as [block]'s flow is collected. */
    private inner class RotationTracker : DisplayManager.DisplayListener {
        @Volatile var rotation = currentRotation()
        fun start() = displays.registerDisplayListener(this, handler)
        fun stop() = displays.unregisterDisplayListener(this)
        private fun currentRotation() = displays.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: 0
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) rotation = currentRotation()
        }
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun samples(): Flow<OrientationSample> = callbackFlow {
        val magnetometer = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val rotationVector = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensors.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelerometer = if (rotationVector == null) sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val rotation = RotationTracker()

        val listener = object : SensorEventListener {
            private val matrix = FloatArray(9)
            private val gravity = FloatArray(3)
            private val field = FloatArray(3)
            private var haveGravity = false
            private var haveField = false
            private var accuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
            private var fieldStrength = 0f
            private var lastBearing = Float.NaN

            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val v = event.values
                        v.copyInto(field, endIndex = 3)
                        haveField = true
                        fieldStrength = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                        accuracy = event.accuracy
                        if (rotationVector == null) emitFromGravityAndField(event.timestamp)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = 3)
                        haveGravity = true
                        emitFromGravityAndField(event.timestamp)
                    }
                    else -> {
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        emit(event.timestamp)
                    }
                }
            }

            private fun emitFromGravityAndField(timestamp: Long) {
                if (haveGravity && haveField && SensorManager.getRotationMatrix(matrix, null, gravity, field)) {
                    emit(timestamp)
                }
            }

            private fun emit(timestamp: Long) {
                val rot = rotation.rotation
                val bearing = pointingBearing(matrix, rot)
                if (!bearing.isNaN()) lastBearing = bearing
                trySend(
                    OrientationSample(
                        bearing = if (bearing.isNaN()) Float.NaN else lastBearing,
                        accuracy = accuracy,
                        fieldMicroTesla = fieldStrength,
                        timestampNs = timestamp,
                    ),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, newAccuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD) accuracy = newAccuracy
            }
        }

        rotation.start()
        rotationVector?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        accelerometer?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        magnetometer?.let {
            val rate = if (rotationVector == null) SensorManager.SENSOR_DELAY_GAME else SensorManager.SENSOR_DELAY_UI
            sensors.registerListener(listener, it, rate, handler)
        }
        awaitClose {
            sensors.unregisterListener(listener)
            rotation.stop()
        }
    }.conflate()

    override fun gravity(): Flow<GravitySample> = callbackFlow {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val rotation = RotationTracker()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val norm = sqrt(x * x + y * y + z * z)
                if (norm < 1e-3f) return
                val rot = rotation.rotation
                trySend(
                    GravitySample(
                        x = screenX(x / norm, y / norm, rot),
                        y = screenY(x / norm, y / norm, rot),
                        z = z / norm,
                        timestampNs = event.timestamp,
                    ),
                )
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        rotation.start()
        sensor?.let { sensors.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler) }
        awaitClose {
            sensors.unregisterListener(listener)
            rotation.stop()
        }
    }.conflate()
}
