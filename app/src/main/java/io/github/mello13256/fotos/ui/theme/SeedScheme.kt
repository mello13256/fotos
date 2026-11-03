package io.github.mello13256.fotos.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Esquema de cores completo a partir de uma única cor (tirada de uma fotografia). Mantém o tom da
 * fotografia e deriva luminosidades com contraste adequado para os papéis do Material 3.
 */
fun seedColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.35f, 0.85f)
    val secondarySat = sat * 0.35f
    val tertiaryHue = hue + 60f
    val tertiarySat = sat * 0.6f
    val neutralSat = 0.10f

    fun tone(h: Float, s: Float, l: Float) =
        Color(ColorUtils.HSLToColor(floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

    return if (dark) {
        darkColorScheme(
            primary = tone(hue, sat, 0.78f),
            onPrimary = tone(hue, sat, 0.18f),
            primaryContainer = tone(hue, sat, 0.30f),
            onPrimaryContainer = tone(hue, sat, 0.90f),
            inversePrimary = tone(hue, sat, 0.40f),
            secondary = tone(hue, secondarySat, 0.78f),
            onSecondary = tone(hue, secondarySat, 0.18f),
            secondaryContainer = tone(hue, secondarySat, 0.28f),
            onSecondaryContainer = tone(hue, secondarySat, 0.90f),
            tertiary = tone(tertiaryHue, tertiarySat, 0.78f),
            onTertiary = tone(tertiaryHue, tertiarySat, 0.18f),
            tertiaryContainer = tone(tertiaryHue, tertiarySat, 0.30f),
            onTertiaryContainer = tone(tertiaryHue, tertiarySat, 0.90f),
            background = tone(hue, neutralSat, 0.07f),
            onBackground = tone(hue, neutralSat, 0.90f),
            surface = tone(hue, neutralSat, 0.07f),
            onSurface = tone(hue, neutralSat, 0.90f),
            surfaceVariant = tone(hue, 0.14f, 0.22f),
            onSurfaceVariant = tone(hue, 0.12f, 0.78f),
            surfaceTint = tone(hue, sat, 0.78f),
            inverseSurface = tone(hue, neutralSat, 0.90f),
            inverseOnSurface = tone(hue, neutralSat, 0.15f),
            outline = tone(hue, 0.08f, 0.55f),
            outlineVariant = tone(hue, 0.10f, 0.28f),
            surfaceBright = tone(hue, neutralSat, 0.22f),
            surfaceDim = tone(hue, neutralSat, 0.07f),
            surfaceContainerLowest = tone(hue, neutralSat, 0.04f),
            surfaceContainerLow = tone(hue, neutralSat, 0.09f),
            surfaceContainer = tone(hue, neutralSat, 0.11f),
            surfaceContainerHigh = tone(hue, neutralSat, 0.14f),
            surfaceContainerHighest = tone(hue, neutralSat, 0.18f),
        )
    } else {
        lightColorScheme(
            primary = tone(hue, sat, 0.40f),
            onPrimary = tone(hue, sat, 0.99f),
            primaryContainer = tone(hue, sat, 0.90f),
            onPrimaryContainer = tone(hue, sat, 0.12f),
            inversePrimary = tone(hue, sat, 0.78f),
            secondary = tone(hue, secondarySat, 0.42f),
            onSecondary = tone(hue, secondarySat, 0.99f),
            secondaryContainer = tone(hue, secondarySat, 0.90f),
            onSecondaryContainer = tone(hue, secondarySat, 0.12f),
            tertiary = tone(tertiaryHue, tertiarySat, 0.40f),
            onTertiary = tone(tertiaryHue, tertiarySat, 0.99f),
            tertiaryContainer = tone(tertiaryHue, tertiarySat, 0.90f),
            onTertiaryContainer = tone(tertiaryHue, tertiarySat, 0.12f),
            background = tone(hue, 0.30f, 0.985f),
            onBackground = tone(hue, neutralSat, 0.11f),
            surface = tone(hue, 0.30f, 0.985f),
            onSurface = tone(hue, neutralSat, 0.11f),
            surfaceVariant = tone(hue, 0.20f, 0.90f),
            onSurfaceVariant = tone(hue, 0.12f, 0.30f),
            surfaceTint = tone(hue, sat, 0.40f),
            inverseSurface = tone(hue, neutralSat, 0.19f),
            inverseOnSurface = tone(hue, neutralSat, 0.95f),
            outline = tone(hue, 0.08f, 0.48f),
            outlineVariant = tone(hue, 0.12f, 0.80f),
            surfaceBright = tone(hue, 0.30f, 0.985f),
            surfaceDim = tone(hue, 0.15f, 0.87f),
            surfaceContainerLowest = tone(hue, 0.30f, 1.0f),
            surfaceContainerLow = tone(hue, 0.25f, 0.965f),
            surfaceContainer = tone(hue, 0.22f, 0.95f),
            surfaceContainerHigh = tone(hue, 0.20f, 0.935f),
            surfaceContainerHighest = tone(hue, 0.18f, 0.915f),
        )
    }
}
