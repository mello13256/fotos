package io.github.mello13256.fotos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {

    @Query(
        """SELECT c.id, c.name, c.isFavorites, COUNT(m.id) AS count,
             (SELECT m2.uri FROM collection_items ci2
                INNER JOIN media m2 ON m2.id = ci2.mediaId
                WHERE ci2.collectionId = c.id AND m2.missingSince IS NULL
                ORDER BY m2.dateTaken DESC LIMIT 1) AS coverUri
           FROM collections c
           LEFT JOIN collection_items ci ON ci.collectionId = c.id
           LEFT JOIN media m ON m.id = ci.mediaId AND m.missingSince IS NULL
           GROUP BY c.id
           ORDER BY c.isFavorites DESC, c.name COLLATE NOCASE""",
    )
    fun observeSummaries(): Flow<List<CollectionSummary>>

    @Query("SELECT * FROM collections WHERE id = :id")
    fun observe(id: Long): Flow<CollectionEntity?>

    @Query(
        """INSERT OR IGNORE INTO collections (id, name, createdAt, isFavorites)
           VALUES (:id, :name, :now, 1)""",
    )
    suspend fun ensureFavorites(id: Long, name: String, now: Long)

    @Insert
    suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM collections WHERE id = :id AND isFavorites = 0")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItems(items: List<CollectionItemEntity>)

    @Query("DELETE FROM collection_items WHERE collectionId = :collectionId AND mediaId IN (:mediaIds)")
    suspend fun removeItems(collectionId: Long, mediaIds: List<Long>)

    @Query(
        """SELECT EXISTS(SELECT 1 FROM collection_items
           WHERE collectionId = :collectionId AND mediaId = :mediaId)""",
    )
    fun observeContains(collectionId: Long, mediaId: Long): Flow<Boolean>
}
