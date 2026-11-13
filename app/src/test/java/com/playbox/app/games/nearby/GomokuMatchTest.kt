package com.playbox.app.games.nearby

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuMatchTest {
    @Test
    fun `host black moves first and turns alternate`() {
        val host = GomokuMatch(localMark = 1)
        val guest = GomokuMatch(localMark = 2)
        assertTrue(host.isLocalTurn())
        assertFalse(guest.isLocalTurn())
        assertTrue(host.playLocal(7, 7))
        assertTrue(guest.playRemote(7, 7))
        assertFalse(host.isLocalTurn())
        assertTrue(guest.isLocalTurn())
        assertTrue(guest.playLocal(7, 8))
        assertTrue(host.playRemote(7, 8))
        assertTrue(host.isLocalTurn())
    }

    @Test
    fun `five connected stones wins`() {
        val host = GomokuMatch(localMark = 1)
        repeat(4) { index ->
            assertTrue(host.playLocal(7, index))
            assertFalse(host.isGameOver)
            assertTrue(host.playRemote(8, index))
        }
        assertTrue(host.playLocal(7, 4))
        assertTrue(host.isGameOver)
        assertTrue(host.winner == 1)
    }
}
