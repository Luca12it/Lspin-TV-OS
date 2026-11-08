package it.lspin.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Terminale semplice: esegue comandi con "sh -c" come utente dell'app (non root). */
class TerminalView(ctx: Context, private val onClose: () -> Unit) : LinearLayout(ctx) {
    private val out = TextView(ctx)
    private val scroll = ScrollView(ctx)
    private val input = EditText(ctx)
    private val prompt = TextView(ctx)
    private var cwd: File = Environment.getExternalStorageDirectory().takeIf { it.exists() } ?: File("/")
    private var proc: Process? = null
    private val hist = ArrayList<String>()
    private var hi = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.BG)
        val bar = LinearLayout(ctx).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.BAR)
            val pd = Ui.dp(ctx, 8f); setPadding(pd, pd, pd, pd)
        }
        val m = Ui.dp(ctx, 4f)
        val t = TextView(ctx); t.text = "Terminale"; t.setTextColor(Color.WHITE); t.textSize = 19f
        t.setPadding(Ui.dp(ctx, 12f), 0, 0, 0)
        bar.addView(t, Ui.lp(0, -2, 1f, m))
        bar.addView(Ui.btn(ctx, "Ferma") { proc?.destroy() }, Ui.lp(-2, -2, 0f, m))
        bar.addView(Ui.btn(ctx, "Pulisci") { out.text = "" }, Ui.lp(-2, -2, 0f, m))
        bar.addView(Ui.btn(ctx, "Chiudi") { onClose() }, Ui.lp(-2, -2, 0f, m))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        out.setTextColor(0xFFD7E3FF.toInt()); out.typeface = Typeface.MONOSPACE; out.textSize = 15f
        val pd = Ui.dp(ctx, 12f); out.setPadding(pd, pd, pd, pd)
        out.text = "LSPIN Terminale - scrivi un comando (es. ls, ip a, ping -c 3 8.8.8.8)\n"
        scroll.addView(out)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        prompt.setTextColor(Theme.VALUE); prompt.typeface = Typeface.MONOSPACE; prompt.textSize = 14f
        prompt.setPadding(pd, 0, pd, 0)
        addView(prompt, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        input.setSingleLine(true); input.typeface = Typeface.MONOSPACE; input.textSize = 17f
        input.setTextColor(0xFF9DFFB0.toInt()); input.background = Ui.rr(0x22FFFFFF, Ui.dp(ctx, 10f).toFloat())
        input.setPadding(pd, pd / 2, pd, pd / 2)
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        input.setOnEditorActionListener { _, _, _ -> submit(); true }
        input.setOnKeyListener { _, k, e ->
            if (e.action == KeyEvent.ACTION_DOWN && (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) && hist.isNotEmpty()) {
                hi = (hi + if (k == KeyEvent.KEYCODE_DPAD_UP) -1 else 1).coerceIn(0, hist.size)
                input.setText(if (hi < hist.size) hist[hi] else ""); input.setSelection(input.text.length); true
            } else false
        }
        addView(input, Ui.lp(-1, -2, 0f, Ui.dp(ctx, 8f)))
        updatePrompt()
        input.requestFocus()
    }

    private fun updatePrompt() { prompt.text = cwd.path + " $" }

    private fun append(s: String) {
        out.append(s)
        val t = out.text
        if (t.length > 40000) out.text = t.subSequence(t.length - 30000, t.length)
        scroll.post { scroll.fullScroll(FOCUS_DOWN) }
    }

    private fun submit() {
        val cmd = input.text.toString().trim()
        input.setText("")
        if (cmd.isEmpty()) return
        hist.add(cmd); hi = hist.size
        append("$ $cmd\n")
        when {
            cmd == "clear" -> out.text = ""
            cmd == "exit" -> onClose()
            cmd == "cd" || cmd.startsWith("cd ") -> {
                val arg = cmd.removePrefix("cd").trim()
                val nd = when {
                    arg.isEmpty() -> Environment.getExternalStorageDirectory()
                    arg.startsWith("/") -> File(arg)
                    else -> File(cwd, arg)
                }
                try {
                    val cd = nd.canonicalFile
                    if (cd.isDirectory) { cwd = cd; updatePrompt() } else append("cd: cartella non trovata\n")
                } catch (e: Exception) { append("cd: errore\n") }
            }
            else -> run(cmd)
        }
    }

    private fun run(cmd: String) {
        Thread {
            try {
                val p = ProcessBuilder("sh", "-c", cmd).directory(cwd).redirectErrorStream(true).start()
                proc = p
                p.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(1024)
                    while (true) {
                        val n = r.read(buf)
                        if (n < 0) break
                        val s = String(buf, 0, n)
                        post { append(s) }
                    }
                }
                p.waitFor()
            } catch (e: Exception) {
                post { append("Errore: " + e.message + "\n") }
            }
        }.start()
    }

    fun destroy() { proc?.destroy() }
}
