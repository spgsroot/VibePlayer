package ru.spgsroot.vibeplayer.ui.dialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.spgsroot.vibeplayer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVideoDialog(
    onDismiss: () -> Unit,
    onGalleryClick: () -> Unit,
    onUrlClick: () -> Unit,
    onBatchClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                stringResource(R.string.add_video),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.add_from_gallery)) },
                leadingContent = { Icon(Icons.Default.Image, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onGalleryClick()
                        onDismiss()
                    }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.add_from_url)) },
                leadingContent = { Icon(Icons.Default.Link, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onUrlClick()
                        onDismiss()
                    }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.add_batch)) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.List, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onBatchClick()
                        onDismiss()
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
