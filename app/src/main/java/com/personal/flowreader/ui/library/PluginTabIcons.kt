package com.personal.flowreader.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.flowreader.data.ScaledBitmap
import com.personal.flowreader.plugin.InstalledPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shipped icons are 192 px; the cap only guards against an oversized `icon.png` from a third-party plugin. */
private const val TAB_ICON_MAX_EDGE = 256

/** Plugin id → packaged `icon.png`, for plugins that ship one. */
@Composable
internal fun rememberPluginTabIcons(plugins: List<InstalledPlugin>): Map<String, ImageBitmap> {
    val icons = mutableMapOf<String, ImageBitmap>()
    plugins.forEach { plugin ->
        val file = plugin.iconFile
        key(plugin.id, file?.lastModified()) {
            val icon by produceState<ImageBitmap?>(null) {
                value = withContext(Dispatchers.IO) {
                    file?.let { ScaledBitmap.decodeFile(it, TAB_ICON_MAX_EDGE)?.asImageBitmap() }
                }
            }
            icon?.let { icons[plugin.id] = it }
        }
    }
    return icons
}
