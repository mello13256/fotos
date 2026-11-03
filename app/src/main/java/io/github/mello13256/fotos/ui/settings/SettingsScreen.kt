package io.github.mello13256.fotos.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.AppSettings
import io.github.mello13256.fotos.data.ColorSource
import io.github.mello13256.fotos.data.ThemeMode
import io.github.mello13256.fotos.ui.mascot.LumiPortrait
import io.github.mello13256.fotos.ui.common.appContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
    var canManageMedia by remember { mutableStateOf(container.trash.canManageWithoutConfirmation()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canManageMedia = container.trash.canManageWithoutConfirmation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_appearance))
            ThemeMode.entries.forEach { mode ->
                ListItem(
                    headlineContent = { Text(stringResource(mode.label)) },
                    leadingContent = { RadioButton(selected = settings.theme == mode, onClick = null) },
                    modifier = Modifier.selectable(
                        selected = settings.theme == mode,
                        role = Role.RadioButton,
                        onClick = { scope.launch { container.settings.setTheme(mode) } },
                    ),
                )
            }

            SectionTitle(stringResource(R.string.settings_colors))
            ColorSource.entries
                .filter { it != ColorSource.WALLPAPER || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
                .forEach { source ->
                    val available = source != ColorSource.PHOTO || settings.seedColor != null
                    val seed = settings.seedColor
                    val swatch: (@Composable () -> Unit)? =
                        if (source == ColorSource.PHOTO && seed != null) ({ ColorSwatch(Color(seed)) }) else null
                    ListItem(
                        headlineContent = { Text(stringResource(source.label)) },
                        supportingContent = { Text(stringResource(source.description)) },
                        leadingContent = {
                            RadioButton(selected = settings.colorSource == source, onClick = null, enabled = available)
                        },
                        trailingContent = swatch,
                        modifier = Modifier.selectable(
                            selected = settings.colorSource == source,
                            enabled = available,
                            role = Role.RadioButton,
                            onClick = { scope.launch { container.settings.setColorSource(source) } },
                        ),
                    )
                }

            SectionTitle(stringResource(R.string.settings_mascot))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_mascot_show)) },
                supportingContent = { Text(stringResource(R.string.settings_mascot_body)) },
                leadingContent = { LumiPortrait(size = 40.dp) },
                trailingContent = {
                    Switch(
                        checked = settings.mascotEnabled,
                        onCheckedChange = { scope.launch { container.settings.setMascotEnabled(it) } },
                    )
                },
            )

            SectionTitle(stringResource(R.string.settings_management))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_manage_media)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                if (canManageMedia) R.string.settings_manage_media_on else R.string.settings_manage_media_off,
                            ),
                        )
                    },
                    trailingContent = { Switch(checked = canManageMedia, onCheckedChange = null) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                )
            }

            SectionTitle(stringResource(R.string.settings_privacy))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_offline)) },
                supportingContent = { Text(stringResource(R.string.settings_offline_body)) },
                leadingContent = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

private val ColorSource.label: Int
    get() = when (this) {
        ColorSource.WALLPAPER -> R.string.colors_wallpaper
        ColorSource.PHOTO -> R.string.colors_photo
        ColorSource.DEFAULT -> R.string.colors_default
    }

private val ColorSource.description: Int
    get() = when (this) {
        ColorSource.WALLPAPER -> R.string.colors_wallpaper_body
        ColorSource.PHOTO -> R.string.colors_photo_body
        ColorSource.DEFAULT -> R.string.colors_default_body
    }

private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }
