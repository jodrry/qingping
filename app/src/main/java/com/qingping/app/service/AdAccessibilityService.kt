package com.qingping.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.qingping.app.R
import com.qingping.app.data.Prefs
import com.qingping.app.data.StatsRepository
import com.qingping.app.util.PackageUtils
import java.util.Locale

class AdAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var currentForegroundPackage: String? = null
    private var lastForegroundPackage: String? = null
    private var packageEnteredAt = 0L
    private var currentClassName: String? = null

    private var lastTouchAt = 0L
    private var lastJumpHandledAt = 0L

    private var splashPackage: String? = null
    private var splashSessionAt = 0L
    private var splashHandled = false
    private var lastSplashHandledAt = 0L
    private var scanScheduled = false

    private val splashScan = Runnable {
        scanScheduled = false
        scanForSkip()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        toast(getString(R.string.service_ready))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return

        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handleWindowState(e)

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (splashPackage != null && !splashHandled &&
                    e.packageName?.toString() == splashPackage
                ) {
                    scheduleScan(180L)
                }
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> {
                if (e.packageName?.toString() != packageName) {
                    lastTouchAt = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    private fun handleWindowState(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty() || pkg == packageName) return

        val cls = event.className?.toString().orEmpty()
        val now = SystemClock.elapsedRealtime()
        val previous = lastForegroundPackage

        currentForegroundPackage = pkg

        if (pkg != previous) {
            maybeGuardJump(previous, pkg, now)

            lastForegroundPackage = pkg
            packageEnteredAt = now
            currentClassName = cls

            if (PackageUtils.isUserFacing(this, pkg)) {
                beginSplashSession(pkg, now)
            }
        } else if (now - packageEnteredAt <= SPLASH_WINDOW_MS && cls != currentClassName) {
            currentClassName = cls
            if (PackageUtils.isUserFacing(this, pkg)) {
                beginSplashSession(pkg, now)
            }
        }
    }

    private fun beginSplashSession(pkg: String, now: Long) {
        if (!Prefs.isSplashEnabled(this)) return
        if (now - lastSplashHandledAt < 3000L) return

        splashPackage = pkg
        splashSessionAt = now
        splashHandled = false
        scanScheduled = false

        handler.removeCallbacks(splashScan)
        for (delay in SCAN_DELAYS) {
            handler.postDelayed(splashScan, delay)
        }
    }

    private fun scheduleScan(delay: Long) {
        if (scanScheduled || splashHandled) return
        scanScheduled = true
        handler.postDelayed(splashScan, delay)
    }

    private fun maybeGuardJump(previous: String?, target: String, now: Long) {
        if (previous == null || previous == target) return
        if (!Prefs.isJumpGuardEnabled(this)) return
        if (!PackageUtils.isRedirectTarget(target)) return
        if (!PackageUtils.isUserFacing(this, previous)) return
        if (PackageUtils.isRedirectTarget(previous) || PackageUtils.isBrowserPackage(previous)) return
        if (lastTouchAt == 0L || now - lastTouchAt > JUMP_TOUCH_WINDOW_MS) return
        if (now - lastJumpHandledAt < JUMP_COOLDOWN_MS) return

        val source = previous
        handler.postDelayed({
            if (currentForegroundPackage != target) return@postDelayed

            if (performGlobalAction(GLOBAL_ACTION_BACK)) {
                lastJumpHandledAt = SystemClock.elapsedRealtime()
                StatsRepository.record(this, source, StatsRepository.TYPE_JUMP)
                toast(getString(R.string.jump_blocked))
            }
        }, 180L)
    }

    private fun scanForSkip() {
        if (splashHandled) return
        val pkg = splashPackage ?: return
        val now = SystemClock.elapsedRealtime()

        if (currentForegroundPackage != pkg) return
        if (now - splashSessionAt > SPLASH_WINDOW_MS) return
        if (!Prefs.isSplashEnabled(this)) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val nodes = ArrayList<AccessibilityNodeInfo>(96)
        val allText = StringBuilder()
        collect(root, nodes, allText, 0)
        val adContext = AD_MARKERS.any { allText.contains(it, ignoreCase = true) }

        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val maxTop = (screenH * 0.55f).toInt()
        val rightThreshold = (screenW * 0.45f).toInt()

        var bestNode: AccessibilityNodeInfo? = null
        var bestRect: Rect? = null
        var bestScore = -1

        for (node in nodes) {
            val label = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { node.contentDescription?.toString()?.trim().orEmpty() }
            if (label.isEmpty()) continue

            val kind = classifyLabel(label) ?: continue
            if (kind == Kind.CLOSE && !adContext) continue

            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() <= 0 || rect.height() <= 0) continue
            if (rect.top > maxTop || rect.bottom < 0) continue

            val rightSide = rect.centerX() >= rightThreshold
            val score = when (kind) {
                Kind.SKIP -> if (rightSide) 100 else 55
                Kind.CLOSE -> if (rightSide) 80 else 30
            }

            if (score > bestScore) {
                bestScore = score
                bestNode = node
                bestRect = rect
            }
        }

        val node = bestNode ?: return
        val rect = bestRect ?: return

        if (tryClick(node, rect)) {
            splashHandled = true
            handler.removeCallbacks(splashScan)
            lastSplashHandledAt = SystemClock.elapsedRealtime()
            StatsRepository.record(this, pkg, StatsRepository.TYPE_SPLASH)
            toast(getString(R.string.splash_skipped))
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        out: MutableList<AccessibilityNodeInfo>,
        text: StringBuilder,
        depth: Int
    ) {
        if (node == null || depth > 28 || out.size >= 600) return

        out.add(node)
        if (text.length < 4000) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
                text.append(it).append(' ')
            }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
                text.append(it).append(' ')
            }
        }

        for (i in 0 until node.childCount) {
            collect(node.getChild(i), out, text, depth + 1)
        }
    }

    private fun classifyLabel(raw: String): Kind? {
        val s = raw.trim().lowercase(Locale.ROOT).replace(WHITESPACE, "")
        if (s.isEmpty() || s.length > 24) return null

        if (s.contains("片头") || s.contains("片尾") || s.contains("intro")) {
            return null
        }

        val isSkip =
            s == "跳过" ||
                s == "跳過" ||
                s == "skip" ||
                s == "skipad" ||
                s.startsWith("跳过广告") ||
                s.startsWith("跳過廣告") ||
                (s.startsWith("跳过") && s.drop(2).all {
                    it.isDigit() || it == 's' || it == '秒'
                }) ||
                (s.startsWith("跳過") && s.drop(2).all {
                    it.isDigit() || it == 's' || it == '秒'
                }) ||
                (s.startsWith("skip") && s.length <= 12)

        if (isSkip) return Kind.SKIP

        val isClose =
            s == "关闭" ||
                s == "关闭广告" ||
                s == "关闭此广告" ||
                s == "close" ||
                s == "×" ||
                s == "✕" ||
                s == "x"

        if (isClose) return Kind.CLOSE
        return null
    }

    private fun tryClick(node: AccessibilityNodeInfo, rect: Rect): Boolean {
        var target: AccessibilityNodeInfo? = node
        var level = 0

        while (target != null && level < 5) {
            if (target.isClickable && target.isEnabled) {
                if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return true
                }
            }
            target = target.parent
            level++
        }

        val x = rect.centerX().coerceAtLeast(0)
        val y = rect.centerY().coerceAtLeast(0)
        return tap(x.toFloat(), y.toFloat())
    }

    private fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        // 系统要求实现，无需额外处理
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private enum class Kind {
        SKIP,
        CLOSE
    }

    companion object {
        private const val TAG = "AdAccessibilityService"
        private const val SPLASH_WINDOW_MS = 6_000L
        private const val JUMP_TOUCH_WINDOW_MS = 1_600L
        private const val JUMP_COOLDOWN_MS = 4_000L

        private val SCAN_DELAYS = longArrayOf(
            220L,
            550L,
            1_000L,
            1_600L,
            2_400L,
            3_400L
        )

        private val AD_MARKERS = arrayOf(
            "广告",
            "赞助",
            "推广",
            "advertisement",
            "sponsored"
        )

        private val WHITESPACE = Regex("\\s+")
    }
}
