package io.github.mello13256.fotos.ui.external

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.ui.common.shareMedia
import io.github.mello13256.fotos.ui.theme.FotosTheme
import io.github.mello13256.fotos.ui.viewer.ImmersiveSystemBars
import io.github.mello13256.fotos.ui.viewer.VideoPlayer
import io.github.mello13256.fotos.ui.viewer.ZoomableImage

/** Abre uma fotografia ou um vídeo pedido por outra app (galeria predefinida). */
class ExternalViewerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri: Uri? = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val mime = intent.type ?: contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/")

        setContent {
            FotosTheme {
                var chromeVisible by remember { mutableStateOf(true) }
                ImmersiveSystemBars(visible = chromeVisible)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    if (isVideo) {
                        VideoPlayer(uri, Modifier.fillMaxSize())
                    } else {
                        ZoomableImage(
                            model = uri.toString(),
                            contentDescription = null,
                            onTap = { chromeVisible = !chromeVisible },
                        )
                    }
                    AnimatedVisibility(visible = chromeVisible, enter = fadeIn(), exit = fadeOut()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                                .statusBarsPadding()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = ::finish) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
                            }
                            Box(Modifier.weight(1f))
                            IconButton(onClick = { shareMedia(listOf(uri), listOf(mime.ifBlank { "*/*" })) }) {
                                Icon(Icons.Outlined.Share, stringResource(R.string.share), tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}
