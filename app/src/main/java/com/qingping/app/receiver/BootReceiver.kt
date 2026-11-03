package com.qingping.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingping.app.data.Prefs
import com.qingping.app.service.GuardService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON" -> {
                if (Prefs.isBootEnabled(context)) {
                    GuardService.start(context)
                }
            }
        }
    }
}
