package it.lspin.tv

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

class Tile(
    val id: String, val label: String, val sub: String,
    val glyph: G, val bigGlyph: G, val c1: Int, val c2: Int,
    val pkg: String?, val builtin: String?, val icon: Drawable? = null
)

object AppRepo {
    class Data(val featured: List<Tile>, val row: List<Tile>)

    private val NAVY1 = 0xFF1C2B63.toInt()
    private val NAVY2 = 0xFF0B0F26.toInt()
    private val PALETTE = listOf(
        Pair(0xFFEF4444.toInt(), 0xFF3A0A0A.toInt()), Pair(0xFFF97316.toInt(), 0xFF3A1703.toInt()),
        Pair(0xFFEAB308.toInt(), 0xFF332A04.toInt()), Pair(0xFF22C55E.toInt(), 0xFF052A14.toInt()),
        Pair(0xFF14B8A6.toInt(), 0xFF042B27.toInt()), Pair(0xFF06B6D4.toInt(), 0xFF052A33.toInt()),
        Pair(0xFF3B82F6.toInt(), 0xFF0A1A44.toInt()), Pair(0xFF8B5CF6.toInt(), 0xFF1E0F4A.toInt()),
        Pair(0xFFEC4899.toInt(), 0xFF3A0C24.toInt())
    )
    // app già presenti come tile dedicati, o sostituite da app interne
    private val skip = setOf(
        "org.xbmc.kodi", "com.spotify.music", "org.videolan.vlc",
        "org.lineageos.jelly", "com.android.documentsui", "org.lineageos.trebuchet",
        "com.android.launcher3", "com.android.quicksearchbox"
    )

    fun load(ctx: Context): Data {
        val pm = ctx.packageManager
        val yt = Tile("youtube", "YouTube", "Video, musica e dirette", G.YT, G.PLAY, 0xFFE5253F.toInt(), 0xFF2A0A4A.toInt(), null, "youtube")
        val kodi = Tile("kodi", "Kodi", "Il media center per film, serie, musica e foto.", G.MONITOR, G.MONITOR, 0xFF3D8BFF.toInt(), 0xFF071644.toInt(), "org.xbmc.kodi", null)
        val spot = Tile("spotify", "Spotify", "Musica e podcast", G.MUSIC, G.SPOTIFY, 0xFF14893F.toInt(), 0xFF031A0C.toInt(), "com.spotify.music", null)
        val vlc = Tile("vlc", "VLC", "Il lettore per ogni file video e audio", G.PLAYBOX, G.CONE, 0xFF9A72FF.toInt(), 0xFF1B1050.toInt(), "org.videolan.vlc", null)
        val br = Tile("browser", "Browser", "Naviga su internet", G.GLOBE, G.GLOBE, 0xFF22CDE8.toInt(), 0xFF052A33.toInt(), null, "browser")
        val files = Tile("files", "File", "Sfoglia file e cartelle", G.FOLDER, G.FOLDER, 0xFFF0B429.toInt(), 0xFF3A2605.toInt(), null, "files")
        val term = Tile("terminal", "Terminale", "Riga di comando", G.TERMINAL, G.TERMINAL, 0xFF22C55E.toInt(), 0xFF052A14.toInt(), null, "terminal")
        val set = Tile("lsettings", "Impostazioni", "Impostazioni di LSPIN TV", G.GEAR, G.GEAR, 0xFF8B5CF6.toInt(), 0xFF1E0F4A.toInt(), null, "settings")

        val featured = listOf(yt, kodi, spot, vlc, br)
        val row = ArrayList<Tile>(featured)
        row.add(files); row.add(term); row.add(set)

        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val user = ArrayList<Tile>(); val sys = ArrayList<Tile>()
        val seen = HashSet<String>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val ai = ri.activityInfo.applicationInfo
            val pkg = ai.packageName
            if (pkg == ctx.packageName || pkg in skip || !seen.add(pkg)) continue
            val isSys = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            var label = ri.loadLabel(pm).toString()
            if (pkg == "com.android.settings") label = "Impostazioni Android"
            if (isSys) {
                val g = guess(pkg, label)
                val cols = colorsFor(g)
                sys.add(Tile(pkg, label, "App di sistema", g, g, cols.first, cols.second, pkg, null))
            } else {
                // app installate: l'icona resta quella originale
                val pc = PALETTE[(pkg.hashCode() and 0x7fffffff) % PALETTE.size]
                user.add(Tile(pkg, label, "App installata", G.GRID, G.GRID, pc.first, pc.second, pkg, null, ri.loadIcon(pm)))
            }
        }
        user.sortBy { it.label.lowercase() }
        sys.sortBy { it.label.lowercase() }
        row.addAll(user); row.addAll(sys)
        return Data(featured, row)
    }

    private fun guess(pkg: String, label: String): G {
        val k = (pkg + " " + label).lowercase()
        return when {
            "setting" in k || "impostaz" in k -> G.GEAR
            "camera" in k || "fotocamera" in k -> G.CAMERA
            "gallery" in k || "photo" in k || "galleria" in k || "foto" in k -> G.IMAGE
            "music" in k || "musica" in k || "audio" in k || "eleven" in k -> G.MUSIC
            "calc" in k -> G.CALC
            "clock" in k || "orolog" in k -> G.CLOCK
            "calendar" in k || "calendario" in k -> G.CAL
            "phone" in k || "dialer" in k || "telefon" in k || "contact" in k || "contatti" in k -> G.PHONE
            "mms" in k || "messag" in k || "sms" in k -> G.CHAT
            "mail" in k -> G.MAIL
            "record" in k || "registr" in k -> G.MIC
            "updater" in k || "download" in k || "aggiorn" in k -> G.DOWN
            "file" in k || "document" in k || "esplora" in k -> G.FOLDER
            "browser" in k || "jelly" in k -> G.GLOBE
            else -> G.GRID
        }
    }

    private fun colorsFor(g: G): Pair<Int, Int> = when (g) {
        G.GEAR -> Pair(0xFF55658A.toInt(), 0xFF1A2236.toInt())
        G.CAMERA -> Pair(0xFF2E7D8C.toInt(), 0xFF0B2630.toInt())
        G.IMAGE -> Pair(0xFFD0537A.toInt(), 0xFF3A1024.toInt())
        G.MUSIC -> Pair(0xFFB04BD8.toInt(), 0xFF2A0F3A.toInt())
        G.CALC -> Pair(0xFFF0902E.toInt(), 0xFF3A2005.toInt())
        G.CLOCK -> Pair(0xFF3D8BFF.toInt(), 0xFF0A1E4A.toInt())
        G.CAL -> Pair(0xFFE04C4C.toInt(), 0xFF3A0F0F.toInt())
        G.PHONE -> Pair(0xFF2FB36B.toInt(), 0xFF06281A.toInt())
        G.CHAT -> Pair(0xFF2F9BD6.toInt(), 0xFF08283A.toInt())
        G.MAIL -> Pair(0xFFE0653A.toInt(), 0xFF3A1508.toInt())
        G.MIC -> Pair(0xFF7A5BFF.toInt(), 0xFF1B1050.toInt())
        G.DOWN -> Pair(0xFF3FB8AF.toInt(), 0xFF0A2D2A.toInt())
        G.FOLDER -> Pair(0xFFE0B03A.toInt(), 0xFF3A2D08.toInt())
        G.GLOBE -> Pair(0xFF3C52B0.toInt(), 0xFF0C1030.toInt())
        else -> Pair(0xFF5D6DD6.toInt(), 0xFF14183F.toInt())
    }
}

/** Disegna la "card" di una tile (sfondo a gradiente, onde, icona, etichetta) in una bitmap in cache. */
object TileArt {
    private val tfMed = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun make(t: Tile, w: Float, h: Float, big: Boolean, s: Float): Bitmap {
        val b = Bitmap.createBitmap(max(1, (w * s).toInt()), max(1, (h * s).toInt()), Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.scale(s, s)
        val rad = min(26f, h * 0.16f)
        val rect = RectF(0f, 0f, w, h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, w, h, Theme.tileTop(big, t.c1), Theme.tileBottom(big, t.c2), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, rad, rad, p)
        p.shader = null

        // onde luminose (solo nel tema neon)
        if (!Theme.PRO) {
        c.save()
        val clip = Path(); clip.addRoundRect(rect, rad, rad, Path.Direction.CW); c.clipPath(clip)
        val w1 = Path()
        w1.moveTo(-10f, h * 0.75f); w1.cubicTo(w * 0.25f, h * 0.05f, w * 0.55f, h * 1.15f, w + 10f, h * 0.2f)
        w1.lineTo(w + 10f, h * 0.42f); w1.cubicTo(w * 0.55f, h * 1.35f, w * 0.25f, h * 0.3f, -10f, h * 1.0f); w1.close()
        p.color = 0x22FFFFFF; c.drawPath(w1, p)
        val w2 = Path()
        w2.moveTo(-10f, h * 0.2f); w2.cubicTo(w * 0.3f, h * 0.9f, w * 0.7f, -h * 0.1f, w + 10f, h * 0.7f)
        w2.lineTo(w + 10f, h * 0.85f); w2.cubicTo(w * 0.7f, h * 0.1f, w * 0.3f, h * 1.1f, -10f, h * 0.4f); w2.close()
        p.color = 0x18FFFFFF; c.drawPath(w2, p)
        c.restore()
        }

        // bordo
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f; p.color = 0x33FFFFFF
        c.drawRoundRect(RectF(0.75f, 0.75f, w - 0.75f, h - 0.75f), rad, rad, p)
        p.style = Paint.Style.FILL

        // icona
        val isz = h * 0.42f
        val icy = h * 0.37f
        val d = t.icon
        if (d != null) {
            val l = (w / 2f - isz / 2f).toInt(); val tp = (icy - isz / 2f).toInt()
            d.setBounds(l, tp, l + isz.toInt(), tp + isz.toInt()); d.draw(c)
        } else {
            Glyphs.draw(c, if (big) t.bigGlyph else t.glyph, w / 2f, icy, isz, Color.WHITE)
        }

        // etichetta
        p.color = Color.WHITE; p.typeface = tfMed; p.textAlign = Paint.Align.CENTER
        p.textSize = (h * 0.17f).coerceIn(21f, 28f)
        var lab = t.label
        if (p.measureText(lab) > w - 24f) {
            val n = p.breakText(lab, true, w - 40f, null)
            lab = lab.substring(0, max(1, n)) + "…"
        }
        c.drawText(lab, w / 2f, h * 0.84f, p)
        return b
    }
}
