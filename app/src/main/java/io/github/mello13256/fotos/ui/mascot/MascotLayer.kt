package io.github.mello13256.fotos.ui.mascot

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.ui.common.appContainer
import kotlin.math.roundToInt

/**
 * Camada por cima da grelha onde o Lumi vive. Não interceta toques fora do próprio Lumi.
 *
 * @param bottomPadding espaço ocupado em baixo (barra de navegação), para o "chão" ficar acima.
 */
@Composable
fun MascotLayer(gridState: LazyGridState, bottomPadding: Dp, modifier: Modifier = Modifier) {
    val container = appContainer()
    val density = LocalDensity.current
    val engine = remember(density) { LumiEngine(density.density) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val bottomPx = with(density) { bottomPadding.toPx() }
    val currentBottom by rememberUpdatedState(bottomPx)
    val textMeasurer = rememberTextMeasurer()
    val phrases = stringArrayResource(R.array.lumi_phrases)
    val favoritePhrase = stringResource(R.string.lumi_favorite)
    val trashPhrase = stringResource(R.string.lumi_trash)
    val themePhrase = stringResource(R.string.lumi_theme)
    val eventPhrases by rememberUpdatedState(
        mapOf(
            MascotEvent.FAVORITE to favoritePhrase,
            MascotEvent.TRASH to trashPhrase,
            MascotEvent.THEME to themePhrase,
        ),
    )

    LaunchedEffect(engine) {
        container.mascotEvents.collect { engine.onEvent(it, eventPhrases[it]) }
    }

    LaunchedEffect(engine, gridState) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                if (layerSize != IntSize.Zero) {
                    val floor = layerSize.height - currentBottom - engine.sizePx
                    engine.update(
                        dt = dt,
                        width = layerSize.width.toFloat(),
                        floor = floor,
                        perches = gridState.perches(),
                        scrolling = gridState.isScrollInProgress,
                    )
                }
            }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { layerSize = it },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            engine.frame
            drawScene(engine, textMeasurer)
        }

        Box(
            Modifier
                .offset { IntOffset(engine.x.roundToInt(), engine.y.roundToInt()).also { engine.frame } }
                .size(with(density) { engine.sizePx.toDp() })
                .semantics { contentDescription = "Lumi" }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    engine.onTap(phrases.random())
                },
        )

        engine.bubble?.let { text ->
            val margin = with(density) { 8.dp.toPx() }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shadowElevation = 3.dp,
                modifier = Modifier.layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                    layout(placeable.width, placeable.height) {
                        engine.frame
                        val centerX = engine.x + engine.sizePx / 2f
                        val bx = (centerX - placeable.width / 2f)
                            .coerceIn(margin, (layerSize.width - placeable.width - margin).coerceAtLeast(margin))
                        val by = (engine.y - placeable.height - margin).coerceAtLeast(margin)
                        placeable.place(bx.roundToInt(), by.roundToInt())
                    }
                },
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private fun LazyGridState.perches(): List<Perch> {
    val info = layoutInfo
    return info.visibleItemsInfo
        .filter { it.contentType == MEDIA_CONTENT_TYPE }
        .map { item ->
            val top = (item.offset.y - info.viewportStartOffset).toFloat()
            Perch(
                key = item.key,
                rect = Rect(
                    left = item.offset.x.toFloat(),
                    top = top,
                    right = (item.offset.x + item.size.width).toFloat(),
                    bottom = top + item.size.height,
                ),
            )
        }
}

/** Tipo de conteúdo das células de fotografia na grelha (ver MediaGrid). */
const val MEDIA_CONTENT_TYPE = 1

private fun DrawScope.drawScene(engine: LumiEngine, textMeasurer: TextMeasurer) {
    if (engine.x < 0f) return
    val size = engine.sizePx

    // Sombra.
    if (engine.grounded) {
        drawOval(
            Color.Black.copy(alpha = 0.16f),
            topLeft = Offset(engine.x + size * 0.18f, engine.y + size * 0.9f),
            size = Size(size * 0.64f, size * 0.14f),
        )
    }

    drawLumi(engine.pose, Offset(engine.x, engine.y), size)

    // Flash da "fotografia".
    val flash = engine.flashAge
    if (flash < FLASH_DURATION) {
        val progress = flash / FLASH_DURATION
        val eye = Offset(engine.x + size / 2f, engine.y + size * 0.51f)
        drawCircle(Color.White.copy(alpha = (1f - progress) * 0.9f), size * (0.2f + progress * 1.6f), eye)
        drawCircle(
            Color.White.copy(alpha = (1f - progress) * 0.6f),
            size * (0.4f + progress * 2.2f),
            eye,
            style = Stroke(width = size * 0.05f),
        )
    }

    for (p in engine.particles) {
        val fade = (1f - p.age / p.life).coerceIn(0f, 1f)
        val center = Offset(p.x, p.y)
        when (p.kind) {
            ParticleKind.HEART -> drawHeart(center, p.size, LumiColors.heart.copy(alpha = fade))
            ParticleKind.SPARK -> drawSpark(center, p.size * (0.6f + fade * 0.4f), LumiColors.spark.copy(alpha = fade))
            ParticleKind.DROP -> drawDrop(center, p.size, LumiColors.drop.copy(alpha = fade))
            ParticleKind.Z, ParticleKind.EXCLAMATION -> {
                val label = if (p.kind == ParticleKind.Z) "z" else "!"
                val style = TextStyle(
                    color = (if (p.kind == ParticleKind.Z) Color(0xFF8E9AAF) else LumiColors.heart).copy(alpha = fade),
                    fontSize = (p.size / density).sp,
                    fontWeight = FontWeight.Bold,
                )
                val layout = textMeasurer.measure(label, style)
                drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }
        }
    }
}

private const val FLASH_DURATION = 0.35f

/** Lumi parado, para ecrãs vazios e para o ecrã de boas-vindas. */
@Composable
fun LumiPortrait(modifier: Modifier = Modifier, size: Dp = 96.dp, sleeping: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "lumi")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "antenna",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "blink",
    )
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "bob",
    )
    Canvas(modifier.size(size)) {
        val px = this.size.minDimension
        val lid = if (blink > 0.95f) 1f - abs((blink - 0.975f) / 0.025f) else 0f
        drawLumi(
            LumiPose(
                antennaWave = wave,
                blink = lid.coerceIn(0f, 1f),
                sleeping = sleeping,
                squash = 1f + (bob - 0.5f) * 0.04f,
            ),
            Offset.Zero,
            px,
        )
    }
}

private fun abs(value: Float) = kotlin.math.abs(value)
