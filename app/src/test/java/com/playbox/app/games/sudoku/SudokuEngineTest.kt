package com.playbox.app.games.sudoku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuEngineTest {

    @Test
    fun `given cell cannot be changed`() {
        val engine = SudokuEngine.newGame()
        val givenIndex = engine.currentCells.indexOfFirst { it.given }
        val row = givenIndex / 9
        val column = givenIndex % 9
        val before = engine.currentCells[givenIndex].value

        val result = engine.setValue(row, column, if (before == 9) 1 else before + 1)

        assertEquals(SudokuInputResult.IGNORED, result)
        assertEquals(before, engine.currentCells[givenIndex].value)
    }

    @Test
    fun `notes toggle on and off`() {
        val engine = SudokuEngine.newGame()
        val index = engine.currentCells.indexOfFirst { !it.given }
        val row = index / 9
        val column = index % 9

        assertTrue(engine.toggleNote(row, column, 3))
        assertTrue(engine.currentCells[index].notes.contains(3))
        assertTrue(engine.toggleNote(row, column, 3))
        assertFalse(engine.currentCells[index].notes.contains(3))
    }

    @Test
    fun `wrong value increments mistakes`() {
        val engine = SudokuEngine.newGame()
        val index = engine.currentCells.indexOfFirst { !it.given }
        val row = index / 9
        val column = index % 9
        val correct = engine.difficulty.solution[index].digitToInt()
        val wrong = if (correct == 9) 1 else correct + 1

        val result = engine.setValue(row, column, wrong)

        assertEquals(SudokuInputResult.WRONG, result)
        assertEquals(1, engine.currentMistakes)
    }

    @Test
    fun `filling the solution completes the puzzle`() {
        val engine = SudokuEngine.newGame()
        engine.currentCells.forEachIndexed { index, cell ->
            if (!cell.given) {
                val value = engine.difficulty.solution[index].digitToInt()
                engine.setValue(index / 9, index % 9, value)
            }
        }

        assertTrue(engine.isCompleted)
    }
}
