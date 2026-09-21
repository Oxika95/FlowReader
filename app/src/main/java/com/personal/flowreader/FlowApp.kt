package com.personal.flowreader

import android.app.Application
import androidx.room.Room
import com.personal.flowreader.data.AppDatabase
import com.personal.flowreader.data.BookCatalog
import com.personal.flowreader.data.MIGRATION_1_2
import com.personal.flowreader.data.MIGRATION_2_3
import com.personal.flowreader.data.MIGRATION_3_4
import com.personal.flowreader.data.MIGRATION_4_5
import com.personal.flowreader.data.MIGRATION_5_6
import com.personal.flowreader.data.SettingsStore
import com.personal.flowreader.library.plugin.LibraryPluginRegistry
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadPlugin
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadRepository
import com.personal.flowreader.tts.TtsController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FlowApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var tts: TtsController
        private set
    lateinit var booksDir: File
        private set
    lateinit var catalog: BookCatalog
        private set
    lateinit var plugins: LibraryPluginRegistry
        private set
    lateinit var royalRoad: RoyalRoadRepository
        private set

    /** Survives ViewModel clear so progress can still flush to Room. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "flow.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()
        settings = SettingsStore(this)
        tts = TtsController(this, settings)
        booksDir = File(filesDir, "books").apply { mkdirs() }
        catalog = BookCatalog(this)
        royalRoad = RoyalRoadRepository(this)
        plugins = LibraryPluginRegistry(listOf(RoyalRoadPlugin()))
        appScope.launch { sweepStaleCache() }
    }

    /** Drop crashed import temps and leftover filter preview clips. */
    private fun sweepStaleCache() {
        val cache = cacheDir
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        cache.listFiles()?.forEach { file ->
            val name = file.name
            val staleImport = name.startsWith("import-") && file.lastModified() < cutoff
            val preview = name == "filter_preview.mp3"
            if (staleImport || preview) {
                runCatching { file.deleteRecursively() }
            }
        }
    }
}
