package it.lspin.tv

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.provider.Settings
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var home: HomeView
    private lateinit var prefs: SharedPreferences
    private var boot: BootView? = null
    private var overlay: View? = null
    private val ui = Handler(Looper.getMainLooper())

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { reload() }
    }

    companion object { var booted = false }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        // permette di aprire file:// con le app esterne (gestore file)
        StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().build())
        prefs = getSharedPreferences("lspin", MODE_PRIVATE)
        Sfx.enabled = prefs.getBoolean("sound", true)
        Sfx.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        setContentView(root)
        home = HomeView(this)
        root.addView(home, FrameLayout.LayoutParams(-1, -1))
        home.fx = prefs.getBoolean("fx", true)
        home.onOpen = { open(it) }
        home.onSetting = { setting(it) }
        refreshSettings()
        reload()
        loadHero()

        registerReceiver(pkgReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addDataScheme("package")
        })

        if (!booted) {
            booted = true
            home.alpha = 0f
            boot = BootView(this) { finishBoot() }
            root.addView(boot, FrameLayout.LayoutParams(-1, -1))
            // animazione e jingle partono insieme appena il suono è pronto (o dopo 1,5 s al massimo)
            val go = { boot?.start(); Sfx.play("boot") }
            Sfx.whenBootReady { ui.post { if (boot != null && !boot!!.isStarted()) go() } }
            ui.postDelayed({ if (boot != null && !boot!!.isStarted()) go() }, 1500)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun finishBoot() {
        val bv = boot ?: return
        boot = null
        bv.animate().alpha(0f).scaleX(1.12f).scaleY(1.12f).setDuration(500).withEndAction { root.removeView(bv) }.start()
        home.scaleX = 1.05f; home.scaleY = 1.05f
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start()
        home.replayIntro()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onResume() {
        super.onResume(); immersive()
        // i pulsanti di navigazione non devono mai restare visibili: se compaiono si nascondono subito
        window.decorView.setOnSystemUiVisibilityChangeListener { v ->
            if (v and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0) window.decorView.postDelayed({ immersive() }, 50)
        }
    }
    override fun onWindowFocusChanged(f: Boolean) { super.onWindowFocusChanged(f); if (f) immersive() }

    // Tasto Home premuto: chiude le app interne e torna alla schermata iniziale
    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        if (boot != null) return
        if (overlay != null) closeOverlay() else home.setPageExternal(0)
    }

    private fun reload() {
        Thread {
            val d = AppRepo.load(this)
            runOnUiThread { home.setData(d.featured, d.row) }
        }.start()
    }

    private fun loadHero() {
        val key = (prefs.getString("tmdb", "") ?: "").ifBlank { BuildConfig.TMDB_KEY }
        if (key.isBlank()) return
        HeroLoader.loadTmdb(this, key, { b, t -> home.setHero(b, t) }, { toast(it) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ------------------------------------------------------------------ apertura app
    private fun open(t: Tile) {
        val b = t.builtin
        if (b != null) { openBuiltin(b); return }
        val pkg = t.pkg ?: return
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) { Sfx.play("error"); toast(t.label + " non è installata"); return }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Sfx.play("open")
        try {
            startActivity(i, ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out).toBundle())
        } catch (e: Exception) { Sfx.play("error"); toast("Impossibile aprire " + t.label) }
    }

    private fun openBuiltin(id: String) {
        when (id) {
            "youtube" -> show(BrowserView(this, "https://www.youtube.com/tv", true) { closeOverlay() })
            "browser" -> show(BrowserView(this, "https://duckduckgo.com", false) { closeOverlay() })
            "files" -> show(FilesView(this) { closeOverlay() })
            "terminal" -> show(TerminalView(this) { closeOverlay() })
            "settings" -> home.openSettings()
        }
        Sfx.play("open")
    }

    private fun show(v: View) {
        if (overlay != null) return
        overlay = v
        v.alpha = 0f; v.translationY = 40f
        root.addView(v, FrameLayout.LayoutParams(-1, -1))
        v.animate().alpha(1f).translationY(0f).setDuration(260).withEndAction {
            if (overlay === v) home.visibility = View.INVISIBLE
        }.start()
    }

    private fun closeOverlay() {
        val v = overlay ?: return
        overlay = null
        home.visibility = View.VISIBLE
        v.animate().alpha(0f).translationY(40f).setDuration(220).withEndAction {
            (v as? BrowserView)?.destroy()
            (v as? TerminalView)?.destroy()
            root.removeView(v)
        }.start()
    }

    // ------------------------------------------------------------------ tasti
    override fun onKeyDown(k: Int, e: KeyEvent): Boolean {
        if (boot != null) { boot?.skip(); return true }
        if (k == KeyEvent.KEYCODE_ESCAPE) { onBackPressed(); return true }
        if (overlay == null && home.handleKey(k)) return true
        return super.onKeyDown(k, e)
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (boot != null) return
        val o = overlay
        if (o != null) {
            val handled = when (o) {
                is BrowserView -> o.onBack()
                is FilesView -> o.onBack()
                else -> false
            }
            if (!handled) { Sfx.play("back"); closeOverlay() }
            return
        }
        home.onBack()   // sulla home non si esce: è il desktop
    }

    // ------------------------------------------------------------------ impostazioni
    private fun homeAlias() = ComponentName(this, "it.lspin.tv.HomeAlias")

    // true quando l'app fa parte dell'immagine Android (/system/priv-app): in quel caso è l'unico desktop
    private fun isSystemApp(): Boolean =
        (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

    private fun isPinned(): Boolean =
        packageManager.getComponentEnabledSetting(homeAlias()) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun refreshSettings() {
        val hasKey = !(prefs.getString("tmdb", "") ?: "").isBlank() || BuildConfig.TMDB_KEY.isNotBlank()
        home.setSettings(listOfNotNull(
            SettingItem("sound", "Suoni dell'interfaccia", "Feedback sonoro su spostamenti e aperture", if (Sfx.enabled) "Attivi" else "Disattivi"),
            SettingItem("fx", "Effetti neon", "Animazioni continue (disattiva per alleggerire la grafica)", if (prefs.getBoolean("fx", true)) "Attivi" else "Disattivi"),
            SettingItem("tmdb", "Copertine dei film", "Chiave API gratuita di themoviedb.org", if (hasKey) "Impostata" else "Non impostata"),
            SettingItem("refresh", "Aggiorna copertine", "Scarica di nuovo i film ora al cinema", ""),
            SettingItem("wifi", "Wi-Fi", "Apri le impostazioni Wi-Fi di Android", ""),
            SettingItem("android", "Impostazioni Android", "Impostazioni complete del sistema", ""),
            if (isSystemApp()) null else SettingItem("unpin", if (isPinned()) "Sgancia dal desktop" else "Riaggancia come desktop",
                "Usa di nuovo il launcher di sistema", if (isPinned()) "Agganciato" else "Sganciato"),
            SettingItem("about", "Informazioni", "LSPIN TV 1.0", "")
        ))
    }

    private fun setting(id: String) {
        when (id) {
            "sound" -> {
                Sfx.enabled = !Sfx.enabled
                prefs.edit().putBoolean("sound", Sfx.enabled).apply()
                Sfx.play(if (Sfx.enabled) "on" else "off")
            }
            "fx" -> {
                val v = !prefs.getBoolean("fx", true)
                prefs.edit().putBoolean("fx", v).apply(); home.fx = v
                Sfx.play(if (v) "on" else "off")
            }
            "tmdb" -> askKey()
            "refresh" -> { toast("Aggiorno le copertine…"); loadHero() }
            "wifi" -> external(Settings.ACTION_WIFI_SETTINGS)
            "android" -> external(Settings.ACTION_SETTINGS)
            "unpin" -> toggleHome()
            "about" -> AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("LSPIN TV 1.0")
                .setMessage("Launcher leggero per Raspberry Pi 3B con LineageOS.\n\nQuesto prodotto usa l'API di TMDB ma non è approvato né certificato da TMDB.")
                .setPositiveButton("OK", null).show()
        }
        refreshSettings()
    }

    private fun external(action: String) {
        try { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { Sfx.play("error"); toast("Impostazione non disponibile") }
    }

    private fun askKey() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_TEXT
        et.setSingleLine(true)
        et.setText(prefs.getString("tmdb", ""))
        AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
            .setTitle("Chiave API TMDB").setView(et)
            .setPositiveButton("Salva") { _, _ ->
                prefs.edit().putString("tmdb", et.text.toString().trim()).apply()
                refreshSettings(); loadHero()
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun toggleHome() {
        val pinned = isPinned()
        packageManager.setComponentEnabledSetting(
            homeAlias(),
            if (pinned) PackageManager.COMPONENT_ENABLED_STATE_DISABLED else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        if (pinned) {
            toast("LSPIN TV sganciato: torna il launcher di sistema")
            try { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        } else {
            toast("Premi Home e scegli LSPIN TV, poi \"Sempre\"")
        }
    }
}
