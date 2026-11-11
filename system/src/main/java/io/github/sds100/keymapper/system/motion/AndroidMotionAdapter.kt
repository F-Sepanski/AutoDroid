package io.github.sds100.keymapper.system.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AndroidMotionAdapter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val detector: MotionComboDetector,
) : MotionAdapter {

    private val sensorManager: SensorManager? by lazy {
        context.applicationContext.getSystemService()
    }

    private val linearAccelerationSensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    }

    private val accelerometerSensor: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    private val _gestures = MutableSharedFlow<MotionGesture>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val gestures: SharedFlow<MotionGesture> = _gestures.asSharedFlow()

    private val _triggeredCombos = MutableSharedFlow<MotionCombo>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val triggeredCombos: SharedFlow<MotionCombo> = _triggeredCombos.asSharedFlow()

    private val _telemetry = MutableStateFlow(MotionTelemetry())
    override val telemetry: StateFlow<MotionTelemetry> = _telemetry.asStateFlow()

    private val registeredCombos = mutableListOf<MotionCombo>()
    private var listening: Boolean = false
    private var isSensorRegistered: Boolean = false
    private var isUsingLinearSensor: Boolean = false

    private val gravity = FloatArray(3)
    private var hasGravity = false
    private val alpha = 0.8f

    override val isListening: Boolean
        get() = synchronized(registeredCombos) { listening }

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null) return

            val (x, y, z) = if (isUsingLinearSensor) {
                Triple(event.values[0], event.values[1], event.values[2])
            } else {
                val filtered = applyHighPassFilter(event.values)
                Triple(filtered[0], filtered[1], filtered[2])
            }

            val timestampMs = System.currentTimeMillis()
            val magnitude = sqrt(x * x + y * y + z * z)
            val direction = detector.processSample(x, y, z, timestampMs)

            _telemetry.value = MotionTelemetry(
                x = x,
                y = y,
                z = z,
                magnitude = magnitude,
                lastDirection = direction ?: _telemetry.value.lastDirection,
                timestampMs = timestampMs,
            )

            if (direction == null) return
            val intensity = maxOf(abs(x), abs(y), abs(z))
            val gesture = MotionGesture(
                direction = direction,
                timestampMs = timestampMs,
                intensity = intensity,
            )
            _gestures.tryEmit(gesture)

            val combos = synchronized(registeredCombos) { registeredCombos.toList() }
            val triggered = detector.feedGesture(gesture, combos)
            if (triggered != null) {
                _triggeredCombos.tryEmit(triggered)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun setSensitivity(threshold: Float, noisePadding: Float) {
        detector.setSensitivity(threshold, noisePadding)
    }

    override fun registerCombos(combos: List<MotionCombo>) {
        synchronized(registeredCombos) {
            registeredCombos.clear()
            registeredCombos.addAll(combos)
            updateSensorStateLocked()
        }
    }

    override fun startListening() {
        synchronized(registeredCombos) {
            if (listening) return
            listening = true
            updateSensorStateLocked()
        }
    }

    override fun stopListening() {
        synchronized(registeredCombos) {
            if (!listening) return
            listening = false
            updateSensorStateLocked()
            detector.reset()
            _telemetry.value = MotionTelemetry()
        }
    }

    private fun updateSensorStateLocked() {
        val shouldListen = listening
        val manager = sensorManager ?: return

        if (shouldListen && !isSensorRegistered) {
            val sensor = linearAccelerationSensor ?: accelerometerSensor
            if (sensor != null) {
                isUsingLinearSensor = (sensor.type == Sensor.TYPE_LINEAR_ACCELERATION)
                hasGravity = false
                manager.registerListener(
                    sensorEventListener,
                    sensor,
                    SensorManager.SENSOR_DELAY_GAME,
                )
                isSensorRegistered = true
            }
        } else if (!shouldListen && isSensorRegistered) {
            manager.unregisterListener(sensorEventListener)
            isSensorRegistered = false
            hasGravity = false
        }
    }

    private fun applyHighPassFilter(values: FloatArray): FloatArray {
        if (!hasGravity) {
            gravity[0] = values[0]
            gravity[1] = values[1]
            gravity[2] = values[2]
            hasGravity = true
        } else {
            gravity[0] = alpha * gravity[0] + (1 - alpha) * values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * values[2]
        }
        return floatArrayOf(
            values[0] - gravity[0],
            values[1] - gravity[1],
            values[2] - gravity[2],
        )
    }
}
