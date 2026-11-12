package io.github.sds100.keymapper.system.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    private val prefs by lazy {
        val appContext = context.applicationContext ?: context
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val savedThreshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)

    val savedNoisePadding: Float
        get() = prefs.getFloat(KEY_NOISE_PADDING, DEFAULT_NOISE_PADDING)

    private val vibrator: Vibrator? by lazy {
        val appContext = context.applicationContext ?: context
        appContext.getSystemService()
    }

    init {
        val initialThreshold = prefs.getFloat(KEY_THRESHOLD, DEFAULT_THRESHOLD)
        val initialNoisePadding = prefs.getFloat(KEY_NOISE_PADDING, DEFAULT_NOISE_PADDING)
        detector.setSensitivity(initialThreshold, initialNoisePadding)
    }

    private val sensorManager: SensorManager? by lazy {
        val appContext = context.applicationContext ?: context
        appContext.getSystemService()
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
                performHapticFeedback()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private fun performHapticFeedback() {
        try {
            val appContext = context.applicationContext ?: context
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = appContext.getSystemService(
                    Context.VIBRATOR_MANAGER_SERVICE,
                ) as? VibratorManager
                val vib = vibratorManager?.defaultVibrator ?: vibrator
                vib?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(50L)
            }
        } catch (_: Throwable) {
            // Safe fallback if vibrator is unavailable
        }
    }

    override fun setSensitivity(threshold: Float, noisePadding: Float) {
        detector.setSensitivity(threshold, noisePadding)
        prefs.edit()
            ?.putFloat(KEY_THRESHOLD, threshold)
            ?.putFloat(KEY_NOISE_PADDING, noisePadding)
            ?.apply()
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

    companion object {
        const val PREFS_NAME = "motion_adapter_preferences"
        const val KEY_THRESHOLD = "motion_threshold"
        const val KEY_NOISE_PADDING = "motion_noise_padding"
        const val DEFAULT_THRESHOLD = 5.0f
        const val DEFAULT_NOISE_PADDING = 0.5f
    }
}
