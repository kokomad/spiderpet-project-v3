package com.khader.spiderpet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Foreground service that hosts the spider overlay (above the home screen) and its "brain".
 * Developer: خضر أبو صالحه (khader abo salha) - mankhader@gmail.com
 */
class SpiderService : Service() {

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "com.khader.spiderpet.STOP"
        const val ACTION_RELOAD = "com.khader.spiderpet.RELOAD"
        const val ACTION_CALL = "com.khader.spiderpet.CALL"
        const val ACTION_WEB = "com.khader.spiderpet.WEB"
        const val ACTION_RESET_WEB = "com.khader.spiderpet.RESET_WEB"
        private const val CHANNEL = "spider_channel"

        fun start(c: Context) {
            c.startForegroundService(Intent(c, SpiderService::class.java))
        }

        fun send(c: Context, action: String) {
            if (running) c.startService(Intent(c, SpiderService::class.java).setAction(action))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var sv: SpiderView? = null
    private var touchView: View? = null
    private var canvasLp: WindowManager.LayoutParams? = null
    private var touchLp: WindowManager.LayoutParams? = null
    private var touchShown = false
    private var alive = false
    private var frameRunning = false
    private val exec: ExecutorService = Executors.newSingleThreadExecutor()
    private var pending = false
    private var lastAi = 0L
    private val recent = ArrayList<String>()

    // ---- lifecycle ------------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
        startFg()
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "امنح صلاحية الظهور فوق التطبيقات أولاً", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupViews()
        alive = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenReceiver, f)
        SpiderBridge.listener = { onBridgeChanged() }
        onBridgeChanged()
        startFrames()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                applyPrefs()
                onBridgeChanged()
            }
            ACTION_CALL -> sv?.callSpider()
            ACTION_WEB -> sv?.startWebNow()
            ACTION_RESET_WEB -> sv?.resetWeb()
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val s = realSize()
        canvasLp?.let { lp ->
            lp.width = s.x
            lp.height = s.y
            sv?.let { v -> wm?.updateViewLayout(v, lp) }
        }
        sv?.setScreen(s.x, s.y)
    }

    override fun onDestroy() {
        alive = false
        running = false
        stopFrames()
        main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
        SpiderBridge.listener = null
        try { sv?.let { wm?.removeView(it) } } catch (e: Exception) { }
        try { touchView?.let { wm?.removeView(it) } } catch (e: Exception) { }
        sv = null
        touchView = null
        exec.shutdownNow()
        super.onDestroy()
    }

    // ---- notification ---------------------------------------------------------------------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "العنكبوت الحي", NotificationManager.IMPORTANCE_MIN)
        )
    }

    private fun startFg() {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SpiderService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("العنكبوت يتجول على شاشتك")
            .setContentText("اضغط لفتح التطبيق")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notif), "إيقاف", stop).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    // ---- views ----------------------------------------------------------------------------

    private fun realSize(): Point {
        val p = Point()
        val w = wm
        if (w == null) {
            val dm = resources.displayMetrics
            p.set(dm.widthPixels, dm.heightPixels)
        } else if (Build.VERSION.SDK_INT >= 30) {
            val b = w.currentWindowMetrics.bounds
            p.set(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            w.defaultDisplay.getRealSize(p)
        }
        return p
    }

    private fun setupViews() {
        val w = wm ?: return
        val s = realSize()
        val v = SpiderView(this)
        v.setScreen(s.x, s.y)
        sv = v
        applyPrefs()

        val lp = WindowManager.LayoutParams(
            s.x, s.y,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        canvasLp = lp
        w.addView(v, lp)

        // small touchable window that follows the spider (tap = scare, long-press = pick up)
        val tv = View(this)
        tv.setBackgroundColor(Color.TRANSPARENT)
        val tsz = (v.sizePx * 1.5f).toInt()
        val tlp = WindowManager.LayoutParams(
            tsz, tsz,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        tlp.gravity = Gravity.TOP or Gravity.START
        tlp.x = -tsz
        tlp.y = -tsz
        touchLp = tlp
        touchView = tv
        tv.visibility = View.GONE
        touchShown = false
        w.addView(tv, tlp)
        installTouch(tv, v)

        v.onIdle = { onIdle() }
        v.onOpenIcon = { label ->
            if (Prefs.openApps(this)) SpiderBridge.clickIcon(label)
        }
    }

    private fun installTouch(tv: View, spider: SpiderView) {
        var downX = 0f
        var downY = 0f
        var dragging = false
        var moved = false
        val longPress = Runnable {
            dragging = true
            spider.beginDrag()
            tv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    dragging = false
                    moved = false
                    main.postDelayed(longPress, 380)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (dragging) {
                        spider.dragTo(e.rawX, e.rawY)
                    } else if (hypot(e.rawX - downX, e.rawY - downY) > 16f * resources.displayMetrics.density) {
                        moved = true
                        main.removeCallbacks(longPress)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (dragging) {
                        dragging = false
                        spider.endDrag()
                    } else if (e.actionMasked == MotionEvent.ACTION_UP && !moved) {
                        spider.flee()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyPrefs() {
        val v = sv ?: return
        val d = resources.displayMetrics.density
        val speed = Prefs.speed(this)
        val newSize = Prefs.sizeDp(this) * d
        v.configure(newSize, 0.4f + (speed - 1) * 0.18f, Prefs.corner(this), Prefs.webOn(this))
        val tsz = (newSize * 1.5f).toInt()
        touchLp?.let { lp ->
            lp.width = tsz
            lp.height = tsz
            touchView?.let { t -> try { wm?.updateViewLayout(t, lp) } catch (e: Exception) { } }
        }
    }

    // ---- frame loop -----------------------------------------------------------------------

    private var lastFrame = 0L
    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!alive || !frameRunning) return
            val dt = if (lastFrame == 0L) 0.016f else ((frameTimeNanos - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrame = frameTimeNanos
            val v = sv
            if (v != null) {
                v.step(dt)
                updateTouchWindow(v)
                v.invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startFrames() {
        if (frameRunning) return
        frameRunning = true
        lastFrame = 0L
        Choreographer.getInstance().postFrameCallback(frameCb)
    }

    private fun stopFrames() {
        frameRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
    }

    private fun updateTouchWindow(v: SpiderView) {
        val tv = touchView ?: return
        val lp = touchLp ?: return
        val shouldShow = !v.hidden
        if (shouldShow != touchShown) {
            touchShown = shouldShow
            tv.visibility = if (shouldShow) View.VISIBLE else View.GONE
        }
        if (!shouldShow) return
        val nx = (v.posX - lp.width / 2f).toInt()
        val ny = (v.posY - lp.height / 2f).toInt()
        if (nx != lp.x || ny != lp.y) {
            lp.x = nx
            lp.y = ny
            try { wm?.updateViewLayout(tv, lp) } catch (e: Exception) { }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> stopFrames()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> if (alive) startFrames()
            }
        }
    }

    // ---- brain ----------------------------------------------------------------------------

    private fun onBridgeChanged() {
        val v = sv ?: return
        val show = !Prefs.homeOnly(this) || !SpiderBridge.a11yConnected || SpiderBridge.onHome
        v.setShown(show)
    }

    private fun onIdle() {
        if (pending) return
        pending = true
        main.postDelayed({ decide() }, 400L + Random.nextLong(2200L))
    }

    private fun buildCtx(v: SpiderView): BrainContext {
        val s = realSize()
        val icons = if (SpiderBridge.onHome) SpiderBridge.icons else emptyList()
        return BrainContext(
            w = s.x, h = s.y, sx = v.posX, sy = v.posY,
            icons = icons,
            webProgress = v.webProgress,
            webEnabled = Prefs.webOn(this),
            corner = Prefs.corner(this),
            hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            recent = ArrayList(recent),
            openApps = Prefs.openApps(this) && SpiderBridge.a11yConnected
        )
    }

    private fun decide() {
        val v = sv
        if (v == null || !alive || v.hidden || !v.isIdle()) {
            pending = false
            return
        }
        val key = Prefs.apiKey(this)
        val now = System.currentTimeMillis()
        val useAi = Prefs.aiOn(this) && key.isNotEmpty() && now - lastAi > Prefs.aiInterval(this) * 1000L &&
            now >= Prefs.aiBlockedUntil(this)
        val ctx = buildCtx(v)
        if (!useAi) {
            finishDecision(LocalBrain.decide(ctx))
            return
        }
        lastAi = now
        val model = Prefs.model(this)
        val appCtx = applicationContext
        try {
            exec.execute {
                val d = GeminiClient.decide(appCtx, key, model, ctx)
                main.post { finishDecision(d ?: LocalBrain.decide(buildCtx(v))) }
            }
        } catch (e: Exception) {
            finishDecision(LocalBrain.decide(ctx))
        }
    }

    private fun finishDecision(d: Decision) {
        pending = false
        val v = sv ?: return
        if (!alive || v.hidden || !v.isIdle()) return
        recent.add(d.act.name.lowercase())
        while (recent.size > 4) recent.removeAt(0)
        v.apply(d)
    }
}
