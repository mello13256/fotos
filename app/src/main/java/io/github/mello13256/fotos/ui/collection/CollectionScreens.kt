package io.github.mello13256.fotos.ui.collection

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.common.gridViewModel
import io.github.mello13256.fotos.ui.grid.GridScreen
import io.github.mello13256.fotos.ui.grid.NameDialog
import kotlinx.coroutines.launch

@Composable
fun CollectionScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onOpen: (MediaSource, Long) -> Unit,
    focusId: Long? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val collection by remember(collectionId) { container.collections.observe(collectionId) }
        .collectAsStateWithLifecycle(null)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val current = collection
    val title = when {
        current == null -> ""
        current.isFavorites -> stringResource(R.string.favorites)
        else -> current.name
    }

    GridScreen(
        viewModel = gridViewModel(MediaSource.Collection(collectionId)),
        title = title,
        onOpen = onOpen,
        onBack = onBack,
        focusId = focusId,
        onFocusConsumed = onFocusConsumed,
        emptyText = stringResource(R.string.empty_collection),
        actions = {
            if (current != null && !current.isFavorites) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            onClick = {
                                menu = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.collection_delete)) },
                            onClick = {
                                menu = false
                                deleting = true
                            },
                        )
                    }
                }
            }
        },
    )

    if (renaming && current != null) {
        NameDialog(
            title = stringResource(R.string.rename),
            initial = current.name,
            confirm = stringResource(R.string.save),
            onConfirm = {
                renaming = false
                scope.launch { container.collections.rename(collectionId, it) }
            },
            onDismiss = { renaming = false },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.collection_delete)) },
            text = { Text(stringResource(R.string.collection_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        container.collections.delete(collectionId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun FolderScreen(
    relativePath: String,
    name: String,
    onBack: () -> Unit,
    onOpen: (MediaSource, Long) -> Unit,
    focusId: Long? = null,
    onFocusConsumed: () -> Unit = {},
) {
    GridScreen(
        viewModel = gridViewModel(MediaSource.Folder(relativePath)),
        title = name.ifBlank { relativePath },
        onOpen = onOpen,
        onBack = onBack,
        focusId = focusId,
        onFocusConsumed = onFocusConsumed,
    )
}
