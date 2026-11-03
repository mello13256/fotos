package io.github.mello13256.fotos.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import io.github.mello13256.fotos.data.db.CollectionSummary
import io.github.mello13256.fotos.data.db.FolderRow
import io.github.mello13256.fotos.data.db.FotosDatabase
import io.github.mello13256.fotos.data.db.MediaEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import java.time.LocalDate
import java.time.ZoneId

/** Pasta do dispositivo com as capas para o mosaico (até 4, da mais recente para a mais antiga). */
data class FolderTile(val folder: FolderRow, val covers: List<String>)

/** Coleção com as capas para o mosaico. */
data class CollectionTile(val collection: CollectionSummary, val covers: List<String>)

/** Fotografias tiradas por esta altura do ano, há [yearsAgo] anos. */
data class Memory(
    val yearsAgo: Int,
    val date: LocalDate,
    val source: MediaSource.Period,
    val photos: List<MediaEntity>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class MediaRepository(database: FotosDatabase) {
    private val dao = database.mediaDao()

    fun pager(source: MediaSource): Flow<PagingData<MediaEntity>> =
        Pager(
            PagingConfig(
                pageSize = PAGE_SIZE,
                prefetchDistance = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE * 2,
                // Os marcadores de posição permitem saltar para qualquer ponto da biblioteca
                // com a barra de deslocamento rápido, sem carregar tudo o que está pelo meio.
                enablePlaceholders = true,
                jumpThreshold = PAGE_SIZE * 3,
            ),
        ) {
            when (source) {
                is MediaSource.Library -> dao.pageLibrary(source.filter.kinds.map { it.code })
                is MediaSource.Collection -> dao.pageCollection(source.id)
                is MediaSource.Folder -> dao.pageFolder(source.relativePath)
                is MediaSource.Period -> dao.pagePeriod(source.start, source.end)
            }
        }.flow

    suspend fun ids(source: MediaSource): List<Long> = when (source) {
        is MediaSource.Library -> dao.idsLibrary(source.filter.kinds.map { it.code })
        is MediaSource.Collection -> dao.idsCollection(source.id)
        is MediaSource.Folder -> dao.idsFolder(source.relativePath)
        is MediaSource.Period -> dao.idsPeriod(source.start, source.end)
    }

    fun observe(id: Long): Flow<MediaEntity?> = dao.observe(id)

    /** Itens pela ordem dos identificadores pedidos. */
    suspend fun byIds(ids: Collection<Long>): List<MediaEntity> {
        val found = ids.chunked(SQL_BATCH).flatMap { dao.byIds(it) }.associateBy { it.id }
        return ids.mapNotNull { found[it] }
    }

    fun folders(): Flow<List<FolderRow>> = dao.observeFolders()

    fun folderTiles(): Flow<List<FolderTile>> = dao.observeFolders().mapLatest { folders ->
        folders.map { FolderTile(it, dao.folderCoverUris(it.relativePath, MOSAIC)) }
    }

    fun collectionTiles(summaries: Flow<List<CollectionSummary>>): Flow<List<CollectionTile>> =
        summaries.mapLatest { collections ->
            collections.map { CollectionTile(it, dao.collectionCoverUris(it.id, MOSAIC)) }
        }

    fun count(): Flow<Int> = dao.observeCount()

    /**
     * Memórias: para cada ano anterior, as fotografias tiradas numa janela de uma semana à volta
     * do dia de hoje. Anos sem fotografias são omitidos.
     */
    suspend fun memories(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): List<Memory> =
        (1..MEMORY_YEARS).mapNotNull { yearsAgo ->
            val date = today.minusYears(yearsAgo.toLong())
            val start = date.minusDays(MEMORY_WINDOW_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = date.plusDays(MEMORY_WINDOW_DAYS + 1).atStartOfDay(zone).toInstant().toEpochMilli()
            val photos = dao.photosInPeriod(start, end, MEMORY_PHOTOS)
            if (photos.isEmpty()) null else Memory(yearsAgo, date, MediaSource.Period(start, end), photos)
        }

    private companion object {
        const val PAGE_SIZE = 150
        const val SQL_BATCH = 500
        const val MOSAIC = 4
        const val MEMORY_YEARS = 30
        const val MEMORY_WINDOW_DAYS = 3L
        const val MEMORY_PHOTOS = 6
    }
}
