package io.github.mello13256.fotos.ui.common

import android.text.format.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cabeçalhos de mês na grelha ("novembro de 2026", "November 2026", ...). */
object MonthFormat {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    /** Chave numérica do mês (ano * 12 + mês) para comparar itens vizinhos. */
    fun monthKey(epochMillis: Long): Int {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return date.year * 12 + date.monthValue
    }

    fun label(epochMillis: Long, locale: Locale = Locale.getDefault()): String {
        val pattern = DateFormat.getBestDateTimePattern(locale, "MMMMyyyy")
        val text = DateTimeFormatter.ofPattern(pattern, locale)
            .format(Instant.ofEpochMilli(epochMillis).atZone(zone))
        return text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
