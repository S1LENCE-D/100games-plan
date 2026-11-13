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
            val colorCount = (4 + (level - 1) / 3).coerceIn(4, 10)
            val random = Random(level * 7919L + 17L)
            repeat(24) { attempt ->
                val working = MutableList(colorCount + 2) { mutableListOf<Int>() }
                repeat(colorCount) { color -> working[color] = MutableList(LIQUID_TUBE_CAPACITY) { color } }
                var reverseMoves = 0
                repeat(30 + level * 6) {
                    val possible = mutableListOf<Triple<Int, Int, Int>>()
                    working.forEachIndexed { from, source ->
                        if (source.isEmpty() || source.size >= LIQUID_TUBE_CAPACITY) return@forEachIndexed
                        val color = source.last()
                        var count = 0
                        for (index in source.indices.reversed()) {
                            if (source[index] != color) break
                            count += 1
                        }
                        working.forEachIndexed { to, target ->
                            if (to == from) return@forEachIndexed
                            if (target.size >= LIQUID_TUBE_CAPACITY) return@forEachIndexed
                            if (target.isEmpty() || target.last() != color) {
                                val maxMove = minOf(count, LIQUID_TUBE_CAPACITY - target.size)
                                for (amount in 1..maxMove) {
                                    // 整段移走时，只有清空原管，反向对应才是合法的正向倒液。
                                    if (amount == count && source.size > count) continue
                                    possible += Triple(from, to, amount)
                                }
                            }
                        }
                    }
                    if (possible.isEmpty()) return@repeat
                    val (from, to, amount) = possible.random(random)
                    val movedColor = working[from].last()
                    repeat(amount) {
                        working[from].removeAt(working[from].lastIndex)
                        working[to].add(movedColor)
                    }
                    reverseMoves += 1
                }
                val emptyCount = working.count { it.isEmpty() }
                val mixed = working.any { tube -> tube.isNotEmpty() && tube.distinct().size > 1 }
                if (mixed && emptyCount >= 1 && working.any { it.isNotEmpty() }) {
                    return LiquidSortEngine(
                        level = level,
                        colorCount = colorCount,
                        tubes = working.map { it.toMutableList() }.toMutableList(),
                        moves = 0,
                    )
                }
            }
            // 极少数情况下退化为可解的手工分层布局。
            val fallback = MutableList(colorCount + 2) { mutableListOf<Int>() }
            repeat(colorCount) { color ->
                fallback[color] = MutableList(LIQUID_TUBE_CAPACITY) { color }
            }
            val a = fallback[0].removeAt(3)
            val b = fallback[1].removeAt(3)
            fallback[colorCount].add(a)
            fallback[colorCount].add(b)
            return LiquidSortEngine(level, colorCount, fallback, 0)
        }
    }
}
