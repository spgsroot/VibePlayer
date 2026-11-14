package ru.spgsroot.peachy.ui.share

import android.os.Bundle
import androidx.activity.ComponentActivity

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShareIntents.forward(this, intent)?.let(::startActivity)
        finish()
    }
}
