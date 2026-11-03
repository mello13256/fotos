package io.github.mello13256.fotos.data

import io.github.mello13256.fotos.data.db.CollectionEntity
import io.github.mello13256.fotos.data.db.CollectionItemEntity
import io.github.mello13256.fotos.data.db.CollectionSummary
import io.github.mello13256.fotos.data.db.FotosDatabase
import kotlinx.coroutines.flow.Flow

/** Coleções virtuais: um item pode estar em várias sem que o ficheiro seja copiado ou movido. */
class CollectionRepository(database: FotosDatabase) {
    private val dao = database.collectionDao()

    val summaries: Flow<List<CollectionSummary>> = dao.observeSummaries()

    fun observe(id: Long): Flow<CollectionEntity?> = dao.observe(id)

    suspend fun ensureFavorites(name: String) =
        dao.ensureFavorites(FAVORITES_ID, name, System.currentTimeMillis())

    suspend fun create(name: String): Long =
        dao.insert(CollectionEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun rename(id: Long, name: String) = dao.rename(id, name.trim())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun add(collectionId: Long, mediaIds: Collection<Long>) {
        val now = System.currentTimeMillis()
        mediaIds.chunked(BATCH).forEach { batch ->
            dao.addItems(batch.map { CollectionItemEntity(collectionId, it, now) })
        }
    }

    suspend fun remove(collectionId: Long, mediaIds: Collection<Long>) {
        mediaIds.chunked(BATCH).forEach { dao.removeItems(collectionId, it) }
    }

    fun isFavorite(mediaId: Long): Flow<Boolean> = dao.observeContains(FAVORITES_ID, mediaId)

    suspend fun setFavorite(mediaId: Long, favorite: Boolean) {
        if (favorite) add(FAVORITES_ID, listOf(mediaId)) else remove(FAVORITES_ID, listOf(mediaId))
    }

    companion object {
        const val FAVORITES_ID = 1L
        private const val BATCH = 500
    }
}
