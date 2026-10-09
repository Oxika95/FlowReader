package com.personal.flowreader.plugin

import com.personal.flowreader.plugin.api.PluginBrowsePage
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginCardActionResult
import com.personal.flowreader.plugin.api.PluginChapter
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginJson
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginPage
import com.personal.flowreader.plugin.api.PluginSession
import com.personal.flowreader.plugin.api.PluginUpdateInfo
import com.personal.flowreader.plugin.api.PluginUpdateQuery
import com.personal.flowreader.plugin.api.PluginWorkDetail
import com.personal.flowreader.plugin.runtime.JsPluginRuntime
import org.json.JSONArray
import org.json.JSONObject

/** Typed Kotlin facade over one plugin's JS contract. */
class PluginSource(
    val manifest: PluginManifest,
    private val runtime: JsPluginRuntime,
) {
    val id: String get() = manifest.id

    suspend fun search(query: String, page: Int = 1): PluginPage {
        require(PluginCapability.Search)
        return PluginJson.page(runtime.call("search", JSONArray().put(query).put(page)))
    }

    suspend fun list(listId: String, page: Int = 1): PluginPage {
        require(PluginCapability.Lists)
        return PluginJson.page(runtime.call("list", JSONArray().put(listId).put(page)))
    }

    /** Entry [id] of a browse list; blank [sort] lets the plugin pick its default. */
    suspend fun browse(id: String, page: Int = 1, sort: String = "", tab: String = ""): PluginBrowsePage {
        require(PluginCapability.Browse)
        return PluginJson.browsePage(runtime.call("browse", JSONArray().put(id).put(page).put(sort).put(tab)))
    }

    suspend fun loadWork(workId: String): PluginWorkDetail {
        val value = runtime.call("loadWork", JSONArray().put(workId)) as? JSONObject
            ?: throw PluginException(PluginErrorCode.Parse, "loadWork returned nothing")
        val detail = PluginJson.detail(value)
        if (detail.chapters.isEmpty()) {
            throw PluginException(PluginErrorCode.Parse, "This story has no chapters")
        }
        return detail
    }

    suspend fun loadChapter(chapter: PluginChapterRef, workId: String, workUrl: String): PluginChapter {
        val value = runtime.call(
            "loadChapter",
            JSONArray()
                .put(PluginJson.chapterRef(chapter))
                .put(JSONObject().put("id", workId).put("url", workUrl)),
        ) as? JSONObject ?: throw PluginException(PluginErrorCode.Parse, "loadChapter returned nothing")
        return PluginJson.chapter(value)
    }

    suspend fun login(fields: Map<String, String>): PluginSession {
        require(PluginCapability.Auth)
        val obj = JSONObject()
        fields.forEach { (k, v) -> obj.put(k, v) }
        return PluginJson.session(runtime.call("login", JSONArray().put(obj)))
    }

    suspend fun logout() {
        require(PluginCapability.Auth)
        runtime.call("logout")
    }

    suspend fun session(): PluginSession {
        if (!manifest.has(PluginCapability.Auth)) return PluginSession()
        return PluginJson.session(runtime.call("session"))
    }

    /** True when the site was updated; false when only local membership should change. */
    suspend fun setMembership(workId: String, listId: String, on: Boolean): Boolean {
        if (!manifest.has(PluginCapability.Membership)) return false
        return runtime.call("setMembership", JSONArray().put(workId).put(listId).put(on)) as? Boolean ?: false
    }

    suspend fun syncProgress(workId: String, chapter: PluginChapterRef) {
        if (!manifest.has(PluginCapability.ProgressSync)) return
        runtime.call("syncProgress", JSONArray().put(workId).put(PluginJson.chapterRef(chapter)))
    }

    /** A plugin-declared media card action was tapped. */
    suspend fun cardAction(workId: String, actionId: String, on: Boolean?): PluginCardActionResult {
        val args = JSONArray().put(workId).put(actionId)
        if (on != null) args.put(on)
        return PluginJson.cardActionResult(runtime.call("cardAction", args))
    }

    /** Latest chapter info for the works the plugin could check cheaply. */
    suspend fun checkUpdates(works: List<PluginUpdateQuery>): List<PluginUpdateInfo> {
        require(PluginCapability.Updates)
        val arr = JSONArray()
        works.forEach { arr.put(PluginJson.updateQuery(it)) }
        return PluginJson.updates(runtime.call("checkUpdates", JSONArray().put(arr)))
    }

    suspend fun resolveUrl(url: String): String? {
        require(PluginCapability.ResolveUrl)
        return (runtime.call("resolveUrl", JSONArray().put(url)) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun require(cap: PluginCapability) {
        if (!manifest.has(cap)) {
            throw PluginException(PluginErrorCode.Unsupported, "${manifest.name} does not support ${cap.key}")
        }
    }
}
