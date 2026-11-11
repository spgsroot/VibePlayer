package ru.spgsroot.vibeplayer.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import ru.spgsroot.vibeplayer.MainActivity

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = sharedUri(intent)
        if (uri == null || !ShareUriPolicy.isImportable(uri.scheme)) {
            finish()
            return
        }
        PendingImport.uri = uri
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        )
        finish()
    }

    private fun sharedUri(intent: Intent?): Uri? {
        return when (intent?.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}
