package io.github.mello13256.fotos.data.media

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.room.withTransaction
import io.github.mello13256.fotos.core.model.IndexedMedia
import io.github.mello13256.fotos.core.model.MediaIdentity
import io.github.mello13256.fotos.core.model.Reconciler
import io.github.mello13256.fotos.data.db.FotosDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Mantém o índice local alinhado com o MediaStore.
 *
 * Corre ao abrir a app e sempre que o MediaStore anuncia alterações (com um pequeno atraso para
 * agrupar rajadas de notificações). Itens que desaparecem não são apagados de imediato: ficam
 * marcados durante [MISSING_GRACE_DAYS] dias, para sobreviverem a um cartão SD retirado, a uma
 * passagem pela reciclagem ou a uma reindexação, sem perder as coleções.
 */
class MediaSync(
    private val resolver: ContentResolver,
    private val database: FotosDatabase,
    private val scope: CoroutineScope,
) {
    private val scanner = MediaStoreScanner(resolver)
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val _isSyncing = MutableStateFlow(false)
    private val _hasSynced = MutableStateFlow(false)
    private var started = false

    /** Verdadeiro enquanto uma sincronização está a decorrer. */
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    /** Verdadeiro depois da primeira sincronização desta sessão. */
    val hasSynced: StateFlow<Boolean> = _hasSynced.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            requestSync()
        }
    }

    /** Arranca a sincronização contínua. Só deve ser chamado com acesso às fotografias concedido. */
    fun start() {
        if (started) {
            requestSync()
            return
        }
        started = true
        resolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        scope.launch {
            for (request in requests) {
                delay(DEBOUNCE_MS)
                runCatching { syncNow() }
                    .onFailure { Log.w(TAG, "Falha a sincronizar com o MediaStore", it) }
            }
        }
        requestSync()
    }

    fun requestSync() {
        requests.trySend(Unit)
    }

    private suspend fun syncNow() = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            val scanned = scanner.scan()
            val dao = database.mediaDao()
            val existing = dao.indexRows()

            // Uma leitura vazia com um índice cheio indica, quase sempre, falta de acesso
            // (permissão retirada, armazenamento indisponível), não uma biblioteca apagada.
            if (scanned.isEmpty() && existing.isNotEmpty()) return@withContext

            val result = Reconciler.reconcile(
                indexed = existing.map {
                    IndexedMedia(
                        id = it.id,
                        identity = MediaIdentity(it.mediaStoreId, it.relativePath, it.displayName, it.size, it.dateTaken),
                    )
                },
                scanned = scanned,
                identityOf = { it.identity },
            )

            val existingById = existing.associateBy { it.id }
            val updates = result.matched.mapNotNull { match ->
                val old = existingById.getValue(match.indexedId)
                val new = match.scanned
                val changed = old.missingSince != null ||
                    old.mediaStoreId != new.identity.mediaStoreId ||
                    old.relativePath != new.identity.relativePath ||
                    old.displayName != new.identity.displayName ||
                    old.size != new.identity.size ||
                    old.dateTaken != new.identity.dateTaken ||
                    old.dateModified != new.dateModified
                if (changed) new.toEntity(id = match.indexedId) else null
            }
            val now = System.currentTimeMillis()

            database.withTransaction {
                updates.chunked(BATCH).forEach { dao.updateAll(it) }
                result.added.chunked(BATCH).forEach { batch -> dao.insertAll(batch.map { it.toEntity() }) }
                result.missing.chunked(BATCH).forEach { dao.markMissing(it, now) }
                dao.purgeMissingBefore(now - TimeUnit.DAYS.toMillis(MISSING_GRACE_DAYS))
            }
        } finally {
            _isSyncing.value = false
            _hasSynced.value = true
        }
    }

    private companion object {
        const val TAG = "MediaSync"
        const val DEBOUNCE_MS = 600L
        const val BATCH = 500
        const val MISSING_GRACE_DAYS = 30L
    }
}
