package it.lspin.tv

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class G { HOME, GRIDF, SLIDERS, PLAY, YT, PLAYBOX, MONITOR, MUSIC, SPOTIFY, CONE, GLOBE, FOLDER,
    TERMINAL, GEAR, CAMERA, IMAGE, CALC, CLOCK, CAL, PHONE, CHAT, MAIL, MIC, DOWN, GRID }

/** Icone vettoriali disegnate a mano su canvas (nessuna risorsa bitmap). */
object Glyphs {
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val p = Path()
    private val r = RectF()

    fun draw(c: Canvas, g: G, cx: Float, cy: Float, size: Float, color: Int = Color.WHITE) {
        st.color = color; fl.color = color; st.strokeWidth = 0.075f
        c.save(); c.translate(cx - size / 2f, cy - size / 2f); c.scale(size, size)

        fun rb(l: Float, t: Float, rt: Float, b: Float, rad: Float, pt: Paint) { r.set(l, t, rt, b); c.drawRoundRect(r, rad, rad, pt) }
        fun ln(x1: Float, y1: Float, x2: Float, y2: Float) { c.drawLine(x1, y1, x2, y2, st) }
        fun tri(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, pt: Paint) {
            p.reset(); p.moveTo(x1, y1); p.lineTo(x2, y2); p.lineTo(x3, y3); p.close(); c.drawPath(p, pt)
        }

        when (g) {
            G.HOME -> {
                p.reset(); p.moveTo(.5f, .14f); p.lineTo(.88f, .48f); p.lineTo(.76f, .48f); p.lineTo(.76f, .84f)
                p.lineTo(.58f, .84f); p.lineTo(.58f, .6f); p.lineTo(.42f, .6f); p.lineTo(.42f, .84f)
                p.lineTo(.24f, .84f); p.lineTo(.24f, .48f); p.lineTo(.12f, .48f); p.close(); c.drawPath(p, fl)
            }
            G.GRIDF -> {
                rb(.12f, .12f, .44f, .44f, .08f, fl); rb(.56f, .12f, .88f, .44f, .08f, fl)
                rb(.12f, .56f, .44f, .88f, .08f, fl); rb(.56f, .56f, .88f, .88f, .08f, fl)
            }
            G.SLIDERS -> {
                for (i in 0..2) { val y = .26f + i * .24f; ln(.12f, y, .88f, y) }
                c.drawCircle(.66f, .26f, .09f, fl); c.drawCircle(.34f, .5f, .09f, fl); c.drawCircle(.7f, .74f, .09f, fl)
            }
            G.PLAY -> {
                c.drawCircle(.5f, .5f, .42f, st); tri(.42f, .33f, .42f, .67f, .69f, .5f, fl)
            }
            G.YT -> {
                rb(.07f, .22f, .93f, .78f, .18f, fl)
                fl.color = 0xFF14183A.toInt(); tri(.42f, .38f, .42f, .62f, .63f, .5f, fl)
            }
            G.PLAYBOX -> {
                rb(.1f, .2f, .9f, .8f, .14f, st); tri(.42f, .36f, .42f, .64f, .66f, .5f, fl)
            }
            G.MONITOR -> {
                rb(.1f, .16f, .9f, .66f, .06f, st); ln(.5f, .66f, .5f, .82f); ln(.3f, .84f, .7f, .84f)
            }
            G.MUSIC -> {
                c.drawCircle(.34f, .74f, .1f, fl); c.drawCircle(.74f, .66f, .1f, fl)
                ln(.44f, .74f, .44f, .26f); ln(.84f, .66f, .84f, .18f)
                st.strokeWidth = .1f; ln(.44f, .26f, .84f, .18f)
            }
            G.SPOTIFY -> {
                c.drawCircle(.5f, .5f, .44f, fl)
                st.color = 0xFF0B1220.toInt(); st.strokeWidth = .065f
                p.reset(); p.moveTo(.27f, .4f); p.quadTo(.5f, .31f, .73f, .42f); c.drawPath(p, st)
                p.reset(); p.moveTo(.3f, .54f); p.quadTo(.5f, .46f, .7f, .55f); c.drawPath(p, st)
                p.reset(); p.moveTo(.33f, .67f); p.quadTo(.5f, .61f, .67f, .68f); c.drawPath(p, st)
            }
            G.CONE -> {
                tri(.5f, .08f, .82f, .8f, .18f, .8f, fl)
                fl.color = 0xFF0B1220.toInt()
                p.reset(); p.moveTo(.45f, .32f); p.lineTo(.55f, .32f); p.lineTo(.6f, .46f); p.lineTo(.4f, .46f); p.close(); c.drawPath(p, fl)
                p.reset(); p.moveTo(.36f, .55f); p.lineTo(.64f, .55f); p.lineTo(.69f, .68f); p.lineTo(.31f, .68f); p.close(); c.drawPath(p, fl)
                fl.color = color; rb(.12f, .78f, .88f, .91f, .04f, fl)
            }
            G.GLOBE -> {
                c.drawCircle(.5f, .5f, .42f, st); r.set(.3f, .08f, .7f, .92f); c.drawOval(r, st)
                ln(.08f, .5f, .92f, .5f); ln(.16f, .3f, .84f, .3f); ln(.16f, .7f, .84f, .7f)
            }
            G.FOLDER -> {
                p.reset(); p.moveTo(.1f, .26f); p.lineTo(.4f, .26f); p.lineTo(.48f, .34f); p.lineTo(.9f, .34f)
                p.lineTo(.9f, .78f); p.lineTo(.1f, .78f); p.close(); c.drawPath(p, st)
            }
            G.TERMINAL -> {
                rb(.1f, .16f, .9f, .84f, .1f, st)
                p.reset(); p.moveTo(.26f, .36f); p.lineTo(.42f, .5f); p.lineTo(.26f, .64f); c.drawPath(p, st)
                ln(.5f, .66f, .72f, .66f)
            }
            G.GEAR -> {
                c.drawCircle(.5f, .5f, .15f, st); c.drawCircle(.5f, .5f, .3f, st); st.strokeWidth = .1f
                for (i in 0 until 8) {
                    val a = i * PI / 4.0; val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    ln(.5f + .32f * ca, .5f + .32f * sa, .5f + .42f * ca, .5f + .42f * sa)
                }
            }
            G.CAMERA -> {
                rb(.1f, .3f, .9f, .8f, .08f, st); c.drawCircle(.5f, .55f, .16f, st)
                p.reset(); p.moveTo(.36f, .3f); p.lineTo(.4f, .2f); p.lineTo(.6f, .2f); p.lineTo(.64f, .3f); c.drawPath(p, st)
            }
            G.IMAGE -> {
                rb(.12f, .18f, .88f, .82f, .06f, st); c.drawCircle(.34f, .4f, .07f, fl)
                p.reset(); p.moveTo(.14f, .76f); p.lineTo(.4f, .5f); p.lineTo(.56f, .66f); p.lineTo(.68f, .56f); p.lineTo(.86f, .74f); c.drawPath(p, st)
            }
            G.CALC -> {
                rb(.2f, .1f, .8f, .9f, .07f, st); rb(.3f, .2f, .7f, .34f, .02f, st)
                for (xx in floatArrayOf(.36f, .5f, .64f)) for (yy in floatArrayOf(.5f, .64f, .78f)) c.drawCircle(xx, yy, .04f, fl)
            }
            G.CLOCK -> {
                c.drawCircle(.5f, .5f, .4f, st); ln(.5f, .5f, .5f, .26f); ln(.5f, .5f, .66f, .58f)
            }
            G.CAL -> {
                rb(.12f, .2f, .88f, .86f, .07f, st); ln(.12f, .4f, .88f, .4f); ln(.32f, .1f, .32f, .28f); ln(.68f, .1f, .68f, .28f)
                r.set(.3f, .52f, .44f, .66f); c.drawRect(r, fl); r.set(.56f, .52f, .7f, .66f); c.drawRect(r, fl)
            }
            G.PHONE -> {
                st.strokeWidth = .14f; r.set(.14f, .14f, .86f, .86f); c.drawArc(r, 100f, 80f, false, st)
            }
            G.CHAT -> {
                rb(.1f, .16f, .9f, .68f, .12f, st)
                p.reset(); p.moveTo(.3f, .68f); p.lineTo(.26f, .86f); p.lineTo(.46f, .68f); c.drawPath(p, st)
                c.drawCircle(.34f, .42f, .035f, fl); c.drawCircle(.5f, .42f, .035f, fl); c.drawCircle(.66f, .42f, .035f, fl)
            }
            G.MAIL -> {
                rb(.1f, .24f, .9f, .78f, .07f, st)
                p.reset(); p.moveTo(.12f, .28f); p.lineTo(.5f, .56f); p.lineTo(.88f, .28f); c.drawPath(p, st)
            }
            G.MIC -> {
                rb(.38f, .1f, .62f, .5f, .12f, st); r.set(.26f, .26f, .74f, .74f); c.drawArc(r, 0f, 180f, false, st)
                ln(.5f, .74f, .5f, .86f); ln(.38f, .88f, .62f, .88f)
            }
            G.DOWN -> {
                ln(.5f, .14f, .5f, .62f)
                p.reset(); p.moveTo(.3f, .44f); p.lineTo(.5f, .64f); p.lineTo(.7f, .44f); c.drawPath(p, st)
                ln(.2f, .84f, .8f, .84f)
            }
            G.GRID -> {
                rb(.14f, .14f, .46f, .46f, .06f, st); rb(.54f, .14f, .86f, .46f, .06f, st)
                rb(.14f, .54f, .46f, .86f, .06f, st); rb(.54f, .54f, .86f, .86f, .06f, st)
            }
        }
        c.restore()
    }
}
