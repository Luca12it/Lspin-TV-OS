package it.lspin.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animazione di accensione: la TV si accende (puntino -> riga -> schermo che si apre con un lampo),
 * compare il logo "LS" che si aggancia al segnale e diventa "LSPIN TV".
 * La timeline coincide con quella del jingle (res/raw/lspin_boot.ogg).
 */
class BootView(ctx: Context, private val onDone: () -> Unit) : View(ctx) {
    private val total = 4.4f

    // ---- timeline (secondi) ----
    private val tDot = 0.12f
    private val tLine0 = 0.30f; private val tLine1 = 0.80f
    private val tOpen0 = 0.80f; private val tOpen1 = 1.20f; private val tFlash1 = 1.80f
    private val tMark0 = 1.55f; private val tMark1 = 2.20f
    private val tRev0 = 2.60f; private val tRev1 = 3.40f

    // ---- geometria del logo (coordinate sorgente: altezza lettere 593..805) ----
    private val pL = Path(); private val pS = Path()
    private val pP = Path(); private val pI = Path(); private val pN = Path(); private val pT = Path(); private val pV = Path()
    private val word = arrayOf(pP, pI, pN, pT, pV)
    private val px = 696f; private val ix = 876f; private val nx = 948f; private val tx = 1158f; private val vx = 1336f
    private val wmL = 348f; private val wmR = 1506f
    private val markCx = 508f
    private val wmCx = (348f + 1506f) / 2f
    private val baseY = 699f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tvGrad: Shader = LinearGradient(tx, 593f, vx + 170f, 805f, Theme.BOOT_ACCENT0, Theme.BOOT_ACCENT1, Shader.TileMode.CLAMP)
    // tema neon: sfumatura viola-blu su tutta la scritta
    private val fullGrad: Shader = LinearGradient(wmL, 593f, wmR, 805f, Theme.BOOT_ACCENT0, Theme.BOOT_ACCENT1, Shader.TileMode.CLAMP)
    private val noiseBmp: Bitmap
    private val noiseShader: BitmapShader
    private var scanShader: BitmapShader? = null
    private var scanStep = 4
    private var vigShader: Shader? = null
    private var hazeShader: Shader? = null
    private val mtx = Matrix()
    private val rect = RectF()
    private var sc = 1f
    private var tm = 0f
    private var finished = false

    private val anim = ValueAnimator.ofFloat(0f, total).apply {
        duration = (total * 1000).toLong()
        interpolator = LinearInterpolator()
        addUpdateListener { tm = it.animatedValue as Float; invalidate() }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(a: Animator) { finish() }
        })
    }

    init {
        // L: gambo + piede che si aggancia alla S
        pL.moveTo(348f, 593f); pL.lineTo(394f, 593f); pL.lineTo(394f, 763f); pL.lineTo(493f, 763f)
        pL.cubicTo(497f, 780f, 508f, 795f, 523f, 805f); pL.lineTo(349f, 805f); pL.close()
        // S: tracciato centrale, poi convertito in contorno pieno (spessore 44)
        val cl = Path()
        cl.moveTo(641f, 641f)
        cl.cubicTo(630f, 620f, 612f, 610f, 589f, 610f)
        cl.cubicTo(557f, 610f, 535f, 627f, 535f, 652f)
        cl.cubicTo(535f, 676f, 553f, 686f, 584f, 695f)
        cl.lineTo(602f, 700f)
        cl.cubicTo(636f, 710f, 646f, 725f, 646f, 746f)
        cl.cubicTo(646f, 772f, 622f, 786f, 592f, 786f)
        cl.cubicTo(561f, 786f, 541f, 770f, 531f, 748f)
        val sp = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 44f; strokeCap = Paint.Cap.BUTT; strokeJoin = Paint.Join.ROUND
        }
        sp.getFillPath(cl, pS)

        // P (gambo + occhiello, con foro)
        pP.fillType = Path.FillType.EVEN_ODD
        pP.moveTo(px, 805f); pP.lineTo(px, 593f); pP.lineTo(px + 78f, 593f)
        pP.arcTo(RectF(px + 4f, 593f, px + 152f, 741f), -90f, 180f)
        pP.lineTo(px + 44f, 741f); pP.lineTo(px + 44f, 805f); pP.close()
        pP.moveTo(px + 44f, 637f); pP.lineTo(px + 78f, 637f)
        pP.arcTo(RectF(px + 48f, 637f, px + 108f, 697f), -90f, 180f)
        pP.lineTo(px + 44f, 697f); pP.close()
        // I
        pI.moveTo(ix, 593f); pI.lineTo(ix + 44f, 593f); pI.lineTo(ix + 44f, 805f); pI.lineTo(ix, 805f); pI.close()
        // N
        pN.moveTo(nx, 805f); pN.lineTo(nx, 593f); pN.lineTo(nx + 46f, 593f); pN.lineTo(nx + 106f, 708f)
        pN.lineTo(nx + 106f, 593f); pN.lineTo(nx + 150f, 593f); pN.lineTo(nx + 150f, 805f)
        pN.lineTo(nx + 104f, 805f); pN.lineTo(nx + 44f, 690f); pN.lineTo(nx + 44f, 805f); pN.close()
        // T
        pT.moveTo(tx, 593f); pT.lineTo(tx + 150f, 593f); pT.lineTo(tx + 150f, 637f); pT.lineTo(tx + 97f, 637f)
        pT.lineTo(tx + 97f, 805f); pT.lineTo(tx + 53f, 805f); pT.lineTo(tx + 53f, 637f); pT.lineTo(tx, 637f); pT.close()
        // V
        pV.moveTo(vx, 593f); pV.lineTo(vx + 48f, 593f); pV.lineTo(vx + 85f, 730f); pV.lineTo(vx + 122f, 593f)
        pV.lineTo(vx + 170f, 593f); pV.lineTo(vx + 108f, 805f); pV.lineTo(vx + 62f, 805f); pV.close()

        // texture di rumore (puntini bianchi con trasparenza casuale)
        val rnd = Random(5)
        val px128 = IntArray(128 * 128) { (rnd.nextInt(256) shl 24) or 0xFFFFFF }
        noiseBmp = Bitmap.createBitmap(px128, 128, 128, Bitmap.Config.ARGB_8888)
        noiseShader = BitmapShader(noiseBmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun finish() { if (finished) return; finished = true; onDone() }

    /** Avvia l'animazione (chiamato quando il jingle è pronto, così partono insieme). */
    fun start() { if (!anim.isStarted && !finished) anim.start() }

    fun isStarted() = anim.isStarted

    fun skip() { if (anim.isStarted && tm > 0.6f) anim.cancel() }

    override fun onDetachedFromWindow() { finished = true; anim.cancel(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) skip()
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sc = min(w / 1920f, h / 1080f)
        // scanline: 1 px di larghezza, metà nera / metà trasparente
        scanStep = max(3, (4f * sc).roundToInt())
        val sb = Bitmap.createBitmap(1, scanStep, Bitmap.Config.ARGB_8888)
        for (y in 0 until scanStep) sb.setPixel(0, y, if (y < scanStep / 2) Color.BLACK else 0)
        scanShader = BitmapShader(sb, Shader.TileMode.CLAMP, Shader.TileMode.REPEAT)
        val r1 = max(w, h) * 0.78f; val r0 = min(w, h) * 0.45f
        vigShader = RadialGradient(w / 2f, h / 2f, r1, intArrayOf(0x00000000, 0x00000000, 0x8C000000.toInt()),
            floatArrayOf(0f, r0 / r1, 1f), Shader.TileMode.CLAMP)
        hazeShader = RadialGradient(w / 2f, h / 2f, max(w, h) * 0.62f, Theme.BOOT_HAZE, 0x00000000, Shader.TileMode.CLAMP)
    }

    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun eo3(t: Float) = 1f - (1f - t).pow(3)
    private fun eio(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
    private fun eoExpo(t: Float) = if (t >= 1f) 1f else 1f - 2f.pow(-10f * t)
    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun col(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255f).toInt() shl 24)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = tm
        c.drawColor(Color.BLACK)
        if (t < tDot) return
        val cy = h / 2f
        val tint = Theme.BOOT_TINT

        // ---------- puntino -> riga -> schermo che si apre ----------
        val lineH = max(2f, 3f * sc)
        val lw = lerp(8f * sc, w, eoExpo(seg(t, tLine0, tLine1)))
        val openP = eio(seg(t, tOpen0, tOpen1))
        val hh = max(lineH, h * openP)
        p.style = Paint.Style.FILL; p.shader = null
        if (t < tOpen0) {
            val a = seg(t, tDot, tDot + 0.06f)
            val x0 = w / 2f - lw / 2f; val x1 = w / 2f + lw / 2f
            p.color = col(tint, a * 0.10f); c.drawRect(x0, cy - 8f * sc, x1, cy + 8f * sc, p)
            p.color = col(tint, a * 0.28f); c.drawRect(x0, cy - 3.5f * sc, x1, cy + 3.5f * sc, p)
            p.color = col(Color.WHITE, a); c.drawRect(x0, cy - lineH / 2f, x1, cy + lineH / 2f, p)
            return
        }

        c.save()
        c.clipRect(0f, cy - hh / 2f, w, cy + hh / 2f)

        // scena: fondo + leggero alone centrale
        c.drawColor(Theme.BOOT_BG)
        p.shader = hazeShader; p.color = Color.WHITE; c.drawRect(0f, 0f, w, h, p); p.shader = null

        drawLogo(c, w, h, t)

        // rumore che si dissolve
        val nA = 0.40f * (1f - seg(t, tOpen1, 2.05f))
        if (nA > 0.005f) {
            mtx.setTranslate(Random.nextInt(128).toFloat(), Random.nextInt(128).toFloat())
            noiseShader.setLocalMatrix(mtx)
            p.shader = noiseShader; p.color = col(Color.WHITE, nA * 0.5f); c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        // banda di tracking che scende
        val tb = seg(t, 1.25f, 2.05f)
        if (tb > 0f && tb < 1f) {
            p.color = col(Color.WHITE, 0.07f * (1f - abs(tb - 0.5f) * 1.4f))
            val y = lerp(-0.1f * h, h, tb)
            c.drawRect(0f, y, w, y + 0.11f * h, p)
        }
        // scanline
        val ss = scanShader
        if (ss != null) {
            p.shader = ss; p.color = col(Color.WHITE, lerp(0.20f, 0.07f, seg(t, tOpen1, 2.6f)))
            c.drawRect(0f, 0f, w, h, p); p.shader = null
        }
        // vignetta
        p.shader = vigShader; p.color = Color.WHITE; c.drawRect(0f, 0f, w, h, p); p.shader = null
        // lampo di accensione
        val fl = if (t < tOpen1) 1f else 1f - eo3(seg(t, tOpen1, tFlash1))
        if (fl > 0f) {
            p.color = col(tint, fl); c.drawRect(0f, 0f, w, h, p)
            p.color = col(Color.WHITE, fl * 0.7f); c.drawRect(0f, 0f, w, h, p)
        }
        c.restore()
    }

    /** Alone morbido (solo tema neon): due tratti larghi e trasparenti attorno alla lettera. */
    private fun glow(c: Canvas, path: Path, a: Float) {
        p.style = Paint.Style.STROKE; p.strokeJoin = Paint.Join.ROUND; p.shader = null
        p.color = col(Theme.BOOT_ACCENT0, 0.14f * a); p.strokeWidth = 26f; c.drawPath(path, p)
        p.color = col(Theme.BOOT_ACCENT0, 0.26f * a); p.strokeWidth = 9f; c.drawPath(path, p)
        p.style = Paint.Style.FILL
    }

    private fun drawLogo(c: Canvas, w: Float, h: Float, t: Float) {
        if (t < tMark0) return
        val sM = min(w * 0.30f, h * 0.45f) / 320f
        val sW = w * 0.56f / (wmR - wmL)
        val e = eio(seg(t, tRev0, tRev1))
        val push = 1f + 0.045f * eo3(seg(t, tMark0, tRev0))
        val s = lerp(sM * push, sW, e)
        val cxs = lerp(markCx, wmCx, e)
        c.save()
        c.translate(w / 2f, h / 2f); c.scale(s, s); c.translate(-cxs, -baseY)
        p.style = Paint.Style.FILL; p.shader = null

        // marchio LS: sfarfallio + sfalsamento a fasce quando il segnale si aggancia
        val p1 = seg(t, tMark0, tMark1)
        var a = 1f
        if (p1 < 0.55f) {
            val pat = floatArrayOf(0f, 0.9f, 0.12f, 1f, 0.3f, 0.85f, 0.5f, 1f)
            a = pat[min(pat.size - 1, (p1 / 0.55f * pat.size).toInt())]
        }
        val amp = (1f - p1).pow(2) * 30f
        if (a > 0f) {
            if (!Theme.PRO && amp <= 0.6f) { glow(c, pL, a); glow(c, pS, a) }
            p.color = col(Color.WHITE, a)
            if (!Theme.PRO) p.shader = fullGrad
            if (amp > 0.6f) {
                val bands = 6; val y0 = 588f; val bh = (808f - 588f) / bands
                for (i in 0 until bands) {
                    c.save()
                    c.clipRect(300f, y0 + i * bh, 720f, y0 + (i + 1) * bh + 0.5f)
                    c.translate(amp * sin(i * 2.3f + t * 55f) * (if (i % 2 == 1) 1f else -1f), 0f)
                    c.drawPath(pL, p); c.drawPath(pS, p)
                    c.restore()
                }
            } else { c.drawPath(pL, p); c.drawPath(pS, p) }
            p.shader = null
        }

        // lettere "PIN TV" che scivolano fuori dalla S
        for (i in word.indices) {
            val r = eo3(seg(t, tRev0 + 0.07f * i + 0.05f, tRev0 + 0.07f * i + 0.55f))
            if (r <= 0f) continue
            val isTv = i >= 3
            c.save(); c.translate((1f - r) * -44f, 0f)
            if (!Theme.PRO) { glow(c, word[i], r); p.shader = fullGrad; p.color = col(Color.WHITE, r) }
            else if (isTv) { p.shader = null; p.color = col(Theme.BOOT_ACCENT0, r) }
            else { p.shader = null; p.color = col(Color.WHITE, r) }
            c.drawPath(word[i], p)
            p.shader = null
            c.restore()
        }

        // filetto sotto la scritta (con tratto colorato sotto "TV")
        val u = eo3(seg(t, tRev1 - 0.15f, tRev1 + 0.55f))
        if (u > 0f) {
            val half = (wmR - wmL) / 2f * u; val y = 805f + 46f
            p.color = col(Color.WHITE, 0.28f); c.drawRect(wmCx - half, y, wmCx + half, y + 2.6f, p)
            val x0 = max(wmCx - half, tx); val x1 = min(wmCx + half, vx + 170f)
            if (Theme.PRO) { if (x1 > x0) { p.color = Theme.BOOT_ACCENT0; c.drawRect(x0, y - 1.2f, x1, y + 3.8f, p) } }
            else { p.shader = fullGrad; p.color = Color.WHITE; c.drawRect(wmCx - half, y - 1.2f, wmCx + half, y + 3.8f, p); p.shader = null }
        }
        c.restore()
    }
}
