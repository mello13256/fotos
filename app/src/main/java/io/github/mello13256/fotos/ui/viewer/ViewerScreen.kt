package io.github.mello13256.fotos.ui.viewer

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.data.db.MediaEntity
import io.github.mello13256.fotos.data.db.mediaKind
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.image.PhotoColors
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.common.sharedMedia
import io.github.mello13256.fotos.ui.common.shareMedia
import io.github.mello13256.fotos.ui.grid.CollectionPickerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    source: MediaSource,
    startId: Long,
    onBack: () -> Unit,
    onCurrentChanged: (Long) -> Unit = {},
) {
    val container = appContainer()
    val viewModel: ViewerViewModel = viewModel(key = "viewer:${source.encode()}") {
        ViewerViewModel(source, container)
    }
    val ids by viewModel.ids.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        val loaded = ids
        when {
            loaded == null -> Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            loaded.isEmpty() -> LaunchedEffect(Unit) { onBack() }
            else -> ViewerPager(viewModel, loaded, startId, onBack, onCurrentChanged)
        }
    }
}

@Composable
private fun ViewerPager(
    viewModel: ViewerViewModel,
    ids: List<Long>,
    startId: Long,
    onBack: () -> Unit,
    onCurrentChanged: (Long) -> Unit,
) {
    val context = LocalContext.current
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    val pagerState = rememberPagerState(initialPage = ids.indexOf(startId).coerceAtLeast(0)) { ids.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    val dismiss = remember { Animatable(0f) }
    // Lido apenas nas fases de layout/desenho, para o arrastamento não recompor o ecrã.
    fun dismissProgress() = (dismiss.value / (screenHeightPx * 0.5f)).coerceIn(0f, 1f)
    val atRest by remember { derivedStateOf { dismiss.value == 0f } }
    val currentOnBack by rememberUpdatedState(onBack)

    ImmersiveSystemBars(visible = chromeVisible && atRest)

    val currentId = ids.getOrNull(pagerState.currentPage.coerceIn(0, ids.lastIndex))
    val current by remember(currentId) { currentId?.let(viewModel::observe) ?: flowOf(null) }
        .collectAsStateWithLifecycle(null)
    val isFavorite by remember(currentId) {
        currentId?.let(viewModel::isFavorite) ?: flowOf(false)
    }.collectAsStateWithLifecycle(false)
    val collections by container.collections.summaries.collectAsStateWithLifecycle(emptyList())

    LaunchedEffect(currentId) { currentId?.let(onCurrentChanged) }

    // Luz ambiente: cor dominante da fotografia atual.
    val currentUri = current?.uri
    val palette by produceState<Palette?>(null, currentUri) {
        value = currentUri?.let { PhotoColors.palette(context.contentResolver, it.toUri()) }
    }
    val ambient by animateColorAsState(
        targetValue = palette?.let(PhotoColors::ambient)?.let { Color(it) } ?: Color(0xFF111111),
        animationSpec = tween(700),
        label = "ambient",
    )

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) currentId?.let(viewModel::onTrashed)
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Deslizar para baixo fecha o visualizador.
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismiss.value > dismissThresholdPx) {
                            currentOnBack()
                        } else {
                            scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.7f)) }
                        }
                    },
                    onDragCancel = { scope.launch { dismiss.animateTo(0f) } },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        scope.launch { dismiss.snapTo((dismiss.value + delta).coerceAtLeast(0f)) }
                    },
                )
            },
    ) {
        // Fundo: a própria fotografia, muito desfocada, tingida pela sua cor dominante.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - dismissProgress() * 0.7f }
                .background(Color.Black),
        ) {
            Crossfade(targetState = currentUri, animationSpec = tween(700), label = "backdrop") { uri ->
                if (uri != null) {
                    AsyncImage(
                        model = remember(uri) { MediaThumbnail(uri.toUri(), BACKDROP_PX) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Uma miniatura minúscula ampliada já fica desfocada, sem o custo de um
                        // efeito de desfoque em ecrã inteiro.
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.6f },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ambient.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.35f), ambient.copy(alpha = 0.7f)),
                        ),
                    ),
            )
        }

        HorizontalPager(
            state = pagerState,
            key = { ids[it] },
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dismiss.value.roundToInt()) }
                .graphicsLayer {
                    val s = 1f - dismissProgress() * 0.15f
                    scaleX = s
                    scaleY = s
                },
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

        AnimatedVisibility(visible = chromeVisible && atRest, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(ambient.copy(alpha = 0.9f), Color.Transparent)))
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
                        .background(Brush.verticalGradient(listOf(Color.Transparent, ambient.copy(alpha = 0.9f))))
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
                        media?.let {
                            scope.launch {
                                val seed = PhotoColors.palette(context.contentResolver, it.uri.toUri())?.let(PhotoColors::seed)
                                if (seed != null) {
                                    container.settings.setPhotoColor(seed)
                                    Toast.makeText(context, R.string.theme_from_photo_done, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Outlined.Palette, stringResource(R.string.theme_from_photo), tint = Color.White)
                    }
                    IconButton(enabled = media != null, onClick = {
                        media?.let { trashLauncher.launch(IntentSenderRequest.Builder(viewModel.trashRequest(it)).build()) }
                    }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.move_to_trash), tint = Color.White)
                    }
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

/**
 * Uma página do visualizador. A fotografia ocupa uma caixa com a sua proporção exata: é essa caixa
 * que se liga à miniatura da grelha na transição. A miniatura (já em cache) aparece de imediato e
 * a imagem em resolução total é desenhada por cima quando chega.
 */
@Composable
private fun MediaPage(media: MediaEntity, isCurrent: Boolean, onTap: () -> Unit) {
    val uri = remember(media.uri) { media.uri.toUri() }
    val thumbnailPainter = rememberAsyncImagePainter(remember(uri) { MediaThumbnail(uri) })
    val thumbnailState by thumbnailPainter.state.collectAsState()
    val loadedSize = (thumbnailState as? AsyncImagePainter.State.Success)?.painter?.intrinsicSize
    val aspect = when {
        loadedSize != null && loadedSize.width > 0f && loadedSize.height > 0f -> loadedSize.width / loadedSize.height
        media.width > 0 && media.height > 0 -> media.width.toFloat() / media.height
        else -> 1f
    }

    if (media.mediaKind.isVideo) {
        var showPlayer by remember { mutableStateOf(false) }
        LaunchedEffect(isCurrent) {
            if (isCurrent) {
                delay(PLAYER_DELAY_MS) // deixar terminar a transição antes de criar o leitor
                showPlayer = true
            } else {
                showPlayer = false
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .aspectRatio(aspect)
                    .sharedMedia(media.id),
            ) {
                Image(thumbnailPainter, media.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (showPlayer) {
                VideoPlayer(uri, Modifier.fillMaxSize())
            } else {
                Icon(Icons.Filled.PlayCircle, null, tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }
    } else {
        ZoomableBox(onTap = onTap) {
            Box(
                Modifier
                    .aspectRatio(aspect)
                    .sharedMedia(media.id),
            ) {
                Image(thumbnailPainter, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private const val BACKDROP_PX = 24
private const val PLAYER_DELAY_MS = 400L

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
