package io.github.mello13256.fotos.ui.viewer

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.data.db.MediaEntity
import io.github.mello13256.fotos.data.db.mediaKind
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.common.shareMedia
import io.github.mello13256.fotos.ui.grid.CollectionPickerDialog
import kotlinx.coroutines.flow.flowOf

@Composable
fun ViewerScreen(
    source: MediaSource,
    startId: Long,
    onBack: () -> Unit,
) {
    val container = appContainer()
    val viewModel: ViewerViewModel = viewModel(key = "viewer:${source.encode()}") {
        ViewerViewModel(source, container)
    }
    val ids by viewModel.ids.collectAsStateWithLifecycle()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val loaded = ids
        when {
            loaded == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            loaded.isEmpty() -> LaunchedEffect(Unit) { onBack() }
            else -> ViewerPager(viewModel, loaded, startId, onBack)
        }
    }
}

@Composable
private fun ViewerPager(
    viewModel: ViewerViewModel,
    ids: List<Long>,
    startId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val pagerState = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { ids.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }

    ImmersiveSystemBars(visible = chromeVisible)

    val currentId = ids.getOrNull(pagerState.currentPage.coerceIn(0, ids.lastIndex))
    val current by remember(currentId) { currentId?.let(viewModel::observe) ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    val isFavorite by remember(currentId) {
        currentId?.let(viewModel::isFavorite) ?: flowOf(false)
    }.collectAsStateWithLifecycle(false)
    val collections by container.collections.summaries.collectAsStateWithLifecycle(emptyList())

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) currentId?.let(viewModel::onTrashed)
    }

    HorizontalPager(
        state = pagerState,
        key = { ids[it] },
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val id = ids[page]
        val media by remember(id) { viewModel.observe(id) }.collectAsStateWithLifecycle(null)
        media?.let {
            MediaPage(
                media = it,
                isCurrent = pagerState.settledPage == page,
                onTap = { chromeVisible = !chromeVisible },
            )
        }
    }

    AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                }
                Text(
                    current?.displayName.orEmpty(),
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                val media = current
                IconButton(enabled = media != null, onClick = {
                    media?.let { context.shareMedia(listOf(it.uri.toUri()), listOf(it.mimeType)) }
                }) {
                    Icon(Icons.Outlined.Share, stringResource(R.string.share), tint = Color.White)
                }
                IconButton(enabled = media != null, onClick = {
                    media?.let { viewModel.setFavorite(it.id, !isFavorite) }
                }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites),
                        tint = Color.White,
                    )
                }
                IconButton(enabled = media != null, onClick = { showPicker = true }) {
                    Icon(Icons.Outlined.LibraryAdd, stringResource(R.string.add_to_collection), tint = Color.White)
                }
                IconButton(enabled = media != null, onClick = {
                    media?.let { trashLauncher.launch(IntentSenderRequest.Builder(viewModel.trashRequest(it)).build()) }
                }) {
                    Icon(Icons.Outlined.Delete, stringResource(R.string.move_to_trash), tint = Color.White)
                }
            }
        }
    }

    if (showPicker && currentId != null) {
        CollectionPickerDialog(
            collections = collections,
            onPick = {
                viewModel.addTo(it, currentId)
                showPicker = false
            },
            onCreate = {
                viewModel.createCollectionWith(it, currentId)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun MediaPage(media: MediaEntity, isCurrent: Boolean, onTap: () -> Unit) {
    val uri = remember(media.uri) { media.uri.toUri() }
    if (media.mediaKind.isVideo) {
        if (isCurrent) {
            VideoPlayer(uri, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = remember(uri) { MediaThumbnail(uri, 1024) },
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }
    } else {
        ZoomableImage(model = media.uri, contentDescription = media.displayName, onTap = onTap)
    }
}

/** Esconde as barras do sistema quando os controlos estão escondidos; ícones sempre claros. */
@Composable
fun ImmersiveSystemBars(visible: Boolean) {
    val view = LocalView.current
    val controller = remember(view) {
        (view.context as? Activity)?.window?.let { WindowCompat.getInsetsController(it, view) }
    }
    DisposableEffect(controller) {
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNavigation = controller?.isAppearanceLightNavigationBars
        controller?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller?.apply {
                show(WindowInsetsCompat.Type.systemBars())
                lightStatus?.let { isAppearanceLightStatusBars = it }
                lightNavigation?.let { isAppearanceLightNavigationBars = it }
            }
        }
    }
    LaunchedEffect(controller, visible) {
        if (visible) {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
