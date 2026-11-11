package ru.spgsroot.vibeplayer.ui.webview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.domain.dsp.HapticInputRouter
import ru.spgsroot.vibeplayer.domain.dsp.HapticSource
import ru.spgsroot.vibeplayer.playback.service.WebViewAudioCaptureService
import javax.inject.Inject

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    @Inject
    lateinit var hapticInputRouter: HapticInputRouter

    private var webView: WebView? = null
    private var captureStarted = false

    private val recordAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestMediaProjectionConsent()
        } else {
            Toast.makeText(this, "RECORD_AUDIO permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        ensureRecordAudioPermission()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            startAudioCaptureService(result.resultCode, data)
        } else {
            Toast.makeText(this, "Audio capture consent was not granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val url = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        if (!isOpenableWebViewUrl(url)) {
            Toast.makeText(this, R.string.error_webview_https, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            WebViewContent(url)
        }
        beginAudioCaptureFlow()
    }

    override fun onStop() {
        stopCapture()
        super.onStop()
    }

    override fun onDestroy() {
        stopCapture()
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun stopCapture() {
        if (!captureStarted && webView == null) return
        runCatching {
            stopService(WebViewAudioCaptureService.stopIntent(applicationContext))
        }
        hapticInputRouter.release(HapticSource.WEBVIEW)
        captureStarted = false
    }

    private fun beginAudioCaptureFlow() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(this, "WebView audio capture requires Android 10+", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        ensureRecordAudioPermission()
    }

    private fun ensureRecordAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            requestMediaProjectionConsent()
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun requestMediaProjectionConsent() {
        val mediaProjectionManager = applicationContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startAudioCaptureService(resultCode: Int, data: Intent) {
        captureStarted = true
        ContextCompat.startForegroundService(
            applicationContext,
            WebViewAudioCaptureService.captureIntent(
                context = applicationContext,
                resultCode = resultCode,
                data = data
            )
        )
    }

    @Composable
    private fun WebViewContent(url: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val target = request.url?.toString().orEmpty()
                            if (isOpenableWebViewUrl(target)) return false
                            Toast.makeText(this@WebViewActivity, R.string.error_webview_https, Toast.LENGTH_SHORT).show()
                            return true
                        }
                    }
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
                    loadUrl(url)
                }
            }
        )
    }

    companion object {
        const val EXTRA_URL = "extra_url"

        fun isOpenableWebViewUrl(url: String): Boolean = url.trim().startsWith("https://")
    }
}
