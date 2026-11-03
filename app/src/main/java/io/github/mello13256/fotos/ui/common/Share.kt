package io.github.mello13256.fotos.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Abre a folha de partilha do sistema para um ou mais itens. */
fun Context.shareMedia(uris: List<Uri>, mimeTypes: List<String>) {
    if (uris.isEmpty()) return
    val mime = when {
        mimeTypes.all { it.startsWith("image/") } -> if (mimeTypes.distinct().size == 1) mimeTypes.first() else "image/*"
        mimeTypes.all { it.startsWith("video/") } -> if (mimeTypes.distinct().size == 1) mimeTypes.first() else "video/*"
        else -> "*/*"
    }
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }.apply {
        type = mime
        clipData = ClipData.newRawUri(null, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(intent, null))
}
