package io.github.mello13256.fotos.ui.photos

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.Memory
import io.github.mello13256.fotos.image.MediaThumbnail
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Carrossel "Por esta altura": fotografias tiradas nesta semana, em anos anteriores. */
@Composable
fun MemoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    if (memories.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.memories_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(memories, key = { it.yearsAgo }) { memory ->
                MemoryCard(memory, onClick = { onOpen(memory) })
            }
        }
        Box(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun MemoryCard(memory: Memory, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "kenBurns")
    // Efeito Ken Burns: aproximação lenta com um ligeiro deslize.
    val zoom by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "zoom",
    )
    val pan by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "pan",
    )

    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(memory.photos.size) {
        while (memory.photos.size > 1) {
            delay(4500)
            index = (index + 1) % memory.photos.size
        }
    }

    val locale = Locale.getDefault()
    val dateText = remember(memory.date, locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale).format(memory.date)
    }

    Box(
        Modifier
            .size(width = 150.dp, height = 200.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AnimatedContent(
            targetState = memory.photos[index.coerceIn(0, memory.photos.lastIndex)],
            transitionSpec = { fadeIn(tween(900)) togetherWith fadeOut(tween(900)) },
            label = "memoryPhoto",
        ) { photo ->
            AsyncImage(
                model = remember(photo.uri) { MediaThumbnail(photo.uri.toUri(), 512) },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan * size.width * 0.04f
                    },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                pluralStringResource(R.plurals.years_ago, memory.yearsAgo, memory.yearsAgo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(dateText, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        }
    }
}
