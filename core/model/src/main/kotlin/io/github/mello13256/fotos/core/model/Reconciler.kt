package io.github.mello13256.fotos.core.model

/** Porque é que um item do MediaStore foi associado a um item já indexado. */
enum class MatchReason {
    /** Mesmo `_ID`, mesmo sítio, mesmo tamanho. */
    UNCHANGED,

    /** O MediaStore atribuiu um novo `_ID` ao mesmo ficheiro (reindexação, restauro). */
    NEW_MEDIA_STORE_ID,

    /** Mesmo `_ID`: o ficheiro foi editado no sítio, renomeado ou movido pelo MediaStore. */
    CHANGED_IN_PLACE,

    /** Novo `_ID` noutra pasta, com o mesmo nome, tamanho e data: movido por outra app. */
    MOVED,
}

data class Match<T>(val indexedId: Long, val scanned: T, val reason: MatchReason)

data class ReconcileResult<T>(
    /** Itens do MediaStore que correspondem a itens já indexados. */
    val matched: List<Match<T>>,
    /** Itens do MediaStore sem correspondência: novos na biblioteca. */
    val added: List<T>,
    /** Identificadores internos que já não existem no MediaStore. */
    val missing: List<Long>,
)

/**
 * Associa o resultado de uma leitura do MediaStore ao índice local, preservando os
 * identificadores internos (e, com eles, as coleções virtuais, etiquetas e favoritos).
 *
 * As passagens vão da correspondência mais segura para a menos segura; cada item indexado é
 * usado no máximo uma vez.
 */
object Reconciler {

    fun <T> reconcile(
        indexed: List<IndexedMedia>,
        scanned: List<T>,
        identityOf: (T) -> MediaIdentity,
    ): ReconcileResult<T> {
        val unmatched = LinkedHashMap<Long, MediaIdentity>(indexed.size * 2)
        val byMediaStoreId = HashMap<Long, MutableList<Long>>()
        val byFingerprint = HashMap<String, MutableList<Long>>()
        val byRelocationKey = HashMap<RelocationKey, MutableList<Long>>()

        for (item in indexed) {
            val identity = item.identity
            unmatched[item.id] = identity
            byMediaStoreId.getOrPut(identity.mediaStoreId) { ArrayList(1) }.add(item.id)
            byFingerprint.getOrPut(identity.fingerprint) { ArrayList(1) }.add(item.id)
            byRelocationKey.getOrPut(identity.relocationKey) { ArrayList(1) }.add(item.id)
        }

        val matched = ArrayList<Match<T>>(scanned.size)
        var pending: List<T> = scanned

        fun firstUnmatched(candidates: List<Long>?, accept: (MediaIdentity) -> Boolean): Long? =
            candidates?.firstOrNull { id -> unmatched[id]?.let(accept) == true }

        fun pass(reason: MatchReason, find: (MediaIdentity) -> Long?) {
            if (unmatched.isEmpty()) return
            val remaining = ArrayList<T>()
            for (item in pending) {
                val id = find(identityOf(item))
                if (id == null) {
                    remaining += item
                } else {
                    unmatched.remove(id)
                    matched += Match(id, item, reason)
                }
            }
            pending = remaining
        }

        pass(MatchReason.UNCHANGED) { s ->
            firstUnmatched(byMediaStoreId[s.mediaStoreId]) { it.fingerprint == s.fingerprint }
        }
        pass(MatchReason.NEW_MEDIA_STORE_ID) { s ->
            firstUnmatched(byFingerprint[s.fingerprint]) { true }
        }
        pass(MatchReason.CHANGED_IN_PLACE) { s ->
            // Só uma das duas coisas pode ter mudado: o sítio (renomeado/movido) ou o conteúdo
            // (editado). Se mudaram ambas, o `_ID` foi provavelmente reutilizado por outro ficheiro.
            firstUnmatched(byMediaStoreId[s.mediaStoreId]) { it.sameLocation(s) || it.sameContent(s) }
        }
        pass(MatchReason.MOVED) { s ->
            firstUnmatched(byRelocationKey[s.relocationKey]) { true }
        }

        return ReconcileResult(matched = matched, added = pending, missing = unmatched.keys.toList())
    }
}
