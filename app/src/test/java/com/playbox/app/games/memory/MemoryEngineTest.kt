package com.playbox.app.games.memory

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryEngineTest {

    @Test
    fun `matching a pair keeps both cards face up`() {
        val engine = MemoryEngine.newGame(Random(1))
        val matching = engine.currentCards
            .withIndex()
            .groupBy { it.value.pairId }
            .values
            .first { it.size == 2 }

        val first = engine.flip(matching[0].index)
        val second = engine.flip(matching[1].index)

        assertEquals(MemoryFlipResult.FIRST, first)
        assertEquals(MemoryFlipResult.MATCH, second)
        assertEquals(1, engine.currentMatchedPairs)
        assertEquals(1, engine.currentMoves)
        assertTrue(engine.currentCards[matching[0].index].matched)
        assertTrue(engine.currentCards[matching[1].index].matched)
    }

    @Test
    fun `mismatching pair can be hidden again`() {
        val engine = MemoryEngine.newGame(Random(2))
        val byPair = engine.currentCards.withIndex().groupBy { it.value.pairId }.values.toList()
        val first = byPair[0][0].index
        val second = byPair[1][0].index

        assertEquals(MemoryFlipResult.FIRST, engine.flip(first))
        assertEquals(MemoryFlipResult.MISMATCH, engine.flip(second))
        assertTrue(engine.isWaitingForHide)

        engine.clearMismatch()

        assertFalse(engine.isWaitingForHide)
        assertFalse(engine.currentCards[first].faceUp)
        assertFalse(engine.currentCards[second].faceUp)
        assertEquals(1, engine.currentMoves)
    }

    @Test
    fun `matching every pair finishes the game`() {
        val engine = MemoryEngine.newGame(Random(3))
        val pairs = engine.currentCards.withIndex().groupBy { it.value.pairId }.values.toList()
        var lastResult: MemoryFlipResult = MemoryFlipResult.IGNORED

        pairs.forEach { pair ->
            engine.flip(pair[0].index)
            lastResult = engine.flip(pair[1].index)
        }

        assertEquals(MemoryFlipResult.WIN, lastResult)
        assertTrue(engine.isGameOver)
        assertEquals(8, engine.currentMatchedPairs)
    }
}

class MemoryDifficultyTest {
    @Test
    fun `medium mode has ten pairs`() {
        val engine = MemoryEngine.newGame(
            random = Random(9),
            difficulty = MemoryDifficulty.MEDIUM,
        )
        assertEquals(20, engine.currentCards.size)
        assertEquals(10, engine.totalPairs)
    }

    @Test
    fun `challenge mode has fifteen pairs`() {
        val engine = MemoryEngine.newGame(
            random = Random(10),
            difficulty = MemoryDifficulty.CHALLENGE,
        )
        assertEquals(30, engine.currentCards.size)
        assertEquals(15, engine.totalPairs)
    }
}
