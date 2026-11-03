package io.github.mello13256.fotos.ui.trash

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.media.TrashedItem
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.ui.common.appContainer
import java.util.concurrent.TimeUnit

/**
 * Reciclagem do sistema: os itens ficam 30 dias recuperáveis. Tocar seleciona; as ações aplicam-se
 * à seleção, ou a tudo quando nada está selecionado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val container = appContainer()
    var trashed by remember { mutableStateOf<List<TrashedItem>?>(null) }
    var selection by remember { mutableStateOf<Set<TrashedItem>>(emptySet()) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        trashed = runCatching { container.trash.trashedItems() }.getOrDefault(emptyList())
        selection = selection.filterTo(HashSet()) { selected -> trashed.orEmpty().any { it.uri == selected.uri } }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            selection = emptySet()
            container.mediaSync.requestSync()
        }
        reload++
    }
    fun launchRequest(sender: IntentSender) = launcher.launch(IntentSenderRequest.Builder(sender).build())
    val targets = selection.ifEmpty { trashed.orEmpty().toSet() }.map { it.uri }

    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selection.isEmpty()) stringResource(R.string.trash)
                        else pluralStringResource(R.plurals.selected_count, selection.size, selection.size),
                    )
                },
                navigationIcon = {
                    if (selection.isEmpty()) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    } else {
                        IconButton(onClick = { selection = emptySet() }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.cancel_selection))
                        }
                    }
                },
                actions = {
                    if (!trashed.isNullOrEmpty()) {
                        IconButton(onClick = { selection = trashed.orEmpty().toSet() }) {
                            Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all))
                        }
                        IconButton(onClick = { launchRequest(container.trash.restoreRequest(targets)) }) {
                            Icon(
                                Icons.Outlined.RestoreFromTrash,
                                stringResource(if (selection.isEmpty()) R.string.restore_all else R.string.restore),
                            )
                        }
                        IconButton(onClick = { launchRequest(container.trash.deleteForeverRequest(targets)) }) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                stringResource(if (selection.isEmpty()) R.string.empty_trash else R.string.delete_forever),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val loaded = trashed
        if (loaded != null && loaded.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.trash_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.trash_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(loaded.orEmpty(), key = { it.uri.toString() }) { item ->
                val selected = item in selection
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { selection = if (selected) selection - item else selection + item },
                ) {
                    AsyncImage(
                        model = remember(item.uri) { MediaThumbnail(item.uri, 256) },
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(if (selected) 0.86f else 1f)
                            .clip(RoundedCornerShape(if (selected) 12.dp else 0.dp)),
                    )
                    val days = TimeUnit.MILLISECONDS.toDays(item.expiresAt - System.currentTimeMillis())
                        .coerceAtLeast(0).toInt()
                    Text(
                        pluralStringResource(R.plurals.days_left, days, days),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    if (item.isVideo) {
                        Icon(
                            Icons.Filled.PlayCircle,
                            null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(16.dp),
                        )
                    }
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
