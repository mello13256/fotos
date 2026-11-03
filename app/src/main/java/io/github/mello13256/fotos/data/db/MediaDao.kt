package io.github.mello13256.fotos.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query(
        """SELECT * FROM media WHERE missingSince IS NULL AND kind IN (:kinds)
           ORDER BY dateTaken DESC, id DESC""",
    )
    fun pageLibrary(kinds: List<Int>): PagingSource<Int, MediaEntity>

    @Query(
        """SELECT id FROM media WHERE missingSince IS NULL AND kind IN (:kinds)
           ORDER BY dateTaken DESC, id DESC""",
    )
    suspend fun idsLibrary(kinds: List<Int>): List<Long>

    @Query(
        """SELECT m.* FROM media m INNER JOIN collection_items ci ON ci.mediaId = m.id
           WHERE ci.collectionId = :collectionId AND m.missingSince IS NULL
           ORDER BY m.dateTaken DESC, m.id DESC""",
    )
    fun pageCollection(collectionId: Long): PagingSource<Int, MediaEntity>

    @Query(
        """SELECT m.id FROM media m INNER JOIN collection_items ci ON ci.mediaId = m.id
           WHERE ci.collectionId = :collectionId AND m.missingSince IS NULL
           ORDER BY m.dateTaken DESC, m.id DESC""",
    )
    suspend fun idsCollection(collectionId: Long): List<Long>

    @Query(
        """SELECT * FROM media WHERE missingSince IS NULL AND relativePath = :relativePath
           ORDER BY dateTaken DESC, id DESC""",
    )
    fun pageFolder(relativePath: String): PagingSource<Int, MediaEntity>

    @Query(
        """SELECT id FROM media WHERE missingSince IS NULL AND relativePath = :relativePath
           ORDER BY dateTaken DESC, id DESC""",
    )
    suspend fun idsFolder(relativePath: String): List<Long>

    @Query("SELECT * FROM media WHERE id = :id")
    fun observe(id: Long): Flow<MediaEntity?>

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE missingSince IS NULL")
    fun observeCount(): Flow<Int>

    /**
     * Pastas do dispositivo. Numa agregação com MAX(), o SQLite devolve as colunas simples da
     * linha que tem o valor máximo: a capa é o item mais recente da pasta.
     */
    @Query(
        """SELECT relativePath, bucketName, COUNT(*) AS count, MAX(dateTaken) AS latest, uri AS coverUri
           FROM media WHERE missingSince IS NULL
           GROUP BY relativePath ORDER BY latest DESC""",
    )
    fun observeFolders(): Flow<List<FolderRow>>

    @Query(
        """SELECT id, mediaStoreId, relativePath, displayName, size, dateTaken, dateModified, missingSince
           FROM media""",
    )
    suspend fun indexRows(): List<IndexRow>

    @Insert
    suspend fun insertAll(items: List<MediaEntity>)

    @Update
    suspend fun updateAll(items: List<MediaEntity>)

    @Query("UPDATE media SET missingSince = :now WHERE id IN (:ids) AND missingSince IS NULL")
    suspend fun markMissing(ids: List<Long>, now: Long)

    @Query("DELETE FROM media WHERE missingSince IS NOT NULL AND missingSince < :before")
    suspend fun purgeMissingBefore(before: Long)
}
