package ru.spgsroot.vibeplayer.domain.dsp

import android.os.SystemClock
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticInputRouter @Inject constructor(
    private val hapticMapper: HapticMapper
) {
    private val lock = Any()
    private var activeSource: HapticSource? = null
    private var lastBlockedLogMs = 0L

    fun acquire(source: HapticSource): Boolean {
        var previousSource: HapticSource? = null
        var shouldReset = false
        val acquired = synchronized(lock) {
            previousSource = activeSource
            when {
                activeSource == null || activeSource == source -> {
                    activeSource = source
                    true
                }
                canAcquireOverActiveSource(requested = source, active = activeSource) -> {
                    activeSource = source
                    shouldReset = true
                    true
                }
                else -> false
            }
        }

        if (shouldReset) {
            hapticMapper.reset()
        }
        if (acquired && previousSource != source) {
            Log.d(TAG, "Haptic source acquired: $source previous=$previousSource")
        } else if (!acquired) {
            logBlocked("acquire", source, previousSource)
        }
        return acquired
    }

    fun emit(source: HapticSource, rawAmplitude: Float): Boolean {
        var currentSource: HapticSource? = null
        var shouldReset = false
        val shouldEmit = synchronized(lock) {
            if (activeSource == null) {
                activeSource = source
                currentSource = source
                true
            } else if (source == HapticSource.PLAYER && activeSource == HapticSource.WEBVIEW) {
                currentSource = activeSource
                activeSource = source
                shouldReset = true
                true
            } else {
                currentSource = activeSource
                activeSource == source
            }
        }
        if (shouldEmit) {
            if (shouldReset) {
                Log.d(TAG, "Haptic source switched by emit: $source previous=$currentSource")
                hapticMapper.reset()
            }
            hapticMapper.emitNonBlocking(normalizeAmplitude(source, rawAmplitude))
        } else {
            logBlocked("emit", source, currentSource)
        }
        return shouldEmit
    }

    fun release(source: HapticSource) {
        val shouldReset = synchronized(lock) {
            if (activeSource == source) {
                activeSource = null
                true
            } else {
                false
            }
        }
        if (shouldReset) {
            Log.d(TAG, "Haptic source released: $source")
            hapticMapper.reset()
        }
    }

    private fun logBlocked(action: String, source: HapticSource, active: HapticSource?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockedLogMs >= LOG_INTERVAL_MS) {
            Log.w(TAG, "Haptic source $action blocked: requested=$source active=$active")
            lastBlockedLogMs = now
        }
    }

    private fun canAcquireOverActiveSource(requested: HapticSource, active: HapticSource?): Boolean {
        return (requested == HapticSource.WEBVIEW && active == HapticSource.PLAYER) ||
            (requested == HapticSource.PLAYER && active == HapticSource.WEBVIEW)
    }

    private fun normalizeAmplitude(source: HapticSource, rawAmplitude: Float): Float {
        if (!rawAmplitude.isFinite()) return 0f
        val clamped = rawAmplitude.coerceIn(0f, 1f)
        return when (source) {
            HapticSource.PLAYER -> clamped
            HapticSource.WEBVIEW -> ((clamped - WEBVIEW_NOISE_FLOOR).coerceAtLeast(0f) * WEBVIEW_GAIN)
                .coerceIn(0f, 1f)
        }
    }

    companion object {
        private const val TAG = "HapticInputRouter"
        private const val LOG_INTERVAL_MS = 1_000L
        private const val WEBVIEW_NOISE_FLOOR = 0.004f
        private const val WEBVIEW_GAIN = 20f
    }
}
