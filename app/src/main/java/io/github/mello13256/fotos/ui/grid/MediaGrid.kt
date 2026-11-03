package io.github.mello13256.fotos.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import io.github.mello13256.fotos.ui.common.formatDuration

/**
 * Grelha de fotografias com:
 * - cabeçalhos por mês;
 * - pinça para mudar o número de colunas;
 * - toque longo + arrastar para selecionar intervalos, com deslocamento automático nas margens.
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
    contentPadding: PaddingValues = PaddingValues(),
    state: LazyGridState = rememberLazyGridState(),
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edgePx = with(density) { 64.dp.toPx() }
    val maxAutoScrollPx = with(density) { 18.dp.toPx() }
    val thumbnailPx = remember(columns) { if (columns <= 2) 512 else if (columns <= 4) 384 else 256 }

    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentColumns by rememberUpdatedState(columns)
    val currentOnColumnsChange by rememberUpdatedState(onColumnsChange)

    // Estado do arrastamento de seleção.
    var dragAnchor by remember { mutableStateOf<Int?>(null) }
    var dragBase by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var dragPointer by remember { mutableStateOf<Offset?>(null) }
    var autoScroll by remember { mutableFloatStateOf(0f) }

    fun mediaAt(index: Int): MediaEntity? =
        if (index in 0 until currentItems.itemCount) (currentItems.peek(index) as? GridItem.Media)?.entity else null

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
        currentOnSelectionChange(dragBase + ids)
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
                        currentOnSelectionChange(if (media.id in selected) selected - media.id else selected + media.id)
                    } else {
                        currentOnOpen(media)
                    }
                }

                Outcome.LONG_PRESS -> {
                    val index = indexAt(down.position)
                    val media = index?.let(::mediaAt) ?: return@awaitEachGesture
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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

            // Fase 3: acompanhar até levantar os dedos; com dois dedos, a pinça muda as colunas.
            var zoom = 1f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) break
                if (pressed >= 2) {
                    zoom *= event.calculateZoom()
                    event.changes.forEach { it.consume() }
                    val step = when {
                        zoom > ZOOM_STEP -> -1
                        zoom < 1 / ZOOM_STEP -> 1
                        else -> 0
                    }
                    if (step != 0) {
                        val next = (currentColumns + step).coerceIn(AppSettings.MIN_COLUMNS, AppSettings.MAX_COLUMNS)
                        if (next != currentColumns) {
                            currentOnColumnsChange(next)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        zoom = 1f
                    }
                }
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.then(gestures),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.key },
            contentType = items.itemContentType { if (it is GridItem.Header) 0 else 1 },
            span = { index ->
                if (items.peek(index) is GridItem.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            },
        ) { index ->
            when (val item = items[index]) {
                is GridItem.Header -> MonthHeader(item.label)
                is GridItem.Media -> MediaCell(
                    media = item.entity,
                    thumbnailPx = thumbnailPx,
                    selectionMode = selection.isNotEmpty(),
                    selected = item.entity.id in selection,
                )
                null -> Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

private enum class Outcome { TAP, LONG_PRESS, SCROLL, PINCH }

private const val ZOOM_STEP = 1.25f

@Composable
private fun MonthHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaCell(
    media: MediaEntity,
    thumbnailPx: Int,
    selectionMode: Boolean,
    selected: Boolean,
) {
    val thumbnail = remember(media.uri, thumbnailPx) { MediaThumbnail(media.uri.toUri(), thumbnailPx) }
    val inset = if (selected) 0.86f else 1f
    Box(
        Modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = thumbnail,
            contentDescription = media.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .scale(inset)
                .clip(if (selected) RoundedCornerShape(12.dp) else RoundedCornerShape(0.dp)),
        )
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
            val tint = if (selected) MaterialTheme.colorScheme.primary else Color.White
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .then(if (selected) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier),
            )
        }
    }
}
