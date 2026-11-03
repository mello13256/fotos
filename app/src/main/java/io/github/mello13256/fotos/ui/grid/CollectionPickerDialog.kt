package io.github.mello13256.fotos.ui.grid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.db.CollectionSummary

/** Escolher uma coleção existente ou criar uma nova. */
@Composable
fun CollectionPickerDialog(
    collections: List<CollectionSummary>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(collections.none { !it.isFavorites }) }
    if (creating) {
        NameDialog(
            title = stringResource(R.string.collection_new),
            initial = "",
            confirm = stringResource(R.string.create),
            onConfirm = onCreate,
            onDismiss = onDismiss,
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_to_collection)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.collection_new)) },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable { creating = true },
                    )
                }
                items(collections, key = { it.id }) { collection ->
                    ListItem(
                        headlineContent = {
                            Text(if (collection.isFavorites) stringResource(R.string.favorites) else collection.name)
                        },
                        supportingContent = {
                            Text(pluralStringResource(R.plurals.items_count, collection.count, collection.count))
                        },
                        leadingContent = {
                            Icon(
                                if (collection.isFavorites) Icons.Filled.Favorite else Icons.Outlined.Collections,
                                contentDescription = null,
                            )
                        },
                        modifier = Modifier.clickable { onPick(collection.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.collection_name)) },
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
