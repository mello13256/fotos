package io.github.mello13256.fotos.ui.albums

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.ui.common.LocalBottomBarSpace
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
    val collections by remember(container) { container.media.collectionTiles(container.collections.summaries) }
        .collectAsStateWithLifecycle(emptyList())
    val folders by remember(container) { container.media.folderTiles() }.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    val bottomBarSpace = LocalBottomBarSpace.current

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tab_albums)) }) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + bottomBarSpace + 16.dp,
            ),
        ) {
            section(R.string.section_collections)
            items(collections, key = { "c${it.collection.id}" }) { tile ->
                val collection = tile.collection
                AlbumTile(
                    title = if (collection.isFavorites) stringResource(R.string.favorites) else collection.name,
                    subtitle = pluralStringResource(R.plurals.items_count, collection.count, collection.count),
                    covers = tile.covers,
                    icon = if (collection.isFavorites) Icons.Filled.Favorite else Icons.Outlined.Collections,
                    onClick = { onOpenCollection(collection.id) },
                )
            }
            item(key = "new") {
                AlbumTile(
                    title = stringResource(R.string.collection_new),
                    subtitle = null,
                    covers = emptyList(),
                    icon = Icons.Filled.Add,
                    onClick = { creating = true },
                )
            }

            section(R.string.section_folders)
            items(folders, key = { "f${it.folder.relativePath}" }) { tile ->
                val folder = tile.folder
                AlbumTile(
                    title = folder.bucketName.ifBlank { folder.relativePath },
                    subtitle = pluralStringResource(R.plurals.items_count, folder.count, folder.count),
                    covers = tile.covers,
                    icon = Icons.Outlined.Folder,
                    onClick = { onOpenFolder(folder.relativePath, folder.bucketName) },
                )
            }

            section(R.string.section_utilities)
            item(key = "trash") {
                AlbumTile(
                    title = stringResource(R.string.trash),
                    subtitle = null,
                    covers = emptyList(),
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
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
        )
    }
}

/**
 * Cartão de álbum: mosaico com as 4 fotografias mais recentes (ou uma só, se houver menos) e o
 * título por cima, sobre um gradiente. Encolhe ligeiramente quando é premido.
 */
@Composable
private fun AlbumTile(
    title: String,
    subtitle: String?,
    covers: List<String>,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "press")
    val shape = RoundedCornerShape(20.dp)

    Box(
        Modifier
            .padding(5.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        when {
            covers.size >= 4 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until 2) {
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (column in 0 until 2) {
                            Cover(covers[row * 2 + column], Modifier.weight(1f))
                        }
                    }
                }
            }
            covers.isNotEmpty() -> Cover(covers.first(), Modifier.fillMaxSize(), sizePx = 512)
            else -> Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(bottom = 24.dp)
                    .size(40.dp),
            )
        }

        val hasCover = covers.isNotEmpty()
        if (hasCover) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.72f))),
            )
        }
        val textColor = if (hasCover) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun Cover(uri: String, modifier: Modifier = Modifier, sizePx: Int = MediaThumbnail.GRID_SIZE_PX) {
    AsyncImage(
        model = remember(uri, sizePx) { MediaThumbnail(uri.toUri(), sizePx) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}
