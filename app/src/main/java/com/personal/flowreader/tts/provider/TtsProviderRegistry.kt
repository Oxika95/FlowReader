package com.personal.flowreader.tts.provider

import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.plugin.PluginManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Discovers system + TTS-plugin engines. */
class TtsProviderRegistry(
    private val plugins: PluginManager,
) {
    private val mutex = Mutex()
    private var cache: Map<String, TtsProvider> = emptyMap()

    suspend fun refresh(systemEngines: List<TtsEngineOption>): List<TtsProvider> = mutex.withLock {
        val map = LinkedHashMap<String, TtsProvider>()
        map[TtsEngineIds.SYSTEM_DEFAULT] = SystemTtsProvider(
            TtsEngineIds.SYSTEM_DEFAULT,
            "Default (system TTS)",
        )
        for (p in plugins.ttsPlugins()) {
            val provider = PluginTtsProvider(plugins, p.manifest)
            map[provider.id] = provider
        }
        for (e in systemEngines) {
            if (e.key == TtsEngineIds.SYSTEM_DEFAULT || e.key == TtsEngineIds.LEGACY_EDGE) continue
            if (TtsEngineIds.isPlugin(e.key)) continue
            map.putIfAbsent(e.key, SystemTtsProvider(e.key, e.label))
        }
        cache = map
        map.values.toList()
    }

    fun get(id: String): TtsProvider? {
        cache[id]?.let { return it }
        // Legacy prefs: "edge" → installed Edge TTS plugin.
        if (id == TtsEngineIds.LEGACY_EDGE) {
            return cache[TtsEngineIds.plugin("edgetts")]
        }
        return null
    }

    fun engineOptions(): List<TtsEngineOption> =
        cache.values.map { TtsEngineOption(it.id, it.label) }
}
