package com.khader.spiderpet

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny programmatic UI toolkit (dark theme, RTL-friendly) - no external dependencies. */
object UI {
    val BG: Int = Color.parseColor("#0B0B10")
    val CARD: Int = Color.parseColor("#15151D")
    val STROKE: Int = Color.parseColor("#2A2A3A")
    val ACCENT: Int = Color.parseColor("#E53935")
    val ACCENT_DARK: Int = Color.parseColor("#8E1B18")
    val TEXT: Int = Color.parseColor("#F2F2F7")
    val SUB: Int = Color.parseColor("#9A9AB0")
    val GREEN: Int = Color.parseColor("#43D17A")
    val FIELD: Int = Color.parseColor("#0F0F16")

    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun roundBg(color: Int, radiusPx: Float, strokeColor: Int = 0, strokePx: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = radiusPx
        g.setColor(color)
        if (strokePx > 0) g.setStroke(strokePx, strokeColor)
        return g
    }

    fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, bottom: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h)
        p.topMargin = top
        p.bottomMargin = bottom
        return p
    }

    fun text(c: Context, s: String, sizeSp: Float = 15f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = s
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        t.gravity = Gravity.START
        return t
    }

    fun card(c: Context): LinearLayout {
        val l = LinearLayout(c)
        l.orientation = LinearLayout.VERTICAL
        l.background = roundBg(CARD, dp(c, 18).toFloat(), STROKE, dp(c, 1))
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        l.layoutDirection = View.LAYOUT_DIRECTION_RTL
        return l
    }

    fun button(c: Context, label: String, color: Int = ACCENT, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = label
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
        b.setTypeface(b.typeface, Typeface.BOLD)
        b.stateListAnimator = null
        b.background = roundBg(color, dp(c, 14).toFloat())
        b.minHeight = dp(c, 46)
        b.minimumHeight = dp(c, 46)
        b.setOnClickListener { onClick() }
        return b
    }

    fun setButtonColor(b: Button, color: Int) {
        b.background = roundBg(color, dp(b.context, 14).toFloat())
    }
}
