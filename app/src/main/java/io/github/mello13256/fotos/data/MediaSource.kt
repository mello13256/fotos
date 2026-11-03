package io.github.mello13256.fotos.data

import io.github.mello13256.fotos.core.model.MediaKind

/** Filtro da biblioteca: separa fotografias, vídeos e capturas de ecrã. */
enum class LibraryFilter(val kinds: List<MediaKind>) {
    ALL(MediaKind.entries),
    PHOTOS(listOf(MediaKind.PHOTO)),
    VIDEOS(listOf(MediaKind.VIDEO, MediaKind.SCREEN_RECORDING)),
    SCREENSHOTS(listOf(MediaKind.SCREENSHOT, MediaKind.SCREEN_RECORDING)),
}

/** Origem de uma lista de itens: a biblioteca, uma coleção virtual ou uma pasta do dispositivo. */
sealed interface MediaSource {
    data class Library(val filter: LibraryFilter = LibraryFilter.ALL) : MediaSource
    data class Collection(val id: Long) : MediaSource
    data class Folder(val relativePath: String) : MediaSource

    /** Representação em texto para passar entre ecrãs. */
    fun encode(): String = when (this) {
        is Library -> "lib:${filter.name}"
        is Collection -> "col:$id"
        is Folder -> "dir:$relativePath"
    }

    companion object {
        fun decode(value: String): MediaSource {
            val type = value.substringBefore(':')
            val arg = value.substringAfter(':', "")
            return when (type) {
                "col" -> arg.toLongOrNull()?.let(::Collection)
                "dir" -> Folder(arg)
                "lib" -> LibraryFilter.entries.firstOrNull { it.name == arg }?.let(::Library)
                else -> null
            } ?: Library()
        }
    }
}
