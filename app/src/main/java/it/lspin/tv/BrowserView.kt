package it.lspin.tv

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout

/** Browser interno (WebView). Con tvMode=true si presenta come TV per aprire YouTube TV. */
class BrowserView(ctx: Context, startUrl: String, tvMode: Boolean, private val onClose: () -> Unit) : LinearLayout(ctx) {
    private val web = WebView(ctx)
    private val url = EditText(ctx)
    private var custom: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private val home = startUrl

    private val chrome = object : WebChromeClient() {
        override fun onShowCustomView(v: View, cb: CustomViewCallback) {
            custom = v; customCb = cb
            (this@BrowserView.parent as? ViewGroup)?.addView(v, ViewGroup.LayoutParams(-1, -1))
        }
        override fun onHideCustomView() {
            custom?.let { (it.parent as? ViewGroup)?.removeView(it) }
            custom = null; customCb?.onCustomViewHidden(); customCb = null
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
        bar.addView(Ui.btn(ctx, "Indietro") { if (web.canGoBack()) web.goBack() }, Ui.lp(-2, -2, 0f, m))
        bar.addView(Ui.btn(ctx, "Avanti") { if (web.canGoForward()) web.goForward() }, Ui.lp(-2, -2, 0f, m))
        bar.addView(Ui.btn(ctx, "Ricarica") { web.reload() }, Ui.lp(-2, -2, 0f, m))
        bar.addView(Ui.btn(ctx, "Home") { web.loadUrl(home) }, Ui.lp(-2, -2, 0f, m))
        url.setSingleLine(true)
        url.setTextColor(Color.WHITE); url.setHintTextColor(0x88FFFFFF.toInt()); url.hint = "Cerca o scrivi un indirizzo"
        url.textSize = 17f
        url.background = Ui.rr(0x22FFFFFF, Ui.dp(ctx, 12f).toFloat())
        val ip = Ui.dp(ctx, 14f); url.setPadding(ip, Ui.dp(ctx, 8f), ip, Ui.dp(ctx, 8f))
        url.imeOptions = EditorInfo.IME_ACTION_GO
        url.setOnEditorActionListener { _, _, _ -> go(); true }
        bar.addView(url, Ui.lp(0, -2, 1f, m))
        bar.addView(Ui.btn(ctx, "Chiudi") { onClose() }, Ui.lp(-2, -2, 0f, m))
        addView(bar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(web, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true; loadWithOverviewMode = true
            if (tvMode) userAgentString =
                "Mozilla/5.0 (PlayStation; PlayStation 4/11.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Safari/605.1.15"
        }
        web.setBackgroundColor(Color.BLACK)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, u: String) { if (!url.hasFocus()) url.setText(u) }
        }
        web.webChromeClient = chrome
        web.loadUrl(startUrl)
        if (tvMode) web.requestFocus()
    }

    private fun go() {
        val t = url.text.toString().trim()
        if (t.isEmpty()) return
        val u = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://duckduckgo.com/?q=" + Uri.encode(t)
        }
        web.loadUrl(u); web.requestFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(url.windowToken, 0)
    }

    fun onBack(): Boolean {
        if (custom != null) { chrome.onHideCustomView(); return true }
        if (web.canGoBack()) { web.goBack(); return true }
        return false
    }

    fun destroy() {
        chrome.onHideCustomView()
        web.stopLoading(); web.loadUrl("about:blank"); web.destroy()
    }
}
