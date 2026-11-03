package io.github.mello13256.fotos.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.util.Size
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cores extraídas das fotografias, para o fundo do visualizador e para o tema. */
object PhotoColors {
    private val cache = LruCache<String, Palette>(64)

    suspend fun palette(resolver: ContentResolver, uri: Uri): Palette? {
        cache.get(uri.toString())?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val thumbnail = resolver.loadThumbnail(uri, Size(SAMPLE_PX, SAMPLE_PX), null)
                val bitmap = if (thumbnail.config == Bitmap.Config.HARDWARE) {
                    thumbnail.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    thumbnail
                }
                Palette.from(bitmap).maximumColorCount(16).generate()
            }.getOrNull()?.also { cache.put(uri.toString(), it) }
        }
    }

    /** Cor viva da fotografia, usada como semente do tema. */
    fun seed(palette: Palette): Int? =
        (palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch)?.rgb

    /** Cor escura e suave, usada como luz ambiente atrás da fotografia. */
    fun ambient(palette: Palette): Int? =
        (palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch)?.rgb

    private const val SAMPLE_PX = 160
}
