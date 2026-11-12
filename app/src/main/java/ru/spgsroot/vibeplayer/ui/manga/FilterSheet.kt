package ru.spgsroot.vibeplayer.ui.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R
import ru.spgsroot.vibeplayer.manga.CatalogQuery
import ru.spgsroot.vibeplayer.manga.PagesFilter
import ru.spgsroot.vibeplayer.manga.SortKey
import ru.spgsroot.vibeplayer.manga.TagFilter
import ru.spgsroot.vibeplayer.manga.TagMode
import ru.spgsroot.vibeplayer.manga.customUploadedRange
import ru.spgsroot.vibeplayer.manga.nextTagMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val datePresets = listOf(
    "uploaded:>2h" to R.string.manga_date_2h,
    "uploaded:>24h" to R.string.manga_date_24h,
    "uploaded:>3d" to R.string.manga_date_3d,
    "uploaded:>7d" to R.string.manga_date_7d,
    "uploaded:>30d" to R.string.manga_date_30d,
    "uploaded:>90d" to R.string.manga_date_90d,
    "uploaded:>180d" to R.string.manga_date_180d,
    "uploaded:>1y" to R.string.manga_date_1y
)
private val tagTypes = listOf(
    "tag" to R.string.manga_tag,
    "artist" to R.string.manga_artist,
    "character" to R.string.manga_character,
    "parody" to R.string.manga_parody,
    "group" to R.string.manga_group,
    "category" to R.string.manga_category,
    "language" to R.string.manga_language
)
private val languages = listOf(
    "english" to R.string.manga_language_english,
    "japanese" to R.string.manga_language_japanese,
    "chinese" to R.string.manga_language_chinese
)
private val sorts = listOf(
    SortKey.Date to R.string.manga_sort_date,
    SortKey.Popular to R.string.manga_sort_popular,
    SortKey.PopularToday to R.string.manga_sort_today,
    SortKey.PopularWeek to R.string.manga_sort_week,
    SortKey.PopularMonth to R.string.manga_sort_month
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    query: CatalogQuery,
    blockedIds: Set<Int>,
    blockedNames: Set<String>,
    onBlockId: (Int) -> Unit,
    onUnblockId: (Int) -> Unit,
    onBlockName: (String) -> Unit,
    onUnblockName: (String) -> Unit,
    onDismiss: () -> Unit,
    onApply: (CatalogQuery) -> Unit
) {
    var tags by remember { mutableStateOf(query.tags) }
    var tagType by remember { mutableStateOf("tag") }
    var tagName by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(query.sort) }
    var preset by remember { mutableStateOf(query.uploaded.takeIf { value -> datePresets.any { it.first == value } }.orEmpty()) }
    var start by remember {
        mutableStateOf(if (preset.isEmpty()) query.uploaded.split(' ').find { it.startsWith("uploaded:>") }?.removePrefix("uploaded:>").orEmpty() else "")
    }
    var end by remember {
        mutableStateOf(query.uploaded.split(' ').find { it.startsWith("uploaded:<") }?.removePrefix("uploaded:<").orEmpty())
    }
    var minPages by remember { mutableStateOf(query.pages.min?.toString().orEmpty()) }
    var maxPages by remember { mutableStateOf(query.pages.max?.toString().orEmpty()) }
    var exactPages by remember { mutableStateOf(query.pages.exact?.toString().orEmpty()) }
    var blockedName by remember { mutableStateOf("") }
    var blockedId by remember { mutableStateOf("") }
    val datesValid = validDate(start) && validDate(end)
    val reversed = datesValid && start.isNotBlank() && end.isNotBlank() && end < start

    fun cycleTag(type: String, name: String) {
        val old = tags.find { it.type == type && it.name == name }
        val mode = nextTagMode(old?.mode)
        tags = tags.filterNot { it.type == type && it.name == name } +
            if (mode == null) emptyList() else listOf(TagFilter(type, name, mode))
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) {
            Text(stringResource(R.string.manga_filters), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(stringResource(R.string.manga_tags), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.manga_tag_cycle), style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tagTypes.forEach { (type, label) ->
                            FilterChip(selected = tagType == type, onClick = { tagType = type }, label = { Text(stringResource(label)) })
                        }
                    }
                    OutlinedTextField(value = tagName, onValueChange = { tagName = it }, singleLine = true,
                        label = { Text(stringResource(R.string.manga_tag_name)) }, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = tagName.isNotBlank(), onClick = {
                        val name = tagName.trim()
                        if (tags.none { it.type == tagType && it.name == name }) tags = tags + TagFilter(tagType, name, TagMode.Include)
                        tagName = ""
                    }) { Text(stringResource(R.string.manga_add_tag)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            FilterChip(selected = tag.mode == TagMode.Include, onClick = { cycleTag(tag.type, tag.name) }, label = {
                                Text(stringResource(if (tag.mode == TagMode.Include) R.string.manga_tag_included else R.string.manga_tag_excluded, "${tag.type}:${tag.name}"))
                            })
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.manga_language), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        languages.forEach { (name, label) ->
                            val mode = tags.find { it.type == "language" && it.name == name }?.mode
                            FilterChip(selected = mode != null, onClick = { cycleTag("language", name) }, label = {
                                val language = stringResource(label)
                                Text(when (mode) {
                                    TagMode.Include -> stringResource(R.string.manga_tag_included, language)
                                    TagMode.Exclude -> stringResource(R.string.manga_tag_excluded, language)
                                    null -> language
                                })
                            })
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.manga_sort), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sorts.forEach { (key, label) ->
                            FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.manga_uploaded), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        datePresets.forEach { (value, label) ->
                            FilterChip(selected = preset == value, onClick = {
                                preset = if (preset == value) "" else value
                                start = ""
                                end = ""
                            }, label = { Text(stringResource(label)) })
                        }
                    }
                    OutlinedTextField(value = start, onValueChange = { start = it; preset = "" }, singleLine = true,
                        label = { Text(stringResource(R.string.manga_date_start)) }, isError = !validDate(start), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = end, onValueChange = { end = it; preset = "" }, singleLine = true,
                        label = { Text(stringResource(R.string.manga_date_end)) }, isError = !validDate(end), modifier = Modifier.fillMaxWidth())
                    if (!datesValid) Text(stringResource(R.string.manga_date_invalid), color = MaterialTheme.colorScheme.error)
                    if (reversed) Text(stringResource(R.string.manga_date_reversed), color = MaterialTheme.colorScheme.error)
                }
                item {
                    Text(stringResource(R.string.manga_pages_filter), style = MaterialTheme.typography.titleMedium)
                    PageField(minPages, { minPages = it }, R.string.manga_pages_min)
                    PageField(maxPages, { maxPages = it }, R.string.manga_pages_max)
                    PageField(exactPages, { exactPages = it }, R.string.manga_pages_exact)
                }
                item {
                    HorizontalDivider()
                    Text(stringResource(R.string.manga_blacklist), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.manga_blacklist_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = blockedName, onValueChange = { blockedName = it }, singleLine = true,
                        label = { Text(stringResource(R.string.manga_tag_name)) }, modifier = Modifier.fillMaxWidth())
                    TextButton(enabled = blockedName.isNotBlank(), onClick = { onBlockName(blockedName.trim()); blockedName = "" }) {
                        Text(stringResource(R.string.manga_block_name))
                    }
                    PageField(blockedId, { blockedId = it }, R.string.manga_tag_id)
                    TextButton(enabled = (blockedId.toIntOrNull() ?: 0) > 0, onClick = {
                        blockedId.toIntOrNull()?.let(onBlockId)
                        blockedId = ""
                    }) { Text(stringResource(R.string.manga_block_id)) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        blockedNames.sorted().forEach { name ->
                            FilterChip(selected = true, onClick = { onUnblockName(name) }, label = { Text(stringResource(R.string.manga_unblock_name, name)) })
                        }
                        blockedIds.sorted().forEach { id ->
                            FilterChip(selected = true, onClick = { onUnblockId(id) }, label = { Text(stringResource(R.string.manga_unblock_id, id)) })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    tags = emptyList()
                    tagType = "tag"
                    tagName = ""
                    sort = SortKey.Date
                    preset = ""
                    start = ""
                    end = ""
                    minPages = ""
                    maxPages = ""
                    exactPages = ""
                }) { Text(stringResource(R.string.manga_reset_filters)) }
                Button(enabled = datesValid, onClick = {
                    onApply(CatalogQuery(
                        text = query.text,
                        tags = tags,
                        sort = sort,
                        uploaded = preset.ifEmpty { customUploadedRange(start, end) },
                        pages = PagesFilter(minPages.toIntOrNull(), maxPages.toIntOrNull(), exactPages.toIntOrNull())
                    ))
                }) { Text(stringResource(R.string.manga_apply_filters)) }
            }
        }
    }
}

@Composable
private fun PageField(value: String, onChange: (String) -> Unit, label: Int) {
    OutlinedTextField(value = value, onValueChange = { next -> if (next.all { it in '0'..'9' }) onChange(next) },
        label = { Text(stringResource(label)) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
}

private fun validDate(value: String): Boolean {
    if (value.isEmpty()) return true
    return try {
        LocalDate.parse(value)
        true
    } catch (e: DateTimeParseException) {
        false
    }
}
