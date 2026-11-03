package io.github.mello13256.fotos.ui.viewer

import android.content.IntentSender
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mello13256.fotos.AppContainer
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.data.db.MediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ViewerViewModel(
    val source: MediaSource,
    private val container: AppContainer,
) : ViewModel() {

    /** Identificadores pela ordem da grelha de onde se veio; `null` enquanto carrega. */
    private val _ids = MutableStateFlow<List<Long>?>(null)
    val ids: StateFlow<List<Long>?> = _ids.asStateFlow()

    init {
        viewModelScope.launch { _ids.value = container.media.ids(source) }
    }

    fun observe(id: Long): Flow<MediaEntity?> = container.media.observe(id)

    fun isFavorite(id: Long): Flow<Boolean> = container.collections.isFavorite(id)

    fun setFavorite(id: Long, favorite: Boolean) {
        viewModelScope.launch { container.collections.setFavorite(id, favorite) }
    }

    fun addTo(collectionId: Long, id: Long) {
        viewModelScope.launch { container.collections.add(collectionId, listOf(id)) }
    }

    fun createCollectionWith(name: String, id: Long) {
        viewModelScope.launch {
            val collectionId = container.collections.create(name)
            container.collections.add(collectionId, listOf(id))
        }
    }

    fun trashRequest(media: MediaEntity): IntentSender =
        container.trash.moveToTrashRequest(listOf(media.uri.toUri()))

    fun onTrashed(id: Long) {
        _ids.value = _ids.value?.minus(id)
        container.mediaSync.requestSync()
    }
}
