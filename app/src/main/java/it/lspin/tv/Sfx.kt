package it.lspin.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Suoni generati al volo (nessun file audio nell'APK). */
object Sfx {
    private const val SR = 22050
    private var pool: SoundPool? = null
    private val ids = HashMap<String, Int>()
    var enabled = true

    // nota: inizio, frequenza, durata, volume, (frequenza finale opzionale = glissando)
    private fun n(start: Double, f: Double, dur: Double, v: Double) = doubleArrayOf(start, f, dur, v)
    private fun g(start: Double, f: Double, dur: Double, v: Double, f2: Double) = doubleArrayOf(start, f, dur, v, f2)

    fun init(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val sp = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
        pool = sp
        sp.setOnLoadCompleteListener { _, id, _ ->
            if (id == ids["boot"]) Handler(Looper.getMainLooper()).post { bootReady = true; bootCb?.also { bootCb = null; it() } }
        }
        try { ids["boot"] = sp.load(ctx, R.raw.lspin_boot, 1) } catch (e: Exception) { }
        fun add(name: String, pcm: ShortArray) {
            try {
                val f = File(ctx.cacheDir, "sfx2_$name.wav")
                f.writeBytes(wav(pcm))
                ids[name] = sp.load(f.path, 1)
            } catch (e: Exception) { }
        }
        // spostamento orizzontale: scala pentatonica, cambia nota a ogni tile
        val scale = doubleArrayOf(1175.0, 1319.0, 1568.0, 1760.0, 2093.0)
        for (i in 0..4) add("m$i", synth(0.09, n(0.0, scale[i], 0.06, 0.26), n(0.0, scale[i] * 2, 0.04, 0.06)))
        add("mv", synth(0.12, g(0.0, 660.0, 0.08, 0.28, 990.0)))
        add("bump", synth(0.16, g(0.0, 150.0, 0.13, 0.45, 90.0)))
        add("tab", synth(0.25, n(0.0, 988.0, 0.08, 0.30), n(0.06, 1480.0, 0.14, 0.30)))
        add("whoosh", synth(0.45, g(0.0, 300.0, 0.4, 0.16, 1800.0), g(0.0, 306.0, 0.4, 0.12, 1850.0)))
        add("swoosh", synth(0.6, g(0.0, 500.0, 0.5, 0.14, 1400.0), g(0.0, 507.0, 0.5, 0.10, 1420.0)))
        add("pop", synth(0.3, g(0.0, 220.0, 0.12, 0.40, 520.0), n(0.03, 880.0, 0.16, 0.22)))
        add("select", synth(0.25, n(0.0, 880.0, 0.09, 0.35), n(0.07, 1320.0, 0.16, 0.35)))
        add("back", synth(0.25, n(0.0, 700.0, 0.09, 0.30), n(0.07, 466.0, 0.16, 0.30)))
        add("on", synth(0.25, n(0.0, 700.0, 0.07, 0.30), n(0.07, 1050.0, 0.14, 0.30)))
        add("off", synth(0.25, n(0.0, 1050.0, 0.07, 0.30), n(0.07, 700.0, 0.14, 0.30)))
        add("open", synth(0.55, n(0.0, 523.0, 0.14, 0.30), n(0.08, 659.0, 0.14, 0.30),
            n(0.16, 784.0, 0.14, 0.30), n(0.24, 1047.0, 0.28, 0.30)))
        add("error", synth(0.3, n(0.0, 220.0, 0.12, 0.35), n(0.1, 165.0, 0.18, 0.35)))
    }

    private var bootReady = false
    private var bootCb: (() -> Unit)? = null

    /** Chiama cb quando il jingle di accensione è caricato (subito se i suoni sono spenti o non disponibili). */
    fun whenBootReady(cb: () -> Unit) {
        if (bootReady || !enabled || ids["boot"] == null) cb() else bootCb = cb
    }

    fun play(name: String, vol: Float = 1f) {
        if (!enabled) return
        val id = ids[name] ?: return
        pool?.play(id, vol, vol, 1, 0, 1f)
    }

    private fun synth(total: Double, vararg notes: DoubleArray): ShortArray {
        val out = FloatArray((total * SR).toInt())
        for (nt in notes) {
            val s0 = (nt[0] * SR).toInt()
            val len = (nt[2] * SR).toInt()
            val f0 = nt[1]
            val f1 = if (nt.size > 4) nt[4] else f0
            var ph = 0.0
            for (i in 0 until len) {
                val idx = s0 + i
                if (idx >= out.size) break
                val fr = f0 + (f1 - f0) * i / len
                ph += 2 * PI * fr / SR
                val att = min(1.0, i / (0.006 * SR))
                val env = att * exp(-3.2 * i / len)
                val v = sin(ph) + 0.25 * sin(2 * ph)
                out[idx] += (v * env * nt[3]).toFloat()
            }
        }
        return ShortArray(out.size) { (out[it].coerceIn(-1f, 1f) * 30000f).toInt().toShort() }
    }

    private fun wav(pcm: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(44 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36 + pcm.size * 2)
        bb.put("WAVE".toByteArray()); bb.put("fmt ".toByteArray())
        bb.putInt(16); bb.putShort(1); bb.putShort(1); bb.putInt(SR); bb.putInt(SR * 2)
        bb.putShort(2); bb.putShort(16)
        bb.put("data".toByteArray()); bb.putInt(pcm.size * 2)
        for (s in pcm) bb.putShort(s)
        return bb.array()
    }
}
