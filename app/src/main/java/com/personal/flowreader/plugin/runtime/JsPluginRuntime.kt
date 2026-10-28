package com.personal.flowreader.plugin.runtime

import android.util.Log
import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.personal.flowreader.debug.DebugLog
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.api.PluginManifest
import java.net.URI
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import android.util.Base64
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * One QuickJS context per plugin, created lazily on a dedicated thread. Calls are serialized
 * and exchange JSON strings across the boundary.
 */
class JsPluginRuntime(
    private val manifest: PluginManifest,
    private val code: String,
    val http: PluginHttp,
    private val secrets: PluginSecrets,
    private val kv: PluginKvStore,
    private val settingsJson: () -> String,
    private val audioDir: java.io.File? = null,
    val ws: PluginWs = PluginWs(manifest.allowedHosts, audioDir),
) {
    private val mutex = Mutex()
    private val html = HtmlHandles()
    private var js: QuickJs? = null
    private var dispatcher: ExecutorCoroutineDispatcher? = null
    private var result: String? = null

    /**
     * Invoke `module.exports[name](...args)` and return the JSON-decoded value
     * (`JSONObject`, `JSONArray`, primitive, or `null`).
     */
    suspend fun call(name: String, args: JSONArray = JSONArray(), timeoutMs: Long = CALL_TIMEOUT_MS): Any? =
        mutex.withLock {
            val thread = dispatcherOrCreate()
            val raw = try {
                withContext(thread) {
                    withTimeout(timeoutMs) {
                        val engine = engineOrCreate()
                        result = null
                        engine.evaluate<Any?>(
                            "__flowSetSettings(${JSONObject.quote(settingsJson())});" +
                                "__flowInvoke(${JSONObject.quote(name)}, ${JSONObject.quote(args.toString())});",
                            "invoke.js",
                        )
                        result
                    }
                }
            } catch (t: TimeoutCancellationException) {
                reset()
                throw PluginException(PluginErrorCode.Timeout, "${manifest.name} took too long to respond", t)
            } catch (t: QuickJsException) {
                reset()
                throw PluginException(PluginErrorCode.Error, t.message ?: "Plugin script error", t)
            } finally {
                html.clear()
            }
            decodeResult(raw ?: throw PluginException(PluginErrorCode.Error, "$name returned no result"))
        }

    /** Whether `module.exports[name]` is a function (for optional contract functions). */
    suspend fun hasFunction(name: String): Boolean = mutex.withLock {
        withContext(dispatcherOrCreate()) {
            engineOrCreate().evaluate<Boolean>(
                "typeof module.exports[${JSONObject.quote(name)}] === 'function'",
                "probe.js",
            )
        }
    }

    fun close() {
        reset()
        dispatcher?.close()
        dispatcher = null
    }

    private fun reset() {
        runCatching { js?.close() }
        js = null
    }

    private fun dispatcherOrCreate(): ExecutorCoroutineDispatcher =
        dispatcher ?: Executors.newSingleThreadExecutor { r ->
            Thread(null, r, "plugin-js-${manifest.id}", THREAD_STACK_BYTES).apply { isDaemon = true }
        }.asCoroutineDispatcher().also { dispatcher = it }

    private suspend fun engineOrCreate(): QuickJs {
        js?.let { return it }
        val engine = QuickJs.create(dispatcherOrCreate())
        engine.memoryLimit = MEMORY_LIMIT_BYTES
        engine.maxStackSize = STACK_LIMIT_BYTES
        bind(engine)
        try {
            val pluginInfo = JSONObject().put("id", manifest.id).put("version", manifest.version).toString()
            engine.evaluate<Any?>(PRELUDE.replace("__PLUGIN_INFO__", JSONObject.quote(pluginInfo)), "prelude.js")
            engine.evaluate<Any?>(code, "${manifest.id}/index.js")
        } catch (t: Throwable) {
            runCatching { engine.close() }
            throw PluginException(PluginErrorCode.Error, "Could not load ${manifest.name}: ${t.message}", t)
        }
        js = engine
        return engine
    }

    private fun bind(engine: QuickJs) {
        engine.function("__host_result") { args -> result = args.getOrNull(0) as? String }
        engine.function("__host_log") { args ->
            val msg = args.getOrNull(0)?.toString().orEmpty()
            Log.i(LOG_TAG, "[${manifest.id}] $msg")
            DebugLog.plugins("${manifest.id} $msg")
        }
        engine.asyncFunction("__host_fetch") { args -> http.fetch(args.getOrNull(0) as? String ?: "{}") }
        engine.asyncFunction("__host_sleep") { args ->
            delay(((args.getOrNull(0) as? Number)?.toLong() ?: 0L).coerceIn(0L, 10_000L))
        }
        engine.function("__url_resolve") { args ->
            val base = args.getOrNull(0) as? String ?: ""
            val href = args.getOrNull(1) as? String ?: ""
            runCatching { URI(base).resolve(href.trim()).toString() }.getOrDefault(href)
        }

        engine.asyncFunction("__store_get") { args -> kv.get(args.str(0)) }
        engine.asyncFunction("__store_set") { args -> kv.set(args.str(0), args.str(1)) }
        engine.asyncFunction("__store_remove") { args -> kv.remove(args.str(0)) }
        engine.asyncFunction("__secrets_get") { args -> secrets.get(args.str(0)) }
        engine.asyncFunction("__secrets_set") { args -> secrets.set(args.str(0), args.str(1)) }
        engine.asyncFunction("__secrets_remove") { args -> secrets.remove(args.str(0)) }
        engine.asyncFunction("__secrets_clear") { _ -> secrets.clear() }
        engine.asyncFunction("__cookies_clear") { _ -> http.cookieJar.clear() }
        engine.asyncFunction("__ws_collect") { args ->
            try {
                ws.collect(args.str(0))
            } catch (t: Throwable) {
                val msg = t.message.orEmpty()
                val status = Regex("""status=(\d+)""").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
                // HTTP 200/401/403 on WS upgrade = service rejection (bad token), not offline.
                val code = when (status) {
                    200, 401, 403 -> "AUTH_REQUIRED"
                    else -> "NETWORK"
                }
                JSONObject()
                    .put(
                        "error",
                        JSONObject()
                            .put("code", code)
                            .put("message", msg.ifBlank { "ws failed" }),
                    )
                    .toString()
            }
        }
        engine.function("__sha256") { args ->
            val input = Base64.decode(args.str(0), Base64.DEFAULT)
            val digest = MessageDigest.getInstance("SHA-256").digest(input)
            Base64.encodeToString(digest, Base64.NO_WRAP)
        }
        engine.function("__sha256_hex") { args ->
            val hex = args.str(0).lowercase().replace(Regex("[^0-9a-f]"), "")
            require(hex.length % 2 == 0) { "sha256Hex needs even-length hex" }
            val input = ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { b ->
                "%02x".format(b.toInt() and 0xff)
            }
        }

        engine.function("__html_parse") { args -> html.parse(args.str(0), args.getOrNull(1) as? String) }
        engine.function("__html_select") { args -> html.select(args.int(0), args.str(1)) }
        engine.function("__html_select_first") { args -> html.selectFirst(args.int(0), args.str(1)) }
        engine.function("__html_text") { args -> html.text(args.int(0)) }
        engine.function("__html_own_text") { args -> html.ownText(args.int(0)) }
        engine.function("__html_html") { args -> html.html(args.int(0)) }
        engine.function("__html_outer") { args -> html.outerHtml(args.int(0)) }
        engine.function("__html_data") { args -> html.data(args.int(0)) }
        engine.function("__html_attr") { args -> html.attr(args.int(0), args.str(1)) }
        engine.function("__html_has_class") { args -> html.hasClass(args.int(0), args.str(1)) }
        engine.function("__html_children") { args -> html.children(args.int(0)) }
        engine.function("__html_parent") { args -> html.parent(args.int(0)) }
        engine.function("__html_contains") { args -> html.contains(args.int(0), args.int(1)) }
        engine.function("__html_remove") { args -> html.remove(args.int(0)) }
    }

    private fun decodeResult(raw: String): Any? {
        val envelope = runCatching { JSONObject(raw) }.getOrElse {
            throw PluginException(PluginErrorCode.Parse, "Plugin returned malformed data")
        }
        if (!envelope.optBoolean("ok", false)) {
            throw PluginException(
                PluginErrorCode.parse(envelope.optString("code")),
                envelope.optString("message").ifBlank { "Plugin error" },
            )
        }
        return if (envelope.isNull("value")) null else envelope.get("value")
    }

    private fun Array<Any?>.str(i: Int): String = getOrNull(i)?.toString().orEmpty()

    private fun Array<Any?>.int(i: Int): Int = (getOrNull(i) as? Number)?.toInt() ?: -1

    companion object {
        private const val LOG_TAG = "FlowPlugin"
        const val CALL_TIMEOUT_MS = 60_000L
        /** Headroom for voice-list JSON and other plugin work; clip audio stays on disk. */
        private const val MEMORY_LIMIT_BYTES = 128L * 1024 * 1024
        private const val STACK_LIMIT_BYTES = 1024L * 1024
        private const val THREAD_STACK_BYTES = 4L * 1024 * 1024

        private val PRELUDE = """
            (function () {
              var g = globalThis;
              function hostError(e) {
                var err = new Error((e && e.message) || 'Error');
                err.code = (e && e.code) || 'ERROR';
                return err;
              }
              function Node(id) { this._id = id; }
              function many(json) { return JSON.parse(json).map(function (i) { return new Node(i); }); }
              function one(i) { return i < 0 ? null : new Node(i); }
              Node.prototype.select = function (css) { return many(__html_select(this._id, String(css))); };
              Node.prototype.selectFirst = function (css) { return one(__html_select_first(this._id, String(css))); };
              Node.prototype.text = function () { return __html_text(this._id); };
              Node.prototype.ownText = function () { return __html_own_text(this._id); };
              Node.prototype.html = function () { return __html_html(this._id); };
              Node.prototype.outerHtml = function () { return __html_outer(this._id); };
              Node.prototype.data = function () { return __html_data(this._id); };
              Node.prototype.attr = function (name) { return __html_attr(this._id, String(name)); };
              Node.prototype.hasClass = function (name) { return __html_has_class(this._id, String(name)); };
              Node.prototype.children = function () { return many(__html_children(this._id)); };
              Node.prototype.parent = function () { return one(__html_parent(this._id)); };
              Node.prototype.contains = function (other) { return !!other && __html_contains(this._id, other._id); };
              Node.prototype.remove = function () { __html_remove(this._id); };

              var settings = {};
              var log = function () {
                __host_log(Array.prototype.map.call(arguments, function (a) {
                  return typeof a === 'string' ? a : JSON.stringify(a);
                }).join(' '));
              };
              g.flow = {
                plugin: JSON.parse(__PLUGIN_INFO__),
                get settings() { return settings; },
                fetch: async function (url, opts) {
                  var r = JSON.parse(await __host_fetch(JSON.stringify({ url: String(url), opts: opts || {} })));
                  if (r.error) throw hostError(r.error);
                  return r;
                },
                html: {
                  parse: function (text, base) {
                    return new Node(__html_parse(text == null ? '' : String(text), base == null ? '' : String(base)));
                  }
                },
                storage: {
                  get: async function (k) { var v = await __store_get(String(k)); return v == null ? null : v; },
                  set: function (k, v) { return __store_set(String(k), String(v)); },
                  remove: function (k) { return __store_remove(String(k)); }
                },
                secrets: {
                  get: async function (k) { var v = await __secrets_get(String(k)); return v == null ? null : v; },
                  set: function (k, v) { return __secrets_set(String(k), String(v)); },
                  remove: function (k) { return __secrets_remove(String(k)); },
                  clear: function () { return __secrets_clear(); }
                },
                cookies: { clear: function () { return __cookies_clear(); } },
                ws: {
                  collect: async function (opts) {
                    var r = JSON.parse(await __ws_collect(JSON.stringify(opts || {})));
                    if (r.error) throw hostError(r.error);
                    return r;
                  }
                },
                crypto: {
                  sha256: function (inputBase64) {
                    return __sha256(String(inputBase64));
                  },
                  sha256Hex: function (inputHex) {
                    return __sha256_hex(String(inputHex));
                  }
                },
                error: function (code, message) { var e = new Error(message || code); e.code = code; return e; },
                url: { resolve: function (base, href) { return __url_resolve(String(base), String(href)); } },
                sleep: function (ms) { return __host_sleep(Number(ms) || 0); },
                log: log
              };
              g.console = { log: log, info: log, warn: log, error: log, debug: log };
              g.module = { exports: {} };
              g.exports = g.module.exports;
              g.__flowSetSettings = function (json) { settings = JSON.parse(json); };
              g.__flowInvoke = function (name, argsJson) {
                var api = g.module.exports || {};
                var fn = api[name];
                if (typeof fn !== 'function') {
                  __host_result(JSON.stringify({ ok: false, code: 'UNSUPPORTED', message: name + ' is not implemented' }));
                  return;
                }
                var args = JSON.parse(argsJson);
                Promise.resolve()
                  .then(function () { return fn.apply(api, args); })
                  .then(function (v) {
                    __host_result(JSON.stringify({ ok: true, value: v === undefined ? null : v }));
                  }, function (e) {
                    __host_result(JSON.stringify({
                      ok: false,
                      code: (e && e.code) || 'ERROR',
                      message: String((e && e.message) || e)
                    }));
                  });
              };
            })();
        """.trimIndent()
    }
}
