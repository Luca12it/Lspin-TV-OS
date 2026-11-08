package it.lspin.tv

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/** Gestore file interno: sfoglia, apre (con l'app giusta) ed elimina con pressione lunga. */
class FilesView(ctx: Context, private val onClose: () -> Unit) : LinearLayout(ctx) {
    private var dir: File = Environment.getExternalStorageDirectory()
    private var items: List<File> = emptyList()
    private val list = ListView(ctx)
    private val title = TextView(ctx)
    private val note = TextView(ctx)

    private val adapter = object : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(i: Int) = items[i]
        override fun getItemId(i: Int) = i.toLong()
        override fun getView(i: Int, v: View?, p: ViewGroup?): View {
            val tv = (v as? TextView) ?: TextView(context).apply {
                setTextColor(Color.WHITE); textSize = 20f; setSingleLine(true)
                val pd = Ui.dp(context, 14f); setPadding(pd * 2, pd, pd * 2, pd)
            }
            val f = items[i]
            tv.text = if (f.isDirectory) "📁  " + f.name else "📄  " + f.name + "    " + size(f.length())
            return tv
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Ui.BG)
        val bar = LinearLayout(ctx).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.BAR)
            val pd = Ui.dp(ctx, 8f); setPadding(pd, pd, pd, pd)
        }
        val m = Ui.dp(ctx, 4f)
        bar.addView(Ui.btn(ctx, "Su") { up() }, Ui.lp(-2, -2, 0f, m))
        title.setTextColor(0xCCFFFFFF.toInt()); title.textSize = 17f; title.setSingleLine(true)
        title.setPadding(Ui.dp(ctx, 12f), 0, 0, 0)
        bar.addView(title, Ui.lp(0, -2, 1f, m))
        bar.addView(Ui.btn(ctx, "Chiudi") { onClose() }, Ui.lp(-2, -2, 0f, m))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        note.setTextColor(0xFFFFD27A.toInt()); note.textSize = 16f
        note.text = "Per vedere i file serve l'accesso: tocca qui e concedilo."
        val np = Ui.dp(ctx, 12f); note.setPadding(np * 2, np, np, np)
        note.setOnClickListener { askPermission() }
        addView(note, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        list.adapter = adapter
        list.divider = null
        list.selector = Ui.rr(Theme.SELECT, Ui.dp(ctx, 10f).toFloat())
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ -> openItem(items[i]) }
        list.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, i: Int, id: Long) { Sfx.play("m2") }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        list.setOnItemLongClickListener { _, _, i, _ -> confirmDelete(items[i]); true }
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
        list.requestFocus()
    }

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    private fun askPermission() {
        try {
            val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(i)
        } catch (e: Exception) { }
    }

    private fun refresh() {
        val ls = dir.listFiles()
        items = (ls?.toList() ?: emptyList()).sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        adapter.notifyDataSetChanged()
        title.text = dir.path
        note.visibility = if (ls == null && !hasAccess()) View.VISIBLE else View.GONE
    }

    private fun up() {
        val p = dir.parentFile
        if (p != null && p.canRead()) { dir = p; Sfx.play("back"); refresh() }
    }

    private fun openItem(f: File) {
        if (f.isDirectory) { dir = f; Sfx.play("select"); refresh(); return }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        try {
            val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.fromFile(f), mime).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i); Sfx.play("open")
        } catch (e: Exception) {
            Sfx.play("error"); Toast.makeText(context, "Nessuna app per aprire questo file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete(f: File) {
        android.app.AlertDialog.Builder(context, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Eliminare?").setMessage(f.name)
            .setPositiveButton("Elimina") { _, _ -> f.deleteRecursively(); refresh() }
            .setNegativeButton("Annulla", null).show()
    }

    fun onBack(): Boolean {
        val root = Environment.getExternalStorageDirectory()
        if (dir.path != root.path && dir.parentFile != null) { up(); return true }
        return false
    }

    private fun size(b: Long): String = when {
        b > 1L shl 30 -> String.format("%.1f GB", b / 1073741824.0)
        b > 1L shl 20 -> String.format("%.1f MB", b / 1048576.0)
        b > 1L shl 10 -> String.format("%.0f KB", b / 1024.0)
        else -> "$b B"
    }
}
