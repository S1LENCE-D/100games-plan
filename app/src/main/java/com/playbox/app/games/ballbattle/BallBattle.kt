package com.playbox.app.games.ballbattle

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

const val BALL_TOTAL_SKILL_POINTS = 12
private const val BASE_BALL_RADIUS = 30f
private const val BASE_BALL_SPEED = 270f

enum class BallAttribute(
    val label: String,
    val shortLabel: String,
    val description: String,
) {
    POWER("力量", "力", "提高碰撞攻击力"),
    VITALITY("生命", "生", "提高生命上限与减伤"),
    AGILITY("速度", "速", "提高移动速度"),
    SIZE("体型", "体", "提高半径与质量"),
    LUCK("暴击", "暴", "提高暴击概率"),
}

@Immutable
data class BallBuild(
    val power: Int = 0,
    val vitality: Int = 0,
    val agility: Int = 0,
    val size: Int = 0,
    val luck: Int = 0,
) {
    val spent: Int get() = power + vitality + agility + size + luck
    val remaining: Int get() = (BALL_TOTAL_SKILL_POINTS - spent).coerceAtLeast(0)

    fun pointsOf(attribute: BallAttribute): Int = when (attribute) {
        BallAttribute.POWER -> power
        BallAttribute.VITALITY -> vitality
        BallAttribute.AGILITY -> agility
        BallAttribute.SIZE -> size
        BallAttribute.LUCK -> luck
    }

    fun withPoint(attribute: BallAttribute, delta: Int): BallBuild {
        val next = (pointsOf(attribute) + delta).coerceAtLeast(0)
        if (delta > 0 && spent >= BALL_TOTAL_SKILL_POINTS) return this
        if (delta < 0 && next == pointsOf(attribute)) return this
        return when (attribute) {
            BallAttribute.POWER -> copy(power = next)
            BallAttribute.VITALITY -> copy(vitality = next)
            BallAttribute.AGILITY -> copy(agility = next)
            BallAttribute.SIZE -> copy(size = next)
            BallAttribute.LUCK -> copy(luck = next)
        }
    }

    val stats: BallStats
        get() = BallStats(
            maxHp = 50 + vitality * 10,
            attack = 7 + power * 2,
            moveSpeed = 0f,
            radius = BASE_BALL_RADIUS + size * 2.2f,
            critChance = (0.03f + luck * 0.025f).coerceAtMost(0.45f),
            mass = 1f + size * 0.30f,
            damageReduction = (vitality * 0.025f).coerceAtMost(0.30f),
        ).let { stats ->
            // 速度与渲染半径等比：球更大时，屏幕上的移动也更快。
            stats.copy(
                moveSpeed = (BASE_BALL_SPEED + agility * 15f) *
                    (stats.radius / BASE_BALL_RADIUS),
            )
        }

    companion object {
        fun random(
            random: Random = Random.Default,
            points: Int = BALL_TOTAL_SKILL_POINTS,
        ): BallBuild {
            val values = IntArray(BallAttribute.entries.size)
            repeat(points) {
                values[random.nextInt(values.size)] += 1
            }
            return BallBuild(
                power = values[0],
                vitality = values[1],
                agility = values[2],
                size = values[3],
                luck = values[4],
            )
        }
    }
}

@Immutable
data class BallStats(
    val maxHp: Int,
    val attack: Int,
    val moveSpeed: Float,
    val radius: Float,
    val critChance: Float,
    val mass: Float,
    val damageReduction: Float,
)

@Immutable
data class BallBody(
    val x: Float,
    val y: Float,
    val vx: Float,
    val vy: Float,
    val radius: Float,
    val hp: Int,
    val maxHp: Int,
)

@Immutable
data class BallDamageEvent(
    val id: Long,
    val x: Float,
    val y: Float,
    val amount: Int,
    val isCritical: Boolean,
    val targetPlayer: Boolean,
    val atMs: Long,
)

@Immutable
data class BallBattleSnapshot(
    val player: BallBody,
    val opponent: BallBody,
    val elapsedMs: Long,
    val collisionPulse: Boolean,
    val winner: Int?,
    val isFinished: Boolean,
    val playerDamageDealt: Int,
    val arenaWidth: Float,
    val arenaHeight: Float,
    val damageEvents: List<BallDamageEvent> = emptyList(),
)

/**
 * 纯 Kotlin 的自动碰撞对战。两个球在封闭场地中反弹，
 * 相撞时同时结算伤害；技能点决定生命、攻击、速度、体型和暴击。
 */
class BallBattleEngine(
    val playerBuild: BallBuild,
    val opponentBuild: BallBuild,
    private val random: Random = Random.Default,
    val arenaWidth: Float = 1000f,
    val arenaHeight: Float = 1000f,
) {
    private val playerStats = playerBuild.stats
    private val opponentStats = opponentBuild.stats
    private var elapsedMs = 0L
    private var lastCollisionMs = -1_000L
    private var collisionFlashUntilMs = -1L
    private var winner: Int? = null
    private var playerDamageDealt = 0
    private var nextDamageEventId = 0L
    private val damageEvents = mutableListOf<BallDamageEvent>()
    private var player: BallBody
    private var opponent: BallBody

    init {
        val spawnY = arenaHeight * (0.42f + random.nextFloat() * 0.16f)
        val playerStartX = arenaWidth * 0.22f
        val opponentStartX = arenaWidth * 0.78f
        player = BallBody(
            x = playerStartX,
            y = spawnY,
            vx = 0f,
            vy = 0f,
            radius = playerStats.radius,
            hp = playerStats.maxHp,
            maxHp = playerStats.maxHp,
        )
        opponent = BallBody(
            x = opponentStartX,
            y = arenaHeight - spawnY,
            vx = 0f,
            vy = 0f,
            radius = opponentStats.radius,
            hp = opponentStats.maxHp,
            maxHp = opponentStats.maxHp,
        )
        val playerVelocity = launchVelocity(playerStartX, player.y, opponent.x, opponent.y, playerStats.moveSpeed)
        val opponentVelocity = launchVelocity(opponent.x, opponent.y, player.x, player.y, opponentStats.moveSpeed)
        player = player.copy(vx = playerVelocity.first, vy = playerVelocity.second)
        opponent = opponent.copy(vx = opponentVelocity.first, vy = opponentVelocity.second)
    }

    val isFinished: Boolean get() = winner != null
    val currentScore: Int
        get() {
            val resultBonus = when (winner) {
                1 -> 300
                0 -> 150
                else -> 0
            }
            return playerDamageDealt * 2 + player.hp.coerceAtLeast(0) * 3 + resultBonus
        }

    fun step(deltaMs: Long) {
        if (isFinished) return
        val clampedDelta = deltaMs.coerceIn(0L, 50L)
        elapsedMs += clampedDelta
        val seconds = clampedDelta / 1000f
        // 高速和较大的球容易在一帧内穿过去；按最大位移细分，保证真正碰到一起。
        val maxTravel = maxOf(
            hypot(player.vx, player.vy),
            hypot(opponent.vx, opponent.vy),
        ) * seconds
        val safeTravel = minOf(player.radius, opponent.radius) * 0.45f
        val substeps = kotlin.math.ceil(maxTravel / safeTravel.coerceAtLeast(1f))
            .toInt()
            .coerceIn(1, 12)
        val subSeconds = seconds / substeps
        repeat(substeps) {
            if (winner != null) return@repeat
            player = clampToArena(moveWithinArena(player, subSeconds, playerStats))
            opponent = clampToArena(moveWithinArena(opponent, subSeconds, opponentStats))
            resolveCollision()
            player = clampToArena(player)
            opponent = clampToArena(opponent)
            if (winner == null) checkHealth()
        }
        if (winner == null && elapsedMs >= MAX_BATTLE_MS) {
            decideByRemainingHealth()
        }
    }

    fun snapshot(): BallBattleSnapshot {
        damageEvents.removeAll { elapsedMs - it.atMs > DAMAGE_TEXT_LIFETIME_MS }
        return BallBattleSnapshot(
            player = player,
            opponent = opponent,
            elapsedMs = elapsedMs,
            collisionPulse = elapsedMs < collisionFlashUntilMs,
            winner = winner,
            isFinished = isFinished,
            playerDamageDealt = playerDamageDealt,
            arenaWidth = arenaWidth,
            arenaHeight = arenaHeight,
            damageEvents = damageEvents.toList(),
        )
    }

    private fun moveWithinArena(body: BallBody, seconds: Float, stats: BallStats): BallBody {
        var x = body.x + body.vx * seconds
        var y = body.y + body.vy * seconds
        var vx = body.vx
        var vy = body.vy
        val radius = body.radius
        if (x - radius < 0f) {
            x = radius
            vx = abs(vx)
        } else if (x + radius > arenaWidth) {
            x = arenaWidth - radius
            vx = -abs(vx)
        }
        if (y - radius < 0f) {
            y = radius
            vy = abs(vy)
        } else if (y + radius > arenaHeight) {
            y = arenaHeight - radius
            vy = -abs(vy)
        }
        return body.copy(x = x, y = y, vx = vx, vy = vy)
    }

    private fun resolveCollision() {
        val dx = opponent.x - player.x
        val dy = opponent.y - player.y
        val distance = hypot(dx, dy)
        val minDistance = player.radius + opponent.radius
        if (distance >= minDistance) return

        val nx: Float
        val ny: Float
        if (distance < 0.0001f) {
            nx = 1f
            ny = 0f
        } else {
            nx = dx / distance
            ny = dy / distance
        }
        val overlap = minDistance - distance
        player = player.copy(
            x = player.x - nx * overlap * 0.5f,
            y = player.y - ny * overlap * 0.5f,
        )
        opponent = opponent.copy(
            x = opponent.x + nx * overlap * 0.5f,
            y = opponent.y + ny * overlap * 0.5f,
        )

        val playerNormalSpeed = player.vx * nx + player.vy * ny
        val opponentNormalSpeed = opponent.vx * nx + opponent.vy * ny
        val impact = playerNormalSpeed - opponentNormalSpeed
        if (impact > 0f) {
            val m1 = playerStats.mass
            val m2 = opponentStats.mass
            val playerAfter = ((m1 - m2) * playerNormalSpeed + 2f * m2 * opponentNormalSpeed) / (m1 + m2)
            val opponentAfter = ((m2 - m1) * opponentNormalSpeed + 2f * m1 * playerNormalSpeed) / (m1 + m2)
            player = player.copy(
                vx = player.vx + (playerAfter - playerNormalSpeed) * nx,
                vy = player.vy + (playerAfter - playerNormalSpeed) * ny,
            )
            opponent = opponent.copy(
                vx = opponent.vx + (opponentAfter - opponentNormalSpeed) * nx,
                vy = opponent.vy + (opponentAfter - opponentNormalSpeed) * ny,
            )
            player = normalizeSpeed(player, playerStats.moveSpeed)
            opponent = normalizeSpeed(opponent, opponentStats.moveSpeed)
        }

        if (elapsedMs - lastCollisionMs < COLLISION_COOLDOWN_MS) return
        lastCollisionMs = elapsedMs
        collisionFlashUntilMs = elapsedMs + COLLISION_FLASH_MS

        val playerDamage = calculateDamage(playerStats, opponentStats, impact.coerceAtLeast(0f))
        val opponentDamage = calculateDamage(opponentStats, playerStats, impact.coerceAtLeast(0f))
        playerDamageDealt += opponentDamage.amount.coerceAtMost(opponent.hp)
        player = player.copy(hp = (player.hp - playerDamage.amount).coerceAtLeast(0))
        opponent = opponent.copy(hp = (opponent.hp - opponentDamage.amount).coerceAtLeast(0))

        damageEvents += BallDamageEvent(
            id = nextDamageEventId++,
            x = player.x,
            y = player.y - player.radius * 0.2f,
            amount = playerDamage.amount,
            isCritical = playerDamage.isCritical,
            targetPlayer = true,
            atMs = elapsedMs,
        )
        damageEvents += BallDamageEvent(
            id = nextDamageEventId++,
            x = opponent.x,
            y = opponent.y - opponent.radius * 0.2f,
            amount = opponentDamage.amount,
            isCritical = opponentDamage.isCritical,
            targetPlayer = false,
            atMs = elapsedMs,
        )
    }

    private fun calculateDamage(
        attacker: BallStats,
        defender: BallStats,
        impact: Float,
    ): DamageResult {
        val speedFactor = (0.70f + impact / 450f).coerceIn(0.70f, 1.80f)
        val massFactor = 0.85f + attacker.mass * 0.12f
        val isCritical = random.nextFloat() < attacker.critChance
        val crit = if (isCritical) 1.80f else 1f
        val raw = attacker.attack * speedFactor * massFactor * crit * (1f - defender.damageReduction)
        return DamageResult(
            amount = raw.roundToInt().coerceAtLeast(2),
            isCritical = isCritical,
        )
    }

    private data class DamageResult(
        val amount: Int,
        val isCritical: Boolean,
    )

    private fun clampToArena(body: BallBody): BallBody = body.copy(
        x = body.x.coerceIn(body.radius, (arenaWidth - body.radius).coerceAtLeast(body.radius)),
        y = body.y.coerceIn(body.radius, (arenaHeight - body.radius).coerceAtLeast(body.radius)),
    )

    private fun normalizeSpeed(body: BallBody, speed: Float): BallBody {
        val length = sqrt(body.vx * body.vx + body.vy * body.vy)
        if (length < 0.0001f) return body.copy(vx = speed)
        return body.copy(vx = body.vx / length * speed, vy = body.vy / length * speed)
    }

    private fun checkHealth() {
        winner = when {
            player.hp <= 0 && opponent.hp <= 0 -> 0
            opponent.hp <= 0 -> 1
            player.hp <= 0 -> 2
            else -> null
        }
        if (winner != null) {
            collisionFlashUntilMs = elapsedMs + COLLISION_FLASH_MS
        }
    }

    private fun decideByRemainingHealth() {
        val playerRatio = player.hp.toFloat() / player.maxHp
        val opponentRatio = opponent.hp.toFloat() / opponent.maxHp
        winner = when {
            abs(playerRatio - opponentRatio) < 0.01f -> 0
            playerRatio > opponentRatio -> 1
            else -> 2
        }
        collisionFlashUntilMs = elapsedMs + COLLISION_FLASH_MS
    }

    private fun launchVelocity(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        speed: Float,
    ): Pair<Float, Float> {
        val baseAngle = atan2(toY - fromY, toX - fromX)
        val angle = baseAngle + (random.nextFloat() - 0.5f) * 0.22f
        return (cos(angle) * speed) to (sin(angle) * speed)
    }

    companion object {
        const val MAX_BATTLE_MS = 45_000L
        private const val COLLISION_COOLDOWN_MS = 340L
        private const val COLLISION_FLASH_MS = 150L
        private const val DAMAGE_TEXT_LIFETIME_MS = 1_000L
    }
}
