package com.playbox.app.games.nearby

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyProtocolTest {

    @Test
    fun `rock paper scissors outcomes`() {
        assertEquals(RpsOutcome.WIN, judgeRps(RpsChoice.ROCK, RpsChoice.SCISSORS))
        assertEquals(RpsOutcome.LOSE, judgeRps(RpsChoice.ROCK, RpsChoice.PAPER))
        assertEquals(RpsOutcome.DRAW, judgeRps(RpsChoice.PAPER, RpsChoice.PAPER))
    }

    @Test
    fun `players alternate`() {
        assertEquals(NearbyPlayer.GUEST, NearbyPlayer.HOST.other())
        assertEquals(NearbyPlayer.HOST, NearbyPlayer.GUEST.other())
    }

    @Test
    fun `roulette has exactly one bang in six pulls`() {
        val engine = RouletteEngine(Random(7))
        var bangs = 0
        repeat(RouletteEngine.CHAMBER_COUNT) {
            if (engine.pull() == RoulettePull.BANG) bangs++
        }

        assertEquals(1, bangs)
        assertTrue(engine.isOver)
    }

    @Test
    fun `safe pull switches turn`() {
        val engine = RouletteEngine(Random(1))
        val first = engine.pull()
        if (first == RoulettePull.SAFE) {
            assertEquals(NearbyPlayer.GUEST, engine.turn)
        } else {
            assertTrue(engine.isOver)
            assertEquals(NearbyPlayer.HOST, engine.loser)
        }
    }

    @Test
    fun `reset clears roulette state`() {
        val engine = RouletteEngine(Random(2))
        repeat(RouletteEngine.CHAMBER_COUNT) { engine.pull() }
        engine.reset()

        assertFalse(engine.isOver)
        assertEquals(0, engine.chamber)
        assertEquals(NearbyPlayer.HOST, engine.turn)
    }
}

class NearbyRoomNameTest {
    @Test
    fun `room name round trips`() {
        val encoded = NearbyProtocol.encodeRoomName("我的房间")
        assertEquals("我的房间", NearbyProtocol.decodeRoomName(encoded))
    }

    @Test
    fun `room name stays within scan response byte limit`() {
        val encoded = NearbyProtocol.encodeRoomName("这是一个很长很长的房间名称")
        assertTrue(encoded.size <= NearbyProtocol.MAX_ROOM_NAME_BYTES)
        assertTrue(NearbyProtocol.decodeRoomName(encoded).isNotEmpty())
    }

    @Test
    fun `blank room name falls back to default`() {
        val encoded = NearbyProtocol.encodeRoomName("   ")
        assertEquals(NearbyProtocol.DEFAULT_ROOM_NAME, NearbyProtocol.decodeRoomName(encoded))
    }
}

class NearbyTttTest {
    @Test
    fun `host X can win on the local board`() {
        val match = TttMatch(localMark = 1)

        assertEquals(true, match.playLocal(0))
        assertEquals(true, match.playRemote(3))
        assertEquals(true, match.playLocal(1))
        assertEquals(true, match.playRemote(4))
        assertEquals(true, match.playLocal(2))

        assertEquals(1, match.winner)
    }

    @Test
    fun `player cannot move on the remote turn`() {
        val match = TttMatch(localMark = 1)
        match.playLocal(0)

        assertEquals(false, match.playLocal(1))
    }

    @Test
    fun `reset clears the board`() {
        val match = TttMatch(localMark = 2)
        match.playRemote(0)
        match.reset()

        assertTrue(match.board.all { it == 0 })
        assertEquals(0, match.winner)
    }
}
