package io.github.mello13256.fotos.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReconcilerTest {

    private fun identity(
        storeId: Long,
        path: String = "DCIM/Camera/",
        name: String = "IMG_$storeId.jpg",
        size: Long = 1_000 + storeId,
        date: Long = 1_700_000_000_000 + storeId,
    ) = MediaIdentity(storeId, path, name, size, date)

    private fun reconcile(indexed: Map<Long, MediaIdentity>, scanned: List<MediaIdentity>) =
        Reconciler.reconcile(indexed.map { (id, identity) -> IndexedMedia(id, identity) }, scanned) { it }

    private fun ReconcileResult<MediaIdentity>.matchOf(indexedId: Long) =
        matched.single { it.indexedId == indexedId }

    @Test
    fun `biblioteca sem alterações mantém todos os itens`() {
        val indexed = (1L..5L).associateWith { identity(storeId = it + 100) }
        val result = reconcile(indexed, indexed.values.toList())

        assertEquals(5, result.matched.size)
        assertTrue(result.matched.all { it.reason == MatchReason.UNCHANGED })
        assertTrue(result.added.isEmpty())
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `reindexação do MediaStore com novos IDs preserva os identificadores internos`() {
        val a = identity(storeId = 10)
        val b = identity(storeId = 11)
        // Após a reindexação, os IDs trocaram: o ficheiro de B ficou com o antigo ID de A.
        val result = reconcile(
            mapOf(1L to a, 2L to b),
            listOf(b.copy(mediaStoreId = 10), a.copy(mediaStoreId = 57)),
        )

        assertEquals(57, result.matchOf(1).scanned.mediaStoreId)
        assertEquals(10, result.matchOf(2).scanned.mediaStoreId)
        assertTrue(result.matched.all { it.reason == MatchReason.NEW_MEDIA_STORE_ID })
        assertTrue(result.added.isEmpty())
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `fotografia editada no sítio continua a ser o mesmo item`() {
        val original = identity(storeId = 10)
        val edited = original.copy(size = original.size + 4_096)
        val result = reconcile(mapOf(1L to original), listOf(edited))

        assertEquals(MatchReason.CHANGED_IN_PLACE, result.matchOf(1).reason)
        assertTrue(result.added.isEmpty())
    }

    @Test
    fun `fotografia renomeada ou movida pelo MediaStore mantém o ID e continua a ser o mesmo item`() {
        val original = identity(storeId = 10)
        val renamed = original.copy(displayName = "Praia.jpg")
        val moved = identity(storeId = 11).let { it to it.copy(relativePath = "Pictures/Viagens/") }

        val result = reconcile(mapOf(1L to original, 2L to moved.first), listOf(renamed, moved.second))

        assertEquals(MatchReason.CHANGED_IN_PLACE, result.matchOf(1).reason)
        assertEquals(MatchReason.CHANGED_IN_PLACE, result.matchOf(2).reason)
    }

    @Test
    fun `fotografia movida por outra app com novo ID é reconhecida`() {
        val original = identity(storeId = 10)
        val moved = original.copy(mediaStoreId = 99, relativePath = "Pictures/Viagens/")
        val result = reconcile(mapOf(1L to original), listOf(moved))

        assertEquals(MatchReason.MOVED, result.matchOf(1).reason)
        assertTrue(result.added.isEmpty())
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun `ID reutilizado por um ficheiro diferente não herda as coleções do anterior`() {
        val deleted = identity(storeId = 10)
        val unrelated = identity(storeId = 10, path = "Download/", name = "fatura.png", size = 5, date = 1)
        val result = reconcile(mapOf(1L to deleted), listOf(unrelated))

        assertTrue(result.matched.isEmpty())
        assertEquals(listOf(unrelated), result.added)
        assertEquals(listOf(1L), result.missing)
    }

    @Test
    fun `cópias idênticas em pastas diferentes não se confundem`() {
        val camera = identity(storeId = 10, path = "DCIM/Camera/", name = "IMG.jpg", size = 500, date = 7)
        val whatsapp = identity(storeId = 11, path = "Pictures/WhatsApp/", name = "IMG.jpg", size = 500, date = 7)
        val result = reconcile(mapOf(1L to camera, 2L to whatsapp), listOf(whatsapp, camera))

        assertEquals(camera, result.matchOf(1).scanned)
        assertEquals(whatsapp, result.matchOf(2).scanned)
        assertTrue(result.matched.all { it.reason == MatchReason.UNCHANGED })
    }

    @Test
    fun `itens novos e eliminados são separados`() {
        val kept = identity(storeId = 10)
        val deleted = identity(storeId = 11)
        val added = identity(storeId = 12)
        val result = reconcile(mapOf(1L to kept, 2L to deleted), listOf(kept, added))

        assertEquals(listOf(1L), result.matched.map { it.indexedId })
        assertEquals(listOf(added), result.added)
        assertEquals(listOf(2L), result.missing)
    }

    @Test
    fun `cada item indexado é usado no máximo uma vez`() {
        val original = identity(storeId = 10)
        val result = reconcile(mapOf(1L to original), listOf(original, original.copy(mediaStoreId = 20)))

        assertEquals(1, result.matched.size)
        assertEquals(1, result.added.size)
    }

    @Test
    fun `índice vazio trata tudo como novo`() {
        val scanned = (1L..3L).map { identity(storeId = it) }
        val result = reconcile(emptyMap(), scanned)

        assertTrue(result.matched.isEmpty())
        assertEquals(scanned, result.added)
        assertTrue(result.missing.isEmpty())
    }
}
