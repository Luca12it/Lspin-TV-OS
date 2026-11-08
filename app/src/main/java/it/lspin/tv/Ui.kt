package it.lspin.tv

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

object Ui {
    val BG = Theme.BG
    val ACCENT = Theme.ACCENT
    val ACCENT2 = Theme.ACCENT2

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun rr(color: Int, r: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = r
    }

    /** Pulsante testuale con stato "focus" evidenziato (utile per tastiera/telecomando). */
    fun btn(ctx: Context, text: String, onClick: () -> Unit): TextView {
        val tv = TextView(ctx)
        tv.text = text
        tv.setTextColor(Color.WHITE)
        tv.textSize = 17f
        tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        tv.gravity = Gravity.CENTER
        val ph = dp(ctx, 16f); val pv = dp(ctx, 8f)
        tv.setPadding(ph, pv, ph, pv)
        val r = dp(ctx, 12f).toFloat()
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_focused), rr(ACCENT, r))
        sl.addState(intArrayOf(android.R.attr.state_pressed), rr(ACCENT2, r))
        sl.addState(intArrayOf(), rr(0x22FFFFFF, r))
        tv.background = sl
        tv.isFocusable = true
        tv.setOnClickListener { Sfx.play("select"); onClick() }
        tv.setOnFocusChangeListener { _, f -> if (f) Sfx.play("m2") }
        return tv
    }

    fun lp(w: Int, h: Int, weight: Float = 0f, margin: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(margin, margin, margin, margin) }

    fun hide(v: View) { v.visibility = View.INVISIBLE }
}

object SysInfo {
    fun ram(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        if (mi.totalMem <= 0L) return 0
        return (100L - mi.availMem * 100L / mi.totalMem).toInt()
    }

    fun ip(): String {
        try {
            val en = NetworkInterface.getNetworkInterfaces() ?: return "offline"
            for (ni in Collections.list(en)) {
                if (ni.isLoopback || !ni.isUp) continue
                for (a in Collections.list(ni.inetAddresses)) {
                    if (a is Inet4Address) return a.hostAddress ?: "offline"
                }
            }
        } catch (e: Exception) { }
        return "offline"
    }

    fun time(): String = SimpleDateFormat("HH:mm", Locale.ITALY).format(Date())

    fun date(): String {
        val s = SimpleDateFormat("EEE d MMM", Locale.ITALY).format(Date()).replace(".", "")
        return s.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
    }
}
