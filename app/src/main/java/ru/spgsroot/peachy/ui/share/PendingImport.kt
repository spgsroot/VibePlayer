package ru.spgsroot.peachy.ui.share

import android.net.Uri

object PendingImport {
    @Volatile
    var uri: Uri? = null

    fun consume(): Uri? = uri.also { uri = null }
}
