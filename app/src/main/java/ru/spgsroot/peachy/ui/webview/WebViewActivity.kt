package ru.spgsroot.peachy.ui.webview

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.data.browser.BrowserStore
import ru.spgsroot.peachy.domain.dsp.HapticInputRouter
import ru.spgsroot.peachy.domain.dsp.HapticSource
import ru.spgsroot.peachy.playback.service.WebViewAudioCaptureService
import ru.spgsroot.peachy.ui.theme.PeachyTheme

/**
 * The in-app browser. Page audio can drive the haptics: the capture is started on request
 * (the launching dialog or the menu switch) and always stops when the screen is left.
 */
@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    @Inject
    lateinit var hapticInputRouter: HapticInputRouter

    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var controller: BrowserController
    private var captureStarted = false
    private var captureOn by mutableStateOf(false)

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
        // The app is dark in every system theme: light status and navigation bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        controller = BrowserController(
            activity = this,
            engine = { viewModel.engine.value },
            blockerOff = { host -> BrowserStore.isBlockerOff(viewModel.allowedNow(), host) },
            onVisit = viewModel::recordVisit
        )

        val url = intent.getStringExtra(EXTRA_URL).orEmpty().trim()
        if (url.isNotEmpty()) controller.navigate(controller.active, url)

        setContent {
            PeachyTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BrowserScreen(
                        controller = controller,
                        viewModel = viewModel,
                        captureOn = captureOn,
                        onCaptureChange = { wanted -> if (wanted) beginAudioCaptureFlow() else stopCapture() },
                        onClose = ::finish
                    )
                }
            }
        }
        if (intent.getBooleanExtra(EXTRA_CAPTURE, false)) beginAudioCaptureFlow()
    }

    override fun onStop() {
        stopCapture()
        super.onStop()
    }

    override fun onDestroy() {
        stopCapture()
        controller.destroyAll()
        super.onDestroy()
    }

    private fun stopCapture() {
        if (captureStarted) {
            runCatching {
                stopService(WebViewAudioCaptureService.stopIntent(applicationContext))
            }
        }
        hapticInputRouter.release(HapticSource.WEBVIEW)
        captureStarted = false
        captureOn = false
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
        captureOn = true
        ContextCompat.startForegroundService(
            applicationContext,
            WebViewAudioCaptureService.captureIntent(
                context = applicationContext,
                resultCode = resultCode,
                data = data
            )
        )
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_CAPTURE = "extra_capture"
    }
}
