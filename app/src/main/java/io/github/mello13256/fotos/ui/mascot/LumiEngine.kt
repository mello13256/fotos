package io.github.mello13256.fotos.ui.mascot

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

/** Fotografia visível na grelha, onde o Lumi se pode sentar. */
data class Perch(val key: Any, val rect: Rect)

internal enum class ParticleKind { HEART, Z, SPARK, DROP, EXCLAMATION }

internal class Particle(
    val kind: ParticleKind,
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val life: Float,
    val size: Float,
) {
    var age = 0f
}

/**
 * Comportamento do Lumi: anda pelo chão da grelha, salta para cima das fotografias, "tira
 * fotografias" com flash, adormece quando ninguém mexe na app e reage ao que o utilizador faz.
 *
 * O estado é atualizado a cada fotograma; só o desenho e a posição são invalidados, sem
 * recompor a interface.
 */
internal class LumiEngine(private val density: Float, private val random: Random = Random.Default) {

    private enum class Mode { WALK, IDLE, SLEEP, JUMP, SIT, FALL, HOP }

    /** Incrementado a cada fotograma para redesenhar. */
    var frame by mutableLongStateOf(0L)
        private set

    var bubble by mutableStateOf<String?>(null)
        private set

    val sizePx = 44f * density
    var x = -1f
        private set
    var y = -1f
        private set
    var pose = LumiPose()
        private set
    var flashAge = 99f
        private set
    var grounded = true
        private set
    val particles = ArrayList<Particle>()

    private var mode = Mode.IDLE
    private var modeTime = 0f
    private var modeDuration = 2f
    private var clock = 0f
    private var lastActivity = 0f
    private var facing = 1f
    private var look = 0f
    private var lookTarget = 0f
    private var legPhase = 0f
    private var antenna = 0f
    private var squash = 1f
    private var blinkTimer = 2f
    private var blinkTime = 0f
    private var vy = 0f
    private var sad = 0f
    private var bubbleUntil = 0f
    private var zTimer = 0f

    private var jumpFrom = Offset.Zero
    private var jumpTo = Offset.Zero
    private var jumpDuration = 0.6f
    private var jumpArc = 0f
    private var perchKey: Any? = null
    private var perchDx = 0f
    private var flashed = false

    private val speed get() = 42f * density
    private val gravity get() = 2600f * density

    fun update(dt: Float, width: Float, floor: Float, perches: List<Perch>, scrolling: Boolean) {
        clock += dt
        modeTime += dt
        if (x < 0f) {
            x = width * 0.12f
            y = floor
            lastActivity = clock
        }
        if (scrolling) {
            lastActivity = clock
            if (mode == Mode.SLEEP) wakeUp()
        }

        // Pequenos movimentos contínuos.
        blinkTimer -= dt
        if (blinkTimer <= 0f) {
            blinkTime = BLINK_DURATION
            blinkTimer = random.nextFloat() * 3f + 2.5f
        }
        if (blinkTime > 0f) blinkTime -= dt
        if (random.nextFloat() < dt * 0.6f) lookTarget = random.nextFloat() * 2f - 1f
        look += (lookTarget - look) * min(1f, dt * 6f)
        squash += (1f - squash) * min(1f, dt * 10f)
        antenna += dt * (if (mode == Mode.WALK) 9f else 3f)
        sad = (sad - dt * 0.4f).coerceAtLeast(0f)
        flashAge += dt
        if (bubble != null && clock > bubbleUntil) bubble = null

        when (mode) {
            Mode.WALK -> {
                y = floor
                x += facing * speed * dt
                legPhase += dt * 12f
                val margin = 8f * density
                if (x < margin) {
                    x = margin
                    facing = 1f
                } else if (x > width - sizePx - margin) {
                    x = width - sizePx - margin
                    facing = -1f
                }
                if (modeTime > modeDuration) enter(Mode.IDLE, random.nextFloat() * 3f + 2f)
            }

            Mode.IDLE -> {
                y = floor
                when {
                    clock - lastActivity > SLEEP_AFTER -> enter(Mode.SLEEP, Float.MAX_VALUE)
                    modeTime > modeDuration -> decideNext(width, floor, perches)
                }
            }

            Mode.SLEEP -> {
                y = floor
                zTimer -= dt
                if (zTimer <= 0f) {
                    zTimer = 1.3f
                    particles += Particle(
                        ParticleKind.Z, x + sizePx * 0.7f, y + sizePx * 0.15f,
                        vx = 10f * density, vy = -22f * density, life = 2.2f, size = 13f * density,
                    )
                }
            }

            Mode.JUMP -> {
                perchKey?.let { key ->
                    val perch = perches.firstOrNull { it.key == key }
                    if (perch == null) {
                        perchKey = null
                        jumpTo = Offset(jumpTo.x, floor)
                    } else {
                        jumpTo = perchPosition(perch)
                    }
                }
                val t = (modeTime / jumpDuration).coerceIn(0f, 1f)
                x = jumpFrom.x + (jumpTo.x - jumpFrom.x) * t
                y = jumpFrom.y + (jumpTo.y - jumpFrom.y) * t - jumpArc * 4f * t * (1f - t)
                squash = if (t < 0.5f) 1.12f else 1f
                if (t >= 1f) {
                    squash = 0.72f
                    if (perchKey != null) {
                        flashed = false
                        enter(Mode.SIT, random.nextFloat() * 3f + 4f)
                    } else {
                        enter(Mode.IDLE, random.nextFloat() * 2f + 1.5f)
                    }
                }
            }

            Mode.SIT -> {
                val perch = perches.firstOrNull { it.key == perchKey }
                if (perch == null || perch.rect.bottom > floor + sizePx || perch.rect.top < 0f) {
                    perchKey = null
                    vy = 0f
                    enter(Mode.FALL, Float.MAX_VALUE)
                } else {
                    val p = perchPosition(perch)
                    x = p.x
                    y = p.y
                    if (!flashed && modeTime > 1.2f) {
                        flashed = true
                        flashAge = 0f
                        repeat(4) { sparkAround(1f) }
                    }
                    if (modeTime > modeDuration) {
                        perchKey = null
                        val targetX = (x + (random.nextFloat() - 0.5f) * 160f * density)
                            .coerceIn(8f * density, width - sizePx - 8f * density)
                        jumpTowards(Offset(targetX, floor))
                    }
                }
            }

            Mode.FALL, Mode.HOP -> {
                vy += gravity * dt
                y += vy * dt
                if (vy > 0f && y >= floor) {
                    y = floor
                    vy = 0f
                    squash = 0.7f
                    enter(Mode.IDLE, random.nextFloat() * 2f + 1f)
                }
            }
        }

        grounded = mode == Mode.WALK || mode == Mode.IDLE || mode == Mode.SLEEP || mode == Mode.SIT

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.age += dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.age >= p.life) iterator.remove()
        }

        pose = LumiPose(
            facing = facing,
            look = look,
            legPhase = legPhase,
            walking = mode == Mode.WALK,
            blink = if (blinkTime > 0f) 1f - abs(blinkTime / BLINK_DURATION * 2f - 1f) else 0f,
            sleeping = mode == Mode.SLEEP,
            squash = squash,
            antennaWave = antenna,
            sad = sad > 0f,
        )
        frame++
    }

    fun onEvent(event: MascotEvent, phrase: String?) {
        lastActivity = clock
        if (mode == Mode.SLEEP) wakeUp()
        when (event) {
            MascotEvent.FAVORITE -> repeat(5) {
                particles += Particle(
                    ParticleKind.HEART,
                    x + sizePx * (0.2f + random.nextFloat() * 0.6f), y,
                    vx = (random.nextFloat() - 0.5f) * 50f * density,
                    vy = -(60f + random.nextFloat() * 50f) * density,
                    life = 1.4f + random.nextFloat() * 0.4f,
                    size = (10f + random.nextFloat() * 6f) * density,
                )
            }
            MascotEvent.TRASH -> {
                sad = 2.5f
                particles += Particle(
                    ParticleKind.DROP, x + sizePx * 0.85f, y + sizePx * 0.25f,
                    vx = 0f, vy = 30f * density, life = 0.9f, size = 9f * density,
                )
            }
            MascotEvent.SELECT -> particles += Particle(
                ParticleKind.EXCLAMATION, x + sizePx * 0.5f, y - 4f * density,
                vx = 0f, vy = -20f * density, life = 0.9f, size = 16f * density,
            )
            MascotEvent.THEME -> repeat(8) { sparkAround(1.6f) }
        }
        if (phrase != null) say(phrase)
        hop()
    }

    fun onTap(phrase: String) {
        lastActivity = clock
        if (mode == Mode.SLEEP) wakeUp()
        say(phrase)
        repeat(3) { sparkAround(1f) }
        hop()
    }

    private fun say(text: String) {
        bubble = text
        bubbleUntil = clock + 2.8f
    }

    private fun hop() {
        if (mode == Mode.WALK || mode == Mode.IDLE || mode == Mode.SLEEP) {
            vy = -620f * density
            squash = 1.2f
            enter(Mode.HOP, Float.MAX_VALUE)
        } else {
            squash = 0.8f
        }
    }

    private fun wakeUp() {
        particles.removeAll { it.kind == ParticleKind.Z }
        enter(Mode.IDLE, 1.5f)
        hop()
    }

    private fun sparkAround(spread: Float) {
        val angle = random.nextFloat() * Math.PI.toFloat() * 2f
        particles += Particle(
            ParticleKind.SPARK,
            x + sizePx / 2f, y + sizePx * 0.45f,
            vx = kotlin.math.cos(angle) * 70f * density * spread,
            vy = kotlin.math.sin(angle) * 70f * density * spread - 30f * density,
            life = 0.7f, size = (7f + random.nextFloat() * 5f) * density,
        )
    }

    private fun decideNext(width: Float, floor: Float, perches: List<Perch>) {
        val roll = random.nextFloat()
        val candidates = perches.filter { perch ->
            perch.rect.top > 0f && perch.rect.bottom < floor - sizePx * 0.3f && perch.rect.width > sizePx * 1.1f
        }
        when {
            roll < 0.4f && candidates.isNotEmpty() -> {
                val perch = candidates.random(random)
                perchKey = perch.key
                perchDx = (perch.rect.width - sizePx) * (0.25f + random.nextFloat() * 0.5f)
                jumpTowards(perchPosition(perch))
            }
            roll < 0.85f -> {
                facing = if (x < width * 0.2f) 1f else if (x > width * 0.7f) -1f else if (random.nextBoolean()) 1f else -1f
                enter(Mode.WALK, random.nextFloat() * 4f + 2f)
            }
            else -> enter(Mode.IDLE, random.nextFloat() * 3f + 2f)
        }
    }

    private fun perchPosition(perch: Perch) =
        Offset(perch.rect.left + perchDx, perch.rect.bottom - sizePx - 2f * density)

    private fun jumpTowards(target: Offset) {
        jumpFrom = Offset(x, y)
        jumpTo = target
        val distance = hypot(target.x - x, target.y - y)
        jumpDuration = 0.45f + distance / (2400f * density)
        jumpArc = 70f * density + distance * 0.15f
        facing = if (target.x >= x) 1f else -1f
        enter(Mode.JUMP, jumpDuration)
    }

    private fun enter(next: Mode, duration: Float) {
        mode = next
        modeTime = 0f
        modeDuration = duration
    }

    private companion object {
        const val BLINK_DURATION = 0.18f
        const val SLEEP_AFTER = 40f
    }
}
