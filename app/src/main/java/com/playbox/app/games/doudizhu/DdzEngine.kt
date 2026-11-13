package com.playbox.app.games.doudizhu

import androidx.compose.runtime.Immutable
import kotlin.random.Random

const val DDZ_PLAYER_COUNT = 3
const val DDZ_HAND_SIZE = 17
const val DDZ_BOTTOM_SIZE = 3

enum class DdzPhase {
    BIDDING,
    PLAYING,
    FINISHED,
}

@Immutable
data class DdzPlay(
    val player: Int,
    val cards: List<DdzCard>,
    val combo: DdzCombo,
)

@Immutable
data class DdzSnapshot(
    val phase: DdzPhase,
    val hands: List<List<DdzCard>>,
    val bottomCards: List<DdzCard>,
    val bottomRevealed: Boolean,
    val currentPlayer: Int,
    val currentBidder: Int,
    val highestBid: Int,
    val highestBidder: Int,
    val landlord: Int,
    val lastPlay: DdzPlay?,
    val passCount: Int,
    val multiplier: Int,
    val lastAction: String,
    val winner: Int?,
    val landlordWon: Boolean?,
    val spring: Boolean,
    val antiSpring: Boolean,
) {
    val isGameOver: Boolean get() = phase == DdzPhase.FINISHED
}

class DdzEngine(
    private val random: Random = Random.Default,
) {
    private val deck = mutableListOf<DdzCard>()
    private val hands = List(DDZ_PLAYER_COUNT) { mutableListOf<DdzCard>() }
    private var bottom = mutableListOf<DdzCard>()
    private val bids = IntArray(DDZ_PLAYER_COUNT) { -1 }
    private val playsCount = IntArray(DDZ_PLAYER_COUNT)
    private var nextId = 0

    var phase: DdzPhase = DdzPhase.BIDDING
        private set
    var currentPlayer: Int = 0
        private set
    var currentBidder: Int = 0
        private set
    var highestBid: Int = 0
        private set
    var highestBidder: Int = -1
        private set
    var landlord: Int = -1
        private set
    var lastPlay: DdzPlay? = null
        private set
    var passCount: Int = 0
        private set
    var multiplier: Int = 1
        private set
    var lastAction: String = ""
        private set
    var winner: Int? = null
        private set
    var landlordWon: Boolean? = null
        private set
    var spring: Boolean = false
        private set
    var antiSpring: Boolean = false
        private set

    init {
        dealNewRound()
    }

    fun handOf(player: Int): List<DdzCard> =
        hands.getOrNull(player)?.toList().orEmpty()

    fun bid(points: Int): Boolean {
        if (phase != DdzPhase.BIDDING) return false
        if (points != 0 && (points !in 1..3 || points <= highestBid)) return false
        val player = currentBidder
        bids[player] = points
        if (points > highestBid) {
            highestBid = points
            highestBidder = player
        }
        lastAction = if (points == 0) {
            "${playerLabel(player)}不叫"
        } else {
            "${playerLabel(player)}叫${points}分"
        }
        if (points == 3) {
            finishBidding()
            return true
        }
        val acted = bids.count { it >= 0 }
        if (acted >= DDZ_PLAYER_COUNT) {
            if (highestBidder >= 0) finishBidding() else dealNewRound()
        } else {
            currentBidder = nextBidder()
        }
        return true
    }

    fun aiBid(): Int {
        val player = currentBidder
        val hand = hands[player]
        val strength = biddingStrength(hand)
        var desired = when {
            strength >= 18 -> 3
            strength >= 12 -> 2
            strength >= 7 -> 1
            else -> 0
        }
        if (desired <= highestBid) desired = 0
        return desired.coerceIn(0, 3)
    }

    fun play(cards: List<DdzCard>): Boolean {
        if (phase != DdzPhase.PLAYING) return false
        val hand = hands[currentPlayer]
        if (cards.isEmpty() || !hand.containsAll(cards)) return false
        val combo = classifyDdz(cards) ?: return false
        if (!canBeatDdz(combo, lastPlay?.combo)) return false
        hand.removeAll(cards.toSet())
        lastPlay = DdzPlay(currentPlayer, cards.sortedBy { it.rank }, combo)
        passCount = 0
        playsCount[currentPlayer] += 1
        if (combo.isBomb) {
            multiplier *= 2
            lastAction = "${playerLabel(currentPlayer)}打出${combo.label()}，倍数 ×2"
        } else {
            lastAction = "${playerLabel(currentPlayer)}打出${combo.label()}"
        }
        if (hand.isEmpty()) {
            finishGame(winnerPlayer = currentPlayer)
        } else {
            currentPlayer = (currentPlayer + 1) % DDZ_PLAYER_COUNT
        }
        return true
    }

    fun pass(): Boolean {
        if (phase != DdzPhase.PLAYING) return false
        val last = lastPlay ?: return false
        if (currentPlayer == last.player) return false
        passCount += 1
        lastAction = "${playerLabel(currentPlayer)}不要"
        if (passCount >= 2) {
            currentPlayer = last.player
            lastPlay = null
            passCount = 0
        } else {
            currentPlayer = (currentPlayer + 1) % DDZ_PLAYER_COUNT
        }
        return true
    }

    fun validMovesForHuman(): List<DdzCombo> =
        candidateCombos(hands[0]).filter { canBeatDdz(it, lastPlay?.combo) }

    fun aiMove(): DdzCombo? {
        if (phase != DdzPhase.PLAYING) return null
        val player = currentPlayer
        val hand = hands[player]
        val candidates = candidateCombos(hand).filter { canBeatDdz(it, lastPlay?.combo) }
        if (candidates.isEmpty()) return null
        val finishing = candidates.firstOrNull { it.cards.size == hand.size }
        if (finishing != null) return finishing
        val last = lastPlay
        val isFarmer = player != landlord
        val teammate = if (isFarmer) {
            (0 until DDZ_PLAYER_COUNT).firstOrNull { it != player && it != landlord }
        } else {
            null
        }
        if (last != null && teammate != null && last.player == teammate) {
            // 队友已经出牌，除非自己能一次出完，否则不要压队友。
            return null
        }
        val nonBomb = candidates.filterNot { it.isBomb }
        if (last == null) {
            return nonBomb.maxWithOrNull(
                compareBy<DdzCombo> { it.cards.size }.thenByDescending { -it.mainRank },
            ) ?: candidates.minByOrNull { it.cards.size }
        }
        val opponentLow = hands.getOrNull(last.player)?.size?.let { it <= 2 } == true
        val useBomb = nonBomb.isEmpty() && (opponentLow || hand.size <= 4)
        if (!useBomb && nonBomb.isEmpty()) return null
        val pool = if (useBomb) candidates else nonBomb
        return pool.minWithOrNull(
            compareBy<DdzCombo> { it.cards.size }
                .thenBy { it.mainRank }
                .thenBy { if (it.isBomb) 1 else 0 },
        )
    }

    fun snapshot(): DdzSnapshot = DdzSnapshot(
        phase = phase,
        hands = hands.map { it.sortedBy { card -> card.rank } },
        bottomCards = bottom.toList(),
        bottomRevealed = phase != DdzPhase.BIDDING,
        currentPlayer = currentPlayer,
        currentBidder = currentBidder,
        highestBid = highestBid,
        highestBidder = highestBidder,
        landlord = landlord,
        lastPlay = lastPlay,
        passCount = passCount,
        multiplier = multiplier,
        lastAction = lastAction,
        winner = winner,
        landlordWon = landlordWon,
        spring = spring,
        antiSpring = antiSpring,
    )

    fun playerLabel(player: Int): String = if (player == 0) "你" else "电脑$player"

    private fun dealNewRound() {
        hands.forEach { it.clear() }
        bottom = mutableListOf()
        bids.fill(-1)
        playsCount.fill(0)
        phase = DdzPhase.BIDDING
        currentPlayer = 0
        currentBidder = 0
        highestBid = 0
        highestBidder = -1
        landlord = -1
        lastPlay = null
        passCount = 0
        multiplier = 1
        winner = null
        landlordWon = null
        spring = false
        antiSpring = false
        buildDeck()
        deck.shuffle(random)
        repeat(DDZ_HAND_SIZE) {
            repeat(DDZ_PLAYER_COUNT) { player ->
                deck.removeLastOrNull()?.let { hands[player].add(it) }
            }
        }
        repeat(DDZ_BOTTOM_SIZE) {
            deck.removeLastOrNull()?.let { bottom.add(it) }
        }
        hands.forEach { it.sortBy { card -> card.rank } }
        lastAction = "发牌完成，等待叫地主"
    }

    private fun finishBidding() {
        landlord = highestBidder.coerceAtLeast(0)
        hands[landlord].addAll(bottom)
        hands[landlord].sortBy { it.rank }
        phase = DdzPhase.PLAYING
        currentPlayer = landlord
        lastPlay = null
        passCount = 0
        lastAction = "${playerLabel(landlord)}成为地主，底牌是 ${bottom.joinToString(" ") { it.rankLabel() }}"
    }

    private fun finishGame(winnerPlayer: Int) {
        winner = winnerPlayer
        landlordWon = winnerPlayer == landlord
        if (landlordWon == true) {
            val farmersPlayed = (0 until DDZ_PLAYER_COUNT).filter { it != landlord }.sumOf { playsCount[it] }
            if (farmersPlayed == 0) {
                spring = true
                multiplier *= 2
            }
        } else {
            if (playsCount[landlord] <= 1) {
                antiSpring = true
                multiplier *= 2
            }
        }
        phase = DdzPhase.FINISHED
        lastAction = if (landlordWon == true) "地主获胜" else "农民获胜"
    }

    private fun nextBidder(): Int {
        var next = currentBidder
        repeat(DDZ_PLAYER_COUNT) {
            next = (next + 1) % DDZ_PLAYER_COUNT
            if (bids[next] < 0) return next
        }
        return next
    }

    private fun biddingStrength(hand: List<DdzCard>): Int {
        val counts = hand.groupingBy { it.rank }.eachCount()
        var score = 0
        score += counts[17].orZero() * 4
        score += counts[16].orZero() * 3
        score += counts[15].orZero() * 2
        score += (counts[14].orZero()) * 1
        score += counts.values.count { it == 4 } * 6
        score += counts.values.count { it == 3 } * 2
        return score
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun buildDeck() {
        deck.clear()
        nextId = 0
        val suits = listOf(DdzSuit.SPADE, DdzSuit.HEART, DdzSuit.CLUB, DdzSuit.DIAMOND)
        for (rank in 3..15) {
            suits.forEach { suit ->
                deck.add(DdzCard(nextId++, rank, suit))
            }
        }
        deck.add(DdzCard(nextId++, 16, DdzSuit.JOKER))
        deck.add(DdzCard(nextId++, 17, DdzSuit.JOKER))
    }

    private fun candidateCombos(hand: List<DdzCard>): List<DdzCombo> {
        val result = mutableListOf<DdzCombo>()
        val byRank = hand.groupBy { it.rank }.toSortedMap()
        fun add(cards: List<DdzCard>) {
            val combo = classifyDdz(cards) ?: return
            if (result.none { it.cards.map { card -> card.id }.toSet() == cards.map { card -> card.id }.toSet() }) {
                result += combo
            }
        }

        byRank.forEach { (_, cards) ->
            add(cards.take(1))
            if (cards.size >= 2) add(cards.take(2))
            if (cards.size >= 3) add(cards.take(3))
            if (cards.size >= 4) add(cards.take(4))
        }
        if (byRank.containsKey(16) && byRank.containsKey(17)) {
            add(listOf(byRank.getValue(16).first(), byRank.getValue(17).first()))
        }
        byRank.forEach { (rank, cards) ->
            if (cards.size >= 3) {
                val triple = cards.take(3)
                byRank.forEach { (otherRank, others) ->
                    if (otherRank != rank) {
                        add(triple + others.take(1))
                        if (others.size >= 2) add(triple + others.take(2))
                    }
                }
            }
            if (cards.size >= 4) {
                val four = cards.take(4)
                val others = byRank.filterKeys { it != rank }.values.flatten()
                if (others.size >= 2) add(four + others.take(2))
                val pairRanks = byRank.filterKeys { it != rank }.values.filter { it.size >= 2 }
                if (pairRanks.size >= 2) add(four + pairRanks.take(2).flatMap { it.take(2) })
            }
        }
        val ranks = byRank.keys.filter { it <= 14 }.sorted()
        for (length in 5..12) {
            ranks.forEach { start ->
                val sequence = (start until start + length).toList()
                if (sequence.all { byRank[it]?.isNotEmpty() == true }) {
                    add(sequence.flatMap { byRank.getValue(it).take(1) })
                }
            }
        }
        for (length in 3..5) {
            ranks.forEach { start ->
                val sequence = (start until start + length).toList()
                if (sequence.all { byRank[it]?.size?.let { size -> size >= 2 } == true }) {
                    add(sequence.flatMap { byRank.getValue(it).take(2) })
                }
            }
        }
        val tripleRanks = byRank.filterValues { it.size >= 3 }.keys.filter { it <= 14 }.sorted()
        for (length in 2..5) {
            tripleRanks.forEach { start ->
                val sequence = (start until start + length).toList()
                if (sequence.all { byRank[it]?.size?.let { size -> size >= 3 } == true }) {
                    val base = sequence.flatMap { byRank.getValue(it).take(3) }
                    add(base)
                    val extras = byRank.filterKeys { it !in sequence }.values.flatten().sortedBy { it.rank }
                    if (extras.size >= length) add(base + extras.take(length))
                    val pairExtras = byRank.filterKeys { it !in sequence }.values.filter { it.size >= 2 }
                    if (pairExtras.size >= length) {
                        add(base + pairExtras.take(length).flatMap { it.take(2) })
                    }
                }
            }
        }
        return result
    }
}
