package com.personal.flowreader.share

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object ShareQueAliasController {
    private const val ALIAS = "com.personal.flowreader.ShareQueAlias"

    fun setEnabled(context: Context, enabled: Boolean) {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, ALIAS)
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        pm.setComponentEnabledSetting(
            component,
            state,
            PackageManager.DONT_KILL_APP,
        )
    }

    fun isEnabled(context: Context): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context.packageName, ALIAS)
        return when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> true // default enabled in manifest
        }
    }
}
