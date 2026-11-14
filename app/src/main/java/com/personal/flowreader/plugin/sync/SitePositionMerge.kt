package com.personal.flowreader.plugin.sync

import com.personal.flowreader.data.ReadingPosition
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginReadPosition
import com.personal.flowreader.plugin.store.PluginReadSession
import com.personal.flowreader.plugin.store.PluginSessionStore
import java.io.File

/** Chapter (+ optional locus) merge used by [TwoWaySync.pullPositions]. */
internal object SitePositionMerge {
    suspend fun merge(
        dir: File,
        manifest: PluginManifest?,
        session: PluginReadSession,
        remote: PluginReadPosition,
        localPos: ReadingPosition?,
        overwrite: Boolean,
        applyRemote: suspend (index: Int, remote: PluginReadPosition) -> Unit,
    ): ProgressAction {
        val workId = session.workId
        val urls = session.toc.map { it.url }
        val localUrl = localPos
            ?.let { urls[PluginSessionStore.savedChapter(session.toc, it.chapterHref, it.chapterIndex)] }
        val remoteUrl = remote.chapterUrl
        val indexOf = { url: String -> ProgressReconcile.tocIndex(urls, url) }
        val baselineRaw = SyncStateStore.read(dir).positions[workId]
        val decision = if (overwrite) {
            val action = if (localUrl != null && indexOf(localUrl) == indexOf(remoteUrl)) {
                ProgressAction.NoOp
            } else {
                ProgressAction.ApplyRemote(remoteUrl)
            }
            SyncStateStore.update(dir) { s ->
                s.copy(outbox = s.outbox.filterNot { it is SyncOp.Position && it.workId == workId })
            }
            ProgressDecision(action, remoteUrl)
        } else {
            ProgressReconcile.decide(
                local = localUrl,
                remote = remoteUrl,
                baseline = PositionBaseline.url(baselineRaw),
                indexOf = indexOf,
            )
        }
        val locusSync = manifest?.has(PluginCapability.LocusSync) == true
        when (val action = decision.action) {
            is ProgressAction.Conflict -> SyncStateStore.update(dir) {
                it.copy(
                    conflicts = it.conflicts + (workId to PositionConflict(
                        workId,
                        action.localUrl,
                        action.remoteUrl,
                        localLabel = LocusPositionMerge.conflictLabel(
                            session,
                            action.localUrl,
                            localPos?.blockIndex ?: 0,
                            localPos?.charOffset ?: 0,
                        ),
                        remoteLabel = LocusPositionMerge.conflictLabel(
                            session,
                            action.remoteUrl,
                            remote.blockIndex,
                            remote.charOffset,
                        ),
                    )),
                )
            }
            is ProgressAction.ApplyRemote -> {
                SyncStateStore.update(dir) { s ->
                    s.copy(outbox = s.outbox.filterNot { it is SyncOp.Position && it.workId == workId })
                }
                LocusPositionMerge.saveBaseline(dir, workId, remote)
                indexOf(action.chapterUrl)?.let { applyRemote(it, remote) }
            }
            is ProgressAction.PushLocal -> {
                val title = session.toc.getOrNull(urls.indexOf(action.chapterUrl))?.title.orEmpty()
                LocusPositionMerge.enqueueLocal(dir, workId, action.chapterUrl, title, localPos)
            }
            ProgressAction.NoOp -> {
                if (locusSync && localUrl != null && indexOf(localUrl) == indexOf(remoteUrl)) {
                    LocusPositionMerge.merge(dir, session, remote, localPos, baselineRaw, overwrite) { pos ->
                        val index = ProgressReconcile.tocIndex(session.toc.map { it.url }, pos.chapterUrl)
                            ?: return@merge
                        applyRemote(index, pos)
                    }
                } else if (decision.baseline != null) {
                    LocusPositionMerge.saveBaselineUrl(dir, workId, decision.baseline)
                }
            }
        }
        return decision.action
    }
}
