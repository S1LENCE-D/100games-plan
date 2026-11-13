package com.playbox.app.games.doudizhu

import androidx.compose.runtime.Immutable

enum class DdzSuit {
    SPADE,
    HEART,
    CLUB,
    DIAMOND,
    JOKER,
}

@Immutable
data class DdzCard(
    val id: Int,
    val rank: Int,
    val suit: DdzSuit,
) {
    val isJoker: Boolean get() = rank >= 16

    fun rankLabel(): String = when (rank) {
        11 -> "J"
        12 -> "Q"
        13 -> "K"
        14 -> "A"
        15 -> "2"
        16 -> "小王"
        17 -> "大王"
        else -> rank.toString()
    }

    fun suitLabel(): String = when (suit) {
        DdzSuit.SPADE -> "♠"
        DdzSuit.HEART -> "♥"
        DdzSuit.CLUB -> "♣"
        DdzSuit.DIAMOND -> "♦"
        DdzSuit.JOKER -> ""
    }
}

enum class DdzComboType {
    SINGLE,
    PAIR,
    TRIPLE,
    TRIPLE_SINGLE,
    TRIPLE_PAIR,
    STRAIGHT,
    PAIR_STRAIGHT,
    AIRPLANE,
    AIRPLANE_SINGLE,
    AIRPLANE_PAIR,
    FOUR_TWO_SINGLE,
    FOUR_TWO_PAIR,
    BOMB,
    ROCKET,
}

@Immutable
data class DdzCombo(
    val type: DdzComboType,
    val mainRank: Int,
    val length: Int,
    val cards: List<DdzCard>,
) {
    val isBomb: Boolean get() = type == DdzComboType.BOMB || type == DdzComboType.ROCKET

    fun label(): String = when (type) {
        DdzComboType.SINGLE -> "单张"
        DdzComboType.PAIR -> "对子"
        DdzComboType.TRIPLE -> "三张"
        DdzComboType.TRIPLE_SINGLE -> "三带一"
        DdzComboType.TRIPLE_PAIR -> "三带二"
        DdzComboType.STRAIGHT -> "顺子"
        DdzComboType.PAIR_STRAIGHT -> "连对"
        DdzComboType.AIRPLANE -> "飞机"
        DdzComboType.AIRPLANE_SINGLE -> "飞机带单"
        DdzComboType.AIRPLANE_PAIR -> "飞机带对"
        DdzComboType.FOUR_TWO_SINGLE -> "四带二"
        DdzComboType.FOUR_TWO_PAIR -> "四带两对"
        DdzComboType.BOMB -> "炸弹"
        DdzComboType.ROCKET -> "王炸"
    }
}

fun classifyDdz(cards: List<DdzCard>): DdzCombo? {
    if (cards.isEmpty()) return null
    val sorted = cards.sortedBy { it.rank }
    val counts = sorted.groupingBy { it.rank }.eachCount()
    val ranks = counts.keys.sorted()
    val n = sorted.size

    if (n == 2 && ranks == listOf(16, 17)) {
        return DdzCombo(DdzComboType.ROCKET, 17, 1, sorted)
    }
    if (n == 1) return DdzCombo(DdzComboType.SINGLE, ranks[0], 1, sorted)
    if (n == 2 && counts.size == 1) return DdzCombo(DdzComboType.PAIR, ranks[0], 1, sorted)
    if (n == 3 && counts.size == 1) return DdzCombo(DdzComboType.TRIPLE, ranks[0], 1, sorted)
    if (n == 4 && counts.size == 1) return DdzCombo(DdzComboType.BOMB, ranks[0], 1, sorted)

    if (n == 4 && counts.size == 2 && counts.values.sorted() == listOf(1, 3)) {
        val triple = counts.entries.first { it.value == 3 }.key
        return DdzCombo(DdzComboType.TRIPLE_SINGLE, triple, 1, sorted)
    }
    if (n == 5 && counts.size == 2 && counts.values.sorted() == listOf(2, 3)) {
        val triple = counts.entries.first { it.value == 3 }.key
        return DdzCombo(DdzComboType.TRIPLE_PAIR, triple, 1, sorted)
    }
    if (n == 6 && counts.size == 3 && counts.values.sorted() == listOf(1, 1, 4)) {
        val four = counts.entries.first { it.value == 4 }.key
        return DdzCombo(DdzComboType.FOUR_TWO_SINGLE, four, 1, sorted)
    }
    if (n == 6 && counts.size == 2 && counts.values.sorted() == listOf(2, 4)) {
        val four = counts.entries.first { it.value == 4 }.key
        return DdzCombo(DdzComboType.FOUR_TWO_SINGLE, four, 1, sorted)
    }
    if (n == 8 && counts.size == 3 && counts.values.sorted() == listOf(2, 2, 4)) {
        val four = counts.entries.first { it.value == 4 }.key
        return DdzCombo(DdzComboType.FOUR_TWO_PAIR, four, 1, sorted)
    }

    if (n >= 5 && counts.values.all { it == 1 } && isConsecutive(ranks) && ranks.last() <= 14) {
        return DdzCombo(DdzComboType.STRAIGHT, ranks.last(), n, sorted)
    }
    if (n >= 6 && n % 2 == 0 && counts.values.all { it == 2 } && isConsecutive(ranks) && ranks.last() <= 14) {
        return DdzCombo(DdzComboType.PAIR_STRAIGHT, ranks.last(), ranks.size, sorted)
    }

    val tripleRanks = ranks.filter { counts[it] == 3 }
    if (n % 3 == 0 && tripleRanks.size == ranks.size && tripleRanks.size >= 2 && isConsecutive(tripleRanks) && tripleRanks.last() <= 14) {
        return DdzCombo(DdzComboType.AIRPLANE, tripleRanks.last(), tripleRanks.size, sorted)
    }
    if (n % 4 == 0) {
        val length = n / 4
        if (length >= 2) {
            val candidates = consecutiveTripleSequences(counts, length)
            for (sequence in candidates) {
                val remaining = counts.filterKeys { it !in sequence }
                if (remaining.values.sum() == length) {
                    return DdzCombo(DdzComboType.AIRPLANE_SINGLE, sequence.last(), length, sorted)
                }
            }
        }
    }
    if (n % 5 == 0) {
        val length = n / 5
        if (length >= 2) {
            val candidates = consecutiveTripleSequences(counts, length)
            for (sequence in candidates) {
                val remaining = counts.filterKeys { it !in sequence }
                if (remaining.size == length && remaining.values.all { it == 2 }) {
                    return DdzCombo(DdzComboType.AIRPLANE_PAIR, sequence.last(), length, sorted)
                }
            }
        }
    }
    return null
}

fun canBeatDdz(candidate: DdzCombo, previous: DdzCombo?): Boolean {
    if (previous == null) return true
    if (candidate.type == DdzComboType.ROCKET) return true
    if (previous.type == DdzComboType.ROCKET) return false
    if (candidate.type == DdzComboType.BOMB && previous.type != DdzComboType.BOMB) return true
    if (candidate.type != previous.type) return false
    if (candidate.length != previous.length) return false
    return candidate.mainRank > previous.mainRank
}

private fun isConsecutive(ranks: List<Int>): Boolean =
    ranks.isNotEmpty() && ranks.zipWithNext().all { (a, b) -> b == a + 1 }

private fun consecutiveTripleSequences(counts: Map<Int, Int>, length: Int): List<List<Int>> {
    val tripleRanks = counts.filterValues { it >= 3 }.keys.filter { it <= 14 }.sorted()
    val result = mutableListOf<List<Int>>()
    for (start in tripleRanks) {
        val sequence = (start until start + length).toList()
        if (sequence.all { counts[it] != null && counts.getValue(it) >= 3 }) result += sequence
    }
    return result
}
