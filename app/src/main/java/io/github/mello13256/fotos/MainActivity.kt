package io.github.mello13256.fotos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mello13256.fotos.data.AppSettings
import io.github.mello13256.fotos.ui.FotosNavHost
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.permission.MediaAccess
import io.github.mello13256.fotos.ui.permission.MediaPermission
import io.github.mello13256.fotos.ui.permission.PermissionScreen
import io.github.mello13256.fotos.ui.theme.FotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FotosRoot() }
    }
}

@Composable
private fun FotosRoot() {
    val container = appContainer()
    val context = LocalContext.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var access by remember { mutableStateOf(MediaPermission.access(context)) }

    // O utilizador pode mudar a permissão (ou a seleção parcial) nas definições a qualquer momento.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        access = MediaPermission.access(context)
        if (access != MediaAccess.NONE) container.mediaSync.requestSync()
    }

    FotosTheme(mode = settings.theme, colorSource = settings.colorSource, seedColor = settings.seedColor) {
        if (access == MediaAccess.NONE) {
            PermissionScreen(onResult = { access = MediaPermission.access(context) })
        } else {
            LaunchedEffect(Unit) { container.mediaSync.start() }
            FotosNavHost(access = access, onAccessChanged = { access = MediaPermission.access(context) })
        }
    }
}
