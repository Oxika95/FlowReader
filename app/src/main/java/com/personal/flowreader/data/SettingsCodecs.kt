package com.personal.flowreader.data

import org.json.JSONArray
import org.json.JSONObject

// Stored-value codecs for SettingsStore: custom library tabs (JSON) and per-tab sort modes (CSV pairs).

internal fun encodeCustomTabs(tabs: List<CustomLibraryTab>): String {
    val arr = JSONArray()
    tabs.sortedBy { it.order }.forEach { tab ->
        arr.put(
            JSONObject()
                .put("id", tab.id)
                .put("title", tab.title)
                .put("order", tab.order),
        )
    }
    return arr.toString()
}

internal fun decodeCustomTabs(raw: String?): List<CustomLibraryTab> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                val title = o.optString("title").trim()
                if (id.isEmpty() || title.isEmpty()) continue
                add(
                    CustomLibraryTab(
                        id = id,
                        title = title,
                        order = o.optInt("order", i),
                    ),
                )
            }
        }.sortedBy { it.order }
    }.getOrDefault(emptyList())
}

/** `tabKey=ModeName` pairs joined by commas. */
internal fun encodeLibrarySorts(map: Map<String, String>): String =
    map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

internal fun decodeLibrarySorts(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',')
        .mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = part.substring(0, i).trim()
            val value = part.substring(i + 1).trim()
            if (key.isEmpty() || value.isEmpty()) null else key to value
        }
        .toMap()
}
