package com.qingping.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.qingping.app.service.AdAccessibilityService

object AccessibilityUtils {

    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AdAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any { flattened ->
            val component = ComponentName.unflattenFromString(flattened)
            component != null &&
                component.packageName == expected.packageName &&
                component.className == expected.className
        }
    }

    fun openSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                // 交给调用方提示
            }
        }
    }
}
