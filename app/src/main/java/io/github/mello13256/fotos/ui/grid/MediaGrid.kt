package io.github.mello13256.fotos.ui.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.data.AppSettings
import io.github.mello13256.fotos.data.db.MediaEntity
import io.github.mello13256.fotos.data.db.mediaKind
import io.github.mello13256.fotos.image.MediaThumbnail
import io.github.mello13256.fotos.ui.common.MonthFormat
import io.github.mello13256.fotos.ui.common.formatDuration
import io.github.mello13256.fotos.ui.common.sharedMedia
import io.github.mello13256.fotos.ui.common.LocalActiveSharedMedia
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Grelha de fotografias com:
 * - cabeçalhos por mês e conteúdo inicial opcional (ex.: memórias);
 * - pinça contínua para mudar o número de colunas, com as células a deslizarem para o novo sítio;
 * - toque longo + arrastar para selecionar intervalos, com deslocamento automático nas margens;
 * - barra lateral de deslocamento rápido com o mês;
 * - transição partilhada com o visualizador.
 */
@Composable
fun MediaGrid(
    items: LazyPagingItems<GridItem>,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: Set<Long>,
    onSelectionChange: (Set<Long>) -> Unit,
    onOpen: (MediaEntity) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    state: LazyGridState = rememberLazyGridState(),
    leading: (@Composable () -> Unit)? = null,
    focusId: Long? = null,
    onFocusConsumed: () -> Unit = {},
    onSelectionStarted: () -> Unit = {},
) {
    val activeShared by LocalActiveSharedMedia.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val edgePx = with(density) { 64.dp.toPx() }
    val maxAutoScrollPx = with(density) { 18.dp.toPx() }
    val leadingCount = if (leading != null) 1 else 0

    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnColumnsChange by rememberUpdatedState(onColumnsChange)
    val currentLeadingCount by rememberUpdatedState(leadingCount)
    val currentOnSelectionStarted by rememberUpdatedState(onSelectionStarted)

    // Ao voltar do visualizador, garantir que a fotografia vista está no ecrã (para a transição).
    if (focusId != null) {
        val configuration = LocalConfiguration.current
        remember(focusId) {
            Snapshot.withoutReadObservation {
                scrollToFocus(
                    focusId = focusId,
                    items = items,
                    state = state,
                    columns = columns,
                    leadingCount = leadingCount,
                    screenHeightDp = configuration.screenHeightDp,
                    screenWidthDp = configuration.screenWidthDp,
                )
            }
            onFocusConsumed()
            true
        }
    }

    // Estado do arrastamento de seleção.
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragBase by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    // Pinça: escala visual enquanto os dedos se movem.
    var pinchScale by remember { mutableFloatStateOf(1f) }
    var pinchOrigin by remember { mutableStateOf(TransformOrigin.Center) }

    // As células só animam a mudança de posição logo a seguir a mudar o número de colunas; no
    // resto do tempo a animação ficaria ativa em cada célula sem necessidade.
    var animatePlacement by remember { mutableStateOf(false) }
    LaunchedEffect(columns) {
        animatePlacement = true
        delay(PLACEMENT_ANIMATION_MS)
        animatePlacement = false
    }

    /** Item na posição da grelha (que inclui o conteúdo inicial). */
    fun mediaAt(gridIndex: Int): MediaEntity? {
        val index = gridIndex - currentLeadingCount
        val list = currentItems
        return if (index in 0 until list.itemCount) (list.peek(index) as? GridItem.Media)?.entity else null
    }

    fun indexAt(position: Offset): Int? {
        val info = state.layoutInfo
        val x = position.x.toInt()
        val y = (position.y + info.viewportStartOffset).toInt()
        return info.visibleItemsInfo.firstOrNull { item ->
            x >= item.offset.x && x < item.offset.x + item.size.width &&
                y >= item.offset.y && y < item.offset.y + item.size.height
        }?.index
    }

    fun extendDragSelection(position: Offset) {
        val anchor = dragAnchor ?: return
        val index = indexAt(position) ?: return
        val range = if (index >= anchor) anchor..index else index..anchor
        val ids = range.mapNotNullTo(HashSet()) { mediaAt(it)?.id }
        val next = dragBase + ids
        if (next.size != currentSelection.size) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        currentOnSelectionChange(next)
    }

    LaunchedEffect(autoScroll) {
        if (autoScroll == 0f) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            state.scrollBy(autoScroll)
            dragPointer?.let(::extendDragSelection)
        }
    }

    val gestures = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Um toque que trava um deslocamento em curso não deve abrir a fotografia.
            val wasScrolling = state.isScrollInProgress
            val slop = viewConfiguration.touchSlop

            // Fase 1: toque, deslocamento, pinça ou toque longo?
            var outcome = Outcome.SCROLL
            val timedOut = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        outcome = Outcome.PINCH
                        return@withTimeoutOrNull
                    }
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) {
                        outcome = if (change?.isConsumed == true) Outcome.SCROLL else Outcome.TAP
                        return@withTimeoutOrNull
                    }
                    if ((change.position - down.position).getDistance() > slop) {
                        outcome = Outcome.SCROLL
                        return@withTimeoutOrNull
                    }
                }
            } == null
            if (timedOut) outcome = Outcome.LONG_PRESS

            when (outcome) {
                Outcome.TAP -> {
                    if (wasScrolling) return@awaitEachGesture
                    val media = indexAt(down.position)?.let(::mediaAt) ?: return@awaitEachGesture
                    if (currentSelection.isNotEmpty()) {
                        val selected = currentSelection
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelectionChange(if (media.id in selected) selected - media.id else selected + media.id)
                    } else {
                        currentOnOpen(media)
                    }
                }

                Outcome.LONG_PRESS -> {
                    val index = indexAt(down.position)
                    val media = index?.let(::mediaAt) ?: return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (currentSelection.isEmpty()) currentOnSelectionStarted()
                    dragAnchor = index
                    dragBase = currentSelection
                    dragPointer = down.position
                    currentOnSelectionChange(currentSelection + media.id)

                    // Fase 2: arrastar para selecionar. Consumir impede a grelha de deslocar.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        val position = change.position
                        dragPointer = position
                        if ((position - down.position).getDistance() > slop) extendDragSelection(position)
                        val height = size.height.toFloat()
                        autoScroll = when {
                            position.y < edgePx -> -maxAutoScrollPx * ((edgePx - position.y) / edgePx).coerceIn(0f, 1f)
                            position.y > height - edgePx ->
                                maxAutoScrollPx * ((position.y - (height - edgePx)) / edgePx).coerceIn(0f, 1f)
                            else -> 0f
                        }
                    }
                    autoScroll = 0f
                    dragAnchor = null
                    dragPointer = null
                }

                Outcome.SCROLL, Outcome.PINCH -> Unit
            }

            if (outcome == Outcome.TAP || outcome == Outcome.LONG_PRESS) return@awaitEachGesture

            // Fase 3: acompanhar até levantar os dedos. Com dois dedos, a grelha acompanha a pinça
            // e, ao passar um limiar, muda o número de colunas.
            var zoom = 1f
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    pinching = true
                    zoom *= event.calculateZoom()
                    val centroid = event.calculateCentroid(useCurrent = true)
                    if (centroid != Offset.Unspecified && size.width > 0 && size.height > 0) {
                        pinchOrigin = TransformOrigin(centroid.x / size.width, centroid.y / size.height)
                    }
                    event.changes.forEach { it.consume() }
                    val step = when {
                        zoom > ZOOM_STEP -> -1
                        zoom < 1 / ZOOM_STEP -> 1
                        else -> 0
                    }
                    val next = (currentColumns + step).coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS)
                    if (step != 0 && next != currentColumns) {
                        currentOnColumnsChange(next)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        zoom = 1f
                    }
                    // Resistência elástica nos limites.
                    pinchScale = zoom.coerceIn(0.8f, 1.25f)
                }
            }
            if (pinching) {
                val from = pinchScale
                scope.launch {
                    animate(from, 1f, animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                        pinchScale = value
                    }
                }
            }
        }
    }

    Box(modifier) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = pinchScale
                    scaleY = pinchScale
                    transformOrigin = pinchOrigin
                }
                .then(gestures),
            contentPadding = PaddingValues(bottom = bottomPadding + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (leading != null) {
                item(key = "leading", span = { GridItemSpan(maxLineSpan) }, contentType = LEADING_CONTENT_TYPE) {
                    leading()
                }
            }
            items(
                count = items.itemCount,
                key = items.itemKey { it.key },
                contentType = items.itemContentType { if (it is GridItem.Header) HEADER_CONTENT_TYPE else MEDIA_CONTENT_TYPE },
                span = { index ->
                    if (items.peek(index) is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                },
            ) { index ->
                when (val item = items[index]) {
                    is GridItem.Header -> MonthHeader(
                        item.label,
                        if (animatePlacement) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null) else Modifier,
                    )
                    is GridItem.Media -> MediaCell(
                        media = item.entity,
                        selectionMode = selection.isNotEmpty(),
                        selected = item.entity.id in selection,
                        shared = item.entity.id == activeShared,
                        modifier = if (animatePlacement) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null) else Modifier,
                    )
                    null -> Box(
                        Modifier
                            .aspectRatio(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }

        FastScroller(
            state = state,
            itemCount = items.itemCount,
            firstIndex = leadingCount,
            labelFor = { index -> labelNear(items, index) },
            bottomPadding = bottomPadding,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

/** Garante que a fotografia vista no visualizador está no ecrã, para a transição de regresso. */
private fun scrollToFocus(
    focusId: Long,
    items: LazyPagingItems<GridItem>,
    state: LazyGridState,
    columns: Int,
    leadingCount: Int,
    screenHeightDp: Int,
    screenWidthDp: Int,
) {
    val index = items.itemSnapshotList.indexOfFirst { (it as? GridItem.Media)?.entity?.id == focusId }
    if (index < 0) return
    val gridIndex = index + leadingCount
    val rows = (screenHeightDp / (screenWidthDp / columns.toFloat())).toInt()
    val first = state.firstVisibleItemIndex
    if (gridIndex < first || gridIndex > first + (rows - 2).coerceAtLeast(1) * columns) {
        state.requestScrollToItem((gridIndex - columns).coerceAtLeast(0))
    }
}

/** Mês do item na posição dada (ou do mais próximo já carregado). */
private fun labelNear(items: LazyPagingItems<GridItem>, index: Int): String? {
    for (offset in 0..60) {
        val i = index - offset
        if (i < 0) break
        when (val item = items.peek(i)) {
            is GridItem.Media -> return MonthFormat.label(item.entity.dateTaken)
            is GridItem.Header -> return item.label
            null -> Unit
        }
    }
    return null
}

private enum class Outcome { TAP, LONG_PRESS, SCROLL, PINCH }

private const val ZOOM_STEP = 1.3f
private const val PLACEMENT_ANIMATION_MS = 700L
private const val MEDIA_CONTENT_TYPE = 1
private const val HEADER_CONTENT_TYPE = 0
private const val LEADING_CONTENT_TYPE = 2

@Composable
private fun MonthHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaCell(
    media: MediaEntity,
    selectionMode: Boolean,
    selected: Boolean,
    shared: Boolean,
    modifier: Modifier = Modifier,
) {
    val thumbnail = remember(media.uri) { MediaThumbnail(media.uri.toUri()) }

    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        // Fora do modo de seleção, a célula é só a imagem: nada de animações por célula.
        val imageModifier = if (selectionMode) {
            val bouncy = spring<Float>(dampingRatio = 0.5f, stiffness = 600f)
            val inset by animateFloatAsState(if (selected) 0.84f else 1f, bouncy, label = "inset")
            val corner by animateDpAsState(if (selected) 14.dp else 0.dp, spring(stiffness = 600f), label = "corner")
            Modifier
                .graphicsLayer {
                    scaleX = inset
                    scaleY = inset
                }
                .clip(RoundedCornerShape(corner))
        } else {
            Modifier
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(imageModifier)
                .then(if (shared) Modifier.sharedMedia(media.id) else Modifier),
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (media.mediaKind.isVideo) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatDuration(media.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .size(16.dp),
                )
            }
        }
        if (selectionMode) {
            val checkScale by animateFloatAsState(
                if (selected) 1f else 0f,
                spring(dampingRatio = 0.5f, stiffness = 600f),
                label = "check",
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp),
            ) {
                Icon(Icons.Outlined.Circle, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = checkScale
                            scaleY = checkScale
                        }
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
    }
}
