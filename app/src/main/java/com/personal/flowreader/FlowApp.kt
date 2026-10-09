package com.personal.flowreader

import android.app.Application
import androidx.room.Room
import com.personal.flowreader.data.AppDatabase
import com.personal.flowreader.data.BookCatalog
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.ProgressWriter
import com.personal.flowreader.data.SettingsStore
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.PluginShareRequest
import com.personal.flowreader.plugin.repo.PluginInstaller
import com.personal.flowreader.plugin.repo.RepoManager
import com.personal.flowreader.plugin.store.PluginBookStore
import com.personal.flowreader.plugin.sync.TwoWaySync
import com.personal.flowreader.plugin.updates.UpdateScheduler
import com.personal.flowreader.share.RouterRules
import com.personal.flowreader.tts.TtsController
import com.personal.flowreader.ui.reader.QueuePlayback
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    lateinit var pluginManager: PluginManager
        private set
    lateinit var pluginBooks: PluginBookStore
        private set
    lateinit var pluginRepos: RepoManager
        private set
    lateinit var pluginInstaller: PluginInstaller
        private set
    lateinit var progress: ProgressWriter
        private set
    lateinit var pluginSync: TwoWaySync
        private set
    internal lateinit var queue: QueuePlayback
        private set

    /** Book a share opened for Auto Play on Share; the reader starts TTS once it loads this book. */
    @Volatile
    var pendingSharePlay: String? = null

    /** Survives ViewModel clear so progress can still flush to Room. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "flow.db")
            .fallbackToDestructiveMigration()
            .build()
        settings = SettingsStore(this)
        tts = TtsController(this, settings)
        booksDir = File(filesDir, "books").apply { mkdirs() }
        catalog = BookCatalog(this)
        pluginManager = PluginManager(this)
        pluginManager.initialize()
        pluginBooks = PluginBookStore(pluginManager, appScope) { settings.pluginCacheDefaultsOnce() }
        pluginSync = TwoWaySync(this)
        pluginBooks.positionChanged = pluginSync::localPositionChanged
        pluginBooks.tocStored = pluginSync::tocStored
        pluginRepos = RepoManager(settings)
        pluginInstaller = PluginInstaller(pluginManager, pluginRepos)
        progress = ProgressWriter(db.progress(), appScope) { update ->
            if (pluginBooks.isPluginBook(update.bookId)) {
                pluginBooks.scheduleMaintain(update.bookId, update.chapterIndex)
            }
        }
        persistSpokenPosition()
        queue = QueuePlayback(this).also { it.start() }
        appScope.launch {
            UpdateScheduler.apply(this@FlowApp, settings.pluginUpdatePrefsOnce())
            UpdateScheduler.applyVersionCheck(this@FlowApp, settings.pluginVersionCheckPrefsOnce())
        }
        appScope.launch { sweepStaleCache() }
        appScope.launch {
            pluginManager.installed.collect { list ->
                val current = settings.shareRouterRulesOnce()
                val seeded = settings.seededPluginShareRuleIdsOnce()
                val (next, nextSeeded) = RouterRules.withPluginHosts(current, list.map { it.manifest }, seeded)
                if (next != current) settings.setShareRouterRules(next)
                if (nextSeeded != seeded) settings.setSeededPluginShareRuleIds(nextSeeded)
            }
        }
    }

    /** Consumed by the plugin's library tab when the share router opens a URL it owns. */
    val pendingPluginShare = MutableStateFlow<PluginShareRequest?>(null)

    /** Activity-registered asker for POST_NOTIFICATIONS (API 33+); must be called on the main thread. */
    @Volatile
    var notificationPermissionAsker: ((onDone: () -> Unit) -> Unit)? = null

    /** Ask for notification permission after the user turned on new-chapter alerts. */
    fun requestNotificationPermission() {
        notificationPermissionAsker?.invoke {}
    }

    /** Playback keeps going with the reader closed; every spoken sentence is the new position. */
    private fun persistSpokenPosition() {
        appScope.launch {
            tts.state
                .map { s -> s.sentence?.takeIf { s.playing && s.bookId.isNotBlank() }?.let { s.bookId to it } }
                .distinctUntilChanged()
                .collect { spoken ->
                    val (bookId, sentence) = spoken ?: return@collect
                    val locus = Locus(sentence.chapterIndex, sentence.blockIndex, sentence.start)
                    progress.submit(progress.locate(bookId, locus, System.currentTimeMillis()))
                }
        }
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
