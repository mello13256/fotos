package io.github.mello13256.fotos.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Animação de entrada/saída do destino de navegação atual. */
val LocalNavAnimatedScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Fotografia que participa na transição para o visualizador (a que foi aberta, ou a última vista
 * ao voltar). Só essa célula da grelha se regista, em vez de todas as visíveis.
 */
val LocalActiveSharedMedia = staticCompositionLocalOf<MutableState<Long?>> { mutableStateOf(null) }

/** Espaço ocupado pela barra de navegação inferior nos ecrãs com separadores. */
val LocalBottomBarSpace = staticCompositionLocalOf<Dp> { 0.dp }

@OptIn(ExperimentalSharedTransitionApi::class)
private val MediaBoundsTransform = BoundsTransform { _, _ ->
    spring(dampingRatio = 0.86f, stiffness = 380f)
}

/**
 * Liga a miniatura da grelha à fotografia no visualizador: ao abrir, a miniatura cresce até
 * ocupar o ecrã; ao voltar, encolhe para o mesmo sítio da grelha.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedMedia(id: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedMedia.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "media-$id"),
            animatedVisibilityScope = animated,
            enter = fadeIn(),
            exit = fadeOut(),
            boundsTransform = MediaBoundsTransform,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
