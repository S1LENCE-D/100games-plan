package com.playbox.app.games.memory

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class MemoryDifficulty(
    val label: String,
    val rows: Int,
    val columns: Int,
    val baseScore: Int,
) {
    EASY("经典 4×4", 4, 4, 1000),
    MEDIUM("进阶 4×5", 4, 5, 1500),
    CHALLENGE("挑战 5×6", 5, 6, 2200),
    ;

    val pairs: Int get() = rows * columns / 2
}

@Serializable
data class MemoryCard(
    val pairId: Int,
    val symbol: String,
    val faceUp: Boolean = false,
    val matched: Boolean = false,
)

@Serializable
data class MemorySnapshot(
    val cards: List<MemoryCard>,
    val moves: Int,
    val matchedPairs: Int,
    val gameOver: Boolean,
    val difficulty: MemoryDifficulty = MemoryDifficulty.EASY,
)

enum class MemoryFlipResult {
    IGNORED,
    FIRST,
    MATCH,
    MISMATCH,
    WIN,
}

/**
 * 可变牌面的记忆翻牌纯逻辑引擎。
 * 翻错的两张牌由界面层在短暂停留后调用 clearMismatch()。
 */
class MemoryEngine private constructor(
    private val cards: MutableList<MemoryCard>,
    private var moves: Int,
    private var matchedPairs: Int,
    private var gameOver: Boolean,
    val difficulty: MemoryDifficulty,
) {
    val currentCards: List<MemoryCard> get() = cards.toList()
    val currentMoves: Int get() = moves
    val currentMatchedPairs: Int get() = matchedPairs
    val totalPairs: Int get() = difficulty.pairs
    val isGameOver: Boolean get() = gameOver
    private var firstIndex: Int? = null
    private var secondIndex: Int? = null

    val isWaitingForHide: Boolean get() = firstIndex != null && secondIndex != null

    fun flip(index: Int): MemoryFlipResult {
        if (gameOver || index !in cards.indices) return MemoryFlipResult.IGNORED
        val card = cards[index]
        if (card.matched || card.faceUp || isWaitingForHide) return MemoryFlipResult.IGNORED

        cards[index] = card.copy(faceUp = true)
        if (firstIndex == null) {
            firstIndex = index
            return MemoryFlipResult.FIRST
        }

        secondIndex = index
        moves += 1
        val first = cards[firstIndex!!]
        val second = cards[index]
        if (first.pairId == second.pairId) {
            cards[firstIndex!!] = first.copy(matched = true)
            cards[index] = second.copy(matched = true)
            firstIndex = null
            secondIndex = null
            matchedPairs += 1
            if (matchedPairs >= totalPairs) {
                gameOver = true
                return MemoryFlipResult.WIN
            }
            return MemoryFlipResult.MATCH
        }
        return MemoryFlipResult.MISMATCH
    }

    fun clearMismatch() {
        val first = firstIndex ?: return
        val second = secondIndex ?: return
        if (cards[first].matched || cards[second].matched) {
            firstIndex = null
            secondIndex = null
            return
        }
        cards[first] = cards[first].copy(faceUp = false)
        cards[second] = cards[second].copy(faceUp = false)
        firstIndex = null
        secondIndex = null
    }

    fun snapshot(): MemorySnapshot = MemorySnapshot(
        cards = cards.toList(),
        moves = moves,
        matchedPairs = matchedPairs,
        gameOver = gameOver,
        difficulty = difficulty,
    )

    companion object {
        private val SYMBOLS = listOf(
            "●", "▲", "■", "★", "◆", "♥", "♣", "✦",
            "✿", "☾", "☀", "♦", "♠", "▼", "✚", "✱", "❖", "◈",
        )

        fun newGame(
            random: Random = Random.Default,
            difficulty: MemoryDifficulty = MemoryDifficulty.EASY,
        ): MemoryEngine {
            val deck = buildList {
                for (pairId in 0 until difficulty.pairs) {
                    add(MemoryCard(pairId = pairId, symbol = SYMBOLS[pairId]))
                    add(MemoryCard(pairId = pairId, symbol = SYMBOLS[pairId]))
                }
            }.shuffled(random).toMutableList()
            return MemoryEngine(
                cards = deck,
                moves = 0,
                matchedPairs = 0,
                gameOver = false,
                difficulty = difficulty,
            )
        }

        fun restore(snapshot: MemorySnapshot): MemoryEngine = MemoryEngine(
            cards = snapshot.cards.toMutableList(),
            moves = snapshot.moves,
            matchedPairs = snapshot.matchedPairs,
            gameOver = snapshot.gameOver,
            difficulty = snapshot.difficulty,
        )
    }
}
