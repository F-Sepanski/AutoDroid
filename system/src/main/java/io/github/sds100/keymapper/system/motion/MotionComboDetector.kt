package io.github.sds100.keymapper.system.motion

import javax.inject.Inject
import kotlin.math.abs

interface MotionComboDetector {
    /**
     * Processa aceleração linear (sem gravidade ou via filtro passa-alta).
     * Retorna MotionDirection detectada caso um pico acima do limiar seja atingido, com debounce temporal.
     */
    fun processSample(x: Float, y: Float, z: Float, timestampMs: Long): MotionDirection?

    /**
     * Adiciona um gesto à fila do combo e verifica se completou alguma sequência cadastrada.
     * Retorna o MotionCombo disparado ou null se nenhum atingiu a correspondência completa.
     */
    fun feedGesture(gesture: MotionGesture, activeCombos: List<MotionCombo>): MotionCombo?

    /**
     * Reseta o histórico de gestos acumulados.
     */
    fun reset()

    companion object {
        const val DEFAULT_THRESHOLD = 3.5f
        const val DEFAULT_DEBOUNCE_MS = 250L
        const val DEFAULT_SHAKE_WINDOW_MS = 150L

        operator fun invoke(
            threshold: Float = DEFAULT_THRESHOLD,
            debounceMs: Long = DEFAULT_DEBOUNCE_MS,
            shakeWindowMs: Long = DEFAULT_SHAKE_WINDOW_MS,
        ): MotionComboDetector = MotionComboDetectorImpl(threshold, debounceMs, shakeWindowMs)
    }
}

class MotionComboDetectorImpl(
    private val threshold: Float = MotionComboDetector.DEFAULT_THRESHOLD,
    private val debounceMs: Long = MotionComboDetector.DEFAULT_DEBOUNCE_MS,
    private val shakeWindowMs: Long = MotionComboDetector.DEFAULT_SHAKE_WINDOW_MS,
) : MotionComboDetector {

    @Inject
    constructor() : this(
        threshold = MotionComboDetector.DEFAULT_THRESHOLD,
        debounceMs = MotionComboDetector.DEFAULT_DEBOUNCE_MS,
        shakeWindowMs = MotionComboDetector.DEFAULT_SHAKE_WINDOW_MS,
    )

    private enum class Axis { X, Y, Z }

    private var lastPeakAxis: Axis? = null
    private var lastPeakSign: Int = 0
    private var lastPeakTimestampMs: Long = 0L
    private var lastGestureTimestampMs: Long = 0L

    private val gestureHistory = mutableListOf<MotionGesture>()

    override fun processSample(x: Float, y: Float, z: Float, timestampMs: Long): MotionDirection? {
        val absX = abs(x)
        val absY = abs(y)
        val absZ = abs(z)
        val maxVal = maxOf(absX, absY, absZ)

        if (maxVal < threshold) {
            return null
        }

        val (axis, sign, direction) = when {
            absY >= absX && absY >= absZ -> {
                val s = if (y > 0) 1 else -1
                val dir = if (s > 0) MotionDirection.UP else MotionDirection.DOWN
                Triple(Axis.Y, s, dir)
            }
            absX >= absY && absX >= absZ -> {
                val s = if (x > 0) 1 else -1
                val dir = if (s > 0) MotionDirection.RIGHT else MotionDirection.LEFT
                Triple(Axis.X, s, dir)
            }
            else -> {
                val s = if (z > 0) 1 else -1
                val dir = if (s > 0) MotionDirection.FORWARD else MotionDirection.BACKWARD
                Triple(Axis.Z, s, dir)
            }
        }

        val dtPeak = timestampMs - lastPeakTimestampMs
        val isShake = lastPeakAxis == axis && sign != lastPeakSign && dtPeak < shakeWindowMs

        if (isShake) {
            lastPeakAxis = axis
            lastPeakSign = sign
            lastPeakTimestampMs = timestampMs
            lastGestureTimestampMs = timestampMs
            return MotionDirection.SHAKE
        }

        val dtGesture = timestampMs - lastGestureTimestampMs
        if (dtGesture < debounceMs) {
            return null
        }

        lastPeakAxis = axis
        lastPeakSign = sign
        lastPeakTimestampMs = timestampMs
        lastGestureTimestampMs = timestampMs
        return direction
    }

    override fun feedGesture(
        gesture: MotionGesture,
        activeCombos: List<MotionCombo>,
    ): MotionCombo? {
        if (gesture.direction == MotionDirection.SHAKE && gestureHistory.isNotEmpty()) {
            val last = gestureHistory.last()
            if (gesture.timestampMs - last.timestampMs < shakeWindowMs &&
                last.direction != MotionDirection.SHAKE
            ) {
                gestureHistory.removeAt(gestureHistory.lastIndex)
            }
        }
        gestureHistory.add(gesture)

        val maxWindow = activeCombos.maxOfOrNull { it.windowTimeoutMs } ?: 5000L
        val cutoff = gesture.timestampMs - maxWindow
        gestureHistory.removeAll { it.timestampMs < cutoff }

        if (gestureHistory.size > 50) {
            gestureHistory.removeAt(0)
        }

        val sortedCombos = activeCombos.sortedByDescending { it.sequence.size }
        for (combo in sortedCombos) {
            val seq = combo.sequence
            val n = seq.size
            if (n == 0 || gestureHistory.size < n) continue

            val candidates = gestureHistory.takeLast(n)
            val matchesSequence = candidates.map { it.direction } == seq
            if (matchesSequence) {
                val duration = candidates.last().timestampMs - candidates.first().timestampMs
                if (duration <= combo.windowTimeoutMs) {
                    repeat(n) {
                        gestureHistory.removeAt(gestureHistory.lastIndex)
                    }
                    return combo
                }
            }
        }

        return null
    }

    override fun reset() {
        gestureHistory.clear()
        lastPeakAxis = null
        lastPeakSign = 0
        lastPeakTimestampMs = 0L
        lastGestureTimestampMs = 0L
    }
}
