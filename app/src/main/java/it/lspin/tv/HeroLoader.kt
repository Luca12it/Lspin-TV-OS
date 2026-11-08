package it.lspin.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object HeroLoader {

    /** Sfondo spaziale generato (usato finché non ci sono le copertine vere). */
    fun fallback(seed: Int): Bitmap {
        val w = 960; val h = 540
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
        val c = Canvas(b)
        val rnd = java.util.Random(seed.toLong() * 7919L)
        val hue = (if (Theme.PRO) floatArrayOf(215f, 222f, 208f, 228f, 218f) else floatArrayOf(265f, 215f, 20f, 330f, 170f))[seed % 5]
        val top = Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.18f))
        val mid = Color.HSVToColor(floatArrayOf((hue + 30f) % 360f, 0.6f, 0.5f))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(top, mid, Theme.BG),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = Color.WHITE
        repeat(90) {
            p.alpha = 60 + rnd.nextInt(160)
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.8f, 0.6f + rnd.nextFloat() * 1.4f, p)
        }
        val px = w * (0.55f + rnd.nextFloat() * 0.3f); val py = h * 0.25f; val pr = h * 0.5f
        p.shader = RadialGradient(px - pr * 0.3f, py - pr * 0.3f, pr * 1.1f,
            intArrayOf(if (Theme.PRO) 0xFFDCE8FF.toInt() else 0xFFFFE2B8.toInt(), Color.HSVToColor(floatArrayOf((hue + 20f) % 360f, 0.5f, 0.7f)), 0xFF0A0A1A.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        p.alpha = 255
        c.drawCircle(px, py, pr, p)
        p.shader = null; p.color = Theme.BG
        val path = Path()
        path.moveTo(0f, h.toFloat()); path.lineTo(0f, h * 0.88f)
        var x = 0f
        while (x < w) { x += 30f + rnd.nextInt(60); path.lineTo(x, h * (0.82f + rnd.nextFloat() * 0.1f)) }
        path.lineTo(w.toFloat(), h.toFloat()); path.close()
        c.drawPath(path, p)
        return b
    }

    /** Scarica i backdrop dei film ora al cinema da TMDB (chiave gratuita). */
    fun loadTmdb(ctx: Context, key: String, ok: (List<Bitmap>, List<String>) -> Unit, err: (String) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        Thread {
            try {
                val dir = ctx.cacheDir
                val now = System.currentTimeMillis()
                dir.listFiles()?.forEach { if (it.name.startsWith("hero_") && now - it.lastModified() > 7L * 86400000L) it.delete() }
                val api = "https://api.themoviedb.org/3/movie/now_playing?language=it-IT&region=IT&page=1&api_key=$key"
                val arr = JSONObject(get(api)).getJSONArray("results")
                val bmps = ArrayList<Bitmap>(); val titles = ArrayList<String>()
                var i = 0
                while (i < arr.length() && bmps.size < 8) {
                    val o = arr.getJSONObject(i); i++
                    val bp = o.optString("backdrop_path", "")
                    if (bp.isEmpty() || bp == "null") continue
                    val f = File(dir, "hero_" + bp.replace("/", "_"))
                    if (!f.exists() || f.length() == 0L) download("https://image.tmdb.org/t/p/w1280$bp", f)
                    val opt = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                    val b = BitmapFactory.decodeFile(f.path, opt) ?: continue
                    bmps.add(b); titles.add(o.optString("title"))
                }
                if (bmps.isEmpty()) main.post { err("Nessuna copertina trovata") } else main.post { ok(bmps, titles) }
            } catch (e: Exception) {
                main.post { err("Copertine non caricate: controlla chiave TMDB e connessione") }
            }
        }.start()
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000
        return c
    }

    private fun get(url: String): String {
        val c = open(url)
        try { return c.inputStream.bufferedReader().use { it.readText() } } finally { c.disconnect() }
    }

    private fun download(url: String, dst: File) {
        val c = open(url)
        try {
            val tmp = File(dst.path + ".tmp")
            c.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            tmp.renameTo(dst)
        } finally { c.disconnect() }
    }
}
