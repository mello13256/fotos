package io.github.mello13256.fotos.core.model

/** Tipo de item, usado para separar fotografias, vídeos e capturas de ecrã. */
enum class MediaKind(val code: Int) {
    PHOTO(0),
    VIDEO(1),
    SCREENSHOT(2),
    SCREEN_RECORDING(3);

    val isVideo: Boolean get() = this == VIDEO || this == SCREEN_RECORDING

    companion object {
        fun fromCode(code: Int): MediaKind = entries.firstOrNull { it.code == code } ?: PHOTO

        private val screenshotFolders = setOf(
            "screenshots", "screenshot", "screen shots",
            "capturas de ecrã", "capturas de ecra", "capturas de tela", "capturas de pantalla",
        )
        private val screenshotPrefixes = listOf("screenshot", "screen_shot", "screen-shot", "captura")

        private val recordingFolders = setOf(
            "screenrecorder", "screenrecords", "screen recordings", "screenrecordings",
            "gravações de ecrã", "gravacoes de ecra", "gravações de tela",
        )
        private val recordingPrefixes = listOf("screen_recording", "screenrecord", "screen-recording", "record_screen")

        /**
         * Classifica um item a partir da pasta, do nome e do tipo MIME, tal como o MediaStore os
         * devolve. As capturas de ecrã não têm marcação própria no Android: são reconhecidas pela
         * pasta onde o sistema as guarda ou pelo prefixo do nome.
         */
        fun classify(relativePath: String?, displayName: String, mimeType: String?): MediaKind {
            val folders = relativePath.orEmpty()
                .split('/')
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            val name = displayName.lowercase()
            val isVideo = mimeType?.lowercase()?.startsWith("video/") == true

            return if (isVideo) {
                val recording = folders.any { it in recordingFolders } ||
                    recordingPrefixes.any { name.startsWith(it) }
                if (recording) SCREEN_RECORDING else VIDEO
            } else {
                val screenshot = folders.any { it in screenshotFolders } ||
                    screenshotPrefixes.any { name.startsWith(it) }
                if (screenshot) SCREENSHOT else PHOTO
            }
        }
    }
}
