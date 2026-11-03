package io.github.mello13256.fotos.ui.viewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs

private const val MAX_SCALE = 6f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Contentor com pinça para ampliar, arrastar quando ampliado e toque duplo. Com o conteúdo no
 * tamanho normal, os gestos de um dedo passam para fora (paginador, deslizar para fechar).
 */
@Composable
fun ZoomableBox(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    onZoomChange: (zoomed: Boolean) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    fun clamp(value: Offset, forScale: Float): Offset {
        val maxX = size.width * (forScale - 1f) / 2f
        val maxY = size.height * (forScale - 1f) / 2f
        return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
    }

    fun setScale(value: Float) {
        val wasZoomed = scale > 1f
        scale = value
        if (wasZoomed != (value > 1f)) currentOnZoomChange(value > 1f)
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            setScale(1f)
                            offset = Offset.Zero
                        } else {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            setScale(DOUBLE_TAP_SCALE)
                            offset = clamp((tap - center) * (1f - DOUBLE_TAP_SCALE), DOUBLE_TAP_SCALE)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                            val target = clamp(offset + pan, newScale)
                            // Ampliado mas já encostado à margem: deixar o paginador mudar de página.
                            val atEdge = pressed == 1 && abs(pan.x) > abs(pan.y) && target.x == offset.x
                            setScale(newScale)
                            offset = if (newScale == 1f) Offset.Zero else target
                            val moved = zoom != 1f || pan != Offset.Zero
                            if (moved && !atEdge) event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}
