package io.github.mello13256.fotos.ui.grid

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.AppSettings
import io.github.mello13256.fotos.data.CollectionRepository
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.ui.common.LocalBottomBarSpace
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.common.shareMedia
import kotlinx.coroutines.launch

/**
 * Ecrã de grelha partilhado pela biblioteca, pelas coleções e pelas pastas. Em modo de seleção,
 * a barra superior passa a mostrar as ações em lote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(
    viewModel: GridViewModel,
    title: String,
    onOpen: (source: MediaSource, mediaId: Long) -> Unit,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    header: @Composable () -> Unit = {},
    emptyText: String = stringResource(R.string.empty_library),
    leading: (@Composable () -> Unit)? = null,
    focusId: Long? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val items = viewModel.items.collectAsLazyPagingItems()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val source by viewModel.source.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    val hasSynced by container.mediaSync.hasSynced.collectAsStateWithLifecycle()
    val collections by container.collections.summaries.collectAsStateWithLifecycle(emptyList())
    var showPicker by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val bottomBarSpace = LocalBottomBarSpace.current

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.onTrashed()
    }

    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                            }
                        }
                    },
                    actions = actions,
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    title = { Text(pluralStringResource(R.plurals.selected_count, selection.size, selection.size)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, stringResource(R.string.cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::selectAll) {
                            Icon(Icons.Outlined.SelectAll, stringResource(R.string.select_all))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val selected = viewModel.selectedItems()
                                context.shareMedia(selected.map { it.uri.toUri() }, selected.map { it.mimeType })
                            }
                        }) {
                            Icon(Icons.Outlined.Share, stringResource(R.string.share))
                        }
                        if (source is MediaSource.Collection) {
                            IconButton(onClick = viewModel::removeSelectionFromCollection) {
                                Icon(Icons.Outlined.RemoveCircleOutline, stringResource(R.string.remove_from_collection))
                            }
                        } else {
                            IconButton(onClick = { viewModel.addSelectionTo(CollectionRepository.FAVORITES_ID) }) {
                                Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.add_to_favorites))
                            }
                        }
                        IconButton(onClick = { showPicker = true }) {
                            Icon(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_collection))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.trashRequestForSelection()?.let {
                                    trashLauncher.launch(IntentSenderRequest.Builder(it).build())
                                }
                            }
                        }) {
                            Icon(Icons.Outlined.Delete, stringResource(R.string.move_to_trash))
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            header()
            val bottomPadding = padding.calculateBottomPadding() + bottomBarSpace
            Box(Modifier.fillMaxSize()) {
                MediaGrid(
                    items = items,
                    columns = settings.gridColumns,
                    onColumnsChange = { scope.launch { container.settings.setGridColumns(it) } },
                    selection = selection,
                    onSelectionChange = viewModel::setSelection,
                    onOpen = { onOpen(source, it.id) },
                    modifier = Modifier.fillMaxSize(),
                    bottomPadding = bottomPadding,
                    state = gridState,
                    leading = leading,
                    focusId = focusId,
                    onFocusConsumed = onFocusConsumed,
                )
                val loading = items.loadState.refresh is LoadState.Loading
                if (items.itemCount == 0 && !loading) {
                    if (!hasSynced) {
                        CircularProgressIndicator(Modifier.align(Alignment.Center))
                    } else {
                        Column(
                            Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Outlined.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                emptyText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        CollectionPickerDialog(
            collections = collections,
            onPick = {
                viewModel.addSelectionTo(it)
                showPicker = false
            },
            onCreate = {
                viewModel.createCollectionWithSelection(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}
