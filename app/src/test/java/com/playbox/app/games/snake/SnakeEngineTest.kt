package com.playbox.app.games.snake

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnakeEngineTest {

    @Test
    fun `snake moves and grows when eating food`() {
        val engine = SnakeEngine.restore(
            snapshot = baseSnapshot(
                head = SnakeCell(5, 5),
                food = SnakeCell(5, 7),
            ),
            random = Random(1),
        )

        engine.tick()
        assertEquals(3, engine.length)
        assertEquals(SnakeCell(5, 6), engine.snakeBody.first())

        engine.tick()
        assertEquals(4, engine.length)
        assertEquals(10, engine.currentScore)
        assertFalse(engine.snakeBody.contains(engine.currentFood))
    }

    @Test
    fun `reversing direction is rejected`() {
        val engine = SnakeEngine.restore(
            snapshot = baseSnapshot(),
            random = Random(1),
        )

        assertFalse(engine.turn(Direction.LEFT))
        assertTrue(engine.turn(Direction.UP))
    }

    @Test
    fun `wall collision ends game and wrap mode teleports`() {
        val wallEngine = SnakeEngine.restore(
            snapshot = baseSnapshot(
                head = SnakeCell(0, 5),
                direction = Direction.UP,
                food = SnakeCell(10, 10),
                wrapAround = false,
            ),
            random = Random(1),
        )
        wallEngine.tick()
        assertTrue(wallEngine.isGameOver)

        val wrapEngine = SnakeEngine.restore(
            snapshot = baseSnapshot(
                head = SnakeCell(0, 5),
                direction = Direction.UP,
                food = SnakeCell(10, 10),
                wrapAround = true,
            ),
            random = Random(1),
        )
        wrapEngine.tick()
        assertFalse(wrapEngine.isGameOver)
        assertEquals(SnakeCell(SNAKE_ROWS - 1, 5), wrapEngine.snakeBody.first())
    }

    @Test
    fun `self collision ends game`() {
        val engine = SnakeEngine.restore(
            snapshot = SnakeSnapshot(
                body = listOf(
                    SnakeCell(5, 5),
                    SnakeCell(5, 4),
                    SnakeCell(4, 4),
                    SnakeCell(4, 5),
                    SnakeCell(4, 6),
                    SnakeCell(5, 6),
                ),
                direction = Direction.LEFT,
                pendingDirection = null,
                food = SnakeCell(10, 10),
                foodsEaten = 2,
                score = 20,
                gameOver = false,
                wrapAround = false,
                difficulty = SnakeDifficulty.NORMAL,
            ),
            random = Random(1),
        )

        engine.tick()

        assertTrue(engine.isGameOver)
    }

    @Test
    fun `eating speeds up the snake`() {
        val slow = SnakeEngine.restore(
            snapshot = baseSnapshot(foodsEaten = 0),
            random = Random(1),
        )
        val fast = SnakeEngine.restore(
            snapshot = baseSnapshot(foodsEaten = 10),
            random = Random(1),
        )

        assertTrue(fast.tickIntervalMillis < slow.tickIntervalMillis)
        assertNotEquals(slow.tickIntervalMillis, fast.tickIntervalMillis)
    }

    @Test
    fun `speed never drops below the configured safety minimum`() {
        val engine = SnakeEngine.restore(
            snapshot = baseSnapshot(foodsEaten = 500),
            random = Random(1),
        )

        assertEquals(
            SnakeDifficulty.NORMAL.minTickMillis,
            engine.tickIntervalMillis,
        )
    }

    private fun baseSnapshot(
        head: SnakeCell = SnakeCell(5, 5),
        direction: Direction = Direction.RIGHT,
        food: SnakeCell = SnakeCell(5, 7),
        foodsEaten: Int = 0,
        wrapAround: Boolean = false,
    ): SnakeSnapshot = SnakeSnapshot(
        body = listOf(
            head,
            SnakeCell(head.row, head.col - 1),
            SnakeCell(head.row, head.col - 2),
        ),
        direction = direction,
        pendingDirection = null,
        food = food,
        foodsEaten = foodsEaten,
        score = foodsEaten * 10,
        gameOver = false,
        wrapAround = wrapAround,
        difficulty = SnakeDifficulty.NORMAL,
    )
}
