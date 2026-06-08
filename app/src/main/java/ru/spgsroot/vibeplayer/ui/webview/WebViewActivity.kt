package ru.spgsroot.vibeplayer.ui.webview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
import ru.spgsroot.vibeplayer.domain.dsp.HapticInputRouter
import ru.spgsroot.vibeplayer.domain.dsp.HapticSource
import ru.spgsroot.vibeplayer.playback.service.WebViewAudioCaptureService
import javax.inject.Inject

@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    @Inject
    lateinit var hapticInputRouter: HapticInputRouter

    private var webView: WebView? = null
    private lateinit var initialUrl: String
    private var bridgeLastLogMs = 0L

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

        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, "Missing WebView URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        initialUrl = url

        setContent {
            WebViewContent(initialUrl)
        }

        beginAudioCaptureFlow()
    }

    override fun onDestroy() {
        runCatching {
            stopService(WebViewAudioCaptureService.stopIntent(applicationContext))
        }
        hapticInputRouter.release(HapticSource.WEBVIEW)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
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
        val serviceIntent = WebViewAudioCaptureService.captureIntent(
            context = applicationContext,
            resultCode = resultCode,
            data = data
        )
        ContextCompat.startForegroundService(applicationContext, serviceIntent)
    }

    @Composable
    private fun WebViewContent(url: String) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    webView = this
                    addJavascriptInterface(WebViewAudioBridge(), JS_BRIDGE_NAME)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            injectWebAudioBridge(view)
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            val message = consoleMessage.message().orEmpty()
                            when {
                                message.startsWith(JS_LOG_PREFIX) -> {
                                    Log.d(TAG, "WebView console: $message")
                                }
                                consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR -> {
                                    Log.w(
                                        TAG,
                                        "WebView console error: $message @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                                    )
                                }
                            }
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    loadUrl(url)
                }
            },
            update = { view ->
                if (view.url != url) {
                    view.loadUrl(url)
                }
            }
        )
    }

    private fun injectWebAudioBridge(view: WebView) {
        Log.d(TAG, "Injecting JS WebAudio bridge into ${view.url}")
        view.evaluateJavascript(WEB_AUDIO_BRIDGE_SCRIPT) { result ->
            Log.d(TAG, "JS WebAudio bridge injection result=$result url=${view.url}")
        }
    }

    inner class WebViewAudioBridge {
        @JavascriptInterface
        fun onAmplitude(rawAmplitude: String) {
            val parsedAmplitude = rawAmplitude.toFloatOrNull()
            if (parsedAmplitude == null || !parsedAmplitude.isFinite()) {
                Log.w(TAG, "Invalid JS WebAudio amplitude=$rawAmplitude")
                return
            }
            val amplitude = parsedAmplitude.coerceIn(0f, 1f)
            hapticInputRouter.acquire(HapticSource.WEBVIEW)
            val emitted = hapticInputRouter.emit(HapticSource.WEBVIEW, amplitude)
            val now = SystemClock.elapsedRealtime()
            if (now - bridgeLastLogMs >= LOG_INTERVAL_MS) {
                Log.d(TAG, "JS WebAudio amplitude=$amplitude emitted=$emitted")
                bridgeLastLogMs = now
            }
        }

        @JavascriptInterface
        fun onBridgeError(message: String) {
            Log.w(TAG, "JS WebAudio bridge error: $message")
        }
    }

    companion object {
        private const val TAG = "WebViewActivity"
        private const val JS_BRIDGE_NAME = "VibePlayerAudioBridge"
        private const val JS_LOG_PREFIX = "[VibePlayer]"
        private const val LOG_INTERVAL_MS = 1_000L
        const val EXTRA_URL = "ru.spgsroot.vibeplayer.ui.webview.EXTRA_URL"

        private val WEB_AUDIO_BRIDGE_SCRIPT = """
            (function() {
              function log(message) {
                if (window.console && window.console.log) console.log('[VibePlayer] ' + message);
              }

              function warn(message) {
                if (window.console && window.console.warn) console.warn('[VibePlayer] ' + message);
              }

              if (window.__vibePlayerAudioBridgeInstalled) {
                log('WebAudio bridge already installed');
                return;
              }
              window.__vibePlayerAudioBridgeInstalled = true;
              log('WebAudio bridge installing');

              var AudioContextCtor = window.AudioContext || window.webkitAudioContext;
              if (!AudioContextCtor) {
                window.VibePlayerAudioBridge && window.VibePlayerAudioBridge.onBridgeError('AudioContext is unavailable');
                return;
              }

              var context = null;
              var attachedElements = new WeakSet();
              var lastNoMediaLogMs = 0;

              function sendAmplitude(value) {
                if (window.VibePlayerAudioBridge && window.VibePlayerAudioBridge.onAmplitude) {
                  window.VibePlayerAudioBridge.onAmplitude(String(value));
                }
              }

              function sendError(message) {
                warn(message);
                if (window.VibePlayerAudioBridge && window.VibePlayerAudioBridge.onBridgeError) {
                  window.VibePlayerAudioBridge.onBridgeError(String(message));
                }
              }

              function ensureContext() {
                if (!context) context = new AudioContextCtor();
                if (context.state === 'suspended') context.resume().catch(function() {});
                return context;
              }

              function attach(element) {
                if (!element || attachedElements.has(element)) return;
                attachedElements.add(element);

                try {
                  var ctx = ensureContext();
                  var source = ctx.createMediaElementSource(element);
                  var analyser = ctx.createAnalyser();
                  analyser.fftSize = 1024;
                  source.connect(analyser);
                  analyser.connect(ctx.destination);
                  log('Attached media element tag=' + element.tagName + ' src=' + (element.currentSrc || element.src || 'inline'));

                  var data = new Uint8Array(analyser.fftSize);
                  function tick() {
                    if (element.isConnected === false) return;
                    if (element.isConnected === undefined && !document.contains(element)) return;
                    if (!element.paused && !element.ended) {
                      if (ctx.state === 'suspended') ctx.resume().catch(function() {});
                      analyser.getByteTimeDomainData(data);
                      var sum = 0;
                      for (var i = 0; i < data.length; i++) {
                        var sample = (data[i] - 128) / 128;
                        sum += sample * sample;
                      }
                      sendAmplitude(Math.sqrt(sum / data.length).toFixed(4));
                    }
                    window.requestAnimationFrame(tick);
                  }
                  window.requestAnimationFrame(tick);
                } catch (error) {
                  sendError(error && error.message ? error.message : error);
                }
              }

              function collectMedia(root, result) {
                if (!root || !root.querySelectorAll) return result;

                var media = root.querySelectorAll('audio,video');
                for (var i = 0; i < media.length; i++) result.push(media[i]);

                var all = root.querySelectorAll('*');
                for (var j = 0; j < all.length; j++) {
                  if (all[j].shadowRoot) collectMedia(all[j].shadowRoot, result);
                }
                return result;
              }

              function scan() {
                var media = collectMedia(document, []);
                if (!media.length) {
                  var now = Date.now();
                  if (now - lastNoMediaLogMs >= 2000) {
                    sendError('No audio/video elements found on page scan');
                    lastNoMediaLogMs = now;
                  }
                  return;
                }
                for (var i = 0; i < media.length; i++) attach(media[i]);
              }

              var originalPlay = HTMLMediaElement.prototype.play;
              HTMLMediaElement.prototype.play = function() {
                attach(this);
                return originalPlay.apply(this, arguments);
              };

              document.addEventListener('click', function() {
                ensureContext();
                scan();
              }, true);

              new MutationObserver(scan).observe(document.documentElement || document, {
                childList: true,
                subtree: true
              });

              scan();
            })();
        """.trimIndent()
    }
}
