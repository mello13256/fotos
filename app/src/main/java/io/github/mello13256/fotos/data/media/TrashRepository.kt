package io.github.mello13256.fotos.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import androidx.core.os.bundleOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrashedItem(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    /** Momento (em milissegundos) em que o sistema elimina o item definitivamente. */
    val expiresAt: Long,
) {
    val isVideo: Boolean get() = mimeType.startsWith("video/")
}

/**
 * Reciclagem do sistema (Android 11+). Os itens ficam recuperáveis durante 30 dias e as
 * operações pedem confirmação ao utilizador, exceto se a app tiver a permissão MANAGE_MEDIA.
 */
class TrashRepository(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver

    fun moveToTrashRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, true).intentSender

    fun restoreRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createTrashRequest(resolver, uris, false).intentSender

    fun deleteForeverRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createDeleteRequest(resolver, uris).intentSender

    /** Verdadeiro se o utilizador permitiu gerir multimédia sem confirmação. */
    fun canManageWithoutConfirmation(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    suspend fun trashedItems(): List<TrashedItem> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            FileColumns._ID,
            FileColumns.MEDIA_TYPE,
            FileColumns.VOLUME_NAME,
            FileColumns.DISPLAY_NAME,
            FileColumns.MIME_TYPE,
            FileColumns.DATE_EXPIRES,
        )
        val args = bundleOf(
            ContentResolver.QUERY_ARG_SQL_SELECTION to "${FileColumns.MEDIA_TYPE} IN (?, ?)",
            ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to arrayOf(
                FileColumns.MEDIA_TYPE_IMAGE.toString(),
                FileColumns.MEDIA_TYPE_VIDEO.toString(),
            ),
            ContentResolver.QUERY_ARG_SQL_SORT_ORDER to "${FileColumns.DATE_EXPIRES} DESC",
            MediaStore.QUERY_ARG_MATCH_TRASHED to MediaStore.MATCH_ONLY,
        )
        val items = ArrayList<TrashedItem>()
        resolver.query(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), projection, args, null)
            ?.use { c ->
                val idCol = c.getColumnIndexOrThrow(FileColumns._ID)
                val typeCol = c.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
                val volumeCol = c.getColumnIndexOrThrow(FileColumns.VOLUME_NAME)
                val nameCol = c.getColumnIndexOrThrow(FileColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
                val expiresCol = c.getColumnIndexOrThrow(FileColumns.DATE_EXPIRES)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val isVideo = c.getInt(typeCol) == FileColumns.MEDIA_TYPE_VIDEO
                    val volume = c.getString(volumeCol) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                    val base = if (isVideo) {
                        MediaStore.Video.Media.getContentUri(volume)
                    } else {
                        MediaStore.Images.Media.getContentUri(volume)
                    }
                    items += TrashedItem(
                        uri = ContentUris.withAppendedId(base, id),
                        displayName = c.getString(nameCol).orEmpty(),
                        mimeType = c.getString(mimeCol) ?: if (isVideo) "video/*" else "image/*",
                        expiresAt = c.getLong(expiresCol) * 1000,
                    )
                }
            }
        items
    }
}
