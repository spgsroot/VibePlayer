package ru.spgsroot.peachy.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import ru.spgsroot.peachy.R

/** One search row inside the existing app bar, not another row below it. */
@Composable
fun HeaderSearch(value: String, onChange: (String) -> Unit, hint: String, onSearch: (() -> Unit)? = null) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(hint, maxLines = 1) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        trailingIcon = {
            if (onSearch != null) IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, stringResource(R.string.manga_search))
            } else if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, stringResource(R.string.media_clear_search))
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
        )
    )
}
