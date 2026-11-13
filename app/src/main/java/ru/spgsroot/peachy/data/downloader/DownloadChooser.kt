package ru.spgsroot.peachy.data.downloader

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Separate VIEW and labelled SEND choosers keep all compatible targets available. */
@Suppress("DEPRECATION")
fun externalDownloadChooser(context: Context, url: String, title: String, sendLink: Boolean = false): Intent {
    val requests = externalDownloadRequests(url)
    require(requests.isNotEmpty()) { "Only HTTP and HTTPS links can be downloaded" }
    val request = requests[if (sendLink) 1 else 0]
    val target = Intent(request.action).also { intent ->
        intent.setDataAndType(request.data?.let(Uri::parse), request.mimeType)
        request.extras.forEach { (key, value) -> intent.putExtra(key, value) }
    }
    val targets = context.packageManager.queryIntentActivities(target, PackageManager.MATCH_DEFAULT_ONLY)
    if (targets.none { it.activityInfo.packageName != context.packageName }) {
        throw ActivityNotFoundException()
    }
    val excluded = targets.filter { it.activityInfo.packageName == context.packageName }
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }.distinct()
    return Intent.createChooser(target, title).apply {
        putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toTypedArray())
    }
}
