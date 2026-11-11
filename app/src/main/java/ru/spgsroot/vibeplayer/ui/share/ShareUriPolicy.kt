package ru.spgsroot.vibeplayer.ui.share

object ShareUriPolicy {
    fun isImportable(scheme: String?): Boolean = scheme == "content"
}
