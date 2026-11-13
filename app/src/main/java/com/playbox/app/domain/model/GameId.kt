package com.playbox.app.domain.model

/**
 * 游戏的稳定标识。路由、持久化与成就系统都使用这里的 slug，
 * 避免在数据层依赖展示名称。
 */
enum class GameId(val slug: String) {
    GAME_2048("2048"),
    TETRIS("tetris"),
    SNAKE("snake"),
    BREAKOUT("breakout"),
    MINESWEEPER("minesweeper"),
    MEMORY("memory"),
    SUDOKU("sudoku"),
    TIC_TAC_TOE("tic_tac_toe"),
    FIFTEEN_PUZZLE("fifteen_puzzle"),
    REACTION("reaction"),
    DINO("dino"),
    BALL_BATTLE("ball_battle"),
    GOMOKU("gomoku"),
    CHESS("chess"),
    XIANGQI("xiangqi"),
    UNO("uno"),
    DOUDIZHU("doudizhu"),
    LIQUID_SORT("liquid_sort"),
    EATCAT("eatcat"),
    ;

    companion object {
        fun fromSlug(slug: String): GameId? = entries.firstOrNull { it.slug == slug }
    }
}
