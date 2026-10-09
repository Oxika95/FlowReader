package com.personal.flowreader

import com.personal.flowreader.data.PositionDomain
import com.personal.flowreader.data.PositionStore
import com.personal.flowreader.data.ProgressUpdate
import com.personal.flowreader.data.QueueStateEntity

/** Writes each position into the one table its session names: Library, Queue, or the plugin's database. */
internal class AppPositionStore(private val app: FlowApp) : PositionStore {
    override suspend fun write(update: ProgressUpdate): Boolean {
        val p = update.toPosition()
        return when (update.domain) {
            PositionDomain.Library -> app.db.library().writePosition(
                update.rowKey, p.chapterIndex, p.chapterHref, p.blockIndex, p.charOffset, p.anchorText,
                p.fraction, p.positionAt, p.positionSessionAt, p.positionSource,
            ) > 0
            PositionDomain.Queue -> {
                val written = app.db.queue().writePosition(
                    update.rowKey, p.chapterIndex, p.chapterHref, p.blockIndex, p.charOffset, p.anchorText,
                    p.fraction, p.positionAt, p.positionSessionAt, p.positionSource,
                ) > 0
                if (written) app.db.queue().setState(QueueStateEntity(currentQueId = update.rowKey, updatedAt = update.at))
                written
            }
            PositionDomain.Plugin -> app.pluginCatalog.writePosition(update)
        }
    }
}
