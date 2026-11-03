package io.github.mello13256.fotos.ui.mascot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.max
import kotlin.math.sin

/**
 * Lumi, a mascote da app: uma criaturinha redonda com um olho em forma de lente de câmara e
 * duas antenas luminosas. É desenhada à mão num espaço de 40 × 40 unidades.
 */
data class LumiPose(
    /** 1 = virado para a direita, -1 = para a esquerda. */
    val facing: Float = 1f,
    /** Para onde olha, de -1 a 1. */
    val look: Float = 0f,
    val legPhase: Float = 0f,
    val walking: Boolean = false,
    /** 0 = olho aberto, 1 = fechado. */
    val blink: Float = 0f,
    val sleeping: Boolean = false,
    /** Achatamento ao aterrar (< 1) ou esticão ao saltar (> 1). */
    val squash: Float = 1f,
    val antennaWave: Float = 0f,
    val sad: Boolean = false,
)

object LumiColors {
    val body = Color(0xFFFFCF4D)
    val bodyLight = Color(0xFFFFE9A6)
    val bodyShade = Color(0xFFF0A51F)
    val outline = Color(0xFF7A4E00)
    val feet = Color(0xFFE08E0B)
    val barrel = Color(0xFF3A3F4B)
    val lens = Color(0xFF1F2A44)
    val iris = Color(0xFF5B8FD1)
    val cheek = Color(0xFFFF8E8E)
    val antennaTip = Color(0xFFFF6F91)
    val heart = Color(0xFFFF4F7B)
    val drop = Color(0xFF6EC3FF)
    val spark = Color(0xFFFFE066)
}

fun DrawScope.drawLumi(pose: LumiPose, topLeft: Offset, sizePx: Float) {
    val u = sizePx / 40f
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(pose.facing, 1f, pivot = Offset(20f * u, 20f * u))
        val sx = 1f / kotlin.math.sqrt(pose.squash.coerceIn(0.5f, 1.6f))
        scale(sx, pose.squash, pivot = Offset(20f * u, 38f * u))
    }) {
        // Pés (atrás do corpo).
        val step = if (pose.walking) sin(pose.legPhase) * 2f * u else 0f
        val liftLeft = if (pose.walking) max(0f, sin(pose.legPhase)) * 1.6f * u else 0f
        val liftRight = if (pose.walking) max(0f, -sin(pose.legPhase)) * 1.6f * u else 0f
        drawOval(LumiColors.feet, Offset(9f * u + step, 33.5f * u - liftLeft), Size(8f * u, 4.5f * u))
        drawOval(LumiColors.feet, Offset(23f * u - step, 33.5f * u - liftRight), Size(8f * u, 4.5f * u))

        // Antenas.
        val wave = sin(pose.antennaWave) * 1.5f * u
        val stroke = Stroke(width = 1.4f * u, cap = StrokeCap.Round)
        val leftTip = Offset(12f * u + wave, 3.5f * u)
        val rightTip = Offset(28f * u + wave * 0.6f, 3.5f * u)
        drawLine(LumiColors.outline, Offset(15.5f * u, 11f * u), leftTip, stroke.width, StrokeCap.Round)
        drawLine(LumiColors.outline, Offset(24.5f * u, 11f * u), rightTip, stroke.width, StrokeCap.Round)
        drawCircle(LumiColors.antennaTip, 2.4f * u, leftTip)
        drawCircle(LumiColors.antennaTip, 2.4f * u, rightTip)
        drawCircle(Color.White.copy(alpha = 0.7f), 0.8f * u, leftTip + Offset(-0.7f * u, -0.7f * u))
        drawCircle(Color.White.copy(alpha = 0.7f), 0.8f * u, rightTip + Offset(-0.7f * u, -0.7f * u))

        // Corpo.
        val bodyTopLeft = Offset(5f * u, 9f * u)
        val bodySize = Size(30f * u, 28f * u)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(LumiColors.bodyLight, LumiColors.body, LumiColors.bodyShade),
                center = Offset(15f * u, 15f * u),
                radius = 26f * u,
            ),
            topLeft = bodyTopLeft,
            size = bodySize,
        )
        drawOval(LumiColors.outline, bodyTopLeft, bodySize, style = Stroke(width = 1.4f * u))

        // Bochechas.
        drawOval(LumiColors.cheek.copy(alpha = 0.45f), Offset(7.5f * u, 24.5f * u), Size(5f * u, 3f * u))
        drawOval(LumiColors.cheek.copy(alpha = 0.45f), Offset(27.5f * u, 24.5f * u), Size(5f * u, 3f * u))

        // Olho-lente.
        val eye = Offset((20f + pose.look * 2.5f) * u, 20.5f * u)
        val blink = if (pose.sleeping) 1f else pose.blink.coerceIn(0f, 1f)
        if (blink >= 0.95f) {
            // Olho fechado: uma linha curva.
            drawArc(
                color = LumiColors.outline,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = eye + Offset(-5.5f * u, -3.5f * u),
                size = Size(11f * u, 6f * u),
                style = Stroke(width = 1.5f * u, cap = StrokeCap.Round),
            )
        } else {
            drawCircle(LumiColors.barrel, 8f * u, eye)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(LumiColors.iris, LumiColors.lens),
                    center = eye + Offset(-1.5f * u, -1.5f * u),
                    radius = 7f * u,
                ),
                radius = 6f * u,
                center = eye,
            )
            drawCircle(Color(0xFF0B1020), 2.6f * u, eye)
            drawCircle(Color.White.copy(alpha = 0.9f), 1.6f * u, eye + Offset(-2.4f * u, -2.4f * u))
            drawCircle(Color.White.copy(alpha = 0.5f), 0.8f * u, eye + Offset(2.2f * u, 2f * u))
            if (blink > 0f) {
                val lid = Path().apply { addOval(androidx.compose.ui.geometry.Rect(eye, 8.2f * u)) }
                clipPath(lid) {
                    drawRect(LumiColors.body, eye + Offset(-8.2f * u, -8.2f * u), Size(16.4f * u, 16.4f * u * blink))
                }
            }
        }

        // Boca.
        if (pose.sad) {
            drawArc(
                LumiColors.outline, 200f, 140f, false,
                topLeft = Offset(17.5f * u, 30f * u), size = Size(5f * u, 3f * u),
                style = Stroke(width = 1.1f * u, cap = StrokeCap.Round),
            )
        } else {
            drawArc(
                LumiColors.outline, 20f, 140f, false,
                topLeft = Offset(17.5f * u, 28f * u), size = Size(5f * u, 3f * u),
                style = Stroke(width = 1.1f * u, cap = StrokeCap.Round),
            )
        }
    }
}

fun DrawScope.drawHeart(center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y + s * 0.9f)
        cubicTo(center.x - s * 1.6f, center.y - s * 0.1f, center.x - s * 0.6f, center.y - s * 1.3f, center.x, center.y - s * 0.4f)
        cubicTo(center.x + s * 0.6f, center.y - s * 1.3f, center.x + s * 1.6f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

fun DrawScope.drawSpark(center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - s)
        quadraticTo(center.x, center.y, center.x + s, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + s)
        quadraticTo(center.x, center.y, center.x - s, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - s)
        close()
    }
    drawPath(path, color)
}

fun DrawScope.drawDrop(center: Offset, size: Float, color: Color) {
    val s = size / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - s)
        cubicTo(center.x + s * 0.2f, center.y - s * 0.4f, center.x + s * 0.8f, center.y + s * 0.1f, center.x, center.y + s)
        cubicTo(center.x - s * 0.8f, center.y + s * 0.1f, center.x - s * 0.2f, center.y - s * 0.4f, center.x, center.y - s)
        close()
    }
    drawPath(path, color)
}
