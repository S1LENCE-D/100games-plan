package com.playbox.app.games.breakout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakoutEngineTest {

    private fun snapshot(
        ballX: Float = BREAKOUT_WORLD_WIDTH / 2f,
        ballY: Float = 300f,
        vx: Float = 0f,
        vy: Float = 100f,
        paddle: Float = BREAKOUT_WORLD_WIDTH / 2f,
        bricks: List<BreakoutBrick> = emptyList(),
        lives: Int = 3,
    ) = BreakoutSnapshot(
        ballX = ballX,
        ballY = ballY,
        velocityX = vx,
        velocityY = vy,
        paddleCenter = paddle,
        bricks = bricks,
        score = 0,
        lives = lives,
        isGameOver = false,
        isWon = false,
    )

    @Test
    fun `hitting a brick removes it and adds score`() {
        val bricks = listOf(BreakoutBrick(row = 0, column = 0))
        val engine = BreakoutEngine.restore(
            snapshot(
                ballX = 27f,
                ballY = 78f,
                vy = -100f,
                bricks = bricks,
            ),
        )

        engine.tick(16L)

        assertFalse(engine.currentBricks.single().alive)
        assertEquals(60, engine.currentScore)
    }

    @Test
    fun `missing the paddle costs one life and resets the ball`() {
        val engine = BreakoutEngine.restore(
            snapshot(
                ballX = 20f,
                ballY = BREAKOUT_WORLD_HEIGHT + 20f,
                vy = 200f,
                lives = 2,
                bricks = listOf(BreakoutBrick(row = 0, column = 0)),
            ),
        )

        engine.tick(16L)

        assertEquals(1, engine.currentLives)
        assertTrue(engine.currentBallY < BREAKOUT_WORLD_HEIGHT)
        assertFalse(engine.isGameOver)
    }

    @Test
    fun `paddle hit sends the ball upward`() {
        val engine = BreakoutEngine.restore(
            snapshot(
                ballX = BREAKOUT_WORLD_WIDTH / 2f,
                ballY = BREAKOUT_PADDLE_Y - 2f,
                vy = 100f,
                paddle = BREAKOUT_WORLD_WIDTH / 2f,
            ),
        )

        engine.tick(16L)

        assertTrue(engine.currentVelocityY < 0f)
    }

    @Test
    fun `clearing all bricks wins the round`() {
        val engine = BreakoutEngine.restore(
            snapshot(
                ballY = 200f,
                vy = 0f,
                bricks = listOf(BreakoutBrick(row = 0, column = 0, alive = false)),
            ),
        )

        engine.tick(16L)

        assertTrue(engine.isWon)
        assertTrue(engine.isGameOver)
    }
}
