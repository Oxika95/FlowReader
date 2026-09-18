package com.personal.flowreader

import android.app.Application
import androidx.room.Room
import com.personal.flowreader.data.AppDatabase
import com.personal.flowreader.tts.TtsController
import java.io.File

class FlowApp : Application() {
    lateinit var db: AppDatabase
        private set
    lateinit var tts: TtsController
        private set
    lateinit var booksDir: File
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(this, AppDatabase::class.java, "flow.db").build()
        tts = TtsController(this)
        booksDir = File(filesDir, "books").apply { mkdirs() }
    }
}
