package com.playbox.app.games.uno

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnoEngineTest {

    @Test
    fun `initial deal contains 108 cards and seven per player`() {
        val engine = UnoEngine(random = Random(1))
        val snapshot = engine.snapshot()
        assertEquals(4, snapshot.hands.size)
        assertTrue(snapshot.hands.all { it.size == 7 })
        assertNotNull(snapshot.discardTop)
        assertEquals(108, snapshot.hands.sumOf { it.size } + snapshot.drawPileSize + 1)
    }

    @Test
    fun `starting card is a number and human goes first`() {
        val engine = UnoEngine(random = Random(2))
        val snapshot = engine.snapshot()
        assertEquals(UnoKind.NUMBER, snapshot.discardTop?.kind)
        assertEquals(0, snapshot.currentPlayer)
        assertTrue(snapshot.humanTurn)
    }

    @Test
    fun `ai chooses a legal playable card or draws`() {
        val engine = UnoEngine(random = Random(3))
        val choice = engine.aiChoice()
        if (choice != null) {
            val card = engine.handOf(engine.currentPlayer)[choice]
            assertTrue(engine.playCard(choice, if (card.isWild) UnoColor.RED else null))
        } else {
            engine.drawForCurrent()
            engine.endTurnAfterDraw()
            assertTrue(engine.snapshot().currentPlayer != 0)
        }
    }

    @Test
    fun `drawing keeps turn until the player ends it`() {
        val engine = UnoEngine(random = Random(4))
        val before = engine.snapshot().currentPlayer
        engine.drawForCurrent()
        assertEquals(before, engine.snapshot().currentPlayer)
        engine.endTurnAfterDraw()
        assertTrue(engine.snapshot().currentPlayer != before)
    }

    @Test
    fun `uno call is only available with two cards on your turn`() {
        val engine = UnoEngine(random = Random(5))
        assertTrue(!engine.callUno(0) || engine.handOf(0).size == 2)
    }
}
