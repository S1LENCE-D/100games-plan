package com.playbox.app.games.tictactoe

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class TicTacToeAi(val label: String) {
    RANDOM("随机"),
    EASY("简单"),
    PERFECT("完美"),
}

@Serializable
data class TicTacToeSnapshot(
    val board: List<Int>,
    val aiLevel: TicTacToeAi,
    val winner: Int,
    val isDraw: Boolean,
)

/**
 * 井字棋引擎：玩家固定 X 先手，AI 使用 O。
 * 0=空，1=X，2=O。
 */
class TicTacToeEngine(
    val aiLevel: TicTacToeAi,
    private val random: Random = Random.Default,
) {
    private val board = MutableList(9) { 0 }
    var winner: Int = 0
        private set
    var isDraw: Boolean = false
        private set
    var isGameOver: Boolean = false
        private set

    fun cellAt(index: Int): Int = board[index]

    fun playPlayer(index: Int): Boolean {
        if (isGameOver || index !in 0..8 || board[index] != 0) return false
        board[index] = PLAYER
        evaluate()
        return true
    }

    fun playAi() {
        if (isGameOver) return
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return
        val index = when (aiLevel) {
            TicTacToeAi.RANDOM -> empty.random(random)
            TicTacToeAi.EASY -> if (random.nextInt(100) < 65) {
                empty.random(random)
            } else {
                bestMove()
            }
            TicTacToeAi.PERFECT -> bestMove()
        }
        board[index] = AI
        evaluate()
    }

    fun snapshot(): TicTacToeSnapshot = TicTacToeSnapshot(
        board = board.toList(),
        aiLevel = aiLevel,
        winner = winner,
        isDraw = isDraw,
    )

    private fun evaluate() {
        val lines = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
        for (line in lines) {
            val first = board[line[0]]
            if (first != 0 && first == board[line[1]] && first == board[line[2]]) {
                winner = first
                isGameOver = true
                return
            }
        }
        if (board.none { it == 0 }) {
            isDraw = true
            isGameOver = true
        }
    }

    private fun bestMove(): Int {
        var bestScore = Int.MIN_VALUE
        var bestIndex = -1
        for (index in board.indices) {
            if (board[index] != 0) continue
            board[index] = AI
            val score = minimax(isAiTurn = false)
            board[index] = 0
            if (score > bestScore) {
                bestScore = score
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun minimax(isAiTurn: Boolean): Int {
        val lines = listOf(
            intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
            intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
            intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
        )
        for (line in lines) {
            val first = board[line[0]]
            if (first != 0 && first == board[line[1]] && first == board[line[2]]) {
                return if (first == AI) 10 else -10
            }
        }
        if (board.none { it == 0 }) return 0
        if (isAiTurn) {
            var best = Int.MIN_VALUE
            for (index in board.indices) {
                if (board[index] != 0) continue
                board[index] = AI
                best = maxOf(best, minimax(false))
                board[index] = 0
            }
            return best
        }
        var best = Int.MAX_VALUE
        for (index in board.indices) {
            if (board[index] != 0) continue
            board[index] = PLAYER
            best = minOf(best, minimax(true))
            board[index] = 0
        }
        return best
    }

    companion object {
        const val PLAYER = 1
        const val AI = 2
    }
}
