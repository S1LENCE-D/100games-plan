package com.playbox.app.games.liquidsort

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.random.Random

const val LIQUID_TUBE_CAPACITY = 4

@Immutable
@Serializable
data class LiquidSortSnapshot(
    val level: Int,
    val colorCount: Int,
    val tubes: List<List<Int>>,
    val moves: Int,
)

/**
 * 彩液分瓶：每个试管最多 4 层，只能把顶部连续同色液体倒入空管或同色顶部试管。
 * 关卡用“从完成态反向拆分”的方式生成，保证有解。
 */
class LiquidSortEngine private constructor(
    val level: Int,
    private val colorCount: Int,
    private val tubes: MutableList<MutableList<Int>>,
    var moves: Int,
) {
    private val history = ArrayDeque<LiquidSortSnapshot>()

    val tubeCount: Int get() = tubes.size
    val currentTubes: List<List<Int>> get() = tubes.map { it.toList() }
    val canUndo: Boolean get() = history.isNotEmpty()

    fun tube(index: Int): List<Int> = tubes.getOrNull(index)?.toList().orEmpty()

    fun canPour(from: Int, to: Int): Boolean {
        if (from == to) return false
        val source = tubes.getOrNull(from) ?: return false
        val target = tubes.getOrNull(to) ?: return false
        if (source.isEmpty() || target.size >= LIQUID_TUBE_CAPACITY) return false
        val color = source.last()
        return target.isEmpty() || target.last() == color
    }

    fun pour(from: Int, to: Int): Boolean {
        if (!canPour(from, to)) return false
        val source = tubes[from]
        val target = tubes[to]
        val color = source.last()
        var amount = 0
        for (index in source.indices.reversed()) {
            if (source[index] != color) break
            amount += 1
        }
        val space = LIQUID_TUBE_CAPACITY - target.size
        val moved = minOf(amount, space)
        history.addLast(snapshot())
        repeat(moved) {
            source.removeAt(source.lastIndex)
            target.add(color)
        }
        moves += 1
        return true
    }

    fun undo(): Boolean {
        val previous = history.removeLastOrNull() ?: return false
        tubes.clear()
        previous.tubes.forEach { tubes.add(it.toMutableList()) }
        moves = previous.moves
        return true
    }

    fun isSolved(): Boolean = tubes.all { tube ->
        tube.isEmpty() || (tube.size == LIQUID_TUBE_CAPACITY && tube.all { it == tube.first() })
    }

    fun snapshot(): LiquidSortSnapshot = LiquidSortSnapshot(
        level = level,
        colorCount = colorCount,
        tubes = currentTubes,
        moves = moves,
    )

    companion object {
        fun newGame(level: Int): LiquidSortEngine = generate(level)

        fun restore(snapshot: LiquidSortSnapshot): LiquidSortEngine = LiquidSortEngine(
            level = snapshot.level,
            colorCount = snapshot.colorCount,
            tubes = snapshot.tubes.map { it.toMutableList() }.toMutableList(),
            moves = snapshot.moves,
        )

        private fun generate(level: Int): LiquidSortEngine {
            val colorCount = (4 + level - 1).coerceAtMost(10)
            val emptyTubes = if (level <= 2) 2 else 1
            val tubeCount = colorCount + emptyTubes
            var bestTubes: List<MutableList<Int>>? = null
            var bestScore = Int.MIN_VALUE

            repeat(18) { attempt ->
                val random = Random(level * 104_729L + attempt * 7_919L + 31L)
                val working = MutableList(tubeCount) { mutableListOf<Int>() }
                repeat(colorCount) { color ->
                    working[color] = MutableList(LIQUID_TUBE_CAPACITY) { color }
                }
                var previousKey: String? = null
                var beforePreviousKey: String? = null
                repeat(80 + level * 24) {
                    val possible = mutableListOf<Triple<Int, Int, Int>>()
                    working.forEachIndexed { from, source ->
                        if (source.isEmpty()) return@forEachIndexed
                        val color = source.last()
                        var count = 0
                        for (index in source.indices.reversed()) {
                            if (source[index] != color) break
                            count += 1
                        }
                        working.forEachIndexed { to, target ->
                            if (to == from || target.size >= LIQUID_TUBE_CAPACITY) return@forEachIndexed
                            if (target.isEmpty() || target.last() != color) {
                                val maxMove = minOf(count, LIQUID_TUBE_CAPACITY - target.size)
                                for (amount in 1..maxMove) {
                                    if (amount == count && source.size > count) continue
                                    possible += Triple(from, to, amount)
                                }
                            }
                        }
                    }
                    if (possible.isEmpty()) return@repeat
                    val before = working.map { it.toList() }
                    val (from, to, amount) = possible.random(random)
                    val movedColor = working[from].last()
                    repeat(amount) {
                        working[from].removeAt(working[from].lastIndex)
                        working[to].add(movedColor)
                    }
                    val key = working.joinToString("|") { tube -> tube.joinToString(",") }
                    if (key == beforePreviousKey) {
                        // 撤销刚刚的拆分，避免生成过程原地踏步。
                        working.clear()
                        before.forEach { working.add(it.toMutableList()) }
                        return@repeat
                    }
                    beforePreviousKey = previousKey
                    previousKey = key
                }
                val emptyCount = working.count { it.isEmpty() }
                val fullUniform = working.count { tube ->
                    tube.size == LIQUID_TUBE_CAPACITY && tube.distinct().size == 1
                }
                val mixed = working.count { it.distinct().size > 1 }
                val changes = working.sumOf { tube ->
                    tube.zipWithNext().count { (a, b) -> a != b }
                }
                val solved = working.all { it.isEmpty() || it.distinct().size == 1 }
                if (solved) return@repeat
                val desiredEmptyPenalty = if (emptyCount < emptyTubes) 80 else (emptyCount - emptyTubes) * 12
                val score = changes * 12 + mixed * 20 - fullUniform * 90 - desiredEmptyPenalty
                if (mixed >= colorCount - 1 && fullUniform <= 0 && score > bestScore) {
                    bestScore = score
                    bestTubes = working.map { it.toMutableList() }
                }
            }

            val selected = bestTubes
            if (selected != null) {
                return LiquidSortEngine(
                    level = level,
                    colorCount = colorCount,
                    tubes = selected.map { it.toMutableList() }.toMutableList(),
                    moves = 0,
                )
            }

            // 兜底：保证至少四个试管参与分层，且仍可通过反向步骤还原。
            val fallback = MutableList(tubeCount) { mutableListOf<Int>() }
            repeat(colorCount) { color ->
                fallback[color] = MutableList(LIQUID_TUBE_CAPACITY) { color }
            }
            val first = fallback[0].removeAt(3)
            val second = fallback[1].removeAt(3)
            fallback[colorCount].add(first)
            fallback[colorCount].add(second)
            return LiquidSortEngine(level, colorCount, fallback, 0)
        }
    }
}
