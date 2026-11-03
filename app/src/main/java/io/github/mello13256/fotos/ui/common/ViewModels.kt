package io.github.mello13256.fotos.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.ui.grid.GridViewModel

@Composable
fun gridViewModel(source: MediaSource, key: String = source.encode()): GridViewModel {
    val container = appContainer()
    return viewModel(key = "grid:$key") { GridViewModel(source, container) }
}
