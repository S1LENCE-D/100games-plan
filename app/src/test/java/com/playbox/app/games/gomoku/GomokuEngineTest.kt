package com.playbox.app.games.gomoku

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuEngineTest {

    @Test
    fun `horizontal five wins`() {
        val engine = GomokuEngine()
        repeat(4) { index ->
            assertTrue(engine.place(7, 3 + index))
            if (index < 3) assertTrue(engine.place(0, index))
        }
        assertTrue(engine.place(0, 3))
        assertTrue(engine.place(7, 7))
        assertEquals(GOMOKU_BLACK, engine.winner)
        assertTrue(engine.winningLine.size >= 5)
    }

    @Test
    fun `diagonal five wins`() {
        val engine = GomokuEngine()
        repeat(4) { index ->
            assertTrue(engine.place(3 + index, 3 + index))
            if (index < 3) assertTrue(engine.place(0, index))
        }
        assertTrue(engine.place(0, 3))
        assertTrue(engine.place(7, 7))
        assertEquals(GOMOKU_BLACK, engine.winner)
    }

    @Test
    fun `undo reopens the last intersection`() {
        val engine = GomokuEngine()
        engine.place(7, 7)
        assertEquals(GOMOKU_BLACK, engine.cell(7, 7))
        assertTrue(engine.undo())
        assertEquals(0, engine.cell(7, 7))
        assertEquals(GOMOKU_BLACK, engine.currentPlayer)
    }

    @Test
    fun `occupied point cannot be played twice`() {
        val engine = GomokuEngine()
        assertTrue(engine.place(0, 3))
        assertTrue(engine.place(7, 7))
        assertFalse(engine.place(7, 7))
    }

    @Test
    fun `ai opens in the center`() {
        val engine = GomokuEngine(random = Random(3))
        val move = engine.aiMove()
        assertNotNull(move)
        assertEquals(7, move?.row)
        assertEquals(7, move?.column)
    }

    @Test
    fun `ai blocks an immediate four`() {
        val engine = GomokuEngine(random = Random(4))
        // Black builds a four on row 7; White answers elsewhere.
        engine.place(7, 3)
        engine.place(0, 0)
        engine.place(7, 4)
        engine.place(0, 1)
        engine.place(7, 5)
        engine.place(0, 2)
        engine.place(7, 6)
        val move = engine.aiMove()
        assertNotNull(move)
        assertTrue(move!!.row == 7 && (move.column == 2 || move.column == 7))
    }
}
