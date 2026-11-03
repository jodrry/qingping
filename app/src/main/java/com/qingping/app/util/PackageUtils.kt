package com.qingping.app.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

object PackageUtils {

    private val SYSTEM_SOURCE_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.miui.securitycenter",
        "com.miui.packageinstaller",
        "com.huawei.systemmanager",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.vivo.permissionmanager"
    )

    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.tencent.mtt",
        "com.UCMobile",
        "com.quark.browser",
        "com.baidu.browser",
        "com.android.browser",
        "com.miui.browser",
        "com.huawei.browser",
        "com.heytap.browser",
        "com.vivo.browser",
        "com.oppo.browser",
        "com.oneplus.browser",
        "com.meizu.flyme.internet",
        "com.qihoo.browser"
    )

    private val STORE_PACKAGES = setOf(
        "com.android.vending",
        "com.xiaomi.market",
        "com.huawei.appmarket",
        "com.oppo.market",
        "com.heytap.market",
        "com.bbk.appstore",
        "com.tencent.android.qqdownloader",
        "com.qihoo.appstore",
        "com.baidu.appsearch",
        "com.lenovo.leos.appstore",
        "com.meizu.mstore",
        "com.samsung.android.galaxyapps"
    )

    private val SHOPPING_PACKAGES = setOf(
        "com.taobao.taobao",
        "com.tmall.wireless",
        "com.jingdong.app.mall",
        "com.xunmeng.pinduoduo",
        "com.achievo.vipshop",
        "com.suning.mobile.ebuy",
        "com.kaola",
        "com.taobao.idlefish",
        "com.shizhuang.duapp"
    )

    private val CONTENT_PACKAGES = setOf(
        "com.ss.android.ugc.aweme",
        "com.smile.gifmaker",
        "com.xingin.xhs"
    )

    private val REDIRECT_TARGETS =
        BROWSER_PACKAGES + STORE_PACKAGES + SHOPPING_PACKAGES + CONTENT_PACKAGES +
            setOf(
                "com.android.intentresolver",
                "com.google.android.intentresolver"
            )

    fun appLabel(context: Context, packageName: String): String {
        val info = applicationInfo(context, packageName) ?: return packageName
        return try {
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun appIcon(context: Context, packageName: String): Drawable? {
        val info = applicationInfo(context, packageName) ?: return null
        return try {
            context.packageManager.getApplicationIcon(info)
        } catch (_: Exception) {
            null
        }
    }

    fun isRedirectTarget(packageName: String): Boolean =
        packageName in REDIRECT_TARGETS

    fun isBrowserPackage(packageName: String): Boolean =
        packageName in BROWSER_PACKAGES

    fun isUserFacing(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (packageName == context.packageName) return false
        if (packageName in SYSTEM_SOURCE_PACKAGES) return false
        if (isLauncher(context, packageName)) return false
        return true
    }

    fun isLauncher(context: Context, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            infos.any { it.activityInfo.packageName == packageName }
        } catch (_: Exception) {
            false
        }
    }

    private fun applicationInfo(context: Context, packageName: String): ApplicationInfo? {
        return try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
    }
}
