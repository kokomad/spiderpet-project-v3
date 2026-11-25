package com.khader.spiderpet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class Mode { IDLE, WALK, INSPECT, WEB, REST, FLEE, EXIT, DRAG }

/**
 * Procedural, realistic black-widow spider: 8 two-segment legs with a tetrapod gait
 * (alternating leg groups), glossy abdomen with red hourglass, burst-and-pause locomotion,
 * and a web that is spun thread by thread in a screen corner.
 * Developer: خضر أبو صالحه (khader abo salha)
 */
class SpiderView(context: Context) : View(context) {

    private class Leg(val idx: Int, val side: Int) {
        var fx = 0f
        var fy = 0f
        var sx = 0f
        var sy = 0f
        var tx = 0f
        var ty = 0f
        var t = 1f
        var dur = 0.15f
        val group: Int get() = (idx + (if (side > 0) 1 else 0)) % 2
        val stepping: Boolean get() = t < 1f
    }

    private class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
        var built = 0f
    }

    companion object {
        private val HIP_A = floatArrayOf(0.25f, 0.19f, 0.13f, 0.07f)
        private val REST_A = floatArrayOf(0.88f, 0.46f, -0.16f, -0.72f)
        private val REST_M = floatArrayOf(0.60f, 0.90f, 0.90f, 0.70f)
        private const val L1 = 0.58f
        private const val L2 = 0.68f
        private val PIF = PI.toFloat()
        private val TWO_PI = (2.0 * PI).toFloat()
        private const val WEB_CHUNK = 8
    }

    // ---- configuration -------------------------------------------------------------------
    var sizePx = 150f
        private set
    var speedFactor = 1f
    var corner = 1
        private set
    var webEnabled = true
    private var W = 1080f
    private var H = 2400f
    private val density = resources.displayMetrics.density

    // ---- state ----------------------------------------------------------------------------
    var posX = 200f
        private set
    var posY = 400f
        private set
    var heading = 1.2f
        private set
    var hidden = true
        private set
    var mode = Mode.IDLE
        private set
    private var vel = 0f
    private var tx = 0f
    private var ty = 0f
    private var time = 0f
    private var modeTimer = 0f
    private var pauseTimer = 0f
    private var idleNotified = false
    private var entering = false
    private var lifted = false
    private var twitchTimer = 1f

    var onIdle: (() -> Unit)? = null
    var onOpenIcon: ((String) -> Unit)? = null

    // inspect
    private var standX = 0f
    private var standY = 0f
    private var inspectArrived = false
    private var inspectSeconds = 3f
    private var openLabel: String? = null
    private var hasProbe = false
    private var probeX = 0f
    private var probeY = 0f
    private var probeTimer = 0f
    private var probeSide = 1

    // web
    private val segs = ArrayList<Seg>()
    private var segIdx = 0
    private var segPhase = 0
    private var chunkLeft = 0
    private var hubX = 0f
    private var hubY = 0f

    // bubble
    private var bubble = ""
    private var bubbleT = 0f

    private val legs = Array(8) { Leg(it % 4, if (it < 4) 1 else -1) }

    // ---- paints ---------------------------------------------------------------------------
    private val legPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0A0A0C"); style = Paint.Style.FILL }
    private val legHi = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55555F"); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val jointHi = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 235, 235, 248) }
    private val shadowStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 0, 0, 0); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val shadowFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#D91E18"); style = Paint.Style.FILL }
    private val webDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 0, 0, 0); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val webLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(225, 238, 243, 250); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val bubbleBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(225, 20, 20, 28) }
    private val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935"); style = Paint.Style.STROKE
    }
    private val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
    }

    private val path = Path()
    private val hg = Path()
    private val rect = RectF()
    private val tmp = FloatArray(2)
    private val knee = FloatArray(2)
    private val mat = Matrix()
    private var abShader: RadialGradient? = null
    private var ceShader: RadialGradient? = null
    private var specShader: RadialGradient? = null
    private var shadowShader: RadialGradient? = null

    init {
        configure(56f * density, 1f, 1, true)
    }

    // ---- public API -----------------------------------------------------------------------

    fun configure(sizePx: Float, speedFactor: Float, corner: Int, webEnabled: Boolean) {
        val resize = abs(this.sizePx - sizePx) > 0.5f || abShader == null
        this.sizePx = sizePx
        this.speedFactor = speedFactor
        this.webEnabled = webEnabled
        val cornerChanged = this.corner != corner
        this.corner = corner
        if (resize) rebuildShaders()
        if (cornerChanged || segs.isEmpty()) planWeb()
        if (resize) placeFeet()
    }

    fun setScreen(w: Int, h: Int) {
        W = w.toFloat()
        H = h.toFloat()
        posX = posX.coerceIn(0f, W)
        posY = posY.coerceIn(0f, H)
        planWeb()
    }

    fun isIdle(): Boolean = mode == Mode.IDLE

    val webProgress: Float
        get() = if (segs.isEmpty()) 1f else segs.sumOf { it.built.toDouble() }.toFloat() / segs.size

    fun say(text: String, seconds: Float = 3.6f) {
        if (text.isBlank()) return
        bubble = text
        bubbleT = seconds
    }

    fun setShown(show: Boolean) {
        if (show && hidden) spawn()
        else if (!show && !hidden && mode != Mode.EXIT) {
            val dl = posX
            val dr = W - posX
            val dt = posY
            val db = H - posY
            val m = min(min(dl, dr), min(dt, db))
            val o = sizePx * 1.6f
            when (m) {
                dl -> { tx = -o; ty = posY }
                dr -> { tx = W + o; ty = posY }
                dt -> { tx = posX; ty = -o }
                else -> { tx = posX; ty = H + o }
            }
            hasProbe = false
            lifted = false
            mode = Mode.EXIT
        }
    }

    fun resetWeb() {
        planWeb()
        say("سأبدأ من جديد...")
    }

    fun startWebNow() {
        if (hidden) return
        if (!webEnabled) {
            say("الشبكة معطلة من الإعدادات")
            return
        }
        beginWeb()
    }

    fun callSpider() {
        if (hidden) return
        say("قادم!")
        goTo(W * 0.5f, H * 0.55f, Mode.WALK)
    }

    fun flee() {
        if (hidden || mode == Mode.DRAG || mode == Mode.EXIT) return
        hasProbe = false
        var nx: Float
        var ny: Float
        var tries = 0
        do {
            nx = sizePx + Random.nextFloat() * (W - 2 * sizePx)
            ny = sizePx + Random.nextFloat() * (H - 2 * sizePx)
            tries++
        } while (hypot(nx - posX, ny - posY) < min(W, H) * 0.3f && tries < 8)
        tx = nx
        ty = ny
        if (Random.nextFloat() < 0.4f) say("!")
        mode = Mode.FLEE
    }

    fun beginDrag() {
        if (hidden) return
        mode = Mode.DRAG
        lifted = true
        hasProbe = false
        say("أنزلني!", 2f)
    }

    fun dragTo(px: Float, py: Float) {
        if (mode != Mode.DRAG) return
        val ox = px - posX
        val oy = py - posY
        if (hypot(ox, oy) > 4f) {
            val want = atan2(oy, ox)
            heading += angDiff(want, heading) * 0.25f
        }
        posX = px
        posY = py
    }

    fun endDrag() {
        if (mode != Mode.DRAG) return
        lifted = false
        toIdle()
    }

    fun apply(d: Decision) {
        if (hidden || mode == Mode.DRAG || mode == Mode.EXIT) return
        say(d.say)
        hasProbe = false
        when (d.act) {
            Act.WANDER -> goTo(d.nx * W, d.ny * H, Mode.WALK)
            Act.INSPECT, Act.OPEN -> {
                val ic = d.icon
                if (ic != null) approachIcon(ic, d.act == Act.OPEN, d.seconds)
                else goTo(d.nx * W, d.ny * H, Mode.WALK)
            }
            Act.WEB -> if (webEnabled) beginWeb() else goTo(d.nx * W, d.ny * H, Mode.WALK)
            Act.REST -> {
                mode = Mode.REST
                modeTimer = d.seconds.coerceIn(1.5f, 12f)
            }
        }
    }

    // ---- behaviours -----------------------------------------------------------------------

    private fun goTo(x: Float, y: Float, m: Mode) {
        tx = x.coerceIn(sizePx * 0.5f, max(sizePx * 0.5f, W - sizePx * 0.5f))
        ty = y.coerceIn(sizePx * 0.5f, max(sizePx * 0.5f, H - sizePx * 0.5f))
        mode = m
    }

    private fun toIdle() {
        mode = Mode.IDLE
        idleNotified = false
        hasProbe = false
        pauseTimer = 0f
    }

    private fun spawn() {
        hidden = false
        entering = true
        val o = sizePx * 1.4f
        when (Random.nextInt(4)) {
            0 -> { posX = -o; posY = H * (0.2f + 0.6f * Random.nextFloat()); heading = 0f }
            1 -> { posX = W + o; posY = H * (0.2f + 0.6f * Random.nextFloat()); heading = PIF }
            2 -> { posX = W * (0.2f + 0.6f * Random.nextFloat()); posY = -o; heading = PIF / 2f }
            else -> { posX = W * (0.2f + 0.6f * Random.nextFloat()); posY = H + o; heading = -PIF / 2f }
        }
        vel = 0f
        placeFeet()
        goTo(W * (0.25f + 0.5f * Random.nextFloat()), H * (0.3f + 0.4f * Random.nextFloat()), Mode.WALK)
    }

    private fun approachIcon(ic: IconTarget, open: Boolean, seconds: Float) {
        var vx = posX - ic.cx
        var vy = posY - ic.cy
        var len = hypot(vx, vy)
        if (len < 1f) {
            val a = Random.nextFloat() * TWO_PI
            vx = cos(a)
            vy = sin(a)
            len = 1f
        }
        val sd = sizePx * 0.9f
        standX = (ic.cx + vx / len * sd).coerceIn(sizePx * 0.5f, W - sizePx * 0.5f)
        standY = (ic.cy + vy / len * sd).coerceIn(sizePx * 0.5f, H - sizePx * 0.5f)
        probeX = ic.cx
        probeY = ic.cy
        inspectArrived = false
        inspectSeconds = seconds.coerceIn(2f, 8f)
        openLabel = if (open) ic.label else null
        mode = Mode.INSPECT
    }

    private fun beginWeb() {
        if (segIdx >= segs.size) {
            say("الشبكة جاهزة!")
            goTo(hubX, hubY, Mode.WALK)
            return
        }
        chunkLeft = WEB_CHUNK
        segPhase = 0
        mode = Mode.WEB
    }

    // ---- simulation -----------------------------------------------------------------------

    fun step(dt: Float) {
        time += dt
        if (hidden) return
        if (bubbleT > 0f) bubbleT -= dt
        if (pauseTimer > 0f) pauseTimer -= dt
        when (mode) {
            Mode.IDLE -> {
                vel += (0f - vel) * min(1f, dt * 8f)
                twitch(dt)
                if (!idleNotified) {
                    idleNotified = true
                    onIdle?.invoke()
                }
            }
            Mode.WALK -> {
                val d = moveToward(tx, ty, 1f, dt, true)
                if (d < sizePx * 0.25f) toIdle()
            }
            Mode.FLEE -> {
                val d = moveToward(tx, ty, 2.8f, dt, false)
                if (d < sizePx * 0.3f) toIdle()
            }
            Mode.EXIT -> {
                val d = moveToward(tx, ty, 2.2f, dt, false)
                if (d < sizePx * 0.4f) {
                    hidden = true
                    vel = 0f
                    mode = Mode.IDLE
                    idleNotified = true
                }
            }
            Mode.REST -> {
                vel += (0f - vel) * min(1f, dt * 8f)
                twitch(dt)
                modeTimer -= dt
                if (modeTimer <= 0f) toIdle()
            }
            Mode.INSPECT -> stepInspect(dt)
            Mode.WEB -> stepWeb(dt)
            Mode.DRAG -> {
                vel = 0f
                heading += sin(time * 11f) * 0.02f
            }
        }
        keepInBounds()
        updateLegs(dt)
    }

    private fun stepInspect(dt: Float) {
        if (!inspectArrived) {
            val d = moveToward(standX, standY, 1f, dt, true)
            if (d < sizePx * 0.3f) {
                inspectArrived = true
                modeTimer = inspectSeconds
                hasProbe = true
                probeTimer = 0f
            }
        } else {
            val diff = angDiff(atan2(probeY - posY, probeX - posX), heading)
            heading += diff.coerceIn(-3f * dt, 3f * dt)
            vel += (0f - vel) * min(1f, dt * 8f)
            probeTimer -= dt
            if (probeTimer <= 0f) {
                probeTimer = 0.28f + Random.nextFloat() * 0.12f
                probeSide = -probeSide
                val leg = legs.first { it.idx == 0 && it.side == probeSide }
                if (!leg.stepping) {
                    beginStep(
                        leg,
                        probeX + (Random.nextFloat() - 0.5f) * sizePx * 0.25f,
                        probeY + (Random.nextFloat() - 0.5f) * sizePx * 0.25f,
                        0.13f
                    )
                }
            }
            modeTimer -= dt
            if (modeTimer <= 0f) {
                hasProbe = false
                val ol = openLabel
                openLabel = null
                if (ol != null) onOpenIcon?.invoke(ol)
                toIdle()
            }
        }
    }

    private fun stepWeb(dt: Float) {
        if (segIdx >= segs.size || chunkLeft <= 0) {
            if (segIdx >= segs.size) say("انتهيت من الشبكة!")
            mode = Mode.REST
            modeTimer = 2f + Random.nextFloat() * 2f
            return
        }
        val sg = segs[segIdx]
        if (segPhase == 0) {
            val d = moveToward(sg.x1, sg.y1, 1.2f, dt, false)
            if (d < sizePx * 0.22f) segPhase = 1
        } else {
            val len = hypot(sg.x2 - sg.x1, sg.y2 - sg.y1)
            val d = moveToward(sg.x2, sg.y2, 0.55f, dt, false)
            sg.built = (1f - d / max(len, 1f)).coerceIn(0f, 1f)
            if (d < sizePx * 0.18f) {
                sg.built = 1f
                segIdx++
                segPhase = 0
                chunkLeft--
            }
        }
    }

    private fun moveToward(px: Float, py: Float, spMul: Float, dt: Float, pauses: Boolean): Float {
        val dx = px - posX
        val dy = py - posY
        val dist = hypot(dx, dy)
        val want = atan2(dy, dx)
        val diff = angDiff(want, heading)
        val maxTurn = 5.5f * dt * (if (dist < sizePx) 1.6f else 1f) * (0.6f + 0.4f * speedFactor)
        heading += diff.coerceIn(-maxTurn, maxTurn)
        if (pauses && pauseTimer <= 0f && dist > sizePx * 1.5f && Random.nextFloat() < dt * 0.4f) {
            pauseTimer = 0.15f + Random.nextFloat() * 0.5f
        }
        val align = max(0f, cos(diff))
        val base = sizePx * 2.4f * speedFactor * spMul
        val near = min(1f, dist / (sizePx * 0.9f) + 0.25f)
        val desired = if (pauseTimer > 0f) 0f else base * align * align * near
        vel += (desired - vel) * min(1f, dt * 7f)
        val move = min(vel * dt, dist)
        heading += sin(time * 6.3f) * 0.01f * min(1f, vel / sizePx)
        posX += cos(heading) * move
        posY += sin(heading) * move
        return dist
    }

    private fun keepInBounds() {
        if (entering) {
            val m = sizePx * 0.5f
            if (posX in m..(W - m) && posY in m..(H - m)) entering = false
            return
        }
        if (mode == Mode.EXIT) return
        val m = if (mode == Mode.WEB) -sizePx * 0.15f else sizePx * 0.45f
        posX = posX.coerceIn(m, W - m)
        posY = posY.coerceIn(m, H - m)
    }

    private fun angDiff(a: Float, b: Float): Float {
        var d = a - b
        while (d > PIF) d -= TWO_PI
        while (d < -PIF) d += TWO_PI
        return d
    }

    // ---- legs -----------------------------------------------------------------------------

    private fun restPos(leg: Leg, out: FloatArray) {
        val a = REST_A[leg.idx] * sizePx
        val m = REST_M[leg.idx] * sizePx * leg.side
        val c = cos(heading)
        val s = sin(heading)
        out[0] = posX + c * a - s * m
        out[1] = posY + s * a + c * m
    }

    private fun hipPos(leg: Leg, out: FloatArray) {
        val a = HIP_A[leg.idx] * sizePx
        val m = 0.05f * sizePx * leg.side
        val c = cos(heading)
        val s = sin(heading)
        out[0] = posX + c * a - s * m
        out[1] = posY + s * a + c * m
    }

    private fun placeFeet() {
        for (l in legs) {
            restPos(l, tmp)
            l.fx = tmp[0]
            l.fy = tmp[1]
            l.t = 1f
        }
    }

    private fun beginStep(leg: Leg, x: Float, y: Float, dur: Float) {
        leg.sx = leg.fx
        leg.sy = leg.fy
        leg.tx = x
        leg.ty = y
        leg.t = 0f
        leg.dur = dur
    }

    private fun twitch(dt: Float) {
        twitchTimer -= dt
        if (twitchTimer > 0f) return
        twitchTimer = 0.8f + Random.nextFloat() * 2.2f
        val leg = legs[Random.nextInt(8)]
        if (leg.stepping) return
        restPos(leg, tmp)
        beginStep(
            leg,
            tmp[0] + (Random.nextFloat() - 0.5f) * sizePx * 0.22f,
            tmp[1] + (Random.nextFloat() - 0.5f) * sizePx * 0.22f,
            0.22f
        )
    }

    private fun updateLegs(dt: Float) {
        val c = cos(heading)
        val s = sin(heading)
        var anyA = false
        var anyB = false
        for (l in legs) {
            if (l.stepping) {
                l.t += dt / l.dur
                if (l.t >= 1f) {
                    l.t = 1f
                    l.fx = l.tx
                    l.fy = l.ty
                } else {
                    val e = l.t * l.t * (3f - 2f * l.t)
                    l.fx = l.sx + (l.tx - l.sx) * e
                    l.fy = l.sy + (l.ty - l.sy) * e
                }
            }
            if (l.stepping) {
                if (l.group == 0) anyA = true else anyB = true
            }
        }
        val speedRel = (vel / sizePx).coerceIn(0f, 4f)
        val thr = sizePx * (if (lifted) 0.07f else 0.2f)
        val dur = if (lifted) 0.08f else (0.17f / (0.7f + speedRel * 0.5f)).coerceIn(0.07f, 0.2f)
        val lead = vel * 0.09f
        for (l in legs) {
            if (l.stepping) continue
            if (hasProbe && l.idx == 0) continue
            restPos(l, tmp)
            var rx = tmp[0] + c * lead
            var ry = tmp[1] + s * lead
            if (lifted) {
                rx += sin(time * 22f + l.idx * 1.7f + l.side) * sizePx * 0.12f
                ry += cos(time * 19f + l.idx * 2.1f) * sizePx * 0.12f
            }
            val dist = hypot(l.fx - rx, l.fy - ry)
            val canStep = if (l.group == 0) !anyB else !anyA
            if (dist > thr && canStep) {
                beginStep(l, rx + c * lead * 0.6f, ry + s * lead * 0.6f, dur)
                if (l.group == 0) anyA = true else anyB = true
            }
        }
        // never let a planted foot be stretched beyond the leg's reach
        val maxReach = (L1 + L2) * sizePx * 0.97f
        for (l in legs) {
            if (l.stepping) continue
            hipPos(l, tmp)
            val dx = l.fx - tmp[0]
            val dy = l.fy - tmp[1]
            val d = hypot(dx, dy)
            if (d > maxReach) {
                l.fx = tmp[0] + dx / d * maxReach
                l.fy = tmp[1] + dy / d * maxReach
            }
        }
    }

    // ---- shaders --------------------------------------------------------------------------

    private fun rebuildShaders() {
        val r = sizePx * 0.30f
        abShader = RadialGradient(
            0f, 0f, r * 1.25f,
            intArrayOf(Color.parseColor("#33333A"), Color.parseColor("#0D0D10"), Color.parseColor("#000000")),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        val rc = sizePx * 0.17f
        ceShader = RadialGradient(
            0f, 0f, rc * 1.3f,
            intArrayOf(Color.parseColor("#2A2A30"), Color.parseColor("#0B0B0E"), Color.parseColor("#000000")),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
        specShader = RadialGradient(
            0f, 0f, r * 0.55f,
            intArrayOf(Color.argb(200, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            null, Shader.TileMode.CLAMP
        )
        shadowShader = RadialGradient(
            0f, 0f, r * 1.7f,
            intArrayOf(Color.argb(95, 0, 0, 0), Color.argb(0, 0, 0, 0)),
            null, Shader.TileMode.CLAMP
        )
        // hourglass (local frame: +x forward)
        val u = r * 0.62f
        hg.reset()
        hg.moveTo(-0.5f * u, -0.21f * u)
        hg.lineTo(0f, -0.05f * u)
        hg.lineTo(0.5f * u, -0.21f * u)
        hg.lineTo(0.5f * u, 0.21f * u)
        hg.lineTo(0f, 0.05f * u)
        hg.lineTo(-0.5f * u, 0.21f * u)
        hg.close()
    }

    // ---- web ------------------------------------------------------------------------------

    private fun planWeb() {
        segs.clear()
        segIdx = 0
        segPhase = 0
        val left = corner == 0 || corner == 2
        val top = corner == 0 || corner == 1
        val cx = if (left) 0f else W
        val cy = if (top) 0f else H
        val sx = if (left) 1f else -1f
        val sy = if (top) 1f else -1f
        val n = 7
        val k = 6
        val rw = min(W * 0.62f, H * 0.30f)
        hubX = cx + sx * rw * 0.12f
        hubY = cy + sy * rw * 0.12f
        val rnd = Random(corner * 31 + 7)
        val px = Array(k + 1) { FloatArray(n) }
        val py = Array(k + 1) { FloatArray(n) }
        for (ki in 1..k) {
            val r = rw * (0.14f + 0.86f * ki / k)
            for (i in 0 until n) {
                val phi = i.toFloat() / (n - 1) * PIF / 2f
                val jit = 1f + (rnd.nextFloat() - 0.5f) * 0.08f
                px[ki][i] = cx + sx * cos(phi) * r * jit
                py[ki][i] = cy + sy * sin(phi) * r * jit
            }
        }
        for (i in 0 until n) segs.add(Seg(cx, cy, px[k][i], py[k][i]))
        for (ki in 1..k) {
            val fwd = ki % 2 == 1
            for (j in 0 until n - 1) {
                val i = if (fwd) j else n - 2 - j
                val a = if (fwd) i else i + 1
                val b = if (fwd) i + 1 else i
                segs.add(Seg(px[ki][a], py[ki][a], px[ki][b], py[ki][b]))
            }
        }
        if (mode == Mode.WEB) toIdle()
    }

    private fun drawWeb(c: Canvas) {
        webDark.strokeWidth = 2.4f * density
        webLight.strokeWidth = 1.1f * density
        for (sg in segs) {
            if (sg.built <= 0.001f) continue
            val ex = sg.x1 + (sg.x2 - sg.x1) * sg.built
            val ey = sg.y1 + (sg.y2 - sg.y1) * sg.built
            c.drawLine(sg.x1, sg.y1, ex, ey, webDark)
            c.drawLine(sg.x1, sg.y1, ex, ey, webLight)
        }
    }

    // ---- drawing --------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        drawWeb(canvas)
        if (hidden) return
        canvas.save()
        if (lifted) canvas.scale(1.15f, 1.15f, posX, posY)
        drawShadow(canvas)
        for (l in legs) drawLeg(canvas, l, true)
        for (l in legs) drawLeg(canvas, l, false)
        drawBody(canvas)
        canvas.restore()
        drawBubble(canvas)
    }

    private fun drawShadow(c: Canvas) {
        val r = sizePx * 0.30f
        val off = if (lifted) sizePx * 0.22f else sizePx * 0.07f
        val sx = posX - cos(heading) * sizePx * 0.17f + off
        val sy = posY - sin(heading) * sizePx * 0.17f + off * 1.3f
        mat.setTranslate(sx, sy)
        shadowShader?.setLocalMatrix(mat)
        shadowFill.shader = shadowShader
        c.drawCircle(sx, sy, r * 1.7f, shadowFill)
    }

    private fun computeKnee(leg: Leg, hx: Float, hy: Float, lift: Float) {
        val dx = leg.fx - hx
        val dy = leg.fy - hy
        val d0 = max(hypot(dx, dy), 0.001f)
        val l1 = L1 * sizePx
        val l2 = L2 * sizePx
        val d = (d0 * (1f - 0.2f * lift)).coerceIn(abs(l1 - l2) + 1f, l1 + l2 - 1f)
        val a = (l1 * l1 - l2 * l2 + d * d) / (2f * d)
        val h = sqrt(max(0f, l1 * l1 - a * a))
        val ux = dx / d0
        val uy = dy / d0
        var pxv = -uy
        var pyv = ux
        val ox = -sin(heading) * leg.side
        val oy = cos(heading) * leg.side
        if (pxv * ox + pyv * oy < 0f) {
            pxv = -pxv
            pyv = -pyv
        }
        knee[0] = hx + ux * a + pxv * h
        knee[1] = hy + uy * a + pyv * h
    }

    private fun taper(c: Canvas, x1: Float, y1: Float, w1: Float, x2: Float, y2: Float, w2: Float, p: Paint) {
        val dx = x2 - x1
        val dy = y2 - y1
        val len = hypot(dx, dy)
        if (len < 0.5f) return
        val nx = -dy / len
        val ny = dx / len
        path.reset()
        path.moveTo(x1 + nx * w1 / 2f, y1 + ny * w1 / 2f)
        path.lineTo(x2 + nx * w2 / 2f, y2 + ny * w2 / 2f)
        path.lineTo(x2 - nx * w2 / 2f, y2 - ny * w2 / 2f)
        path.lineTo(x1 - nx * w1 / 2f, y1 - ny * w1 / 2f)
        path.close()
        c.drawPath(path, p)
    }

    private fun drawLeg(c: Canvas, leg: Leg, shadow: Boolean) {
        hipPos(leg, tmp)
        val hx = tmp[0]
        val hy = tmp[1]
        val lift = if (leg.stepping) sin(PIF * leg.t) else 0f
        computeKnee(leg, hx, hy, lift)
        val kx = knee[0]
        val ky = knee[1]
        if (shadow) {
            val off = sizePx * (0.05f + 0.08f * lift) + (if (lifted) sizePx * 0.15f else 0f)
            shadowStroke.strokeWidth = sizePx * 0.045f
            c.drawLine(hx + off, hy + off * 1.3f, kx + off, ky + off * 1.3f, shadowStroke)
            c.drawLine(kx + off, ky + off * 1.3f, leg.fx + off, leg.fy + off * 1.3f, shadowStroke)
            return
        }
        val s = sizePx
        taper(c, hx, hy, s * 0.055f, kx, ky, s * 0.043f, legPaint)
        c.drawCircle(kx, ky, s * 0.032f, legPaint)
        taper(c, kx, ky, s * 0.042f, leg.fx, leg.fy, s * 0.006f, legPaint)
        legHi.strokeWidth = max(1f, s * 0.011f)
        c.drawLine(hx, hy, kx, ky, legHi)
        c.drawLine(kx, ky, (kx + leg.fx) / 2f, (ky + leg.fy) / 2f, legHi)
        c.drawCircle(kx - s * 0.008f, ky - s * 0.01f, s * 0.014f, jointHi)
        c.drawCircle((kx + leg.fx) / 2f, (ky + leg.fy) / 2f, s * 0.008f, jointHi)
    }

    private fun drawBody(c: Canvas) {
        val s = sizePx
        val r = s * 0.30f
        val rc = s * 0.17f
        val ch = cos(heading)
        val sh = sin(heading)
        val deg = heading * 57.29578f
        val breathe = 1f + sin(time * 2.2f) * 0.015f
        val wob = sin(time * 9f) * 0.04f * min(1f, vel / s)

        // light comes from the upper-left of the screen
        val wx = -0.34f
        val wy = -0.42f
        val lx = (wx * ch + wy * sh) * r
        val ly = (-wx * sh + wy * ch) * r

        // abdomen
        val ax = posX - ch * s * 0.17f
        val ay = posY - sh * s * 0.17f
        c.save()
        c.translate(ax, ay)
        c.rotate(deg + wob * 57.29578f)
        c.scale(breathe, breathe)
        mat.setTranslate(lx, ly)
        abShader?.setLocalMatrix(mat)
        bodyPaint.shader = abShader
        rect.set(-r * 1.08f, -r * 0.95f, r * 1.08f, r * 0.95f)
        c.drawOval(rect, bodyPaint)
        bodyPaint.shader = null
        c.translate(-r * 0.12f, 0f)
        c.drawPath(hg, redPaint)
        c.restore()

        mat.setTranslate(ax + wx * r, ay + wy * r)
        specShader?.setLocalMatrix(mat)
        specPaint.shader = specShader
        c.drawCircle(ax + wx * r, ay + wy * r, r * 0.55f, specPaint)

        // cephalothorax
        val cx = posX + ch * s * 0.20f
        val cy = posY + sh * s * 0.20f
        c.save()
        c.translate(cx, cy)
        c.rotate(deg)
        mat.setTranslate(lx * 0.5f, ly * 0.5f)
        ceShader?.setLocalMatrix(mat)
        bodyPaint.shader = ceShader
        rect.set(-rc * 1.05f, -rc * 0.85f, rc * 1.1f, rc * 0.85f)
        c.drawOval(rect, bodyPaint)
        bodyPaint.shader = null
        // chelicerae
        c.drawCircle(rc * 0.95f, -rc * 0.32f, rc * 0.2f, legPaint)
        c.drawCircle(rc * 0.95f, rc * 0.32f, rc * 0.2f, legPaint)
        c.restore()
        mat.setTranslate(cx + wx * rc * 0.6f, cy + wy * rc * 0.6f)
        specShader?.setLocalMatrix(mat)
        specPaint.alpha = 140
        c.drawCircle(cx + wx * rc * 0.6f, cy + wy * rc * 0.6f, rc * 0.45f, specPaint)
        specPaint.alpha = 255

        // pedipalps (little feelers)
        val wig = sin(time * 8f) * 0.18f
        for (side in intArrayOf(-1, 1)) {
            val bx = cx + ch * rc * 0.9f - sh * side * rc * 0.35f
            val by = cy + sh * rc * 0.9f + ch * side * rc * 0.35f
            val a = heading + side * (0.45f + wig)
            val ex = bx + cos(a) * s * 0.14f
            val ey = by + sin(a) * s * 0.14f
            taper(c, bx, by, s * 0.03f, ex, ey, s * 0.012f, legPaint)
        }
    }

    private fun drawBubble(c: Canvas) {
        if (bubbleT <= 0f || bubble.isEmpty()) return
        val ts = max(sizePx * 0.22f, 13f * density)
        bubbleText.textSize = ts
        bubbleStroke.strokeWidth = 1.2f * density
        val w = bubbleText.measureText(bubble) + ts * 1.2f
        val h = ts * 1.9f
        val alpha = (min(1f, bubbleT / 0.5f) * 255).toInt()
        var left = posX - w / 2f
        left = left.coerceIn(6f * density, max(6f * density, W - w - 6f * density))
        var top = posY - sizePx * 1.25f - h
        if (top < 6f * density) top = posY + sizePx * 1.1f
        rect.set(left, top, left + w, top + h)
        bubbleBg.alpha = (alpha * 0.88f).toInt()
        bubbleStroke.alpha = alpha
        bubbleText.alpha = alpha
        c.drawRoundRect(rect, h / 2f, h / 2f, bubbleBg)
        c.drawRoundRect(rect, h / 2f, h / 2f, bubbleStroke)
        c.drawText(bubble, left + w / 2f, top + h / 2f + ts * 0.35f, bubbleText)
    }
}
