package com.playbox.app.games.game2048

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Game2048EngineTest {

    @Test
    fun `merge moves tiles and awards merged value`() {
        val engine = Game2048Engine.restore(
            snapshot = Snapshot2048(
                tiles = listOf(
                    Tile2048(id = 1, value = 2, row = 0, col = 0),
                    Tile2048(id = 2, value = 2, row = 0, col = 1),
                    Tile2048(id = 3, value = 4, row = 0, col = 2),
                ),
                nextTileId = 4,
            ),
            random = Random(7),
        )

        val outcome = engine.move(MoveDirection.LEFT)

        assertTrue(outcome.moved)
        assertEquals(4, outcome.scoreGained)
        assertEquals(4, engine.currentScore)
        assertEquals(3, outcome.animation?.motions?.size)
        assertEquals(4, engine.boardTiles.first { it.row == 0 && it.col == 0 }.value)
        assertEquals(4, engine.boardTiles.first { it.row == 0 && it.col == 1 }.value)
        assertEquals(3, engine.boardTiles.size)
    }

    @Test
    fun `invalid direction does not spawn a tile or use undo`() {
        val engine = Game2048Engine.restore(
            snapshot = Snapshot2048(
                tiles = listOf(
                    Tile2048(id = 1, value = 2, row = 0, col = 0),
                    Tile2048(id = 2, value = 4, row = 0, col = 2),
                ),
                nextTileId = 3,
            ),
            random = Random(1),
        )

        val outcome = engine.move(MoveDirection.UP)

        assertFalse(outcome.moved)
        assertEquals(0, outcome.scoreGained)
        assertNull(outcome.animation)
        assertEquals(2, engine.boardTiles.size)
        assertEquals(0, engine.currentScore)
        assertFalse(engine.canUndo)
    }

    @Test
    fun `undo restores the previous board once per game`() {
        val originalTiles = listOf(
            Tile2048(id = 1, value = 2, row = 0, col = 0),
            Tile2048(id = 2, value = 2, row = 0, col = 1),
        )
        val engine = Game2048Engine.restore(
            snapshot = Snapshot2048(
                tiles = originalTiles,
                nextTileId = 3,
            ),
            random = Random(2),
        )

        engine.move(MoveDirection.LEFT)
        assertTrue(engine.canUndo)
        assertTrue(engine.undo())
        assertEquals(originalTiles, engine.boardTiles)
        assertEquals(0, engine.currentScore)
        assertFalse(engine.canUndo)
        assertFalse(engine.undo())
    }

    @Test
    fun `new game starts with two tiles`() {
        val engine = Game2048Engine.newGame(random = Random(3))

        assertEquals(2, engine.boardTiles.size)
        assertTrue(engine.boardTiles.all { it.value == 2 || it.value == 4 })
        assertEquals(0, engine.currentScore)
        assertFalse(engine.gameOver)
    }
}
