package com.playbox.app.games.breakout

import kotlinx.serialization.Serializable

const val BREAKOUT_WORLD_WIDTH = 360f
const val BREAKOUT_WORLD_HEIGHT = 540f
const val BREAKOUT_BRICK_ROWS = 6
const val BREAKOUT_BRICK_COLUMNS = 8
const val BREAKOUT_PADDLE_WIDTH = 72f
const val BREAKOUT_PADDLE_HEIGHT = 10f
const val BREAKOUT_PADDLE_Y = 500f
const val BREAKOUT_BALL_RADIUS = 6f
private const val BRICK_MARGIN = 8f
private const val BRICK_GAP = 4f
private const val BRICK_TOP = 64f
private const val BRICK_HEIGHT = 18f
private const val BALL_SPEED = 310f

@Serializable
data class BreakoutBrick(
    val row: Int,
    val column: Int,
    val alive: Boolean = true,
) {
    val points: Int get() = (BREAKOUT_BRICK_ROWS - row) * 10
}

@Serializable
data class BreakoutSnapshot(
    val ballX: Float,
    val ballY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val paddleCenter: Float,
    val bricks: List<BreakoutBrick>,
    val score: Int,
    val lives: Int,
    val isGameOver: Boolean,
    val isWon: Boolean,
)

/**
 * 打砖块纯逻辑引擎。世界坐标固定为 360×540，屏幕层只负责等比缩放。
 */
class BreakoutEngine private constructor(
    private var ballX: Float,
    private var ballY: Float,
    private var velocityX: Float,
    private var velocityY: Float,
    private var paddleCenter: Float,
    private val bricks: MutableList<BreakoutBrick>,
    private var score: Int,
    private var lives: Int,
    private var gameOver: Boolean,
    private var won: Boolean,
) {
    val currentBallX: Float get() = ballX
    val currentBallY: Float get() = ballY
    val currentVelocityX: Float get() = velocityX
    val currentVelocityY: Float get() = velocityY
    val currentPaddleCenter: Float get() = paddleCenter
    val currentBricks: List<BreakoutBrick> get() = bricks.toList()
    val currentScore: Int get() = score
    val currentLives: Int get() = lives
    val isGameOver: Boolean get() = gameOver
    val isWon: Boolean get() = won
    val bricksRemaining: Int get() = bricks.count { it.alive }

    fun movePaddle(fraction: Float) {
        paddleCenter = (fraction.coerceIn(0f, 1f) * BREAKOUT_WORLD_WIDTH)
            .coerceIn(BREAKOUT_PADDLE_WIDTH / 2f, BREAKOUT_WORLD_WIDTH - BREAKOUT_PADDLE_WIDTH / 2f)
    }

    fun tick(deltaMillis: Long) {
        if (gameOver) return
        val dt = deltaMillis.coerceIn(0L, 32L) / 1000f
        if (dt <= 0f) return

        ballX += velocityX * dt
        ballY += velocityY * dt

        if (ballX - BREAKOUT_BALL_RADIUS <= 0f) {
            ballX = BREAKOUT_BALL_RADIUS
            velocityX = kotlin.math.abs(velocityX)
        } else if (ballX + BREAKOUT_BALL_RADIUS >= BREAKOUT_WORLD_WIDTH) {
            ballX = BREAKOUT_WORLD_WIDTH - BREAKOUT_BALL_RADIUS
            velocityX = -kotlin.math.abs(velocityX)
        }
        if (ballY - BREAKOUT_BALL_RADIUS <= 0f) {
            ballY = BREAKOUT_BALL_RADIUS
            velocityY = kotlin.math.abs(velocityY)
        }

        collidePaddle()
        collideBricks()

        if (ballY - BREAKOUT_BALL_RADIUS > BREAKOUT_WORLD_HEIGHT) {
            lives -= 1
            if (lives <= 0) {
                gameOver = true
            } else {
                resetBall()
            }
        }

        if (bricks.none { it.alive }) {
            won = true
            gameOver = true
        }
    }

    private fun collidePaddle() {
        if (velocityY <= 0f) return
        val paddleLeft = paddleCenter - BREAKOUT_PADDLE_WIDTH / 2f
        val paddleRight = paddleCenter + BREAKOUT_PADDLE_WIDTH / 2f
        val ballBottom = ballY + BREAKOUT_BALL_RADIUS
        if (ballBottom < BREAKOUT_PADDLE_Y) return
        if (ballBottom > BREAKOUT_PADDLE_Y + BREAKOUT_PADDLE_HEIGHT + 8f) return
        if (ballX !in paddleLeft..paddleRight) return

        ballY = BREAKOUT_PADDLE_Y - BREAKOUT_BALL_RADIUS
        val hitOffset = ((ballX - paddleCenter) / (BREAKOUT_PADDLE_WIDTH / 2f)).coerceIn(-1f, 1f)
        velocityX = hitOffset * BALL_SPEED * 0.72f
        velocityY = -kotlin.math.sqrt((BALL_SPEED * BALL_SPEED - velocityX * velocityX).coerceAtLeast(1f))
    }

    private fun collideBricks() {
        val ballLeft = ballX - BREAKOUT_BALL_RADIUS
        val ballRight = ballX + BREAKOUT_BALL_RADIUS
        val ballTop = ballY - BREAKOUT_BALL_RADIUS
        val ballBottom = ballY + BREAKOUT_BALL_RADIUS
        val brickWidth = (BREAKOUT_WORLD_WIDTH - BRICK_MARGIN * 2f - BRICK_GAP * (BREAKOUT_BRICK_COLUMNS - 1)) /
            BREAKOUT_BRICK_COLUMNS

        for (index in bricks.indices) {
            val brick = bricks[index]
            if (!brick.alive) continue
            val left = BRICK_MARGIN + brick.column * (brickWidth + BRICK_GAP)
            val top = BRICK_TOP + brick.row * (BRICK_HEIGHT + BRICK_GAP)
            val right = left + brickWidth
            val bottom = top + BRICK_HEIGHT
            if (ballRight < left || ballLeft > right || ballBottom < top || ballTop > bottom) continue

            bricks[index] = brick.copy(alive = false)
            score += brick.points
            val overlapX = minOf(ballRight - left, right - ballLeft)
            val overlapY = minOf(ballBottom - top, bottom - ballTop)
            if (overlapX < overlapY) {
                velocityX = -velocityX
            } else {
                velocityY = -velocityY
            }
            break
        }
    }

    private fun resetBall() {
        ballX = paddleCenter
        ballY = BREAKOUT_PADDLE_Y - 26f
        velocityX = if (paddleCenter < BREAKOUT_WORLD_WIDTH / 2f) 140f else -140f
        velocityY = -BALL_SPEED
    }

    fun snapshot(): BreakoutSnapshot = BreakoutSnapshot(
        ballX = ballX,
        ballY = ballY,
        velocityX = velocityX,
        velocityY = velocityY,
        paddleCenter = paddleCenter,
        bricks = bricks.toList(),
        score = score,
        lives = lives,
        isGameOver = gameOver,
        isWon = won,
    )

    companion object {
        fun newGame(): BreakoutEngine {
            val bricks = buildList {
                repeat(BREAKOUT_BRICK_ROWS) { row ->
                    repeat(BREAKOUT_BRICK_COLUMNS) { column ->
                        add(BreakoutBrick(row = row, column = column))
                    }
                }
            }
            return BreakoutEngine(
                ballX = BREAKOUT_WORLD_WIDTH / 2f,
                ballY = BREAKOUT_PADDLE_Y - 30f,
                velocityX = 140f,
                velocityY = -BALL_SPEED,
                paddleCenter = BREAKOUT_WORLD_WIDTH / 2f,
                bricks = bricks.toMutableList(),
                score = 0,
                lives = 3,
                gameOver = false,
                won = false,
            )
        }

        fun restore(snapshot: BreakoutSnapshot): BreakoutEngine = BreakoutEngine(
            ballX = snapshot.ballX,
            ballY = snapshot.ballY,
            velocityX = snapshot.velocityX,
            velocityY = snapshot.velocityY,
            paddleCenter = snapshot.paddleCenter,
            bricks = snapshot.bricks.toMutableList(),
            score = snapshot.score,
            lives = snapshot.lives,
            gameOver = snapshot.isGameOver,
            won = snapshot.isWon,
        )
    }
}
