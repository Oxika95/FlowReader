package com.personal.flowreader.ui.library

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.flowreader.plugin.InstalledPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plugin id → packaged `icon.png`, for plugins that ship one. */
@Composable
internal fun rememberPluginTabIcons(plugins: List<InstalledPlugin>): Map<String, ImageBitmap> {
    val icons = mutableMapOf<String, ImageBitmap>()
    plugins.forEach { plugin ->
        val file = plugin.iconFile
        key(plugin.id, file?.lastModified()) {
            val icon by produceState<ImageBitmap?>(null) {
                value = withContext(Dispatchers.IO) {
                    file?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() }
                }
            }
            icon?.let { icons[plugin.id] = it }
        }
    }
    return icons
}
