package com.playbox.app.games.tetris

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetrisEngineTest {

    @Test
    fun `new game exposes seven unique pieces from the first bag`() {
        val engine = TetrisEngine.newGame(random = Random(42), startLevel = 1)
        val snapshot = engine.snapshot()
        val firstBag = listOfNotNull(snapshot.active?.type) + snapshot.queue.take(6)

        assertEquals(7, firstBag.size)
        assertEquals(TetrominoType.entries.toSet(), firstBag.toSet())
        assertNotNull(engine.nextType)
        assertEquals(200, engine.boardValues.size)
    }

    @Test
    fun `rotation generates four distinct orientations for T piece`() {
        val rotations = (0..3).map { rotation ->
            TetrisEngine.previewCells(TetrominoType.T, rotation).toSet()
        }

        assertEquals(4, rotations.toSet().size)
        assertTrue(rotations.all { it.size == 4 })
    }

    @Test
    fun `hard drop fills bottom row and clears a line`() {
        val emptyBoard = IntArray(TETRIS_ROWS * TETRIS_COLUMNS).also { board ->
            for (column in 0..5) {
                board[(TETRIS_ROWS - 1) * TETRIS_COLUMNS + column] = TetrominoType.I.ordinal + 1
            }
        }
        val engine = TetrisEngine.restore(
            snapshot = TetrisSnapshot(
                board = emptyBoard.toList(),
                active = PieceSnapshot(
                    type = TetrominoType.I,
                    rotation = 0,
                    row = 18,
                    col = 6,
                ),
                queue = listOf(TetrominoType.T, TetrominoType.L),
                score = 0,
                lines = 0,
                level = 1,
                startLevel = 1,
                gameOver = false,
            ),
            random = Random(1),
        )

        engine.hardDrop()

        assertEquals(1, engine.clearedLines)
        assertEquals(100, engine.currentScore)
        assertTrue(engine.boardValues.all { it == 0 })
    }

    @Test
    fun `gravity delay decreases as level rises`() {
        val levelOne = TetrisEngine.newGame(random = Random(5), startLevel = 1)
        val levelFive = TetrisEngine.newGame(random = Random(5), startLevel = 5)

        assertTrue(levelOne.gravityDelayMillis() > levelFive.gravityDelayMillis())
    }
}
