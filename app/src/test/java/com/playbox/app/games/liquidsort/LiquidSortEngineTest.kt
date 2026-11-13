package com.playbox.app.games.liquidsort

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSortEngineTest {

    @Test
    fun `generated level is mixed and has a free tube`() {
        val engine = LiquidSortEngine.newGame(1)
        assertFalse(engine.isSolved())
        assertTrue(engine.currentTubes.any { it.isEmpty() })
        assertTrue(engine.currentTubes.any { it.distinct().size > 1 })
    }

    @Test
    fun `pouring moves the top contiguous color`() {
        val engine = LiquidSortEngine.restore(
            LiquidSortSnapshot(
                level = 1,
                colorCount = 2,
                tubes = listOf(
                    mutableListOf(0, 1, 1),
                    mutableListOf(1),
                    mutableListOf(),
                    mutableListOf(0, 0, 0),
                ),
                moves = 0,
            ),
        )
        assertTrue(engine.pour(0, 2))
        assertEquals(listOf(0), engine.tube(0))
        assertEquals(listOf(1, 1), engine.tube(2))
    }

    @Test
    fun `pouring is rejected for a different top color`() {
        val engine = LiquidSortEngine.restore(
            LiquidSortSnapshot(
                level = 1,
                colorCount = 2,
                tubes = listOf(mutableListOf(0), mutableListOf(1), mutableListOf()),
                moves = 0,
            ),
        )
        assertFalse(engine.pour(0, 1))
    }

    @Test
    fun `undo restores the previous tubes`() {
        val engine = LiquidSortEngine.newGame(1)
        val before = engine.currentTubes
        val from = engine.currentTubes.indexOfFirst { it.isNotEmpty() }
        val to = engine.currentTubes.indexOfFirst { it.isEmpty() }
        assertTrue(engine.pour(from, to))
        assertTrue(engine.undo())
        assertEquals(before, engine.currentTubes)
    }

    @Test
    fun `snapshot round trips`() {
        val engine = LiquidSortEngine.newGame(3)
        val json = Json { ignoreUnknownKeys = true }
        val restored = LiquidSortEngine.restore(
            json.decodeFromString<LiquidSortSnapshot>(json.encodeToString(engine.snapshot())),
        )
        assertEquals(engine.currentTubes, restored.currentTubes)
        assertEquals(engine.level, restored.level)
    }

    @Test
    fun `solved state detects separated colors`() {
        val engine = LiquidSortEngine.restore(
            LiquidSortSnapshot(
                level = 1,
                colorCount = 2,
                tubes = listOf(
                    mutableListOf(0, 0, 0, 0),
                    mutableListOf(1, 1, 1, 1),
                    mutableListOf(),
                ),
                moves = 12,
            ),
        )
        assertTrue(engine.isSolved())
        assertNotEquals(0, engine.moves)
    }
}
