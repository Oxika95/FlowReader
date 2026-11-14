package com.personal.flowreader.plugin.sync

import com.personal.flowreader.data.ReadingPosition
import com.personal.flowreader.plugin.api.PluginReadPosition
import com.personal.flowreader.plugin.store.PluginReadSession
import java.io.File

/** Locus-aware helpers for [TwoWaySync] when the plugin has `locusSync`. */
internal object LocusPositionMerge {
    fun conflictLabel(session: PluginReadSession, url: String, block: Int, char: Int): String {
        val title = session.toc.firstOrNull { it.url == url }?.title?.ifBlank { null } ?: "Chapter"
        return if (block > 0 || char > 0) "$title · §${block + 1}:$char" else title
    }

    fun pendingFrom(position: PluginReadPosition, overwrite: Boolean) = PendingPosition(
        position.id,
        position.chapterUrl,
        position.chapterTitle,
        overwrite,
        position.blockIndex,
        position.charOffset,
        position.anchorText,
        position.fraction,
        position.updatedAt,
    )

    fun saveBaselineUrl(dir: File, workId: String, url: String?) {
        SyncStateStore.update(dir) { s ->
            s.copy(
                positions = if (url == null) s.positions else s.positions + (workId to url),
                conflicts = s.conflicts - workId,
            )
        }
    }

    fun saveBaseline(dir: File, workId: String, remote: PluginReadPosition) {
        SyncStateStore.update(dir) { s ->
            s.copy(
                positions = s.positions + (workId to PositionBaseline.encode(
                    remote.chapterUrl,
                    remote.blockIndex,
                    remote.charOffset,
                    remote.anchorText,
                    remote.fraction,
                    remote.updatedAt,
                )),
                conflicts = s.conflicts - workId,
            )
        }
    }

    fun enqueueLocal(
        dir: File,
        workId: String,
        chapterUrl: String,
        title: String,
        localPos: ReadingPosition?,
    ) {
        SyncStateStore.update(dir) {
            it.enqueuePosition(
                SyncOp.Position(
                    workId,
                    chapterUrl,
                    title,
                    blockIndex = localPos?.blockIndex ?: 0,
                    charOffset = localPos?.charOffset ?: 0,
                    anchorText = localPos?.anchorText.orEmpty(),
                    fraction = localPos?.fraction ?: 0f,
                    positionAt = localPos?.positionAt ?: 0L,
                ),
            )
        }
    }

    /**
     * Same-chapter locus merge. [applyRemote] writes the remote locus; [pushLocal] enqueues the
     * local locus. Returns whether a remote apply ran.
     */
    suspend fun merge(
        dir: File,
        session: PluginReadSession,
        remote: PluginReadPosition,
        localPos: ReadingPosition?,
        baselineRaw: String?,
        overwrite: Boolean,
        applyRemote: suspend (PluginReadPosition) -> Unit,
    ) {
        val workId = session.workId
        val local = localPos?.let {
            LocusReconcile.Point(it.blockIndex, it.charOffset, it.anchorText, it.fraction, it.positionAt)
        }
        val remotePoint = LocusReconcile.Point(
            remote.blockIndex,
            remote.charOffset,
            remote.anchorText,
            remote.fraction,
            remote.updatedAt,
        )
        val baseline = PositionBaseline.locus(baselineRaw)?.let { LocusReconcile.fromOp(it) }
        val action = if (overwrite) {
            LocusReconcile.Action.ApplyRemote(remotePoint)
        } else {
            LocusReconcile.decide(local, remotePoint, baseline)
        }
        when (action) {
            is LocusReconcile.Action.NoOp -> saveBaseline(dir, workId, remote)
            is LocusReconcile.Action.ApplyRemote -> {
                SyncStateStore.update(dir) { s ->
                    s.copy(outbox = s.outbox.filterNot { it is SyncOp.Position && it.workId == workId })
                }
                saveBaseline(dir, workId, remote)
                applyRemote(remote)
            }
            is LocusReconcile.Action.PushLocal -> {
                val title = session.toc.getOrNull(
                    ProgressReconcile.tocIndex(session.toc.map { it.url }, remote.chapterUrl) ?: return,
                )?.title.orEmpty()
                SyncStateStore.update(dir) {
                    it.enqueuePosition(
                        SyncOp.Position(
                            workId,
                            remote.chapterUrl,
                            title,
                            blockIndex = action.local.blockIndex,
                            charOffset = action.local.charOffset,
                            anchorText = action.local.anchorText,
                            fraction = action.local.fraction,
                            positionAt = action.local.positionAt,
                        ),
                    )
                }
            }
            is LocusReconcile.Action.Conflict -> SyncStateStore.update(dir) {
                it.copy(
                    conflicts = it.conflicts + (workId to PositionConflict(
                        workId,
                        remote.chapterUrl,
                        remote.chapterUrl,
                        localLabel = conflictLabel(session, remote.chapterUrl, action.local.blockIndex, action.local.charOffset),
                        remoteLabel = conflictLabel(session, remote.chapterUrl, action.remote.blockIndex, action.remote.charOffset),
                    )),
                )
            }
        }
    }
}
