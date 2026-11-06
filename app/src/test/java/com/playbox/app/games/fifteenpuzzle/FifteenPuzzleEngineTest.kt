package com.playbox.app.games.fifteenpuzzle

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FifteenPuzzleEngineTest {

    @Test
    fun `new game contains every tile exactly once`() {
        val engine = FifteenPuzzleEngine.newGame(Random(1))
        val cells = (0..15).map(engine::cellAt)

        assertEquals((0..15).toSet(), cells.toSet())
        assertEquals(16, cells.size)
        assertFalse(engine.isSolved())
    }

    @Test
    fun `only a tile adjacent to the blank can move`() {
        val engine = FifteenPuzzleEngine.newGame(Random(2))
        val blank = (0..15).first { engine.cellAt(it) == 0 }
        val adjacent = when (blank) {
            0 -> 1
            1 -> 2
            2 -> 3
            3 -> 7
            4 -> 0
            5 -> 1
            6 -> 2
            7 -> 3
            8 -> 4
            9 -> 5
            10 -> 6
            11 -> 7
            12 -> 8
            13 -> 9
            14 -> 10
            else -> 11
        }

        assertTrue(engine.move(adjacent))
        assertEquals(0, engine.cellAt(adjacent))
        assertFalse(engine.move(adjacent))
    }
}

class FifteenPuzzleSlideTest {
    @Test
    fun `tap slides a whole row toward the blank`() {
        val engine = FifteenPuzzleEngine.newGame(Random(7))
        val blank = (0..15).first { engine.cellAt(it) == 0 }
        val rowStart = (blank / 4) * 4
        val target = if (blank % 4 < 2) rowStart + 3 else rowStart

        val moved = engine.slideTo(target)

        assertTrue(moved >= 2)
        assertEquals(target, (0..15).first { engine.cellAt(it) == 0 })
        assertEquals(moved, engine.currentMoves())
    }

    @Test
    fun `swipe moves the tile in the gesture direction`() {
        val engine = FifteenPuzzleEngine.newGame(Random(8))
        val blank = (0..15).first { engine.cellAt(it) == 0 }
        val column = blank % 4
        val dx = if (column > 0) -1 else 1
        val target = blank - dx

        val moved = engine.swipe(dx, 0)

        assertEquals(1, moved)
        assertEquals(target, (0..15).first { engine.cellAt(it) == 0 })
    }
}
