package ru.spgsroot.peachy.ui.share

object ShareUriPolicy {
    fun isImportable(scheme: String?): Boolean = scheme == "content"
}
