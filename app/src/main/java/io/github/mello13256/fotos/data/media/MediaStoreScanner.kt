package io.github.mello13256.fotos.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import io.github.mello13256.fotos.core.model.MediaIdentity
import io.github.mello13256.fotos.core.model.MediaKind
import io.github.mello13256.fotos.data.db.MediaEntity

/** Um item tal como o MediaStore o descreve neste momento. */
data class ScannedMedia(
    val identity: MediaIdentity,
    val uri: String,
    val bucketName: String,
    val dateModified: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val kind: MediaKind,
) {
    fun toEntity(id: Long = 0, missingSince: Long? = null) = MediaEntity(
        id = id,
        mediaStoreId = identity.mediaStoreId,
        uri = uri,
        relativePath = identity.relativePath,
        displayName = identity.displayName,
        bucketName = bucketName,
        size = identity.size,
        dateTaken = identity.dateTaken,
        dateModified = dateModified,
        mimeType = mimeType,
        width = width,
        height = height,
        durationMs = durationMs,
        kind = kind.code,
        missingSince = missingSince,
    )
}

/** Lê todas as fotografias e vídeos visíveis para a app, em todos os volumes externos. */
class MediaStoreScanner(private val resolver: ContentResolver) {

    fun scan(): List<ScannedMedia> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.VOLUME_NAME,
            FileColumns.RELATIVE_PATH,
            FileColumns.DISPLAY_NAME,
            FileColumns.BUCKET_DISPLAY_NAME,
            FileColumns.SIZE,
            FileColumns.DATE_TAKEN,
            FileColumns.DATE_MODIFIED,
            FileColumns.DATE_ADDED,
            FileColumns.MIME_TYPE,
            FileColumns.WIDTH,
            FileColumns.HEIGHT,
            FileColumns.DURATION,
        )
        val selection = "${FileColumns.MEDIA_TYPE} IN (?, ?)"
        val args = arrayOf(
            FileColumns.MEDIA_TYPE_IMAGE.toString(),
            FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )

        val result = ArrayList<ScannedMedia>()
        resolver.query(collection, projection, selection, args, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(FileColumns._ID)
            val typeCol = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
            val volumeCol = c.getColumnIndexOrThrow(FileColumns.VOLUME_NAME)
            val pathCol = c.getColumnIndexOrThrow(FileColumns.RELATIVE_PATH)
            val nameCol = c.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
            val bucketCol = c.getColumnIndexOrThrow(FileColumns.BUCKET_DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(FileColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(FileColumns.DATE_TAKEN)
            val modifiedCol = c.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
            val addedCol = c.getColumnIndexOrThrow(FileColumns.DATE_ADDED)
            val mimeCol = c.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
            val widthCol = c.getColumnIndexOrThrow(FileColumns.WIDTH)
            val heightCol = c.getColumnIndexOrThrow(FileColumns.HEIGHT)
            val durationCol = c.getColumnIndexOrThrow(FileColumns.DURATION)

            result.ensureCapacity(c.count)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val isVideo = c.getInt(typeCol) == FileColumns.MEDIA_TYPE_VIDEO
                val volume = c.getString(volumeCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val base = if (isVideo) {
                    MediaStore.Video.Media.getContentUri(volume)
                } else {
                    MediaStore.Images.Media.getContentUri(volume)
                }
                val relativePath = c.getString(pathCol).orEmpty()
                val name = c.getString(nameCol).orEmpty()
                val mime = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*"
                val modifiedSeconds = c.getLong(modifiedCol)
                val dateTaken = c.getLong(takenCol).takeIf { it > 0 }
                    ?: (modifiedSeconds.takeIf { it > 0 } ?: c.getLong(addedCol)) * 1000

                result += ScannedMedia(
                    identity = MediaIdentity(
                        mediaStoreId = id,
                        relativePath = relativePath,
                        displayName = name,
                        size = c.getLong(sizeCol),
                        dateTaken = dateTaken,
                    ),
                    uri = ContentUris.withAppendedId(base, id).toString(),
                    bucketName = c.getString(bucketCol) ?: relativePath.trimEnd('/').substringAfterLast('/'),
                    dateModified = modifiedSeconds * 1000,
                    mimeType = mime,
                    width = c.getInt(widthCol),
                    height = c.getInt(heightCol),
                    durationMs = c.getLong(durationCol),
                    kind = MediaKind.classify(relativePath, name, mime),
                )
            }
        }
        return result
    }
}
