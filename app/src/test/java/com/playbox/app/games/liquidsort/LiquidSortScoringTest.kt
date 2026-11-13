package com.playbox.app.games.liquidsort

import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidSortScoringTest {
    @Test
    fun `higher levels score more for the same moves`() {
        assertTrue(
            LiquidSortScoring.levelScore(level = 2, colorCount = 5, moves = 12) >
                LiquidSortScoring.levelScore(level = 1, colorCount = 4, moves = 12),
        )
    }

    @Test
    fun `more moves proportionally increase the level score`() {
        assertTrue(
            LiquidSortScoring.levelScore(level = 3, colorCount = 6, moves = 20) >
                LiquidSortScoring.levelScore(level = 3, colorCount = 6, moves = 10),
        )
    }
}
