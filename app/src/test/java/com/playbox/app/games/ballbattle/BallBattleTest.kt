package com.playbox.app.games.ballbattle

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BallBattleTest {

    @Test
    fun `random build always spends total points`() {
        repeat(50) {
            val build = BallBuild.random(Random(it))
            assertEquals(BALL_TOTAL_SKILL_POINTS, build.spent)
            assertEquals(0, build.remaining)
        }
    }

    @Test
    fun `adding a point changes derived stats`() {
        val base = BallBuild()
        val powered = base.withPoint(BallAttribute.POWER, 1)
        val vital = base.withPoint(BallAttribute.VITALITY, 1)

        assertEquals(1, powered.power)
        assertTrue(powered.stats.attack > base.stats.attack)
        assertTrue(vital.stats.maxHp > base.stats.maxHp)
        assertTrue(vital.stats.damageReduction > base.stats.damageReduction)
    }

    @Test
    fun `cannot spend more than the point budget`() {
        var build = BallBuild()
        repeat(20) { build = build.withPoint(BallAttribute.POWER, 1) }
        assertEquals(BALL_TOTAL_SKILL_POINTS, build.spent)
        assertEquals(BALL_TOTAL_SKILL_POINTS, build.power)
    }

    @Test
    fun `battle reaches a result and deals damage`() {
        val engine = BallBattleEngine(
            playerBuild = BallBuild(power = 4, vitality = 4, agility = 2, size = 1, luck = 1),
            opponentBuild = BallBuild(power = 2, vitality = 3, agility = 3, size = 2, luck = 2),
            random = Random(7),
        )
        var steps = 0
        while (!engine.isFinished && steps < 4000) {
            engine.step(16L)
            steps += 1
        }
        val snapshot = engine.snapshot()
        assertTrue(engine.isFinished)
        assertTrue(snapshot.elapsedMs > 0)
        assertTrue(snapshot.playerDamageDealt > 0)
        assertTrue(snapshot.winner in 0..2)
    }

    @Test
    fun `balls remain inside the arena`() {
        val engine = BallBattleEngine(
            playerBuild = BallBuild(agility = 12),
            opponentBuild = BallBuild(size = 12),
            random = Random(9),
        )
        repeat(1200) {
            engine.step(16L)
            val snapshot = engine.snapshot()
            assertTrue(snapshot.player.x - snapshot.player.radius >= -1f)
            assertTrue(snapshot.player.x + snapshot.player.radius <= snapshot.arenaWidth + 1f)
            assertTrue(snapshot.opponent.y - snapshot.opponent.radius >= -1f)
            assertTrue(snapshot.opponent.y + snapshot.opponent.radius <= snapshot.arenaHeight + 1f)
        }
    }
}

class BallBattleFeedbackTest {

    @Test
    fun `base ball speed is raised`() {
        assertTrue(BallBuild().stats.moveSpeed >= 175f)
    }

    @Test
    fun `collision emits damage events with positive amounts`() {
        val engine = BallBattleEngine(
            playerBuild = BallBuild(power = 6, vitality = 3, agility = 2, luck = 1),
            opponentBuild = BallBuild(power = 3, vitality = 4, agility = 3, size = 2),
            random = Random(11),
        )
        var sawDamage = false
        repeat(1200) {
            engine.step(16L)
            val events = engine.snapshot().damageEvents
            if (events.isNotEmpty()) {
                sawDamage = true
                assertTrue(events.all { it.amount > 0 })
            }
        }
        assertTrue(sawDamage)
    }
}
