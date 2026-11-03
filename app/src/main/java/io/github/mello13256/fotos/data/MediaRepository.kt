package io.github.mello13256.fotos.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import io.github.mello13256.fotos.data.db.FolderRow
import io.github.mello13256.fotos.data.db.FotosDatabase
import io.github.mello13256.fotos.data.db.MediaEntity
import kotlinx.coroutines.flow.Flow

class MediaRepository(database: FotosDatabase) {
    private val dao = database.mediaDao()

    fun pager(source: MediaSource): Flow<PagingData<MediaEntity>> =
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false)) {
            when (source) {
                is MediaSource.Library -> dao.pageLibrary(source.filter.kinds.map { it.code })
                is MediaSource.Collection -> dao.pageCollection(source.id)
                is MediaSource.Folder -> dao.pageFolder(source.relativePath)
            }
        }.flow

    suspend fun ids(source: MediaSource): List<Long> = when (source) {
        is MediaSource.Library -> dao.idsLibrary(source.filter.kinds.map { it.code })
        is MediaSource.Collection -> dao.idsCollection(source.id)
        is MediaSource.Folder -> dao.idsFolder(source.relativePath)
    }

    fun observe(id: Long): Flow<MediaEntity?> = dao.observe(id)

    /** Itens pela ordem dos identificadores pedidos. */
    suspend fun byIds(ids: Collection<Long>): List<MediaEntity> {
        val found = ids.chunked(SQL_BATCH).flatMap { dao.byIds(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    fun folders(): Flow<List<FolderRow>> = dao.observeFolders()

    fun count(): Flow<Int> = dao.observeCount()

    private companion object {
        const val PAGE_SIZE = 150
        const val SQL_BATCH = 500
    }
}
