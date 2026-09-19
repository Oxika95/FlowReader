package com.personal.flowreader

import android.app.Application
import androidx.room.Room
import com.personal.flowreader.data.AppDatabase
import com.personal.flowreader.data.SettingsStore
import com.personal.flowreader.tts.TtsController
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FlowApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var tts: TtsController
        private set
    lateinit var booksDir: File
        private set

    /** Survives ViewModel clear so progress can still flush to Room. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "flow.db").build()
        settings = SettingsStore(this)
        tts = TtsController(this, settings)
        booksDir = File(filesDir, "books").apply { mkdirs() }
    }
}
