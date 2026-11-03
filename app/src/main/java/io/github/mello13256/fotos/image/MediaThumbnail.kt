package io.github.mello13256.fotos.image

import android.content.ContentResolver
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Miniatura de um item do MediaStore. Usa as miniaturas que o sistema já mantém em cache, o que
 * é muito mais rápido do que descodificar o ficheiro original, e funciona também para vídeos.
 */
data class MediaThumbnail(val uri: Uri, val sizePx: Int = GRID_SIZE_PX) {
    companion object {
        /** Tamanho único para a grelha e o visualizador, para partilharem a cache. */
        const val GRID_SIZE_PX = 384
    }
}

class MediaThumbnailFetcher(
    private val data: MediaThumbnail,
    private val resolver: ContentResolver,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bitmap = resolver.loadThumbnail(data.uri, Size(data.sizePx, data.sizePx), null)
        ImageFetchResult(
            image = bitmap.asImage(),
            // Marcado como não reduzido para o Coil reutilizar a mesma miniatura da cache em
            // qualquer tamanho: é ela que aparece primeiro no visualizador e na transição.
            isSampled = false,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<MediaThumbnail> {
        override fun create(data: MediaThumbnail, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbnailFetcher(data, resolver)
    }
}

class MediaThumbnailKeyer : Keyer<MediaThumbnail> {
    override fun key(data: MediaThumbnail, options: Options): String = "thumb:${data.uri}@${data.sizePx}"
}
