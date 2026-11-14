package ru.spgsroot.peachy.ui.webview

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.spgsroot.peachy.R
import ru.spgsroot.peachy.browser.BrowserUrls
import ru.spgsroot.peachy.browser.FoundVideo
import ru.spgsroot.peachy.browser.MediaKind
import ru.spgsroot.peachy.data.browser.BrowserStore
import ru.spgsroot.peachy.ui.dialog.NoteDialog

private enum class Sheet { TABS, BOOKMARKS, HISTORY, VIDEOS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    controller: BrowserController,
    viewModel: BrowserViewModel,
    captureOn: Boolean,
    onCaptureChange: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val activity = LocalContext.current as android.app.Activity
    val tab = controller.active
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val allowed by viewModel.allowedHosts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val pageKey = BrowserStore.pageKey(tab.url)
    val bookmarked = pageKey != null && bookmarks.any { it.url == pageKey }
    val blockerOff = BrowserStore.isBlockerOff(allowed, tab.host)
    val pageNotes by viewModel.pageNotes.collectAsStateWithLifecycle()
    val hasNote = pageKey != null && pageNotes.containsKey(pageKey)
    var editingNote by remember { mutableStateOf(false) }
    if (editingNote && pageKey != null) {
        NoteDialog(
            title = stringResource(R.string.note_title_page),
            initial = pageNotes[pageKey].orEmpty(),
            onSave = { text ->
                viewModel.savePageNote(pageKey, text)
                editingNote = false
            },
            onDismiss = { editingNote = false }
        )
    }

    val msgSaved = stringResource(R.string.browser_video_saved)
    val msgFailed = stringResource(R.string.browser_video_failed)
    val msgStream = stringResource(R.string.browser_video_stream)
    val msgUpdating = stringResource(R.string.browser_filters_updating)
    val msgUpdateFailed = stringResource(R.string.browser_filters_failed)
    val msgUpdated = stringResource(R.string.browser_filters_updated)

    fun say(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    val fullscreen = controller.fullscreenView
    LaunchedEffect(fullscreen != null) {
        val bars = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen != null) bars.hide(WindowInsetsCompat.Type.systemBars()) else bars.show(WindowInsetsCompat.Type.systemBars())
    }

    BackHandler {
        when {
            fullscreen != null -> controller.hideFullscreen()
            sheet != null -> sheet = null
            tab.canGoBack -> controller.goBack(tab)
            tab.webView != null -> controller.showStart(tab)
            controller.tabs.size > 1 -> controller.close(tab)
            else -> onClose()
        }
    }

    if (fullscreen != null) {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { fullscreen })
        }
        return
    }

    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            AddressBar(tab, controller)
            if (tab.loading) {
                LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val view = tab.webView
                if (view == null) {
                    StartPage(
                        bookmarks = bookmarks.map { it.title to it.url },
                        recent = history.take(8).map { it.title to it.url },
                        onOpen = { controller.navigate(tab, it) }
                    )
                } else {
                    key(tab.id) { WebHost(view) }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.goBack(tab) }, enabled = tab.canGoBack) {
                    Icon(Icons.Filled.ArrowBack, stringResource(R.string.browser_back))
                }
                IconButton(onClick = { controller.goForward(tab) }, enabled = tab.canGoForward) {
                    Icon(Icons.Filled.ArrowForward, stringResource(R.string.browser_forward))
                }
                IconButton(onClick = {
                    controller.refreshVideos(tab)
                    sheet = Sheet.VIDEOS
                }) {
                    BadgedBox(badge = { if (tab.videos.isNotEmpty()) Badge { Text(tab.videos.size.toString()) } }) {
                        Icon(Icons.Filled.Movie, stringResource(R.string.browser_videos))
                    }
                }
                IconButton(onClick = { sheet = Sheet.TABS }) {
                    Box(
                        Modifier.size(24.dp).border(BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), RoundedCornerShape(5.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(controller.tabs.size.toString(), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.browser_menu))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_new_tab)) },
                            onClick = { menuOpen = false; controller.newTab() }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_home)) },
                            enabled = tab.webView != null,
                            onClick = { menuOpen = false; controller.showStart(tab) }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, null)
                            },
                            text = {
                                Text(stringResource(if (bookmarked) R.string.browser_remove_bookmark else R.string.browser_add_bookmark))
                            },
                            enabled = pageKey != null,
                            onClick = {
                                menuOpen = false
                                viewModel.toggleBookmark(tab.url, tab.title, bookmarked)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_bookmarks)) },
                            onClick = { menuOpen = false; sheet = Sheet.BOOKMARKS }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.History, null) },
                            text = { Text(stringResource(R.string.browser_history)) },
                            onClick = { menuOpen = false; sheet = Sheet.HISTORY }
                        )
                        DropdownMenuItem(
                            leadingIcon = { Icon(if (hasNote) Icons.Filled.StickyNote2 else Icons.Filled.EditNote, null) },
                            text = { Text(stringResource(R.string.browser_note_page)) },
                            enabled = pageKey != null,
                            onClick = { menuOpen = false; editingNote = true }
                        )
                        HorizontalDivider()
                        val host = tab.host
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(stringResource(R.string.browser_block_ads))
                                    if (tab.webView != null && !blockerOff) {
                                        Text(
                                            stringResource(R.string.browser_blocked_count, tab.blocked),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            trailingIcon = { Switch(checked = !blockerOff, onCheckedChange = null, enabled = host != null) },
                            enabled = host != null,
                            onClick = {
                                if (host != null) viewModel.setBlockerEnabled(host, enabled = blockerOff) { controller.reload(tab) }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_update_filters)) },
                            onClick = {
                                menuOpen = false
                                say(msgUpdating)
                                viewModel.updateFilters { result ->
                                    result.fold(
                                        onSuccess = { say(msgUpdated.format(it)) },
                                        onFailure = { say(msgUpdateFailed) }
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_capture)) },
                            trailingIcon = { Switch(checked = captureOn, onCheckedChange = null) },
                            onClick = { onCaptureChange(!captureOn) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.browser_close_browser)) },
                            onClick = { menuOpen = false; onClose() }
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
    }

    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            when (sheet) {
                Sheet.TABS -> TabsSheet(
                    controller = controller,
                    onPick = { controller.select(it); sheet = null },
                    onNew = { controller.newTab(); sheet = null }
                )
                Sheet.BOOKMARKS -> LinkList(
                    empty = stringResource(R.string.browser_no_bookmarks),
                    rows = bookmarks.map { Triple(it.title, it.url, it.url) },
                    onOpen = { controller.navigate(tab, it); sheet = null },
                    onDelete = viewModel::removeBookmark
                )
                Sheet.HISTORY -> Column {
                    TextButton(
                        onClick = viewModel::clearHistory,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text(stringResource(R.string.browser_clear_history)) }
                    LinkList(
                        empty = stringResource(R.string.browser_no_history),
                        rows = history.map { Triple(it.title, it.url, it.url) },
                        onOpen = { controller.navigate(tab, it); sheet = null },
                        onDelete = viewModel::removeVisit
                    )
                }
                Sheet.VIDEOS -> VideosSheet(
                    videos = tab.videos,
                    onSave = { video, done ->
                        viewModel.download(video, tab.host, controller.downloadHeaders(tab, video.url), tab.title.ifBlank { null }) { result ->
                            done()
                            say(
                                result.fold(
                                    onSuccess = { msgSaved },
                                    onFailure = { if (video.kind == MediaKind.STREAM) msgStream else msgFailed }
                                )
                            )
                        }
                    }
                )
                null -> Unit
            }
        }
    }
}

@Composable
private fun AddressBar(tab: BrowserTab, controller: BrowserController) {
    val focus = LocalFocusManager.current
    var text by remember(tab.id, tab.url) { mutableStateOf(tab.url) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.browser_address_hint)) },
        leadingIcon = {
            if (tab.url.startsWith("https://")) Icon(Icons.Filled.Lock, null, Modifier.size(18.dp))
        },
        trailingIcon = {
            if (tab.webView != null) {
                if (tab.loading) {
                    IconButton(onClick = { controller.stop(tab) }) { Icon(Icons.Filled.Close, stringResource(R.string.browser_stop)) }
                } else {
                    IconButton(onClick = { controller.reload(tab) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.browser_reload)) }
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            focus.clearFocus()
            controller.navigate(tab, text)
        }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun WebHost(webView: android.webkit.WebView) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { FrameLayout(it) },
        update = { frame ->
            if (frame.getChildAt(0) !== webView) {
                frame.removeAllViews()
                (webView.parent as? ViewGroup)?.removeView(webView)
                frame.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }
    )
}

@Composable
private fun StartPage(bookmarks: List<Pair<String, String>>, recent: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle(stringResource(R.string.browser_bookmarks)) }
        if (bookmarks.isEmpty()) {
            item { Text(stringResource(R.string.browser_no_bookmarks), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(bookmarks, key = { "b" + it.second }) { (title, url) -> LinkRow(title, url) { onOpen(url) } }
        if (recent.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.browser_history)) }
            items(recent, key = { "h" + it.second }) { (title, url) -> LinkRow(title, url) { onOpen(url) } }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
}

@Composable
private fun LinkRow(title: String, url: String, trailing: @Composable (() -> Unit)? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = trailing,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun LinkList(
    empty: String,
    rows: List<Triple<String, String, String>>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (rows.isEmpty()) {
        Text(empty, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn {
        items(rows, key = { it.third }) { (title, url, key) ->
            LinkRow(
                title = title,
                url = url,
                trailing = {
                    IconButton(onClick = { onDelete(key) }) { Icon(Icons.Filled.Delete, stringResource(R.string.browser_delete)) }
                },
                onClick = { onOpen(url) }
            )
        }
    }
}

@Composable
private fun TabsSheet(controller: BrowserController, onPick: (BrowserTab) -> Unit, onNew: () -> Unit) {
    Column {
        Button(onClick = onNew, modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.browser_new_tab)) }
        Spacer(Modifier.size(8.dp))
        LazyColumn {
            items(controller.tabs.toList(), key = { it.id }) { item ->
                val label = item.title.ifBlank { BrowserUrls.displayHost(item.url).ifBlank { stringResource(R.string.browser_untitled) } }
                ListItem(
                    headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (item.url.isNotEmpty()) Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { controller.close(item) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.browser_close_tab))
                        }
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = if (item.id == controller.activeId) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.clickable { onPick(item) }
                )
            }
        }
    }
}

@Composable
private fun VideosSheet(videos: List<FoundVideo>, onSave: (FoundVideo, () -> Unit) -> Unit) {
    var saving by remember { mutableStateOf(setOf<String>()) }
    if (videos.isEmpty()) {
        Text(stringResource(R.string.browser_no_videos), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    LazyColumn {
        items(videos, key = { it.url }) { video ->
            val name = video.url.substringBefore('#').substringBefore('?').substringAfterLast('/').ifBlank { video.url }
            ListItem(
                headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(
                        if (video.kind == MediaKind.STREAM) stringResource(R.string.browser_video_stream)
                        else video.url,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                trailingContent = {
                    if (video.kind == MediaKind.FILE) {
                        val busy = video.url in saving
                        IconButton(
                            enabled = !busy,
                            onClick = {
                                saving = saving + video.url
                                onSave(video) { saving = saving - video.url }
                            }
                        ) { Icon(Icons.Filled.SaveAlt, stringResource(if (busy) R.string.browser_video_saving else R.string.browser_video_save)) }
                    }
                }
            )
        }
    }
}
