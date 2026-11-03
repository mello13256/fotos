package io.github.mello13256.fotos.ui.grid

import android.content.IntentSender
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import io.github.mello13256.fotos.AppContainer
import io.github.mello13256.fotos.data.MediaSource
import io.github.mello13256.fotos.data.db.MediaEntity
import io.github.mello13256.fotos.ui.common.MonthFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Elemento da grelha: um cabeçalho de mês ou um item. */
sealed interface GridItem {
    val key: Any

    data class Header(val monthKey: Int, val label: String) : GridItem {
        override val key: Any get() = "month-$monthKey"
    }

    data class Media(val entity: MediaEntity) : GridItem {
        override val key: Any get() = entity.id
    }
}

class GridViewModel(
    initialSource: MediaSource,
    private val container: AppContainer,
) : ViewModel() {

    private val _source = MutableStateFlow(initialSource)
    val source: StateFlow<MediaSource> = _source.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<GridItem>> = _source
        .flatMapLatest { container.media.pager(it) }
        .map { paging ->
            paging
                .map { entity -> val item: GridItem = GridItem.Media(entity); item }
                .insertSeparators { before: GridItem?, after: GridItem? ->
                    val next = (after as? GridItem.Media)?.entity ?: return@insertSeparators null
                    val previous = (before as? GridItem.Media)?.entity
                    val month = MonthFormat.monthKey(next.dateTaken)
                    if (previous == null || MonthFormat.monthKey(previous.dateTaken) != month) {
                        GridItem.Header(month, MonthFormat.label(next.dateTaken))
                    } else {
                        null
                    }
                }
        }
        .cachedIn(viewModelScope)

    fun setSource(source: MediaSource) {
        if (_source.value != source) {
            _source.value = source
            clearSelection()
        }
    }

    fun setSelection(ids: Set<Long>) {
        _selection.value = ids
    }

    fun toggle(id: Long) {
        _selection.value = _selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    fun selectAll() {
        viewModelScope.launch { _selection.value = container.media.ids(_source.value).toSet() }
    }

    suspend fun selectedItems(): List<MediaEntity> = container.media.byIds(_selection.value)

    fun addSelectionTo(collectionId: Long) {
        val ids = _selection.value
        viewModelScope.launch {
            container.collections.add(collectionId, ids)
            clearSelection()
        }
    }

    fun createCollectionWithSelection(name: String) {
        val ids = _selection.value
        viewModelScope.launch {
            val id = container.collections.create(name)
            container.collections.add(id, ids)
            clearSelection()
        }
    }

    fun removeSelectionFromCollection() {
        val collection = _source.value as? MediaSource.Collection ?: return
        val ids = _selection.value
        viewModelScope.launch {
            container.collections.remove(collection.id, ids)
            clearSelection()
        }
    }

    suspend fun trashRequestForSelection(): IntentSender? {
        val uris = selectedItems().map { it.uri.toUri() }
        return if (uris.isEmpty()) null else container.trash.moveToTrashRequest(uris)
    }

    fun onTrashed() {
        clearSelection()
        container.mediaSync.requestSync()
    }
}
