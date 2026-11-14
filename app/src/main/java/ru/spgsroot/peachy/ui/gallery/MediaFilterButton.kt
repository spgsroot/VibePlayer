package ru.spgsroot.peachy.ui.gallery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.spgsroot.peachy.R

@Composable
fun MediaFilterButton(current: MediaFilter, onApply: (MediaFilter) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.FilterList, stringResource(R.string.media_filters),
            tint = if (current == MediaFilter()) LocalContentColor.current else MaterialTheme.colorScheme.primary)
    }
    if (open) MediaFilterDialog(current, { open = false }) { onApply(it); open = false }
}

@Composable
private fun MediaFilterDialog(current: MediaFilter, onDismiss: () -> Unit, onApply: (MediaFilter) -> Unit) {
    var sort by remember { mutableStateOf(current.sort) }
    var min by remember { mutableStateOf(current.minBytes?.let { (it.toDouble() / MIB).toString() }.orEmpty()) }
    var max by remember { mutableStateOf(current.maxBytes?.let { (it.toDouble() / MIB).toString() }.orEmpty()) }
    fun bytes(value: String): Long? = value.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 && it < Long.MAX_VALUE.toDouble() / MIB }?.let { (it * MIB).toLong() }
    val minBytes = bytes(min)
    val maxBytes = bytes(max)
    val valid = (min.isBlank() || minBytes != null) && (max.isBlank() || maxBytes != null) &&
        (minBytes == null || maxBytes == null || maxBytes >= minBytes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.media_filters)) },
        text = {
            Column(Modifier.heightIn(max = 390.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in MediaSort.entries) {
                    TextButton(onClick = { sort = option }, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = sort == option, onClick = null)
                        Text(stringResource(option.label()), Modifier.weight(1f))
                    }
                }
                OutlinedTextField(min, { min = it }, label = { Text(stringResource(R.string.media_min_size)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(max, { max = it }, label = { Text(stringResource(R.string.media_max_size)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                if (!valid) Text(stringResource(R.string.media_size_range_error), color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onApply(current.copy(sort = sort, minBytes = minBytes, maxBytes = maxBytes)) }) { Text(stringResource(R.string.manga_apply_filters)) } },
        dismissButton = {
            TextButton(onClick = { onApply(current.copy(sort = MediaSort.Newest, minBytes = null, maxBytes = null)) }) { Text(stringResource(R.string.media_all)) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

private const val MIB = 1_048_576.0
private fun MediaSort.label(): Int = when (this) {
    MediaSort.Newest -> R.string.media_sort_newest
    MediaSort.NameAscending -> R.string.media_sort_name_asc
    MediaSort.NameDescending -> R.string.media_sort_name_desc
    MediaSort.SizeAscending -> R.string.media_sort_size_asc
    MediaSort.SizeDescending -> R.string.media_sort_size_desc
}
