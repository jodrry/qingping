package com.qingping.app.data

import android.content.Context

object Prefs {

    private const val FILE = "qingping_prefs"
    private const val KEY_SPLASH = "splash_enabled"
    private const val KEY_JUMP = "jump_enabled"
    private const val KEY_BOOT = "boot_enabled"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isSplashEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_SPLASH, true)

    fun setSplashEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_SPLASH, enabled).apply()
    }

    fun isJumpGuardEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_JUMP, true)

    fun setJumpGuardEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_JUMP, enabled).apply()
    }

    fun isBootEnabled(context: Context): Boolean =
        sp(context).getBoolean(KEY_BOOT, true)

    fun setBootEnabled(context: Context, enabled: Boolean) {
        sp(context).edit().putBoolean(KEY_BOOT, enabled).apply()
    }
}
