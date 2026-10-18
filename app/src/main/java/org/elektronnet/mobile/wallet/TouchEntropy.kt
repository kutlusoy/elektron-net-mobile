package org.elektronnet.mobile.wallet

import java.nio.ByteBuffer
import java.security.MessageDigest

class TouchEntropy {
    private val pool = MessageDigest.getInstance("SHA-512")
    private var lastX = Float.NaN
    private var lastY = Float.NaN
    private var lastNanos = 0L
    private var lastStep: Pair<Float, Float>? = null
    private var straightRun = 0
    private var events = 0
    private var credit = 0.0

    val acceptedEvents: Int get() = events

    val estimatedBits: Double get() = credit

    val progress: Float get() = (credit / TARGET_BITS).coerceIn(0.0, 1.0).toFloat()

    val complete: Boolean get() = credit >= TARGET_BITS

    init {
        pool.update(DOMAIN)
    }

    fun add(x: Float, y: Float, nanos: Long, pressure: Float = 0f) {
        if (complete) return
        val buffer = ByteBuffer.allocate(24)
        buffer.putFloat(x).putFloat(y).putLong(nanos).putFloat(pressure)
        pool.update(buffer.array(), 0, buffer.position())
        if (lastX.isNaN()) {
            remember(x, y, nanos)
            return
        }
        val dx = x - lastX
        val dy = y - lastY
        val dt = nanos - lastNanos
        if (dt < MIN_INTERVAL_NANOS || Math.hypot(dx.toDouble(), dy.toDouble()) < MIN_STEP) return
        val step = dx to dy
        val previous = lastStep
        val repeated = previous != null && previous == step
        val straight = previous != null && isStraight(previous, step)
        straightRun = if (straight) straightRun + 1 else 0
        remember(x, y, nanos)
        lastStep = step
        events++
        if (!repeated && straightRun < MAX_STRAIGHT_RUN) credit += BITS_PER_EVENT
    }

    fun finish(): ByteArray {
        check(complete) { "touch entropy not complete" }
        val out = pool.digest()
        lastStep = null
        return out.copyOf(32)
    }

    private fun remember(x: Float, y: Float, nanos: Long) {
        lastX = x
        lastY = y
        lastNanos = nanos
    }

    private fun isStraight(a: Pair<Float, Float>, b: Pair<Float, Float>): Boolean {
        val cross = a.first * b.second - a.second * b.first
        val scale = Math.hypot(a.first.toDouble(), a.second.toDouble()) * Math.hypot(b.first.toDouble(), b.second.toDouble())
        return scale > 0 && Math.abs(cross) / scale < STRAIGHT_TOLERANCE
    }

    companion object {
        const val TARGET_BITS = 256.0
        const val BITS_PER_EVENT = 0.5
        const val MIN_STEP = 4.0
        const val MIN_INTERVAL_NANOS = 4_000_000L
        const val MAX_STRAIGHT_RUN = 12
        const val STRAIGHT_TOLERANCE = 0.02
        private val DOMAIN = "elektron-touch-entropy-v1".toByteArray()
    }
}
