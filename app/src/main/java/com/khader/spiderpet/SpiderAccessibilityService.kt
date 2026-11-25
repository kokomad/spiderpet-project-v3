package com.khader.spiderpet

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Lets the spider know (1) when the home screen is visible and (2) where the app icons are.
 * No data is stored; app names are only sent to Gemini if the AI brain is enabled.
 */
class SpiderAccessibilityService : AccessibilityService() {

    private val main = Handler(Looper.getMainLooper())
    private var homePkg: String? = null
    private val scanRunnable = Runnable { scanIcons() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        homePkg = findHomePackage()
        SpiderBridge.service = this
        SpiderBridge.a11yConnected = true
        SpiderBridge.updateOnHome(true)
        scheduleScan(600)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg == "com.android.systemui" || pkg.contains("inputmethod") || pkg.contains("keyboard")) return
                val home = pkg == homePkg || pkg == packageName
                SpiderBridge.updateOnHome(home)
                if (pkg == homePkg) scheduleScan(450)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (pkg == homePkg && SpiderBridge.onHome) scheduleScan(800)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        main.removeCallbacks(scanRunnable)
        SpiderBridge.reset()
        return super.onUnbind(intent)
    }

    private fun scheduleScan(delay: Long) {
        main.removeCallbacks(scanRunnable)
        main.postDelayed(scanRunnable, delay)
    }

    private fun findHomePackage(): String? {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    private fun scanIcons() {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != homePkg) return
        val density = resources.displayMetrics.density
        val minPx = (30 * density).toInt()
        val maxPx = (150 * density).toInt()
        val out = ArrayList<IconTarget>()

        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 14 || out.size >= 48) return
            val label = (n.text ?: n.contentDescription)?.toString()
            if (!label.isNullOrBlank() && label.length <= 28 && n.isVisibleToUser &&
                (n.isClickable || n.isLongClickable)
            ) {
                val r = Rect()
                n.getBoundsInScreen(r)
                if (r.width() in minPx..maxPx && r.height() in minPx..maxPx) {
                    out.add(IconTarget(label.trim(), r))
                }
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }

        walk(root, 0)
        SpiderBridge.updateIcons(out)
    }

    /** Used when the AI (and the user's opt-in setting) lets the spider "tap" an app icon. */
    fun clickLabel(label: String): Boolean {
        val root = rootInActiveWindow ?: return false
        var done = false
        fun walk(n: AccessibilityNodeInfo?, d: Int) {
            if (n == null || done || d > 14) return
            val l = (n.text ?: n.contentDescription)?.toString()?.trim()
            if (l == label && (n.isClickable || n.isLongClickable)) {
                done = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), d + 1)
        }
        walk(root, 0)
        return done
    }
}
