package io.github.mello13256.fotos.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MediaKindTest {

    @Test
    fun `fotografia da câmara`() {
        assertEquals(MediaKind.PHOTO, MediaKind.classify("DCIM/Camera/", "IMG_20260101.jpg", "image/jpeg"))
    }

    @Test
    fun `capturas de ecrã reconhecidas pela pasta`() {
        assertEquals(MediaKind.SCREENSHOT, MediaKind.classify("Pictures/Screenshots/", "img.png", "image/png"))
        assertEquals(MediaKind.SCREENSHOT, MediaKind.classify("DCIM/Screenshots/", "x.jpg", "image/jpeg"))
        assertEquals(MediaKind.SCREENSHOT, MediaKind.classify("Pictures/Capturas de ecrã/", "x.png", "image/png"))
    }

    @Test
    fun `capturas de ecrã reconhecidas pelo nome`() {
        assertEquals(MediaKind.SCREENSHOT, MediaKind.classify("Download/", "Screenshot_20260101-101010.png", "image/png"))
    }

    @Test
    fun `pasta com nome parecido não é captura de ecrã`() {
        assertEquals(MediaKind.PHOTO, MediaKind.classify("Pictures/MyScreenshotsArt/", "arte.png", "image/png"))
    }

    @Test
    fun `vídeos e gravações de ecrã`() {
        assertEquals(MediaKind.VIDEO, MediaKind.classify("DCIM/Camera/", "VID_1.mp4", "video/mp4"))
        assertEquals(MediaKind.SCREEN_RECORDING, MediaKind.classify("DCIM/Screen recordings/", "a.mp4", "video/mp4"))
        assertEquals(MediaKind.SCREEN_RECORDING, MediaKind.classify("Movies/", "screen_recording_1.mp4", "video/mp4"))
    }

    @Test
    fun `código persistido volta ao mesmo tipo`() {
        MediaKind.entries.forEach { assertEquals(it, MediaKind.fromCode(it.code)) }
        assertEquals(MediaKind.PHOTO, MediaKind.fromCode(-1))
    }
}
