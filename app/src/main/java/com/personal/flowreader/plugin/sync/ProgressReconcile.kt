package com.personal.flowreader.plugin.sync

sealed interface ProgressAction {
    data object NoOp : ProgressAction
    data class ApplyRemote(val chapterUrl: String) : ProgressAction
    data class PushLocal(val chapterUrl: String) : ProgressAction
    data class Conflict(val localUrl: String, val remoteUrl: String) : ProgressAction
}

/** [baseline] is the chapter URL to store after the action (unchanged for NoOp and Conflict). */
data class ProgressDecision(val action: ProgressAction, val baseline: String?)

object ProgressReconcile {
    /**
     * ToC index of a site chapter URL: exact match, else the same URL without scheme, query or
     * trailing slash, else the same path with its last segment (a slug after a numeric id) dropped.
     */
    fun tocIndex(tocUrls: List<String>, url: String): Int? {
        tocUrls.indexOf(url).takeIf { it >= 0 }?.let { return it }
        val key = loose(url) ?: return null
        tocUrls.indexOfFirst { loose(it) == key }.takeIf { it >= 0 }?.let { return it }
        val stem = slugless(key) ?: return null
        return tocUrls.indexOfFirst { u -> loose(u)?.let(::slugless) == stem }.takeIf { it >= 0 }
    }

    private fun loose(url: String): String? {
        val m = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://([^/?#]+)([^?#]*)").find(url.trim()) ?: return null
        val host = m.groupValues[1].lowercase().removePrefix("www.")
        return host + m.groupValues[2].trimEnd('/')
    }

    private fun slugless(key: String): String? {
        val parts = key.split('/')
        if (parts.size < 3 || parts[parts.size - 2].toLongOrNull() == null) return null
        return parts.dropLast(1).joinToString("/")
    }

    /**
     * Three-way merge of one story's reading chapter. [indexOf] maps a chapter URL to its ToC index,
     * so slug changes compare equal. Without a [baseline] (first sync) the further chapter wins and
     * nothing prompts. A remote chapter missing from the ToC waits for the next ToC refresh.
     */
    fun decide(
        local: String?,
        remote: String?,
        baseline: String?,
        indexOf: (String) -> Int?,
    ): ProgressDecision {
        if (remote == null) return ProgressDecision(ProgressAction.NoOp, baseline)
        val remoteIndex = indexOf(remote) ?: return ProgressDecision(ProgressAction.NoOp, baseline)
        if (local == null) return ProgressDecision(ProgressAction.ApplyRemote(remote), remote)
        fun same(a: String, b: String): Boolean {
            val ia = indexOf(a)
            val ib = indexOf(b)
            return if (ia != null && ib != null) ia == ib else a == b
        }
        if (same(local, remote)) return ProgressDecision(ProgressAction.NoOp, local)
        if (baseline == null) {
            val localIndex = indexOf(local) ?: -1
            return if (remoteIndex > localIndex) {
                ProgressDecision(ProgressAction.ApplyRemote(remote), remote)
            } else {
                ProgressDecision(ProgressAction.PushLocal(local), local)
            }
        }
        val localMoved = !same(local, baseline)
        val remoteMoved = !same(remote, baseline)
        return when {
            localMoved && remoteMoved -> ProgressDecision(ProgressAction.Conflict(local, remote), baseline)
            remoteMoved -> ProgressDecision(ProgressAction.ApplyRemote(remote), remote)
            else -> ProgressDecision(ProgressAction.PushLocal(local), local)
        }
    }
}
