package ru.spgsroot.peachy.ui.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BadParcelableException
import android.os.Parcelable
import ru.spgsroot.peachy.MainActivity

/** Both entry activities use the same extraction policy; ClipData is also the URI-grant carrier. */
object ShareIntents {
    /** The URIs this intent carries, importable or not; empty when its extras are unreadable. */
    @Suppress("DEPRECATION")
    private fun candidates(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        if (intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE, Intent.ACTION_VIEW)) {
            return emptyList()
        }
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(uris::add)
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                ?.filterIsInstance<Uri>()?.let(uris::addAll)
            Intent.ACTION_VIEW -> intent.data?.let(uris::add)
        }
        intent.clipData?.let { clip ->
            for (index in 0 until clip.itemCount) clip.getItemAt(index).uri?.let(uris::add)
        }
        return uris.distinct()
    }

    fun streams(intent: Intent?): List<Uri> = try {
        candidates(intent).filter { ShareUriPolicy.isImportable(it.scheme) }
    } catch (_: BadParcelableException) {
        emptyList()
    } catch (_: ClassCastException) {
        emptyList()
    }

    /** How many shared items the app refuses (not `content://`, or unreadable extras) so the user is told. */
    fun rejected(intent: Intent?): Int = try {
        candidates(intent).count { !ShareUriPolicy.isImportable(it.scheme) }
    } catch (_: BadParcelableException) {
        1
    } catch (_: ClassCastException) {
        1
    }

    fun forward(context: Context, source: Intent?): Intent? {
        val uris = streams(source)
        if (uris.isEmpty()) return null
        return Intent(context, MainActivity::class.java).apply {
            action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = source?.type
            if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris.single())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            // Put every forwarded stream in ClipData, not just EXTRA_STREAM: Android derives URI
            // grants from this carrier when the receiver activity finishes or MainActivity exists.
            clipData = ClipData.newRawUri("Shared media", uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (source != null && source.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0) {
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
        }
    }
}
