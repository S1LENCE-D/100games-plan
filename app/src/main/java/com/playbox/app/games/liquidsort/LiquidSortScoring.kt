package com.playbox.app.games.liquidsort

object LiquidSortScoring {
    /**
     * 关卡基础分随关卡和颜色数增加，步数按比例放大本关得分；总分在关卡间累计。
     */
    fun levelScore(level: Int, colorCount: Int, moves: Int): Int {
        val difficulty = 120 + level * 80 + colorCount * 40
        val moveFactor = 100 + moves * 3
        return (difficulty * moveFactor / 100).coerceAtLeast(1)
    }
}
