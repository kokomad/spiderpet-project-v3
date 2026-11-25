package com.khader.spiderpet

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Main screen: start/stop the spider, permissions, quick commands. Developer: خضر أبو صالحه */
class MainActivity : Activity() {

    private class PermRow(val status: TextView, val button: Button, val ok: () -> Boolean)

    private lateinit var statusTv: TextView
    private lateinit var toggleBtn: Button
    private val rows = ArrayList<PermRow>()
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    private fun dp(v: Int) = UI.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.BG

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(UI.BG)
        scroll.isFillViewport = true
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(18), dp(30), dp(18), dp(28))
        scroll.addView(root, LinearLayout.LayoutParams(UI.MATCH, UI.WRAP))
        setContentView(scroll)

        // header
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher_foreground)
        logo.background = UI.roundBg(Color.parseColor("#12121A"), dp(22).toFloat(), UI.STROKE, dp(1))
        header.addView(logo, LinearLayout.LayoutParams(dp(76), dp(76)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(dp(14), 0, dp(14), 0)
        titles.addView(UI.text(this, "العنكبوت الحي", 26f, UI.TEXT, true))
        titles.addView(UI.text(this, "رفيق ذكي يعيش على شاشة هاتفك وينسج شبكته", 13f, UI.SUB))
        header.addView(titles, LinearLayout.LayoutParams(0, UI.WRAP, 1f))
        root.addView(header, UI.lp(bottom = dp(18)))

        // status + toggle
        val status = UI.card(this)
        statusTv = UI.text(this, "● متوقف", 17f, UI.SUB, true)
        status.addView(statusTv, UI.lp(bottom = dp(10)))
        toggleBtn = UI.button(this, "تشغيل العنكبوت") { toggle() }
        status.addView(toggleBtn, UI.lp(h = dp(54)))
        root.addView(status, UI.lp(bottom = dp(14)))

        // permissions
        val perms = UI.card(this)
        perms.addView(UI.text(this, "الصلاحيات", 18f, UI.TEXT, true), UI.lp(bottom = dp(4)))
        perms.addView(
            UI.text(this, "العنكبوت يحتاج هذه الصلاحيات ليظهر فوق الشاشة الرئيسية ويعرف أماكن التطبيقات.", 12.5f, UI.SUB),
            UI.lp(bottom = dp(8))
        )
        addPerm(
            perms, "الظهور فوق التطبيقات", "ضرورية: لرسم العنكبوت على الشاشة",
            { Settings.canDrawOverlays(this) }, "منح"
        ) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        addPerm(
            perms, "خدمة إمكانية الوصول", "لمعرفة الشاشة الرئيسية وأماكن الأيقونات والتفاعل معها",
            { a11yEnabled() }, "تفعيل"
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "اختر «العنكبوت الحي» ثم فعّله", Toast.LENGTH_LONG).show()
        }
        perms.addView(
            UI.text(
                this,
                "إن كان الخيار رمادياً (تثبيت من خارج المتجر): افتح معلومات التطبيق ← القائمة ⋮ ← «السماح بالإعدادات المقيّدة» ثم أعد المحاولة.",
                12f, UI.SUB
            ), UI.lp(top = dp(2), bottom = dp(4))
        )
        perms.addView(
            UI.button(this, "فتح معلومات التطبيق", Color.parseColor("#2A2A3A")) {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }, UI.lp(h = dp(44), bottom = dp(8))
        )
        addPerm(
            perms, "الإشعارات", "لإظهار زر الإيقاف السريع أثناء التشغيل",
            { notifGranted() }, "منح"
        ) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
        addPerm(
            perms, "تجاهل توفير البطارية", "اختياري: حتى لا يوقف النظام العنكبوت",
            { batteryOk() }, "السماح"
        ) {
            try {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        root.addView(perms, UI.lp(bottom = dp(14)))

        // quick commands
        val cmds = UI.card(this)
        cmds.addView(UI.text(this, "أوامر سريعة", 18f, UI.TEXT, true), UI.lp(bottom = dp(10)))
        val r1 = LinearLayout(this)
        r1.orientation = LinearLayout.HORIZONTAL
        r1.layoutDirection = View.LAYOUT_DIRECTION_RTL
        val dark = Color.parseColor("#2A2A3A")
        r1.addView(UI.button(this, "📍 تعال", dark) { SpiderService.send(this, SpiderService.ACTION_CALL) },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        r1.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        r1.addView(UI.button(this, "🕸 انسج", dark) { SpiderService.send(this, SpiderService.ACTION_WEB) },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        r1.addView(View(this), LinearLayout.LayoutParams(dp(8), 1))
        r1.addView(UI.button(this, "🧹 امسح", dark) { SpiderService.send(this, SpiderService.ACTION_RESET_WEB) },
            LinearLayout.LayoutParams(0, dp(46), 1f))
        cmds.addView(r1, UI.lp())
        cmds.addView(
            UI.text(this, "نصيحة: اضغط على العنكبوت ليهرب، واضغط مطولاً لتحمله وتضعه حيث تشاء.", 12f, UI.SUB),
            UI.lp(top = dp(10))
        )
        root.addView(cmds, UI.lp(bottom = dp(14)))

        root.addView(
            UI.button(this, "⚙  الإعدادات والذكاء الاصطناعي", UI.ACCENT_DARK) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }, UI.lp(h = dp(54), bottom = dp(18))
        )

        val credit = UI.text(
            this,
            "تطوير: ${Prefs.DEV_NAME_AR} (${Prefs.DEV_NAME_EN})\n${Prefs.DEV_EMAIL}",
            12.5f, UI.SUB
        )
        credit.gravity = Gravity.CENTER
        root.addView(credit, UI.lp())
    }

    private fun addPerm(
        parent: LinearLayout, title: String, desc: String,
        ok: () -> Boolean, actionLabel: String, action: () -> Unit
    ) {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.layoutDirection = View.LAYOUT_DIRECTION_RTL
        row.setPadding(0, dp(6), 0, dp(6))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(UI.text(this, title, 15f, UI.TEXT, true))
        col.addView(UI.text(this, desc, 12f, UI.SUB))
        row.addView(col, LinearLayout.LayoutParams(0, UI.WRAP, 1f))
        val st = UI.text(this, "", 18f, UI.GREEN, true)
        st.visibility = View.GONE
        row.addView(st, LinearLayout.LayoutParams(UI.WRAP, UI.WRAP))
        val b = UI.button(this, actionLabel) { action() }
        val bp = LinearLayout.LayoutParams(UI.WRAP, dp(40))
        bp.marginStart = dp(8)
        row.addView(b, bp)
        parent.addView(row, UI.lp())
        rows.add(PermRow(st, b, ok))
    }

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return s.contains(packageName) && s.contains("SpiderAccessibilityService")
    }

    private fun notifGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun batteryOk(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun toggle() {
        if (SpiderService.running) {
            stopService(Intent(this, SpiderService::class.java))
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "امنح صلاحية «الظهور فوق التطبيقات» أولاً", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
                return
            }
            SpiderService.start(this)
            Toast.makeText(this, "اذهب إلى الشاشة الرئيسية لترى العنكبوت 🕷", Toast.LENGTH_LONG).show()
        }
        handler.postDelayed({ refresh() }, 500)
    }

    private fun refresh() {
        val on = SpiderService.running
        statusTv.text = if (on) "● يعمل الآن" else "● متوقف"
        statusTv.setTextColor(if (on) UI.GREEN else UI.SUB)
        toggleBtn.text = if (on) "إيقاف العنكبوت" else "تشغيل العنكبوت"
        UI.setButtonColor(toggleBtn, if (on) Color.parseColor("#3A3A4A") else UI.ACCENT)
        for (r in rows) {
            val ok = r.ok()
            r.status.text = "✓"
            r.status.visibility = if (ok) View.VISIBLE else View.GONE
            r.button.visibility = if (ok) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }
}
