package io.github.mello13256.fotos.ui.photos

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mello13256.fotos.R
import io.github.mello13256.fotos.data.LibraryFilter
import io.github.mello13256.fotos.data.Memory
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.ui.common.appContainer
import io.github.mello13256.fotos.ui.common.gridViewModel
import io.github.mello13256.fotos.ui.grid.GridScreen
import io.github.mello13256.fotos.ui.permission.MediaAccess
import io.github.mello13256.fotos.ui.permission.MediaPermission

@Composable
fun PhotosScreen(
    access: MediaAccess,
    onAccessChanged: () -> Unit,
    onOpen: (MediaSource, Long) -> Unit,
    onOpenSettings: () -> Unit,
    focusId: Long? = null,
    onFocusConsumed: () -> Unit = {},
) {
    val container = appContainer()
    val viewModel = gridViewModel(MediaSource.Library(), key = "library")
    val source by viewModel.source.collectAsStateWithLifecycle()
    val filter = (source as? MediaSource.Library)?.filter ?: LibraryFilter.ALL
    val libraryCount by remember(container) { container.media.count() }.collectAsStateWithLifecycle(0)
    val memories by produceState(emptyList<Memory>(), libraryCount) {
        value = runCatching { container.media.memories() }.getOrDefault(emptyList())
    }
    val showMemories = memories.isNotEmpty() && (filter == LibraryFilter.ALL || filter == LibraryFilter.PHOTOS)
    val memoriesContent: (@Composable () -> Unit)? =
        if (showMemories) ({ MemoriesRow(memories) { onOpen(it.source, it.photos.first().id) } }) else null

    GridScreen(
        viewModel = viewModel,
        title = stringResource(R.string.tab_photos),
        onOpen = onOpen,
        focusId = focusId,
        onFocusConsumed = onFocusConsumed,
        leading = memoriesContent,

        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.settings))
            }
        },
        header = {
            Column {
                if (access == MediaAccess.PARTIAL) PartialAccessBanner(onAccessChanged)
                FilterRow(filter) { viewModel.setSource(MediaSource.Library(it)) }
            }
        },
    )
}

@Composable
private fun FilterRow(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label)) },
            )
        }
    }
}

private val LibraryFilter.label: Int
    get() = when (this) {
        LibraryFilter.ALL -> R.string.filter_all
        LibraryFilter.PHOTOS -> R.string.filter_photos
        LibraryFilter.VIDEOS -> R.string.filter_videos
        LibraryFilter.SCREENSHOTS -> R.string.filter_screenshots
    }

@Composable
private fun PartialAccessBanner(onAccessChanged: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        onAccessChanged()
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(stringResource(R.string.partial_access_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.partial_access_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            TextButton(onClick = { launcher.launch(MediaPermission.requested) }) {
                Text(stringResource(R.string.partial_access_action))
            }
        }
    }
}
