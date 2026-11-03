package io.github.mello13256.fotos.ui.albums

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.grid.NameDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onOpenCollection: (Long) -> Unit,
    onOpenFolder: (relativePath: String, name: String) -> Unit,
    onOpenTrash: () -> Unit,
) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val collections by container.collections.summaries.collectAsStateWithLifecycle(emptyList())
    val folders by remember(container) { container.media.folders() }.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_albums)) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            section(R.string.section_collections)
            item(key = "new") {
                AlbumTile(
                    title = stringResource(R.string.collection_new),
                    subtitle = null,
                    coverUri = null,
                    icon = Icons.Filled.Add,
                    onClick = { creating = true },
                )
            }
            items(collections, key = { "c${it.id}" }) { collection ->
                AlbumTile(
                    title = if (collection.isFavorites) stringResource(R.string.favorites) else collection.name,
                    subtitle = pluralStringResource(R.plurals.items_count, collection.count, collection.count),
                    coverUri = collection.coverUri,
                    icon = if (collection.isFavorites) Icons.Filled.Favorite else Icons.Outlined.Collections,
                    onClick = { onOpenCollection(collection.id) },
                )
            }

            section(R.string.section_folders)
            items(folders, key = { "f${it.relativePath}" }) { folder ->
                AlbumTile(
                    title = folder.bucketName.ifBlank { folder.relativePath },
                    subtitle = pluralStringResource(R.plurals.items_count, folder.count, folder.count),
                    coverUri = folder.coverUri,
                    icon = Icons.Outlined.Folder,
                    onClick = { onOpenFolder(folder.relativePath, folder.bucketName) },
                )
            }

            section(R.string.section_utilities)
            item(key = "trash") {
                AlbumTile(
                    title = stringResource(R.string.trash),
                    subtitle = null,
                    coverUri = null,
                    icon = Icons.Outlined.Delete,
                    onClick = onOpenTrash,
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = stringResource(R.string.collection_new),
            initial = "",
            confirm = stringResource(R.string.create),
            onConfirm = { name ->
                creating = false
                scope.launch { onOpenCollection(container.collections.create(name)) }
            },
            onDismiss = { creating = false },
        )
    }
}

private fun LazyGridScope.section(@StringRes title: Int) {
    item(key = "section-$title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
        )
    }
}

@Composable
private fun AlbumTile(
    title: String,
    subtitle: String?,
    coverUri: String?,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (coverUri != null) {
                AsyncImage(
                    model = remember(coverUri) { MediaThumbnail(coverUri.toUri(), 384) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
