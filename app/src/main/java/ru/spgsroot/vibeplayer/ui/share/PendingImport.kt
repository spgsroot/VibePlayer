package ru.spgsroot.vibeplayer.ui.share

import android.net.Uri

object PendingImport {
    @Volatile
    var uri: Uri? = null

    fun consume(): Uri? = uri.also { uri = null }
}
