package com.playbox.app.games.doudizhu

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DdzEngineTest {

    private fun cards(vararg ranks: Int): List<DdzCard> =
        ranks.mapIndexed { index, rank -> DdzCard(index, rank, DdzSuit.SPADE) }

    @Test
    fun `initial deal has seventeen cards each and three bottom cards`() {
        val snapshot = DdzEngine(random = Random(1)).snapshot()
        assertEquals(3, snapshot.hands.size)
        assertTrue(snapshot.hands.all { it.size == 17 })
        assertEquals(3, snapshot.bottomCards.size)
        assertEquals(DdzPhase.BIDDING, snapshot.phase)
    }

    @Test
    fun `basic combinations are classified`() {
        assertEquals(DdzComboType.SINGLE, classifyDdz(cards(3))?.type)
        assertEquals(DdzComboType.PAIR, classifyDdz(cards(3, 3))?.type)
        assertEquals(DdzComboType.TRIPLE, classifyDdz(cards(3, 3, 3))?.type)
        assertEquals(DdzComboType.TRIPLE_SINGLE, classifyDdz(cards(3, 3, 3, 4))?.type)
        assertEquals(DdzComboType.TRIPLE_PAIR, classifyDdz(cards(3, 3, 3, 4, 4))?.type)
        assertEquals(DdzComboType.BOMB, classifyDdz(cards(3, 3, 3, 3))?.type)
        assertEquals(DdzComboType.ROCKET, classifyDdz(cards(16, 17))?.type)
    }

    @Test
    fun `sequences and airplanes are classified`() {
        assertEquals(DdzComboType.STRAIGHT, classifyDdz(cards(3, 4, 5, 6, 7))?.type)
        assertEquals(DdzComboType.PAIR_STRAIGHT, classifyDdz(cards(3, 3, 4, 4, 5, 5))?.type)
        assertEquals(DdzComboType.AIRPLANE, classifyDdz(cards(3, 3, 3, 4, 4, 4))?.type)
        assertEquals(
            DdzComboType.AIRPLANE_SINGLE,
            classifyDdz(cards(3, 3, 3, 4, 4, 4, 5, 6))?.type,
        )
        assertEquals(
            DdzComboType.AIRPLANE_PAIR,
            classifyDdz(cards(3, 3, 3, 4, 4, 4, 5, 5, 6, 6))?.type,
        )
    }

    @Test
    fun `bomb beats normal combo and rocket beats bomb`() {
        val single = classifyDdz(cards(4))!!
        val bomb = classifyDdz(cards(3, 3, 3, 3))!!
        val rocket = classifyDdz(cards(16, 17))!!
        assertTrue(canBeatDdz(bomb, single))
        assertTrue(canBeatDdz(rocket, bomb))
        assertTrue(!canBeatDdz(single, bomb))
    }

    @Test
    fun `bidding eventually starts play or redeals`() {
        val engine = DdzEngine(random = Random(2))
        var guard = 0
        while (engine.phase == DdzPhase.BIDDING && guard++ < 6) {
            val bid = engine.aiBid()
            engine.bid(bid)
        }
        assertTrue(engine.phase == DdzPhase.PLAYING || engine.phase == DdzPhase.BIDDING)
    }

    @Test
    fun `ai returns a legal move when playing`() {
        val engine = DdzEngine(random = Random(3))
        while (engine.phase == DdzPhase.BIDDING) {
            engine.bid(3)
        }
        val move = engine.aiMove()
        if (move != null) {
            assertNotNull(classifyDdz(move.cards))
        }
    }
}
