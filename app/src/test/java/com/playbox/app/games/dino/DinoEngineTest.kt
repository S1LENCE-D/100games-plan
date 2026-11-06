package com.playbox.app.games.dino

import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DinoEngineTest {

    @Test
    fun `jump leaves the ground and gravity returns the dino`() {
        val engine = DinoEngine(Random(1))

        assertTrue(engine.jump())
        engine.update(0.1f)
        assertTrue(engine.dinoY > 0f)
        assertTrue(engine.isJumping)

        repeat(100) { engine.update(0.016f) }
        assertFalse(engine.isJumping)
    }

    @Test
    fun `cannot jump twice in the air`() {
        val engine = DinoEngine(Random(2))
        engine.jump()

        assertFalse(engine.jump())
    }

    @Test
    fun `score increases while running`() {
        val engine = DinoEngine(Random(3))
        repeat(60) { engine.update(0.016f) }

        assertTrue(engine.score > 0)
    }

    @Test
    fun `an unjumped cactus ends the run`() {
        val engine = DinoEngine(Random(4))
        repeat(500) { engine.update(0.016f) }

        assertTrue(engine.isGameOver)
    }
}
