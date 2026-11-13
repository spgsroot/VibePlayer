package ru.spgsroot.peachy.ui.webview

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.ByteArrayInputStream
import org.json.JSONObject
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.browser.BrowserUrls
import ru.spgsroot.peachy.browser.FoundVideo
import ru.spgsroot.peachy.browser.MediaSniffer
import ru.spgsroot.peachy.browser.RequestKinds
import ru.spgsroot.peachy.browser.MediaKind
import ru.spgsroot.peachy.browser.adblock.FilterEngine
import ru.spgsroot.peachy.browser.adblock.Hosts

/** One browser tab. The WebView exists only once the tab has loaded something. */
class BrowserTab(val id: Int) {
    var webView by mutableStateOf<WebView?>(null)
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    var videos by mutableStateOf<List<FoundVideo>>(emptyList())

    /** Host of the page being shown. Read from WebView's request threads, so it is volatile. */
    @Volatile var host: String? = null

    internal fun noteVideo(video: FoundVideo) {
        if (videos.any { it.url == video.url } || videos.size >= BrowserController.MAX_VIDEOS) return
        videos = videos + video
    }
}

/**
 * Owns the tabs and their WebViews. Everything here runs on the main thread except the
 * request interception, which only reads volatile state and posts its effects to the main thread.
 */
class BrowserController(
    private val activity: Activity,
    private val engine: () -> FilterEngine,
    private val blockerOff: (String?) -> Boolean,
    private val onVisit: (url: String, title: String) -> Unit
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var activeId by mutableIntStateOf(0)
        private set

    /** A page element (usually a video) the page asked to show full screen. */
    var fullscreenView by mutableStateOf<View?>(null)
        private set

    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private val main = Handler(Looper.getMainLooper())
    private var nextId = 1

    val active: BrowserTab get() = tabs.first { it.id == activeId }

    init {
        newTab()
    }

    fun newTab(input: String? = null): BrowserTab {
        if (tabs.size >= MAX_TABS) {
            toast(R.string.browser_too_many_tabs)
            return active
        }
        val tab = BrowserTab(nextId++)
        tabs += tab
        activeId = tab.id
        if (input != null) navigate(tab, input)
        return tab
    }

    fun select(tab: BrowserTab) {
        active.webView?.onPause()
        activeId = tab.id
        tab.webView?.onResume()
    }

    fun close(tab: BrowserTab) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        tab.webView?.let(::destroy)
        tab.webView = null
        tabs.remove(tab)
        if (tabs.isEmpty()) {
            newTab()
        } else if (tab.id == activeId) {
            select(tabs[(index - 1).coerceAtLeast(0)])
        }
    }

    /** Loads what was typed in the address bar: an address, or a search. */
    fun navigate(tab: BrowserTab, input: String) {
        val target = BrowserUrls.resolve(input)
        if (target == null) {
            toast(R.string.error_webview_https)
            return
        }
        tab.url = target
        tab.host = Hosts.hostOf(target)
        webViewOf(tab).loadUrl(target)
    }

    /** Back to the start page of the tab. */
    fun showStart(tab: BrowserTab) {
        tab.webView?.let(::destroy)
        tab.webView = null
        tab.url = ""
        tab.title = ""
        tab.host = null
        tab.videos = emptyList()
        tab.blocked = 0
        tab.loading = false
        tab.canGoBack = false
        tab.canGoForward = false
    }

    fun goBack(tab: BrowserTab) {
        tab.webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun goForward(tab: BrowserTab) {
        tab.webView?.takeIf { it.canGoForward() }?.goForward()
    }

    fun reload(tab: BrowserTab) {
        tab.webView?.reload()
    }

    fun stop(tab: BrowserTab) {
        tab.webView?.stopLoading()
    }

    /** Videos the page has loaded, plus the ones its `<video>` elements point at right now. */
    fun refreshVideos(tab: BrowserTab) {
        tab.webView?.evaluateJavascript(MediaSniffer.COLLECT_SCRIPT) { raw ->
            MediaSniffer.parseCollected(raw).forEach(tab::noteVideo)
        }
    }

    /** Headers a download of [url] needs to look like the page's own request. */
    fun downloadHeaders(tab: BrowserTab, url: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        if (tab.url.isNotEmpty()) out["Referer"] = tab.url
        tab.webView?.settings?.userAgentString?.let { out["User-Agent"] = it }
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let { out["Cookie"] = it }
        return out
    }

    fun hideFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        (view.parent as? android.view.ViewGroup)?.removeView(view)
    }

    fun destroyAll() {
        hideFullscreen()
        tabs.forEach { tab -> tab.webView?.let(::destroy); tab.webView = null }
    }

    private fun destroy(view: WebView) {
        view.stopLoading()
        view.webViewClient = WebViewClient()
        view.webChromeClient = null
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        view.destroy()
    }

    private fun toast(resId: Int) = Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show()

    private fun tabOf(view: WebView?): BrowserTab? = tabs.firstOrNull { it.webView === view }

    fun webViewOf(tab: BrowserTab): WebView = tab.webView ?: createWebView(tab).also { tab.webView = it }

    private fun createWebView(tab: BrowserTab): WebView = WebView(activity).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setSupportMultipleWindows(false)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        setDownloadListener { url, _, _, mimeType, _ ->
            if (mimeType.startsWith("video/") && MediaSniffer.isAllowedDownload(url, tab.host)) {
                tab.noteVideo(FoundVideo(url, MediaSniffer.classify(url) ?: MediaKind.FILE))
                toast(R.string.browser_download_offered)
            } else {
                toast(R.string.browser_download_unsupported)
            }
        }
        webViewClient = Client()
        webChromeClient = Chrome()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url?.toString().orEmpty()
            if (BrowserUrls.isWebUrl(target) && target.startsWith("https://", ignoreCase = true)) return false
            // A page may link to plain HTTP or to an app scheme; the browser follows neither.
            if (target.startsWith("http://", ignoreCase = true) && BrowserUrls.isWebUrl(target)) {
                view.loadUrl("https://" + target.substring("http://".length))
            } else {
                toast(R.string.error_webview_https)
            }
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url?.toString() ?: return null
            val tab = tabOf(view)
            if (request.isForMainFrame) {
                tab?.host = Hosts.hostOf(url)
            } else if (tab != null && !blockerOff(tab.host)) {
                val kind = RequestKinds.guess(url, request.requestHeaders, false)
                if (engine().shouldBlock(url, tab.host, kind)) {
                    main.post { tab.blocked++ }
                    return WebResourceResponse("text/plain", "utf-8", 204, "No Content", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
            }
            val media = MediaSniffer.classify(url)
            if (media != null && tab != null) main.post { tab.noteVideo(FoundVideo(url, media)) }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            val tab = tabOf(view) ?: return
            tab.url = url
            tab.host = Hosts.hostOf(url)
            tab.loading = true
            tab.blocked = 0
            tab.videos = emptyList()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            hideAds(view)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            val tab = tabOf(view) ?: return
            tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }

        override fun onPageFinished(view: WebView, url: String) {
            val tab = tabOf(view) ?: return
            tab.loading = false
            tab.url = url
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
            hideAds(view)
            refreshVideos(tab)
            onVisit(url, view.title.orEmpty())
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // The page's process died; drop the view so the tab can be reloaded instead of crashing the app.
            val tab = tabOf(view) ?: return true
            tab.webView = null
            tab.loading = false
            destroy(view)
            toast(R.string.browser_page_crashed)
            return true
        }
    }

    private fun hideAds(view: WebView) {
        val tab = tabOf(view) ?: return
        val host = tab.host ?: return
        if (blockerOff(host)) return
        val css = engine().cosmeticCss(host)
        if (css.isEmpty()) return
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('peachy-hide');if(!s){s=document.createElement('style');" +
                "s.id='peachy-hide';(document.head||document.documentElement).appendChild(s);}" +
                "s.textContent=${JSONObject.quote(css)};})()",
            null
        )
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tabOf(view)?.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            tabOf(view)?.title = title.orEmpty()
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            hideFullscreen()
            fullscreenView = view
            fullscreenCallback = callback
        }

        override fun onHideCustomView() {
            hideFullscreen()
        }
    }

    companion object {
        const val MAX_TABS = 10
        const val MAX_VIDEOS = 40
    }
}
