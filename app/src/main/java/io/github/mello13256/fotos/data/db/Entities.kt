package io.github.mello13256.fotos.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.mello13256.fotos.core.model.MediaKind

/**
 * Item da biblioteca. O [id] é interno e estável; o [mediaStoreId] é o do sistema e pode mudar.
 * As coleções referenciam sempre o [id], nunca o ficheiro.
 */
@Entity(
    tableName = "media",
    indices = [
        Index("mediaStoreId"),
        Index("relativePath"),
        Index(value = ["missingSince", "dateTaken"]),
    ],
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val uri: String,
    val relativePath: String,
    val displayName: String,
    val bucketName: String,
    val size: Long,
    /** Data de captura em milissegundos (com recurso à data de modificação quando não existe). */
    val dateTaken: Long,
    val dateModified: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val kind: Int,
    /** Preenchido quando o ficheiro deixa de aparecer no MediaStore; limpo se voltar. */
    val missingSince: Long? = null,
)

val MediaEntity.mediaKind: MediaKind get() = MediaKind.fromCode(kind)

/** Coleção virtual: um agrupamento de referências, sem copiar nem mover ficheiros. */
@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val isFavorites: Boolean = false,
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class CollectionItemEntity(
    val collectionId: Long,
    val mediaId: Long,
    val addedAt: Long,
)

/** Projeção mínima usada na reconciliação com o MediaStore. */
data class IndexRow(
    val id: Long,
    val mediaStoreId: Long,
    val relativePath: String,
    val displayName: String,
    val size: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val missingSince: Long?,
)

data class FolderRow(
    val relativePath: String,
    val bucketName: String,
    val count: Int,
    val latest: Long,
    val coverUri: String,
)

data class CollectionSummary(
    val id: Long,
    val name: String,
    val isFavorites: Boolean,
    val count: Int,
    val coverUri: String?,
)
