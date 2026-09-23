package com.personal.flowreader.tts

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Gain-shaped crossfade curves for overlapping two PCM clips.
 * Default [EqualPower] keeps perceived loudness roughly constant (A²+B²≈1).
 */
object Crossfade {
    enum class Curve {
        EqualPower,
        Linear,
        SquareRoot,
        SCurve,
    }

    /** Gains for outgoing (A) and incoming (B) at [progress] in 0..1. */
    fun gains(progress: Float, curve: Curve = Curve.EqualPower): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        return when (curve) {
            Curve.EqualPower -> {
                val angle = p * (PI.toFloat() / 2f)
                cos(angle) to sin(angle)
            }
            Curve.Linear -> (1f - p) to p
            Curve.SquareRoot -> sqrt(1f - p) to sqrt(p)
            Curve.SCurve -> {
                val s = p * p * (3f - 2f * p)
                (1f - s) to s
            }
        }
    }

    /**
     * Clamp requested overlap so it never exceeds half of the shorter clip
     * (Flick-style short-source guard). [sampleCount] is interleaved PCM length.
     */
    fun clampOverlapMs(
        requestedMs: Int,
        currentSampleCount: Int,
        nextSampleCount: Int,
        sampleRate: Int,
        channels: Int,
    ): Int {
        if (requestedMs <= 0 || sampleRate <= 0 || channels <= 0) return 0
        val ch = channels.coerceAtLeast(1)
        val currentDurMs = (currentSampleCount / ch.toLong() * 1000L / sampleRate).toInt()
        val nextDurMs = (nextSampleCount / ch.toLong() * 1000L / sampleRate).toInt()
        val halfShorter = min(currentDurMs, nextDurMs) / 2
        if (halfShorter <= 0) return 0
        return requestedMs.coerceAtMost(halfShorter)
    }

    /** Mix one interleaved PCM sample pair with [curve] gains. */
    fun mixSample(a: Short, b: Short, progress: Float, curve: Curve = Curve.EqualPower): Short {
        val (ga, gb) = gains(progress, curve)
        val mixed = a.toFloat() * ga + b.toFloat() * gb
        return mixed
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}
