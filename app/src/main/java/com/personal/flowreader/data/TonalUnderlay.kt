package com.personal.flowreader.data

/** When the tonal underlay sounds during playback. */
enum class UnderlayMode(val key: String, val label: String) {
    Off("off", "Off"),
    Device("device", "Auto"),
    Always("always", "On"),
    ;

    companion object {
        fun fromKey(key: String?): UnderlayMode? = entries.firstOrNull { it.key == key }

        /** Mode for prefs written before `tts_underlay_mode`, where level `0` meant Off. */
        fun legacy(storedLevel: Float?, btAddress: String): UnderlayMode = when {
            storedLevel == null || storedLevel >= 0f -> Off
            btAddress.isBlank() -> Always
            else -> Device
        }
    }
}

object TonalUnderlay {
    const val DEFAULT_LEVEL = -60f

    /** Audible levels only; silence is expressed by [UnderlayMode.Off]. */
    val LEVEL_STEPS: FloatArray = TtsPrefs.TONAL_UNDERLAY_STEPS.filter { it < 0f }.toFloatArray()

    fun coerceLevel(level: Float): Float {
        var best = LEVEL_STEPS[0]
        var bestDist = Float.MAX_VALUE
        for (step in LEVEL_STEPS) {
            val dist = kotlin.math.abs(step - level)
            if (dist < bestDist) {
                best = step
                bestDist = dist
            }
        }
        return best
    }

    fun levelIndex(level: Float): Int = LEVEL_STEPS.indexOfFirst { it == coerceLevel(level) }

    /** Stored level, or [DEFAULT_LEVEL] when missing or the legacy Off value. */
    fun storedLevel(raw: Float?): Float =
        if (raw == null || raw >= 0f) DEFAULT_LEVEL else coerceLevel(raw)

    /** Level fed to the keep-alive oscillator; `0` = silent. */
    fun effectiveLevel(
        mode: UnderlayMode,
        level: Float,
        btAddress: String,
        targetActive: Boolean,
    ): Float = when (mode) {
        UnderlayMode.Off -> 0f
        UnderlayMode.Always -> coerceLevel(level)
        UnderlayMode.Device ->
            if (btAddress.isNotBlank() && targetActive) coerceLevel(level) else 0f
    }
}
