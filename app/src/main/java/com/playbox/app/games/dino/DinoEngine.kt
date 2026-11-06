package com.playbox.app.games.dino

import kotlin.random.Random

data class DinoObstacle(
    val id: Int,
    val x: Float,
    val width: Float,
    val height: Float,
)

/**
 * 横向奔跑小恐龙纯逻辑引擎。
 * 世界坐标宽 320、高 120，地面高度 100；跳跃与碰撞都与画面解耦。
 */
class DinoEngine(
    private val random: Random = Random.Default,
) {
    var dinoY: Float = 0f
        private set
    var isJumping: Boolean = false
        private set
    var score: Int = 0
        private set
    var speed: Float = BASE_SPEED
        private set
    var isGameOver: Boolean = false
        private set
    val obstacles: List<DinoObstacle> get() = obstacleList.toList()

    private val obstacleList = mutableListOf<DinoObstacle>()
    private var velocityY = 0f
    private var distance = 0f
    private var spawnIn = 190f
    private var nextId = 0

    fun reset() {
        dinoY = 0f
        velocityY = 0f
        isJumping = false
        score = 0
        speed = BASE_SPEED
        isGameOver = false
        distance = 0f
        spawnIn = 190f
        obstacleList.clear()
        nextId = 0
    }

    fun jump(): Boolean {
        if (isGameOver || isJumping) return false
        velocityY = JUMP_VELOCITY
        isJumping = true
        return true
    }

    fun update(deltaSeconds: Float) {
        if (isGameOver) return
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return

        speed = (speed + 2.5f * dt).coerceAtMost(MAX_SPEED)
        distance += speed * dt
        score = (distance / 10f).toInt()

        if (isJumping) {
            dinoY += velocityY * dt
            velocityY += GRAVITY * dt
            if (dinoY <= 0f) {
                dinoY = 0f
                velocityY = 0f
                isJumping = false
            }
        }

        val dx = speed * dt
        spawnIn -= dx
        val moved = obstacleList.map { it.copy(x = it.x - dx) }
        obstacleList.clear()
        obstacleList.addAll(moved.filter { it.x + it.width > -30f })

        if (spawnIn <= 0f) {
            spawnObstacle()
            spawnIn = random.nextFloat() * 140f + 200f
        }

        if (collides()) {
            isGameOver = true
        }
    }

    private fun spawnObstacle() {
        val large = random.nextInt(100) < 35
        obstacleList += DinoObstacle(
            id = nextId++,
            x = WORLD_WIDTH + 24f,
            width = if (large) 25f else 17f,
            height = if (large) 50f else 35f,
        )
    }

    private fun collides(): Boolean {
        val dinoLeft = DINO_X
        val dinoRight = DINO_X + DINO_WIDTH
        return obstacleList.any { obstacle ->
            val horizontal = dinoRight > obstacle.x && dinoLeft < obstacle.x + obstacle.width
            val vertical = dinoY < obstacle.height
            horizontal && vertical
        }
    }

    companion object {
        const val WORLD_WIDTH = 320f
        const val WORLD_HEIGHT = 120f
        const val GROUND_Y = 100f
        const val DINO_X = 44f
        const val DINO_WIDTH = 44f
        const val DINO_HEIGHT = 47f
        private const val BASE_SPEED = 130f
        private const val MAX_SPEED = 260f
        private const val JUMP_VELOCITY = 440f
        private const val GRAVITY = -1000f
    }
}
