package com.personal.flowreader.share

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.AbstractComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.personal.flowreader.MainActivity
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.design.layer.FlowOverlayHost
import com.personal.flowreader.ui.theme.FlowTheme

object ShareOverlayPermission {
    fun canDrawOverlays(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

object ShareDispatch {
    const val ACTION_EXECUTE = "com.personal.flowreader.action.SHARE_EXECUTE"
    const val EXTRA_KIND = "share_kind"
    const val EXTRA_TEXT = "share_text"
    const val EXTRA_TITLE = "share_title"
    const val EXTRA_URL = "share_url"
    const val EXTRA_LANDING = "share_landing"
    const val EXTRA_LIBRARY_TAB = "share_library_tab"
    const val EXTRA_SELECTOR = "share_selector"
    const val EXTRA_TITLE_CSS = "share_title_css"
    const val EXTRA_REMOVE_CSS = "share_remove_css"
    const val EXTRA_PARSE_RULE_ID = "share_parse_rule_id"

    const val KIND_FILES = "files"
    const val KIND_QUEUE = "queue"
    const val KIND_CRAWL = "crawl"
    const val KIND_PLUGIN = "plugin"
    const val EXTRA_PLUGIN_ID = "share_plugin_id"

    /** The chooser's plugin option and its label (plugin display name when installed). */
    fun pluginChoice(chooser: ShareAction.ShowChooser, url: String): Pair<String, ShareAction> {
        val pluginId = chooser.pluginRule.pluginId.orEmpty()
        val label = com.personal.flowreader.plugin.PluginManager.displayNames[pluginId] ?: "Plugin"
        return label to ShareAction.Plugin(pluginId, url)
    }

    fun intentFor(context: Context, action: ShareAction): Intent {
        val i = Intent(context, MainActivity::class.java).apply {
            this.action = ACTION_EXECUTE
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }
        when (action) {
            is ShareAction.ToFiles -> {
                i.putExtra(EXTRA_KIND, KIND_FILES)
                i.putExtra(EXTRA_TEXT, action.text)
                action.titleHint?.let { i.putExtra(EXTRA_TITLE, it) }
                if (action.libraryTabId.isNotBlank()) {
                    i.putExtra(EXTRA_LIBRARY_TAB, action.libraryTabId)
                }
            }
            is ShareAction.ToQueue -> {
                i.putExtra(EXTRA_KIND, KIND_QUEUE)
                i.putExtra(EXTRA_TEXT, action.text)
                action.titleHint?.let { i.putExtra(EXTRA_TITLE, it) }
            }
            is ShareAction.Crawl -> {
                i.putExtra(EXTRA_KIND, KIND_CRAWL)
                i.putExtra(EXTRA_URL, action.url)
                i.putExtra(EXTRA_LANDING, action.landing.id)
                i.putExtra(EXTRA_PARSE_RULE_ID, action.rule.id)
                ParseRules.effectiveSelectors(action.rule)?.let { sel ->
                    sel.body?.let { i.putExtra(EXTRA_SELECTOR, it) }
                    sel.title?.let { i.putExtra(EXTRA_TITLE_CSS, it) }
                    sel.remove?.let { i.putExtra(EXTRA_REMOVE_CSS, it) }
                }
            }
            is ShareAction.Plugin -> {
                i.putExtra(EXTRA_KIND, KIND_PLUGIN)
                i.putExtra(EXTRA_PLUGIN_ID, action.pluginId)
                i.putExtra(EXTRA_URL, action.url)
            }
            is ShareAction.ShowChooser -> error("Chooser is not executable")
        }
        return i
    }
}

/**
 * Floating chooser drawn over other apps (TYPE_APPLICATION_OVERLAY). Hosts a Compose view with
 * its own lifecycle / saved-state / back owners, since there is no Activity behind it.
 */
class ShareOverlayController(private val context: Context) {
    private var root: View? = null
    private var owner: OverlayWindowOwner? = null

    fun show(
        chooser: ShareAction.ShowChooser,
        onPick: (ShareAction) -> Unit,
        onCancel: () -> Unit,
    ): Boolean {
        dismiss()
        if (!ShareOverlayPermission.canDrawOverlays(context)) return false
        if (chooser.payload.url == null) return false
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        val windowOwner = OverlayWindowOwner()
        val cancel = { onCancel(); dismiss() }
        windowOwner.backDispatcher.addCallback(windowOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })
        val view = BackAwareComposeView(context, windowOwner.backDispatcher).apply {
            setViewTreeLifecycleOwner(windowOwner)
            setViewTreeSavedStateRegistryOwner(windowOwner)
            setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides windowOwner) {
                    FlowTheme(mode = ThemeMode.Oled) {
                        FlowOverlayHost {
                            ShareChooserCard(
                                chooser = chooser,
                                onPick = { onPick(it); dismiss() },
                                onCancel = cancel,
                            )
                        }
                    }
                }
            }
        }
        windowOwner.start()
        owner = windowOwner
        root = view
        wm.addView(view, params)
        return true
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        owner?.stop()
        owner = null
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        }
    }
}

private class OverlayWindowOwner : SavedStateRegistryOwner, OnBackPressedDispatcherOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val dispatcher = OnBackPressedDispatcher()

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val onBackPressedDispatcher: OnBackPressedDispatcher get() = dispatcher
    val backDispatcher: OnBackPressedDispatcher get() = dispatcher

    fun start() {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}

/** Overlay windows get Back as a key event; forward it to the Compose back dispatcher. */
private class BackAwareComposeView(
    context: Context,
    private val dispatcher: OnBackPressedDispatcher,
) : AbstractComposeView(context) {
    private var body: (@Composable () -> Unit)? = null

    fun setContent(content: @Composable () -> Unit) {
        body = content
        if (isAttachedToWindow) createComposition()
    }

    @Composable
    override fun Content() {
        body?.invoke()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) dispatcher.onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}