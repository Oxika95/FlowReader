package com.personal.flowreader.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How tab bars draw their destinations. */
enum class TabStyle(val label: String) {
    Icons("Icons"),
    Text("Text"),
}

/** Library and Settings chrome: book layout and tab style, observed live by both screens. */
data class ChromePrefs(
    val libraryView: LibraryViewMode = LibraryViewMode.List,
    val libraryTabs: TabStyle = TabStyle.Icons,
    val settingsTabs: TabStyle = TabStyle.Icons,
)

private val KEY_LIBRARY_VIEW = stringPreferencesKey("library_view")
private val KEY_LIBRARY_TAB_STYLE = stringPreferencesKey("library_tab_style")
private val KEY_SETTINGS_TAB_STYLE = stringPreferencesKey("settings_tab_style")

val SettingsStore.chrome: Flow<ChromePrefs>
    get() = store.data.map { p ->
        ChromePrefs(
            libraryView = enumOr(p[KEY_LIBRARY_VIEW], LibraryViewMode.List),
            libraryTabs = enumOr(p[KEY_LIBRARY_TAB_STYLE], TabStyle.Icons),
            settingsTabs = enumOr(p[KEY_SETTINGS_TAB_STYLE], TabStyle.Icons),
        )
    }.distinctUntilChanged()

suspend fun SettingsStore.setLibraryViewMode(mode: LibraryViewMode) {
    store.edit { it[KEY_LIBRARY_VIEW] = mode.name }
}

suspend fun SettingsStore.setLibraryTabStyle(style: TabStyle) {
    store.edit { it[KEY_LIBRARY_TAB_STYLE] = style.name }
}

suspend fun SettingsStore.setSettingsTabStyle(style: TabStyle) {
    store.edit { it[KEY_SETTINGS_TAB_STYLE] = style.name }
}

private inline fun <reified E : Enum<E>> enumOr(raw: String?, default: E): E =
    enumValues<E>().firstOrNull { it.name == raw } ?: default
