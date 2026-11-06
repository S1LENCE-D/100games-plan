package com.playbox.app.games.snake

import kotlin.random.Random
import kotlinx.serialization.Serializable

const val SNAKE_COLUMNS = 20
const val SNAKE_ROWS = 24

@Serializable
enum class Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    ;

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}

@Serializable
enum class SnakeDifficulty(
    val label: String,
    val baseTickMillis: Long,
    val minTickMillis: Long,
    val foodsPerSpeedUp: Int,
    val speedUpStepMillis: Long,
) {
    EASY(
        label = "悠闲",
        baseTickMillis = 230L,
        minTickMillis = 150L,
        foodsPerSpeedUp = 4,
        speedUpStepMillis = 12L,
    ),
    NORMAL(
        label = "标准",
        baseTickMillis = 180L,
        minTickMillis = 115L,
        foodsPerSpeedUp = 3,
        speedUpStepMillis = 12L,
    ),
    FAST(
        label = "极速",
        baseTickMillis = 145L,
        minTickMillis = 95L,
        foodsPerSpeedUp = 2,
        speedUpStepMillis = 10L,
    ),
}

@Serializable
data class SnakeCell(
    val row: Int,
    val col: Int,
)

@Serializable
data class SnakeSnapshot(
    val body: List<SnakeCell>,
    val direction: Direction,
    val pendingDirection: Direction?,
    val food: SnakeCell,
    val foodsEaten: Int,
    val score: Int,
    val gameOver: Boolean,
    val wrapAround: Boolean,
    val difficulty: SnakeDifficulty,
)

/**
 * 网格贪吃蛇纯逻辑引擎。蛇头位于 body.first()，每次 tick 前进一格。
 * 速度随吃到果实数量提升，可通过配置切换是否穿墙。
 */
class SnakeEngine private constructor(
    private val random: Random,
    private var body: MutableList<SnakeCell>,
    private var direction: Direction,
    private var pendingDirection: Direction?,
    private var food: SnakeCell,
    private var foodsEaten: Int,
    private var score: Int,
    private var gameOver: Boolean,
    val wrapAround: Boolean,
    val difficulty: SnakeDifficulty,
) {
    val snakeBody: List<SnakeCell> get() = body.toList()
    val currentFood: SnakeCell get() = food
    val currentDirection: Direction get() = direction
    val currentScore: Int get() = score
    val eatenFoods: Int get() = foodsEaten
    val isGameOver: Boolean get() = gameOver
    val length: Int get() = body.size

    val tickIntervalMillis: Long
        get() {
            val speedSteps = foodsEaten / difficulty.foodsPerSpeedUp
            return (difficulty.baseTickMillis - speedSteps * difficulty.speedUpStepMillis)
                .coerceAtLeast(difficulty.minTickMillis)
        }

    fun turn(nextDirection: Direction): Boolean {
        if (gameOver) return false
        if (nextDirection == direction || nextDirection == direction.opposite()) return false
        if (nextDirection == pendingDirection) return true
        pendingDirection = nextDirection
        return true
    }

    fun tick() {
        if (gameOver) return

        val queued = pendingDirection
        if (queued != null && queued != direction && queued != direction.opposite()) {
            direction = queued
        }
        pendingDirection = null

        val head = body.first()
        var nextRow = head.row + direction.rowDelta()
        var nextCol = head.col + direction.colDelta()

        if (wrapAround) {
            nextRow = (nextRow + SNAKE_ROWS) % SNAKE_ROWS
            nextCol = (nextCol + SNAKE_COLUMNS) % SNAKE_COLUMNS
        } else if (nextRow !in 0 until SNAKE_ROWS || nextCol !in 0 until SNAKE_COLUMNS) {
            gameOver = true
            return
        }

        val next = SnakeCell(row = nextRow, col = nextCol)
        val eating = next == food
        val bodyToCheck = if (eating) body else body.dropLast(1)
        if (next in bodyToCheck) {
            gameOver = true
            return
        }

        body.add(0, next)
        if (eating) {
            foodsEaten += 1
            score += SCORE_PER_FOOD
            val newFood = spawnFood()
            if (newFood == null) {
                gameOver = true
            } else {
                food = newFood
            }
        } else {
            body.removeAt(body.lastIndex)
        }
    }

    fun snapshot(): SnakeSnapshot = SnakeSnapshot(
        body = body.toList(),
        direction = direction,
        pendingDirection = pendingDirection,
        food = food,
        foodsEaten = foodsEaten,
        score = score,
        gameOver = gameOver,
        wrapAround = wrapAround,
        difficulty = difficulty,
    )

    private fun spawnFood(): SnakeCell? {
        val occupied = body.toHashSet()
        val emptyCells = buildList {
            for (row in 0 until SNAKE_ROWS) {
                for (col in 0 until SNAKE_COLUMNS) {
                    val cell = SnakeCell(row = row, col = col)
                    if (cell !in occupied) add(cell)
                }
            }
        }
        if (emptyCells.isEmpty()) return null
        return emptyCells[random.nextInt(emptyCells.size)]
    }

    companion object {
        private const val SCORE_PER_FOOD = 10

        fun newGame(
            random: Random = Random.Default,
            difficulty: SnakeDifficulty = SnakeDifficulty.NORMAL,
            wrapAround: Boolean = false,
        ): SnakeEngine {
            val headRow = SNAKE_ROWS / 2
            val headCol = SNAKE_COLUMNS / 2 + 1
            val initialBody = mutableListOf(
                SnakeCell(row = headRow, col = headCol),
                SnakeCell(row = headRow, col = headCol - 1),
                SnakeCell(row = headRow, col = headCol - 2),
            )

            val engine = SnakeEngine(
                random = random,
                body = initialBody,
                direction = Direction.RIGHT,
                pendingDirection = null,
                food = SnakeCell(row = 0, col = 0),
                foodsEaten = 0,
                score = 0,
                gameOver = false,
                wrapAround = wrapAround,
                difficulty = difficulty,
            )

            val initialFood = run {
                val occupied = initialBody.toHashSet()
                val emptyCells = buildList {
                    for (row in 0 until SNAKE_ROWS) {
                        for (col in 0 until SNAKE_COLUMNS) {
                            val cell = SnakeCell(row = row, col = col)
                            if (cell !in occupied) add(cell)
                        }
                    }
                }
                emptyCells[random.nextInt(emptyCells.size)]
            }
            engine.food = initialFood
            return engine
        }

        fun restore(
            snapshot: SnakeSnapshot,
            random: Random = Random.Default,
        ): SnakeEngine = SnakeEngine(
            random = random,
            body = snapshot.body.toMutableList(),
            direction = snapshot.direction,
            pendingDirection = snapshot.pendingDirection,
            food = snapshot.food,
            foodsEaten = snapshot.foodsEaten.coerceAtLeast(0),
            score = snapshot.score.coerceAtLeast(0),
            gameOver = snapshot.gameOver,
            wrapAround = snapshot.wrapAround,
            difficulty = snapshot.difficulty,
        )
    }
}

private fun Direction.rowDelta(): Int = when (this) {
    Direction.UP -> -1
    Direction.DOWN -> 1
    Direction.LEFT, Direction.RIGHT -> 0
}

private fun Direction.colDelta(): Int = when (this) {
    Direction.LEFT -> -1
    Direction.RIGHT -> 1
    Direction.UP, Direction.DOWN -> 0
}
