package io.github.mello13256.fotos.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.mello13256.fotos.AppContainer
import io.github.mello13256.fotos.FotosApp

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as FotosApp).container
