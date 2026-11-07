package com.personal.flowreader.ui.debug

/**
 * Destinations that scripts/smoke.ps1 can open through the `flow_debug_route` intent extra
 * (debug builds only). Only names listed here parse; anything else is ignored.
 */
sealed interface DebugRoute {
    data object Library : DebugRoute
    data object FirstBook : DebugRoute
    data class Settings(val tab: Int) : DebugRoute
    data class Plugin(val id: String) : DebugRoute

    companion object {
        const val EXTRA = "flow_debug_route"

        /** Settings tab names in the order of the Settings overlay's tab bar. */
        val SettingsTabs = listOf("about", "audio", "filters", "import", "layout", "plugins")

        private val PluginId = Regex("[A-Za-z0-9_-]{1,64}")

        fun parse(raw: String?): DebugRoute? {
            val name = raw?.trim().orEmpty()
            return when {
                name == "library" -> Library
                name == "reader/first" -> FirstBook
                name.startsWith("settings/") -> {
                    val index = SettingsTabs.indexOf(name.removePrefix("settings/"))
                    if (index >= 0) Settings(index) else null
                }
                name.startsWith("plugin/") -> {
                    val id = name.removePrefix("plugin/")
                    if (PluginId.matches(id)) Plugin(id) else null
                }
                else -> null
            }
        }
    }
}
