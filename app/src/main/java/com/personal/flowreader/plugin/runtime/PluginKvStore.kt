package com.personal.flowreader.plugin.runtime

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.flow.first

/** `flow.storage`: one Preferences DataStore per plugin (DataStore allows one instance per file). */
class PluginKvStore private constructor(private val store: DataStore<Preferences>) {
    suspend fun get(key: String): String? = store.data.first()[stringPreferencesKey(key)]

    suspend fun set(key: String, value: String) {
        store.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        store.edit { it.remove(stringPreferencesKey(key)) }
    }

    companion object {
        private val instances = HashMap<String, PluginKvStore>()

        fun forFile(file: File): PluginKvStore = synchronized(instances) {
            instances.getOrPut(file.absolutePath) {
                file.parentFile?.mkdirs()
                PluginKvStore(PreferenceDataStoreFactory.create(produceFile = { file }))
            }
        }
    }
}
