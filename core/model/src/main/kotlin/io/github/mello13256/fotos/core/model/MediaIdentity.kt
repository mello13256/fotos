package io.github.mello13256.fotos.core.model

/**
 * Atributos de um ficheiro usados para o reconhecer ao longo do tempo.
 *
 * O `_ID` do MediaStore não é estável: muda após uma reindexação, um restauro de cópia de
 * segurança ou a troca de cartão SD. Por isso a identidade de cada item da biblioteca é
 * reconstruída a partir destes atributos sempre que o MediaStore muda.
 */
data class MediaIdentity(
    val mediaStoreId: Long,
    val relativePath: String,
    val displayName: String,
    val size: Long,
    val dateTaken: Long,
) {
    /** Mesmo ficheiro no mesmo sítio, com o mesmo conteúdo (aproximado pelo tamanho). */
    val fingerprint: String get() = "$relativePath$displayName|$size"

    /** Mesmo ficheiro noutra pasta: nome, tamanho e data de captura coincidem. */
    internal val relocationKey: RelocationKey get() = RelocationKey(displayName, size, dateTaken)

    internal fun sameLocation(other: MediaIdentity): Boolean =
        relativePath == other.relativePath && displayName == other.displayName

    internal fun sameContent(other: MediaIdentity): Boolean =
        size == other.size && dateTaken == other.dateTaken
}

internal data class RelocationKey(val displayName: String, val size: Long, val dateTaken: Long)

/** Item já presente no índice local, com o seu identificador interno (estável). */
data class IndexedMedia(val id: Long, val identity: MediaIdentity)
