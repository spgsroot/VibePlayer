package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.TagCollection
import ru.spgsroot.vibeplayer.manga.TagFilter
import ru.spgsroot.vibeplayer.manga.TagLibrary
import ru.spgsroot.vibeplayer.manga.TagMode
import ru.spgsroot.vibeplayer.manga.TagRef

internal val tagTypes = listOf(
    "tag" to R.string.manga_tag,
    "artist" to R.string.manga_artist,
    "character" to R.string.manga_character,
    "parody" to R.string.manga_parody,
    "group" to R.string.manga_group,
    "category" to R.string.manga_category,
    "language" to R.string.manga_language
)

private val tabTitles = listOf(
    R.string.manga_tag_tab_all,
    R.string.manga_tag_tab_favorites,
    R.string.manga_tag_tab_collections
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagBrowserScreen(
    onSearch: (List<TagFilter>) -> Unit,
    onBack: () -> Unit,
    initialType: String = "tag",
    viewModel: TagBrowserViewModel = hiltViewModel()
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(initialType) { viewModel.start(initialType) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manga_tags)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.content_description_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (selection.isNotEmpty()) {
                SelectionBar(
                    selection = selection,
                    onRemove = viewModel::remove,
                    onClear = viewModel::clearSelection,
                    onSearch = { onSearch(selection) }
                )
            }
            TabRow(selectedTabIndex = tab) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(title)) })
                }
            }
            when (tab) {
                0 -> AllTab(list, selection, library, viewModel)
                1 -> FavoritesTab(library, selection, viewModel)
                else -> CollectionsTab(library, selection, viewModel)
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selection: List<TagFilter>,
    onRemove: (TagFilter) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(selection, key = { it.type + ":" + it.name }) { filter ->
                val prefix = if (filter.mode == TagMode.Exclude) "\u2212 " else "+ "
                InputChip(
                    selected = true,
                    onClick = { onRemove(filter) },
                    label = { Text(prefix + filter.name, maxLines = 1) },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, stringResource(R.string.manga_tag_remove), Modifier.size(18.dp))
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSearch) { Text(stringResource(R.string.manga_tag_search_selected, selection.size)) }
            TextButton(onClick = onClear) { Text(stringResource(R.string.manga_tag_clear)) }
        }
    }
}

@Composable
private fun AllTab(
    list: TagListState,
    selection: List<TagFilter>,
    library: TagLibrary,
    viewModel: TagBrowserViewModel
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = list.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(stringResource(R.string.manga_tag_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(tagTypes, key = { it.first }) { (type, label) ->
                FilterChip(
                    selected = list.type == type,
                    onClick = { viewModel.selectType(type) },
                    label = { Text(stringResource(label), maxLines = 1) }
                )
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            items(list.tags, key = { it.id }) { tag ->
                val ref = TagRef(tag.type, tag.name)
                TagRow(
                    label = tag.name,
                    count = tag.count,
                    mode = selection.modeOf(ref),
                    onClick = { viewModel.cycle(ref) }
                ) {
                    FavoriteButton(library.isFavorite(ref)) { viewModel.toggleFavorite(ref) }
                    AddToCollectionButton(ref, library.collections, viewModel::toggleInCollection)
                }
            }
            item {
                when {
                    list.loading -> Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    list.failed -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.manga_tag_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.manga_retry)) }
                    }
                    list.tags.isEmpty() -> Text(
                        stringResource(R.string.manga_tag_empty),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                    list.hasNext -> {
                        LaunchedEffect(list.tags.size) { viewModel.loadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(library: TagLibrary, selection: List<TagFilter>, viewModel: TagBrowserViewModel) {
    if (library.favorites.isEmpty()) {
        Text(stringResource(R.string.manga_tag_favorites_empty), modifier = Modifier.fillMaxWidth().padding(16.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(library.favorites, key = { it.type + ":" + it.name }) { ref ->
            TagRow(
                label = ref.name,
                count = null,
                mode = selection.modeOf(ref),
                onClick = { viewModel.cycle(ref) }
            ) {
                FavoriteButton(true) { viewModel.toggleFavorite(ref) }
                AddToCollectionButton(ref, library.collections, viewModel::toggleInCollection)
            }
        }
    }
}

@Composable
private fun CollectionsTab(library: TagLibrary, selection: List<TagFilter>, viewModel: TagBrowserViewModel) {
    var newName by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(stringResource(R.string.manga_tag_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.createCollection(newName)
                        newName = ""
                    }
                ) { Text(stringResource(R.string.manga_tag_collection_create)) }
            }
        }
        if (library.collections.isEmpty()) {
            item { Text(stringResource(R.string.manga_tag_collections_empty), modifier = Modifier.padding(16.dp)) }
        }
        items(library.collections, key = { it.name }) { collection ->
            val open = expanded == collection.name
            Row(
                Modifier.fillMaxWidth().clickable { expanded = if (open) null else collection.name }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${collection.name} (${collection.tags.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { renaming = collection.name }) {
                    Icon(Icons.Filled.Edit, stringResource(R.string.manga_tag_collection_rename))
                }
                IconButton(onClick = {
                    if (expanded == collection.name) expanded = null
                    viewModel.deleteCollection(collection.name)
                }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.manga_tag_collection_delete))
                }
            }
            if (open) {
                if (collection.tags.isEmpty()) {
                    Text(
                        stringResource(R.string.manga_tag_collection_empty),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                } else {
                    TextButton(
                        onClick = { viewModel.include(collection.tags) },
                        modifier = Modifier.padding(start = 12.dp)
                    ) { Text(stringResource(R.string.manga_tag_collection_select)) }
                    collection.tags.forEach { ref ->
                        TagRow(
                            label = ref.name,
                            count = null,
                            mode = selection.modeOf(ref),
                            onClick = { viewModel.cycle(ref) }
                        ) {
                            IconButton(onClick = { viewModel.toggleInCollection(collection.name, ref) }) {
                                Icon(Icons.Filled.Close, stringResource(R.string.manga_tag_remove))
                            }
                        }
                    }
                }
            }
        }
    }

    renaming?.let { current ->
        RenameDialog(
            current = current,
            onConfirm = { name ->
                viewModel.renameCollection(current, name)
                if (expanded == current) expanded = name.trim()
                renaming = null
            },
            onDismiss = { renaming = null }
        )
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manga_tag_collection_rename)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun TagRow(
    label: String,
    count: Int?,
    mode: TagMode?,
    onClick: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    val color = when (mode) {
        TagMode.Include -> MaterialTheme.colorScheme.primary
        TagMode.Exclude -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (mode) {
        TagMode.Include -> "+ "
        TagMode.Exclude -> "\u2212 "
        null -> ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prefix + label, color = color, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        actions()
    }
}

@Composable
private fun FavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            stringResource(R.string.manga_tag_favorite),
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddToCollectionButton(
    tag: TagRef,
    collections: List<TagCollection>,
    onToggle: (String, TagRef) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, stringResource(R.string.manga_tag_add_to_collection))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (collections.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.manga_tag_no_collections)) },
                    enabled = false,
                    onClick = {}
                )
            }
            collections.forEach { collection ->
                DropdownMenuItem(
                    text = { Text(collection.name) },
                    trailingIcon = {
                        if (tag in collection.tags) Icon(Icons.Filled.Check, null)
                    },
                    onClick = {
                        onToggle(collection.name, tag)
                        open = false
                    }
                )
            }
        }
    }
}

private fun List<TagFilter>.modeOf(tag: TagRef): TagMode? =
    find { it.type == tag.type && it.name == tag.name }?.mode
