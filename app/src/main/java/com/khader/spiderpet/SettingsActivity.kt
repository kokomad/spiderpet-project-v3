package com.khader.spiderpet

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings: AI (Gemini) • behavior • about developer. Developer: خضر أبو صالحه (khader abo salha) */
class SettingsActivity : Activity() {

    private lateinit var aiStatus: TextView
    private val cornerBtns = ArrayList<Button>()

    private fun dp(v: Int) = UI.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = UI.BG
        window.navigationBarColor = UI.BG

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(UI.BG)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.setPadding(dp(18), dp(30), dp(18), dp(28))
        scroll.addView(root, LinearLayout.LayoutParams(UI.MATCH, UI.WRAP))
        setContentView(scroll)

        root.addView(UI.text(this, "الإعدادات", 26f, UI.TEXT, true), UI.lp(bottom = dp(14)))

        buildAiCard(root)
        buildBehaviorCard(root)
        buildAboutCard(root)
    }

    // ---- AI -------------------------------------------------------------------------------

    private fun buildAiCard(root: LinearLayout) {
        val card = UI.card(this)
        card.addView(UI.text(this, "🧠 الذكاء الاصطناعي (Gemini)", 18f, UI.TEXT, true), UI.lp(bottom = dp(4)))
        card.addView(
            UI.text(
                this,
                "يقرر Gemini حركات العنكبوت: أي أيقونة يتفحص، متى ينسج، وأين يتجول. بدون مفتاح يعمل العقل المحلي.",
                12.5f, UI.SUB
            ), UI.lp(bottom = dp(10))
        )

        addSwitch(card, "تفعيل عقل Gemini", Prefs.aiOn(this)) { Prefs.setAiOn(this, it) }

        card.addView(UI.text(this, "مفتاح Gemini API", 13f, UI.SUB), UI.lp(top = dp(10), bottom = dp(4)))
        val key = field(Prefs.apiKey(this), "الصق المفتاح هنا (AIza...)")
        key.transformationMethod = PasswordTransformationMethod.getInstance()
        key.onChange { Prefs.setApiKey(this, it) }
        card.addView(key, UI.lp())
        val show = CheckBox(this)
        show.text = "إظهار المفتاح"
        show.setTextColor(UI.SUB)
        show.setOnCheckedChangeListener { _, checked ->
            key.transformationMethod =
                if (checked) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
        }
        card.addView(show, UI.lp())

        card.addView(UI.text(this, "اسم النموذج", 13f, UI.SUB), UI.lp(top = dp(6), bottom = dp(4)))
        val model = field(Prefs.model(this), Prefs.DEFAULT_MODEL)
        model.onChange { Prefs.setModel(this, it) }
        card.addView(model, UI.lp())

        aiStatus = UI.text(this, Prefs.aiStatus(this).ifEmpty { "لم يتم الاختبار بعد" }, 12.5f, UI.SUB)
        card.addView(
            UI.button(this, "اختبار الاتصال بـ Gemini") { testAi() },
            UI.lp(h = dp(46), top = dp(12), bottom = dp(8))
        )
        card.addView(aiStatus, UI.lp(bottom = dp(8)))

        val link = UI.text(this, "احصل على مفتاح مجاني من: aistudio.google.com/apikey", 12.5f, Color.parseColor("#6EA8FF"))
        link.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        card.addView(link, UI.lp(bottom = dp(6)))
        card.addView(
            UI.text(
                this,
                "الخصوصية: عند التفعيل يُرسل إلى Gemini فقط أسماء الأيقونات ومواضعها على الشاشة الرئيسية وموقع العنكبوت. لا تُرسل أي صور أو رسائل أو بيانات شخصية.",
                11.5f, UI.SUB
            ), UI.lp()
        )
        root.addView(card, UI.lp(bottom = dp(14)))
    }

    private fun testAi() {
        aiStatus.text = "جارٍ الاختبار..."
        aiStatus.setTextColor(UI.SUB)
        val key = Prefs.apiKey(this)
        val model = Prefs.model(this)
        val ctx = applicationContext
        Thread {
            val r = GeminiClient.ping(ctx, key, model)
            runOnUiThread {
                aiStatus.text = r
                aiStatus.setTextColor(if (r.startsWith("✓")) UI.GREEN else Color.parseColor("#FF7B72"))
            }
        }.start()
    }

    // ---- behavior -------------------------------------------------------------------------

    private fun buildBehaviorCard(root: LinearLayout) {
        val card = UI.card(this)
        card.addView(UI.text(this, "🕷 سلوك العنكبوت", 18f, UI.TEXT, true), UI.lp(bottom = dp(8)))

        addSeek(card, "الحجم", 32, 120, Prefs.sizeDp(this), { "$it dp" }) { Prefs.setSizeDp(this, it) }
        addSeek(card, "سرعة الحركة", 1, 10, Prefs.speed(this), { "$it / 10" }) { Prefs.setSpeed(this, it) }

        addSeek(card, "الفاصل بين طلبات Gemini", 10, 300, Prefs.aiInterval(this), { "$it ثانية" }) { Prefs.setAiInterval(this, it) }

        card.addView(UI.text(this, "زاوية الشبكة", 13f, UI.SUB), UI.lp(top = dp(10), bottom = dp(6)))
        val labels = arrayOf("↖ أعلى اليسار", "↗ أعلى اليمين", "↙ أسفل اليسار", "↘ أسفل اليمين")
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        for (rowIdx in 0..1) {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            for (colIdx in 0..1) {
                val idx = rowIdx * 2 + colIdx
                val b = UI.button(this, labels[idx], Color.parseColor("#2A2A3A")) {
                    Prefs.setCorner(this, idx)
                    paintCorners()
                }
                cornerBtns.add(b)
                val p = LinearLayout.LayoutParams(0, dp(44), 1f)
                p.setMargins(dp(3), dp(3), dp(3), dp(3))
                r.addView(b, p)
            }
            grid.addView(r, UI.lp())
        }
        card.addView(grid, UI.lp(bottom = dp(8)))
        paintCorners()

        addSwitch(card, "بناء الشبكة", Prefs.webOn(this)) { Prefs.setWebOn(this, it) }
        addSwitch(card, "الظهور على الشاشة الرئيسية فقط", Prefs.homeOnly(this)) { Prefs.setHomeOnly(this, it) }
        addSwitch(card, "السماح للعنكبوت بفتح التطبيقات (تجريبي)", Prefs.openApps(this)) { Prefs.setOpenApps(this, it) }
        card.addView(
            UI.text(
                this,
                "الظهور على الرئيسية فقط وفتح التطبيقات يحتاجان تفعيل خدمة إمكانية الوصول من الشاشة الرئيسية للتطبيق.",
                11.5f, UI.SUB
            ), UI.lp(top = dp(4))
        )
        root.addView(card, UI.lp(bottom = dp(14)))
    }

    private fun paintCorners() {
        val sel = Prefs.corner(this)
        for (i in cornerBtns.indices) {
            UI.setButtonColor(cornerBtns[i], if (i == sel) UI.ACCENT else Color.parseColor("#2A2A3A"))
        }
    }

    // ---- about ----------------------------------------------------------------------------

    private fun buildAboutCard(root: LinearLayout) {
        val card = UI.card(this)
        card.addView(UI.text(this, "ℹ حول التطبيق", 18f, UI.TEXT, true), UI.lp(bottom = dp(8)))
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" }
        card.addView(UI.text(this, "العنكبوت الحي — الإصدار $ver", 15f, UI.TEXT), UI.lp(bottom = dp(10)))

        card.addView(UI.text(this, "المطوّر", 12.5f, UI.SUB))
        card.addView(UI.text(this, Prefs.DEV_NAME_AR, 20f, UI.TEXT, true))
        card.addView(UI.text(this, Prefs.DEV_NAME_EN, 14f, UI.SUB), UI.lp(bottom = dp(8)))

        val mail = UI.text(this, "✉  ${Prefs.DEV_EMAIL}", 15f, Color.parseColor("#6EA8FF"))
        mail.setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Prefs.DEV_EMAIL}")))
            } catch (e: Exception) {
                Toast.makeText(this, Prefs.DEV_EMAIL, Toast.LENGTH_LONG).show()
            }
        }
        card.addView(mail, UI.lp(bottom = dp(8)))
        val dev = UI.text(this, "صُنع بـ ❤ ليعيش عنكبوتك على شاشتك.", 12.5f, UI.SUB)
        dev.gravity = Gravity.START
        card.addView(dev, UI.lp())
        root.addView(card, UI.lp())
    }

    // ---- helpers --------------------------------------------------------------------------

    private fun field(value: String, hint: String): EditText {
        val e = EditText(this)
        e.setText(value)
        e.hint = hint
        e.setTextColor(UI.TEXT)
        e.setHintTextColor(Color.parseColor("#5A5A70"))
        e.setSingleLine(true)
        e.inputType = InputType.TYPE_CLASS_TEXT
        e.textDirection = View.TEXT_DIRECTION_LTR
        e.background = UI.roundBg(UI.FIELD, dp(12).toFloat(), UI.STROKE, dp(1))
        e.setPadding(dp(12), dp(10), dp(12), dp(10))
        return e
    }

    private fun EditText.onChange(cb: (String) -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { cb(s?.toString() ?: "") }
        })
    }

    private fun addSwitch(parent: LinearLayout, label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        val sw = Switch(this)
        sw.text = label
        sw.setTextColor(UI.TEXT)
        sw.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14.5f)
        sw.isChecked = value
        sw.setOnCheckedChangeListener { _, checked -> onChange(checked) }
        sw.setPadding(0, dp(8), 0, dp(8))
        parent.addView(sw, UI.lp())
    }

    private fun addSeek(
        parent: LinearLayout, title: String, min: Int, max: Int, value: Int,
        fmt: (Int) -> String, onChange: (Int) -> Unit
    ) {
        val head = UI.text(this, "$title: ${fmt(value)}", 13.5f, UI.TEXT)
        parent.addView(head, UI.lp(top = dp(6)))
        val sb = SeekBar(this)
        sb.max = max - min
        sb.progress = value - min
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                head.text = "$title: ${fmt(min + p)}"
                if (fromUser) onChange(min + p)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        parent.addView(sb, UI.lp())
    }

    override fun onPause() {
        // apply the new settings live to the running spider
        SpiderService.send(this, SpiderService.ACTION_RELOAD)
        super.onPause()
    }
}
