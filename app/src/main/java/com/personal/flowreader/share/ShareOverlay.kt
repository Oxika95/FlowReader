package com.personal.flowreader.share

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.view.WindowManager
import com.personal.flowreader.MainActivity

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

    const val KIND_FILES = "files"
    const val KIND_QUEUE = "queue"
    const val KIND_CRAWL = "crawl"
    const val KIND_RR_PLUGIN = "rr_plugin"

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
                val (content, title, remove) = ParseRules.effectiveSelectors(action.rule)
                content?.let { i.putExtra(EXTRA_SELECTOR, it) }
                title?.let { i.putExtra(EXTRA_TITLE_CSS, it) }
                remove?.let { i.putExtra(EXTRA_REMOVE_CSS, it) }
            }
            is ShareAction.RoyalRoadPlugin -> {
                i.putExtra(EXTRA_KIND, KIND_RR_PLUGIN)
                i.putExtra(EXTRA_URL, action.url)
            }
            is ShareAction.ShowChooser -> error("Chooser is not executable")
        }
        return i
    }
}

/** WindowManager floating chooser (Views — no Compose lifecycle required). */
class ShareOverlayController(private val context: Context) {
    private var root: LinearLayout? = null

    fun show(
        chooser: ShareAction.ShowChooser,
        onPick: (ShareAction) -> Unit,
        onCancel: () -> Unit,
    ): Boolean {
        dismiss()
        if (!ShareOverlayPermission.canDrawOverlays(context)) return false
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val pad = dp(16)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
        }

        val payload = chooser.payload
        val url = payload.url ?: return false
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(0xFF1C1B1F.toInt())
                cornerRadius = dp(16).toFloat()
            }
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(pad, pad, pad, pad)
            }
        }

        fun addTitle(text: String, sizeSp: Float = 18f, color: Int = Color.WHITE) {
            card.addView(
                TextView(context).apply {
                    this.text = text
                    setTextColor(color)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                    setPadding(0, 0, 0, dp(8))
                },
            )
        }

        fun addBtn(label: String, outline: Boolean = false, onClick: () -> Unit) {
            card.addView(
                Button(context).apply {
                    text = label
                    if (outline) {
                        setBackgroundColor(0xFF2B2930.toInt())
                    }
                    setOnClickListener {
                        onClick()
                        dismiss()
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(6) }
                },
            )
        }

        val fallbackLabel = when (val fb = chooser.urlFallback) {
            is ShareAction.Crawl ->
                if (fb.landing.id == RouterLanding.FILES) "Parse → Files" else "Parse → Queue"
            is ShareAction.ToQueue -> "Queue URL"
            is ShareAction.ToFiles -> "Files"
            else -> "URL default"
        }

        addTitle("Send to Flow Reader")
        addTitle(url.take(140), sizeSp = 13f, color = 0xFFCAC4D0.toInt())
        addBtn("Plugin") { onPick(ShareAction.RoyalRoadPlugin(url)) }
        addBtn(fallbackLabel, outline = true) { onPick(chooser.urlFallback) }
        addBtn("Cancel", outline = true) { onCancel() }

        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x99000000.toInt())
            gravity = Gravity.CENTER
            addView(card)
        }
        outer.setOnClickListener { onCancel(); dismiss() }
        card.isClickable = true
        root = outer
        wm.addView(outer, params)
        return true
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
}
