package com.khader.spiderpet

import android.os.Handler
import android.os.Looper

/** Shared state between the accessibility service and the overlay service (same process). */
object SpiderBridge {
    private val main = Handler(Looper.getMainLooper())

    @Volatile var onHome: Boolean = true
        private set
    @Volatile var a11yConnected: Boolean = false
    @Volatile var icons: List<IconTarget> = emptyList()
    @Volatile var service: SpiderAccessibilityService? = null
    @Volatile var listener: (() -> Unit)? = null

    fun updateOnHome(v: Boolean) {
        if (onHome != v) {
            onHome = v
            if (!v) icons = emptyList()
            notifyChanged()
        }
    }

    fun updateIcons(list: List<IconTarget>) {
        icons = list
        notifyChanged()
    }

    fun reset() {
        a11yConnected = false
        service = null
        icons = emptyList()
        onHome = true
        notifyChanged()
    }

    fun clickIcon(label: String): Boolean = service?.clickLabel(label) ?: false

    private fun notifyChanged() {
        main.post { listener?.invoke() }
    }
}
