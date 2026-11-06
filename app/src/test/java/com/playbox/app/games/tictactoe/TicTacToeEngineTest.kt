package com.playbox.app.games.tictactoe

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TicTacToeEngineTest {

    @Test
    fun `player move places an X`() {
        val engine = TicTacToeEngine(TicTacToeAi.RANDOM, Random(1))

        assertTrue(engine.playPlayer(4))

        assertEquals(TicTacToeEngine.PLAYER, engine.cellAt(4))
    }

    @Test
    fun `occupied cell cannot be played`() {
        val engine = TicTacToeEngine(TicTacToeAi.RANDOM, Random(2))
        engine.playPlayer(0)

        assertFalse(engine.playPlayer(0))
    }

    @Test
    fun `ai always takes a turn after the player`() {
        val engine = TicTacToeEngine(TicTacToeAi.PERFECT, Random(3))
        engine.playPlayer(0)

        engine.playAi()

        assertEquals(1, (0..8).count { engine.cellAt(it) == TicTacToeEngine.AI })
    }
}
