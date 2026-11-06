package com.playbox.app.games.minesweeper

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinesweeperEngineTest {

    @Test
    fun `first reveal is always safe`() {
        val engine = MinesweeperEngine.newGame(MinesweeperDifficulty.EASY, Random(7))

        engine.reveal(4, 4)

        val clicked = engine.currentCells.first { it.row == 4 && it.column == 4 }
        assertFalse(clicked.isMine)
        assertTrue(clicked.revealed)
        assertEquals(10, engine.currentCells.count { it.isMine })
    }

    @Test
    fun `flag toggles and updates remaining mine count`() {
        val engine = MinesweeperEngine.newGame(MinesweeperDifficulty.EASY, Random(3))

        assertTrue(engine.toggleFlag(0, 0))
        assertTrue(engine.currentCells.first { it.row == 0 && it.column == 0 }.flagged)
        assertEquals(9, engine.minesRemaining)

        engine.toggleFlag(0, 0)
        assertFalse(engine.currentCells.first { it.row == 0 && it.column == 0 }.flagged)
        assertEquals(10, engine.minesRemaining)
    }

    @Test
    fun `revealing a mine loses the game`() {
        val cells = buildList {
            repeat(9) { row ->
                repeat(9) { column ->
                    add(MineCell(row = row, column = column, isMine = row == 0 && column == 0))
                }
            }
        }
        val engine = MinesweeperEngine.restore(
            MinesweeperSnapshot(
                cells = cells,
                difficulty = MinesweeperDifficulty.EASY,
                initialized = true,
                isGameOver = false,
                isWon = false,
            ),
        )

        val outcome = engine.reveal(0, 0)

        assertEquals(RevealOutcome.MINE, outcome)
        assertTrue(engine.isGameOver)
        assertFalse(engine.isWon)
    }

    @Test
    fun `revealing the last safe cell wins`() {
        val cells = buildList {
            repeat(9) { row ->
                repeat(9) { column ->
                    add(
                        MineCell(
                            row = row,
                            column = column,
                            isMine = false,
                            revealed = !(row == 8 && column == 8),
                            adjacentMines = 1,
                        ),
                    )
                }
            }
        }
        val engine = MinesweeperEngine.restore(
            MinesweeperSnapshot(
                cells = cells,
                difficulty = MinesweeperDifficulty.EASY,
                initialized = true,
                isGameOver = false,
                isWon = false,
            ),
        )

        val outcome = engine.reveal(8, 8)

        assertEquals(RevealOutcome.WIN, outcome)
        assertTrue(engine.isWon)
    }
}
