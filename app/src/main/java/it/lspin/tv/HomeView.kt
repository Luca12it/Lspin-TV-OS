package it.lspin.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.SweepGradient
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class SettingItem(val id: String, val title: String, val sub: String, val value: String)

class Hero {
    val bmps = ArrayList<Bitmap>()
    val titles = ArrayList<String>()
    var idx = 0
    var prev = -1
    var t = 1f
    fun next() { if (bmps.size < 2) return; prev = idx; idx = (idx + 1) % bmps.size; t = 0f }
}

/**
 * Schermata principale disegnata interamente su Canvas (una sola View): molto leggera.
 * Coordinate di progetto 1672x941 (come lo screenshot), scalate sullo schermo reale.
 */
class HomeView(ctx: Context) : View(ctx) {
    var onOpen: ((Tile) -> Unit)? = null
    var onSetting: ((String) -> Unit)? = null
    var page = 0
        private set

    private var zone = 2          // 0 barra alta, 1 in evidenza, 2 riga app, 3 griglia, 4 impostazioni
    private var col = 0
    private var gIdx = 0
    private var sIdx = 0
    private var featured: List<Tile> = emptyList()
    private var apps: List<Tile> = emptyList()
    private var settings: List<SettingItem> = emptyList()
    private var lastTile: Tile? = null
    val hero = Hero()

    private val ui = Handler(Looper.getMainLooper())
    private var s = 1f; private var ox = 0f; private var oy = 0f
    private var vw = 0f; private var vh = 0f

    // cursore (coordinate di progetto)
    private var cx = 57f; private var cy = 760f; private var cw = 132f; private var ch = 160f
    private var tx = 57f; private var ty = 760f; private var tw = 132f; private var th = 160f
    private var scrollX = 0f; private var scrollXT = 0f
    private var scrollY = 0f; private var scrollYT = 0f
    private var textT = 1f; private var pageT = 1f
    private var last = 0L

    // effetti neon / animazioni
    var fx = true
    private var phase = 0f
    private var introT = 0f
    private var introStarted = false
    private var press = 0f
    private var ripple = 1f
    private var rpx = 0f; private var rpy = 0f; private var rpw = 0f; private var rph = 0f
    private var bx = 75f
    private val NEON = Theme.NEON
    private val sweep = SweepGradient(0f, 0f,
        intArrayOf(0xFF9B5CFF.toInt(), 0xFF4D7CFF.toInt(), 0xFF4DE1FF.toInt(), 0xFFFF4DD8.toInt(), 0xFF9B5CFF.toInt()), null)
    private val mtx = Matrix()
    private val shimShader = LinearGradient(-80f, 0f, 80f, 0f,
        intArrayOf(0x00FFFFFF, 0x55FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    private val shimPaint = Paint()
    private val shimPath = Path()
    private val r3 = RectF()

    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold = Typeface.create("sans-serif", Typeface.BOLD)
    private val tfLight = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val tfCond = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val r1 = RectF(); private val r2 = RectF(); private val dst = RectF(); private val src = Rect()
    private val tri = Path()
    private val topX = floatArrayOf(114f, 217f, 313f)
    private val topG = arrayOf(G.HOME, G.GRIDF, G.SLIDERS)
    private val leftP = Paint(); private val botP = Paint(); private val topP = Paint()
    private val dimP = Paint().apply { color = (Theme.SHADE or 0xE0000000.toInt()) }
    private val glow1 = Paint(); private val glow2 = Paint()
    private val artCache = LruCache<String, Bitmap>(60)
    private var ramS = ""; private var ipS = ""; private var timeS = ""; private var dateS = ""

    private val infoRun = object : Runnable {
        override fun run() { refreshInfo(); invalidate(); ui.postDelayed(this, 20000) }
    }
    private val heroRun = object : Runnable {
        override fun run() {
            if (hero.bmps.size > 1 && visibility == VISIBLE && page == 0) Sfx.play("swoosh", 0.3f)
            hero.next(); invalidate(); ui.postDelayed(this, 8000)
        }
    }

    init {
        isFocusable = false
        Thread {
            val l = (1..3).map { HeroLoader.fallback(it) }
            ui.post {
                if (hero.bmps.isEmpty()) {
                    hero.bmps.addAll(l); hero.titles.addAll(listOf("", "", "")); invalidate()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ dati
    fun setData(f: List<Tile>, a: List<Tile>) {
        featured = f; apps = a
        if (lastTile == null) lastTile = f.firstOrNull()
        gIdx = gIdx.coerceIn(0, max(0, a.size - 1))
        if (zone == 2) col = col.coerceIn(0, max(0, a.size - 1))
        retarget(true)
        if (!introStarted && a.isNotEmpty()) { introStarted = true; introT = 0f }
        invalidate()
    }

    fun replayIntro() { introStarted = true; introT = 0f; invalidate() }

    fun setSettings(l: List<SettingItem>) { settings = l; invalidate() }

    fun setHero(b: List<Bitmap>, t: List<String>) {
        if (b.isEmpty()) return
        hero.bmps.clear(); hero.titles.clear()
        hero.bmps.addAll(b); hero.titles.addAll(t)
        hero.idx = 0; hero.prev = -1; hero.t = 1f
        invalidate()
    }

    fun openSettings() { setPage(2) }
    fun setPageExternal(p: Int) { if (page != p) setPage(p) }

    fun onBack(): Boolean {
        if (page != 0) { Sfx.play("back"); setPage(0); return true }
        return false
    }

    private fun refreshInfo() {
        ramS = "RAM " + SysInfo.ram(context) + "%"; ipS = SysInfo.ip()
        timeS = SysInfo.time(); dateS = SysInfo.date()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshInfo(); ui.postDelayed(infoRun, 20000); ui.postDelayed(heroRun, 8000)
    }

    override fun onVisibilityChanged(changedView: View, vis: Int) {
        super.onVisibilityChanged(changedView, vis)
        if (vis == VISIBLE) { last = 0L; invalidate() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow(); ui.removeCallbacksAndMessages(null)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        vw = w.toFloat(); vh = h.toFloat()
        s = min(vw / 1672f, vh / 941f)
        ox = (vw - 1672f * s) / 2f; oy = (vh - 941f * s) / 2f
        val hh = oy + 540f * s
        leftP.shader = LinearGradient(0f, 0f, vw * 0.62f, 0f, (Theme.SHADE or 0xE6000000.toInt()), Theme.SHADE, Shader.TileMode.CLAMP)
        botP.shader = LinearGradient(0f, oy + 560f * s, 0f, vh, Theme.SHADE, (Theme.SHADE or 0xE0000000.toInt()), Shader.TileMode.CLAMP)
        topP.shader = LinearGradient(0f, 0f, 0f, oy + 140f * s, 0xA0000000.toInt(), 0x00000000.toInt(), Shader.TileMode.CLAMP)
        glow1.shader = RadialGradient(vw * 0.04f, vh * 0.98f, vw * 0.5f, Theme.GLOW1, 0x00000000.toInt(), Shader.TileMode.CLAMP)
        glow2.shader = RadialGradient(vw * 0.97f, vh * 0.98f, vw * 0.4f, Theme.GLOW2, 0x00000000.toInt(), Shader.TileMode.CLAMP)
        artCache.evictAll()
        retarget(true)
    }

    // ------------------------------------------------------------------ geometria
    private fun topRect(i: Int, o: RectF) { val c = topX[i]; o.set(c - 39f, 36f, c + 39f, 94f) }
    private fun featRect(i: Int, o: RectF) { o.set(55f + i * 317f, 527f, 355f + i * 317f, 693f) }
    private fun appRect(i: Int, sx: Float, o: RectF) { o.set(57f + i * 150f - sx, 760f, 189f + i * 150f - sx, 920f) }
    private fun gridRect(i: Int, sy: Float, o: RectF) {
        val c = i % 6; val r = i / 6
        o.set(31f + c * 272f, 200f + r * 172f - sy, 281f + c * 272f, 350f + r * 172f - sy)
    }
    private fun setRect(i: Int, o: RectF) { o.set(160f, 190f + i * 90f, 1512f, 266f + i * 90f) }
    private fun maxScrollX() = max(0f, 57f + apps.size * 150f - 18f + 37f - 1672f)

    private fun focusRect(o: RectF, useTarget: Boolean) {
        when (zone) {
            0 -> topRect(col, o)
            1 -> featRect(col, o)
            2 -> appRect(col, if (useTarget) scrollXT else scrollX, o)
            3 -> gridRect(gIdx, if (useTarget) scrollYT else scrollY, o)
            else -> setRect(sIdx, o)
        }
    }

    private fun retarget(snap: Boolean) {
        if (zone == 2 && apps.isNotEmpty()) {
            val x = 57f + col * 150f
            if (x - scrollXT < 57f) scrollXT = x - 57f
            else if (x + 132f - scrollXT > 1635f) scrollXT = x + 132f - 1635f
            scrollXT = scrollXT.coerceIn(0f, maxScrollX())
        }
        if (zone == 3 && apps.isNotEmpty()) {
            val y = 200f + (gIdx / 6) * 172f
            if (y - scrollYT < 190f) scrollYT = y - 200f
            else if (y + 150f - scrollYT > 925f) scrollYT = y + 150f - 925f
            val rows = (apps.size + 5) / 6
            scrollYT = scrollYT.coerceIn(0f, max(0f, 200f + rows * 172f - 6f - 941f))
        }
        focusRect(r1, true)
        tx = r1.left; ty = r1.top; tw = r1.width(); th = r1.height()
        if (snap) {
            cx = tx; cy = ty; cw = tw; ch = th
            scrollX = scrollXT; scrollY = scrollYT
        }
        invalidate()
    }

    private fun updateLast() {
        val t = when (zone) {
            1 -> featured.getOrNull(col)
            2 -> apps.getOrNull(col)
            3 -> apps.getOrNull(gIdx)
            else -> null
        }
        if (t != null && t !== lastTile) { lastTile = t; textT = 0f }
    }

    private fun enterContent() {
        when (page) {
            0 -> { zone = 2; col = col.coerceIn(0, max(0, apps.size - 1)) }
            1 -> { zone = 3; gIdx = 0; scrollYT = 0f }
            else -> { zone = 4; sIdx = 0 }
        }
    }

    private fun setPage(p: Int) {
        page = p; pageT = 0f; introStarted = true; introT = 0f
        Sfx.play("whoosh", 0.6f)
        enterContent(); updateLast(); retarget(true)
    }

    // ------------------------------------------------------------------ input
    fun handleKey(k: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-1, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(1, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -1)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, 1)
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> activate()
            else -> return false
        }
        return true
    }

    private fun move(dx: Int, dy: Int) {
        var ok = false
        when (zone) {
            0 -> if (dx != 0) {
                val n = (col + dx).coerceIn(0, 2); ok = n != col; col = n
            } else if (dy > 0) { enterContent(); ok = true }
            1 -> if (featured.isEmpty()) return else if (dx != 0) {
                val n = (col + dx).coerceIn(0, featured.size - 1); ok = n != col; col = n
            } else if (dy > 0 && apps.isNotEmpty()) { zone = 2; col = min(col, apps.size - 1); ok = true }
            else if (dy < 0) { zone = 0; col = page; ok = true }
            2 -> if (apps.isEmpty()) return else if (dx != 0) {
                val n = (col + dx).coerceIn(0, apps.size - 1); ok = n != col; col = n
            } else if (dy < 0) { zone = 0; col = page; ok = true }
            3 -> if (apps.isEmpty()) return else if (dx != 0) {
                val n = (gIdx + dx).coerceIn(0, apps.size - 1); ok = n != gIdx; gIdx = n
            } else {
                val n = gIdx + dy * 6
                if (dy < 0 && gIdx < 6) { zone = 0; col = 1; ok = true }
                else if (n in apps.indices) { gIdx = n; ok = true }
            }
            else -> if (dy < 0) {
                if (sIdx == 0) { zone = 0; col = 2; ok = true } else { sIdx--; ok = true }
            } else if (dy > 0 && sIdx < settings.size - 1) { sIdx++; ok = true }
        }
        if (ok) {
            if (dx != 0) { val n = (if (zone == 3) gIdx else col) % 5; Sfx.play("m$n") } else Sfx.play("mv")
            updateLast(); retarget(false)
        } else if (dx != 0 || dy != 0) {
            Sfx.play("bump"); cx += dx * 14f; cy += dy * 14f; invalidate()   // piccolo rimbalzo
        }
    }

    private fun activate() {
        ripple = 0f; rpx = cx; rpy = cy; rpw = cw; rph = ch; press = 1f
        when (zone) {
            0 -> if (col != page) { Sfx.play("tab"); setPage(col) }
            1 -> featured.getOrNull(col)?.let { Sfx.play("pop"); onOpen?.invoke(it) }
            2 -> apps.getOrNull(col)?.let { Sfx.play("pop"); onOpen?.invoke(it) }
            3 -> apps.getOrNull(gIdx)?.let { Sfx.play("pop"); onOpen?.invoke(it) }
            else -> settings.getOrNull(sIdx)?.let { Sfx.play("select"); onSetting?.invoke(it.id) }
        }
        invalidate()
    }

    private var downX = 0f; private var downY = 0f; private var dragging = false
    private var startSX = 0f; private var startSY = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val x = (e.x - ox) / s; val y = (e.y - oy) / s
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = x; downY = y; dragging = false; startSX = scrollXT; startSY = scrollYT }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - downX; val dy = y - downY
                if (!dragging && (abs(dx) > 16f || abs(dy) > 16f)) dragging = true
                if (dragging) {
                    if (page == 0 && downY > 760f) {
                        scrollXT = (startSX - dx).coerceIn(0f, maxScrollX()); scrollX = scrollXT; invalidate()
                    } else if (page == 1) {
                        val rows = (apps.size + 5) / 6
                        val mx = max(0f, 200f + rows * 172f - 6f - 941f)
                        scrollYT = (startSY - dy).coerceIn(0f, mx); scrollY = scrollYT; invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> { if (!dragging) tap(x, y); dragging = false }
        }
        return true
    }

    private fun tap(x: Float, y: Float) {
        for (i in 0..2) {
            topRect(i, r1)
            if (r1.contains(x, y)) { zone = 0; col = i; if (i != page) { Sfx.play("tab"); setPage(i) } else retarget(true); return }
        }
        when (page) {
            0 -> {
                if (y >= 740f) for (i in apps.indices) {
                    appRect(i, scrollX, r1); if (r1.contains(x, y)) { hit(2, i); return }
                }
            }
            1 -> for (i in apps.indices) {
                gridRect(i, scrollY, r1); if (y > 185f && r1.contains(x, y)) { hit(3, i); return }
            }
            else -> for (i in settings.indices) {
                setRect(i, r1); if (r1.contains(x, y)) { zone = 4; sIdx = i; retarget(true); Sfx.play("select"); onSetting?.invoke(settings[i].id); return }
            }
        }
    }

    private fun hit(z: Int, i: Int) {
        zone = z
        if (z == 3) gIdx = i else col = i
        updateLast(); retarget(true)
        ripple = 0f; rpx = cx; rpy = cy; rpw = cw; rph = ch; press = 1f; Sfx.play("pop")
        val t = if (z == 1) featured.getOrNull(i) else apps.getOrNull(i)
        if (t != null) onOpen?.invoke(t)
    }

    // ------------------------------------------------------------------ disegno
    private fun ease(t: Float) = 1f - (1f - t).pow(3)
    private fun withA(col: Int, a: Int) = (col and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun step(dt: Float): Boolean {
        var busy = false
        val k = 1f - exp(-dt * 16f)
        fun lerp(a: Float, b: Float): Float {
            val d = b - a
            if (abs(d) > 0.3f) { busy = true; return a + d * k }
            return b
        }
        cx = lerp(cx, tx); cy = lerp(cy, ty); cw = lerp(cw, tw); ch = lerp(ch, th)
        scrollX = lerp(scrollX, scrollXT); scrollY = lerp(scrollY, scrollYT)
        if (textT < 1f) { textT = min(1f, textT + dt / 0.35f); busy = true }
        if (pageT < 1f) { pageT = min(1f, pageT + dt / 0.4f); busy = true }
        if (hero.t < 1f) { hero.t = min(1f, hero.t + dt / 0.9f); busy = true }
        phase += dt
        if (introStarted && introT < 1f) { introT = min(1f, introT + dt); busy = true }
        if (press > 0f) { press = max(0f, press - dt / 0.25f); busy = true }
        if (ripple < 1f) { ripple = min(1f, ripple + dt / 0.5f); busy = true }
        bx = lerp(bx, topX[page] - 39f)
        return busy
    }

    @Suppress("DEPRECATION")
    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        var dt = if (last == 0L) 0.016f else (now - last) / 1e9f
        if (dt > 0.05f) dt = 0.05f
        last = now
        val busy = step(dt)

        c.drawColor(Ui.BG)
        drawHero(c)

        c.save(); c.translate(ox, oy); c.scale(s, s)
        drawTop(c)
        val trans = pageT < 1f
        if (trans) c.saveLayerAlpha(0f, 150f, 1672f, 941f, (ease(pageT) * 255).toInt())
        when (page) { 0 -> drawHome(c); 1 -> drawGrid(c); else -> drawSettings(c) }
        if (trans) c.restore()
        drawCursor(c)
        c.restore()

        if (busy) postInvalidateOnAnimation()
        else if (fx) postInvalidateDelayed(42)
        else last = 0L
    }

    private fun drawHero(c: Canvas) {
        if (hero.bmps.isEmpty()) return
        val full = page != 0
        val hh = vh
        c.save(); c.clipRect(0f, 0f, vw, hh)
        val e = ease(hero.t)
        if (hero.t < 1f && hero.prev in hero.bmps.indices) {
            cover(c, hero.bmps[hero.prev], -e * vw * 0.3f, hh)
            cover(c, hero.bmps[hero.idx.coerceIn(0, hero.bmps.size - 1)], (1f - e) * vw, hh)
        } else cover(c, hero.bmps[hero.idx.coerceIn(0, hero.bmps.size - 1)], 0f, hh)
        if (full) c.drawRect(0f, 0f, vw, vh, dimP)
        else { c.drawRect(0f, 0f, vw, hh, leftP); c.drawRect(0f, 0f, vw, hh, botP); c.drawRect(0f, 0f, vw, hh, topP) }
        c.restore()
    }

    private fun cover(c: Canvas, b: Bitmap, x: Float, hh: Float) {
        val kb = if (fx) 1f / (1f + 0.045f * (0.5f + 0.5f * sin(phase * 0.22f))) else 1f
        val br = b.width.toFloat() / b.height; val dr = vw / hh
        if (br > dr) {
            val w = b.height * dr * kb; val h2 = b.height * kb
            src.set(((b.width - w) / 2f).toInt(), ((b.height - h2) / 2f).toInt(), ((b.width + w) / 2f).toInt(), ((b.height + h2) / 2f).toInt())
        } else {
            val h = b.width / dr * kb; val w2 = b.width * kb
            src.set(((b.width - w2) / 2f).toInt(), ((b.height - h) / 2f).toInt(), ((b.width + w2) / 2f).toInt(), ((b.height + h) / 2f).toInt())
        }
        dst.set(x, 0f, x + vw, hh)
        c.drawBitmap(b, src, dst, bmpPaint)
    }

    private fun txt(c: Canvas, str: String, x: Float, y: Float, size: Float, color: Int,
                    al: Paint.Align = Paint.Align.LEFT, tf: Typeface = tfMed) {
        tp.textSize = size; tp.color = color; tp.textAlign = al; tp.typeface = tf; tp.letterSpacing = 0f
        c.drawText(str, x, y, tp)
    }

    private fun drawTop(c: Canvas) {
        fp.style = Paint.Style.FILL; fp.shader = null; fp.color = Theme.PILL
        r1.set(52f, 30f, 370f, 100f); c.drawRoundRect(r1, 35f, 35f, fp)
        fp.style = Paint.Style.STROKE; fp.strokeWidth = 1.5f; fp.color = 0x55FFFFFF
        c.drawRoundRect(r1, 35f, 35f, fp)
        fp.style = Paint.Style.FILL
        // bolla attiva che scorre tra le schede, con bagliore neon
        val pl = if (fx) 0.8f + 0.2f * sin(phase * 3f) else 1f
        r2.set(bx, 36f, bx + 78f, 94f)
        if (!Theme.PRO) for (i in 4 downTo 1) {
            fp.color = withA(NEON, ((26 - i * 5) * pl).toInt())
            r3.set(r2.left - i * 3f, r2.top - i * 3f, r2.right + i * 3f, r2.bottom + i * 3f)
            c.drawRoundRect(r3, 29f + i * 3f, 29f + i * 3f, fp)
        }
        fp.shader = LinearGradient(r2.left, r2.top, r2.right, r2.bottom, Theme.BUBBLE0, Theme.BUBBLE1, Shader.TileMode.CLAMP)
        c.drawRoundRect(r2, 29f, 29f, fp); fp.shader = null
        for (i in 0..2) {
            val on = abs(topX[i] - (bx + 39f)) < 40f
            Glyphs.draw(c, topG[i], topX[i], 65f, 32f, if (on) (if (Theme.PRO) 0xFF0A1024.toInt() else Color.WHITE) else 0xD0FFFFFF.toInt())
        }
        tp.setShadowLayer(6f, 0f, 1f, 0x88000000.toInt())
        tp.typeface = tfMed; tp.textSize = 20f
        val ipW = tp.measureText(ipS)
        val divX = 1470f - ipW - 18f
        // info in alto a destra: nessuno sfondo "glass", solo testo
        fp.style = Paint.Style.FILL; fp.shader = null
        txt(c, ramS, divX - 18f, 71f, 20f, 0xE0FFFFFF.toInt(), Paint.Align.RIGHT)
        fp.color = 0x55FFFFFF
        c.drawRect(divX, 50f, divX + 1.5f, 78f, fp)
        txt(c, ipS, 1470f, 71f, 20f, 0xE0FFFFFF.toInt(), Paint.Align.RIGHT)
        c.drawRect(1507f, 38f, 1509f, 92f, fp)
        txt(c, timeS, 1627f, 66f, 40f, Color.WHITE, Paint.Align.RIGHT, tfMed)
        txt(c, dateS, 1627f, 94f, 18f, 0xCCFFFFFF.toInt(), Paint.Align.RIGHT)
        tp.clearShadowLayer()
    }

    private fun drawHome(c: Canvas) {
        val n = hero.bmps.size
        if (n > 0) {
            val shown = if (hero.t < 0.5f && hero.prev >= 0) hero.prev else hero.idx
            val raw = hero.titles.getOrNull(shown) ?: ""
            val real = raw.isNotBlank()
            val title = (if (real) raw else "LSPIN TV").uppercase()
            if (real) {
                fp.style = Paint.Style.FILL; fp.shader = null; fp.color = Theme.PILL
                r1.set(70f, 330f, 238f, 368f); c.drawRoundRect(r1, 19f, 19f, fp)
                fp.style = Paint.Style.STROKE; fp.strokeWidth = 1.5f; fp.color = 0x66FFFFFF
                c.drawRoundRect(r1, 19f, 19f, fp); fp.style = Paint.Style.FILL
                tp.typeface = tfBold; tp.textSize = 15f; tp.color = Color.WHITE; tp.textAlign = Paint.Align.CENTER
                tp.letterSpacing = 0.18f; c.drawText("AL CINEMA", 154f, 355f, tp); tp.letterSpacing = 0f
            }
            tp.setShadowLayer(18f, 0f, 4f, 0x99000000.toInt())
            var size = 92f
            tp.typeface = tfBold; tp.textSize = size
            while (tp.measureText(title) > 1000f && size > 40f) { size -= 4f; tp.textSize = size }
            txt(c, title, 70f, 460f, size, Color.WHITE, Paint.Align.LEFT, tfBold)
            if (real) txt(c, "Ora nelle sale", 74f, 508f, 23f, 0xFFE3E9F7.toInt(), Paint.Align.LEFT, tfLight)
            tp.clearShadowLayer()
            // indicatore dei film: pillole arrotondate
            fp.style = Paint.Style.FILL; fp.shader = null
            var px = 74f
            for (i in 0 until n) {
                val on = i == hero.idx
                val w = if (on) 44f else 12f
                fp.color = if (on) Color.WHITE else 0x66FFFFFF
                r1.set(px, 534f, px + w, 542f); c.drawRoundRect(r1, 4f, 4f, fp)
                px += w + 10f
            }
        }
        // app: icone libere, senza riquadri
        c.save(); c.clipRect(0f, 700f, 1672f, 941f)
        for (i in apps.indices) {
            appRect(i, scrollX, r1)
            if (r1.right < -20f || r1.left > 1692f) continue
            drawTile(c, apps[i], r1, false, i)
        }
        c.restore()
    }

    private fun drawGrid(c: Canvas) {
        txt(c, "Tutte le app", 82f, 168f, 40f, Color.WHITE, Paint.Align.LEFT, tfBold)
        c.save(); c.clipRect(0f, 182f, 1672f, 941f)
        for (i in apps.indices) {
            gridRect(i, scrollY, r1)
            if (r1.bottom < 182f || r1.top > 941f) continue
            drawTile(c, apps[i], r1, false, i)
        }
        c.restore()
    }

    private fun drawSettings(c: Canvas) {
        txt(c, "Impostazioni", 82f, 168f, 40f, Color.WHITE, Paint.Align.LEFT, tfBold)
        for (i in settings.indices) {
            setRect(i, r1)
            fp.style = Paint.Style.FILL; fp.color = Theme.CARD
            c.drawRoundRect(r1, 18f, 18f, fp)
            fp.style = Paint.Style.STROKE; fp.strokeWidth = 1.5f; fp.color = 0x22FFFFFF
            c.drawRoundRect(r1, 18f, 18f, fp)
            val it = settings[i]
            txt(c, it.title, 200f, r1.top + 34f, 28f, Color.WHITE)
            txt(c, it.sub, 200f, r1.top + 62f, 19f, 0xAAC8CCE6.toInt(), Paint.Align.LEFT, tfLight)
            txt(c, it.value, 1472f, r1.top + 46f, 26f, Theme.VALUE, Paint.Align.RIGHT)
        }
        fp.style = Paint.Style.FILL
    }

    private fun prox(r: RectF): Float {
        val dx = abs(r.centerX() - (cx + cw / 2f)) / (r.width() * 0.9f)
        val dy = abs(r.centerY() - (cy + ch / 2f)) / (r.height() * 0.8f)
        return max(0f, 1f - dx) * max(0f, 1f - dy)
    }

    private fun art(t: Tile, w: Float, h: Float, big: Boolean): Bitmap {
        val key = t.id + "|" + w.toInt() + "x" + h.toInt() + "|" + big
        val hit = artCache.get(key)
        if (hit != null) return hit
        val b = TileArt.make(t, w, h, big, s)
        artCache.put(key, b)
        return b
    }

    /** Colore tenue (pastello) per le icone libere. */
    private fun soft(col: Int): Int {
        val r = (col shr 16) and 255; val g = (col shr 8) and 255; val b = col and 255
        return Color.rgb(r + (255 - r) * 3 / 10, g + (255 - g) * 3 / 10, b + (255 - b) * 3 / 10)
    }

    private fun drawTile(c: Canvas, t: Tile, r: RectF, big: Boolean, idx: Int) {
        val h = r.height()
        val loc = ease((introT * 1.6f - min(idx, 8) * 0.07f).coerceIn(0f, 1f))
        if (loc <= 0f) return
        val p = prox(r)
        val sc = (1f + 0.12f * p) * (1f - 0.04f * press * p)
        val a = (loc * 255).toInt()
        val isz = 68f
        val icy = r.top + h * 0.34f + (1f - loc) * 30f
        c.save(); c.translate(r.centerX(), icy); c.scale(sc, sc)
        val d = t.icon
        if (d != null) {
            val hs = (isz / 2f).toInt()
            d.setBounds(-hs, -hs, hs, hs); d.alpha = a; d.draw(c); d.alpha = 255
        } else {
            Glyphs.draw(c, t.glyph, 0f, 0f, isz, withA(soft(t.c1), a))
        }
        c.restore()
        // etichetta
        val sel = p > 0.5f
        val tf = if (sel) tfBold else tfMed
        tp.typeface = tf; tp.textSize = 20f
        var lab = t.label
        if (tp.measureText(lab) > 128f) lab = lab.substring(0, max(1, tp.breakText(lab, true, 116f, null))) + "\u2026"
        tp.setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
        txt(c, lab, r.centerX(), r.top + h * 0.76f, 20f, withA(if (sel) Color.WHITE else 0xCCFFFFFF.toInt(), a), Paint.Align.CENTER, tf)
        tp.clearShadowLayer()
    }

    private fun drawCursor(c: Canvas) {
        if (zone in 1..3) {
            fp.style = Paint.Style.FILL; fp.shader = null; fp.color = Color.WHITE
            val mx = cx + cw / 2f
            val bw2 = if (zone == 1) 34f else 26f
            r1.set(mx - bw2, cy + ch - 16f, mx + bw2, cy + ch - 11f)
            c.drawRoundRect(r1, 3f, 3f, fp)
            return
        }
        val tab = zone == 0
        val inf = if (tab) 0f else 4f          // righe impostazioni: bordo appena fuori dalla riga, mai sulle vicine
        r1.set(cx - inf, cy - inf, cx + cw + inf, cy + ch + inf)
        val rad = if (tab) r1.height() / 2f else min(28f, ch * 0.14f + 5f)
        val gs = (min(cw, ch) / 150f).coerceIn(0.4f, 1f)          // bagliore proporzionale all'elemento
        val pulse = if (fx) 0.82f + 0.18f * sin(phase * 3.4f) else 1f
        glowPaint.shader = null
        for (i in 5 downTo 1) {
            glowPaint.strokeWidth = i * 3.4f * gs; glowPaint.color = NEON
            glowPaint.alpha = ((56 - i * 8) * pulse * (if (Theme.PRO) 0.7f else 1f)).toInt()
            c.drawRoundRect(r1, rad, rad, glowPaint)
        }
        // bordo neon con gradiente che ruota
        mtx.setRotate(if (fx) phase * 70f else 0f); mtx.postTranslate(r1.centerX(), r1.centerY())
        sweep.setLocalMatrix(mtx)
        if (Theme.PRO) { glowPaint.shader = null; glowPaint.color = Color.WHITE }   // bordo bianco pulito
        else glowPaint.shader = sweep
        glowPaint.strokeWidth = if (tab) 2.2f else 3.4f; glowPaint.alpha = 255
        c.drawRoundRect(r1, rad, rad, glowPaint)
        // filo luminoso interno
        glowPaint.shader = null; glowPaint.color = 0x55FFFFFF; glowPaint.strokeWidth = 1f
        r2.set(r1); r2.inset(3f, 3f)
        c.drawRoundRect(r2, max(2f, rad - 3f), max(2f, rad - 3f), glowPaint)
        // onda all'attivazione
        if (ripple < 1f) {
            val e = ease(ripple); val g = e * (if (tab) 14f else 38f)
            r2.set(rpx - g, rpy - g, rpx + rpw + g, rpy + rph + g)
            glowPaint.color = NEON; glowPaint.alpha = ((1f - e) * 200).toInt(); glowPaint.strokeWidth = 3f
            c.drawRoundRect(r2, rad + g, rad + g, glowPaint)
        }
    }
}
