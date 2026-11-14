package com.personal.flowreader.plugin.sync

/**
 * Three-way merge of a position inside the same chapter. Used only when the plugin has
 * `locusSync` and [ProgressReconcile] already decided the chapter URLs match.
 *
 * Rules mirror [ProgressReconcile.decide]: no baseline → further position wins and nothing
 * prompts; one side moved → that side; both moved → conflict.
 */
object LocusReconcile {
    data class Point(
        val blockIndex: Int = 0,
        val charOffset: Int = 0,
        val anchorText: String = "",
        val fraction: Float = 0f,
        val positionAt: Long = 0L,
    ) {
        val hasProgress: Boolean
            get() = blockIndex > 0 || charOffset > 0 || anchorText.isNotEmpty() || fraction > 0f
    }

    sealed interface Action {
        data object NoOp : Action
        data class ApplyRemote(val remote: Point) : Action
        data class PushLocal(val local: Point) : Action
        data class Conflict(val local: Point, val remote: Point) : Action
    }

    fun decide(local: Point?, remote: Point?, baseline: Point?): Action {
        if (remote == null || !remote.hasProgress) return Action.NoOp
        if (local == null || !local.hasProgress) return Action.ApplyRemote(remote)
        if (same(local, remote)) return Action.NoOp
        if (baseline == null || !baseline.hasProgress) {
            return if (further(remote, local)) Action.ApplyRemote(remote) else Action.PushLocal(local)
        }
        val localMoved = !same(local, baseline)
        val remoteMoved = !same(remote, baseline)
        return when {
            localMoved && remoteMoved -> Action.Conflict(local, remote)
            remoteMoved -> Action.ApplyRemote(remote)
            else -> Action.PushLocal(local)
        }
    }

    fun same(a: Point, b: Point): Boolean =
        a.blockIndex == b.blockIndex && a.charOffset == b.charOffset &&
            (a.anchorText.isEmpty() || b.anchorText.isEmpty() || a.anchorText == b.anchorText)

    /** Prefer a higher fraction, then a later block, then a later character. */
    fun further(a: Point, b: Point): Boolean = when {
        a.fraction != b.fraction -> a.fraction > b.fraction
        a.blockIndex != b.blockIndex -> a.blockIndex > b.blockIndex
        else -> a.charOffset > b.charOffset
    }

    fun fromOp(op: SyncOp.Position): Point = Point(
        blockIndex = op.blockIndex,
        charOffset = op.charOffset,
        anchorText = op.anchorText,
        fraction = op.fraction,
        positionAt = op.positionAt,
    )
}
