package io.github.mello13256.fotos.ui.grid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Pega lateral para atravessar a biblioteca rapidamente. Aparece durante o deslocamento e, ao ser
 * arrastada, mostra o mês correspondente numa etiqueta.
 *
 * @param itemCount número de elementos da grelha a partir de [firstIndex].
 * @param labelFor etiqueta (mês) para o elemento na posição dada, relativa a [firstIndex].
 */
@Composable
fun FastScroller(
    state: LazyGridState,
    itemCount: Int,
    firstIndex: Int,
    labelFor: (Int) -> String?,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    if (itemCount < MIN_ITEMS) return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thumbHeight = 44.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    var lastLabel by remember { mutableStateOf<String?>(null) }
    val currentLabelFor by rememberUpdatedState(labelFor)
    val currentCount by rememberUpdatedState(itemCount)

    val scrolling = state.isScrollInProgress
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) {
            visible = true
        } else {
            delay(1500)
            visible = false
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxHeight()
            .padding(bottom = bottomPadding),
    ) {
        val trackPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(1f)
        val currentTrack by rememberUpdatedState(trackPx)
        val currentFirst by rememberUpdatedState(firstIndex)
        val fraction = if (dragging) {
            dragFraction
        } else {
            val visibleCount = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val index = (state.firstVisibleItemIndex - firstIndex).coerceAtLeast(0)
            (index.toFloat() / (itemCount - visibleCount).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
        val thumbY = (fraction * trackPx).roundToInt()

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Row(
                Modifier.offset { IntOffset(0, thumbY) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnimatedVisibility(visible = dragging && lastLabel != null, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(
                            lastLabel.orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .size(width = 34.dp, height = thumbHeight)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    val visibleCount = state.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                                    val index = (state.firstVisibleItemIndex - currentFirst).coerceAtLeast(0)
                                    dragFraction = (index.toFloat() / (currentCount - visibleCount).coerceAtLeast(1))
                                        .coerceIn(0f, 1f)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                                onVerticalDrag = { change, delta ->
                                    change.consume()
                                    dragFraction = (dragFraction + delta / currentTrack).coerceIn(0f, 1f)
                                    val target = (dragFraction * (currentCount - 1)).roundToInt()
                                    state.requestScrollToItem(target + currentFirst)
                                    val label = currentLabelFor(target)
                                    if (label != null && label != lastLabel) {
                                        lastLabel = label
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                            )
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.UnfoldMore, contentDescription = null, modifier = Modifier.width(20.dp))
                    }
                }
            }
        }
    }
}

private const val MIN_ITEMS = 120
