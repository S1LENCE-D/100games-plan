package com.playbox.app.games.uno

import androidx.compose.runtime.Immutable
import kotlin.random.Random

const val UNO_PLAYER_COUNT = 4
const val UNO_HAND_SIZE = 7

@Immutable
data class UnoSnapshot(
    val hands: List<List<UnoCard>>,
    val discardTop: UnoCard?,
    val drawPileSize: Int,
    val currentColor: UnoColor,
    val currentPlayer: Int,
    val direction: Int,
    val winner: Int?,
    val hasDrawnThisTurn: Boolean,
    val unoDeclared: Boolean,
    val lastAction: String,
    val playableIndices: List<Int>,
) {
    val isGameOver: Boolean get() = winner != null
    val humanTurn: Boolean get() = currentPlayer == 0 && winner == null
}

/**
 * 标准 4 人 UNO：你 + 3 个电脑。
 * 支持数字/颜色匹配、跳过、反转、+2、万能牌、+4、抽牌、牌堆重洗和 UNO 宣告。
 */
class UnoEngine(
    private val playerCount: Int = UNO_PLAYER_COUNT,
    private val random: Random = Random.Default,
) {
    private val drawPile = ArrayDeque<UnoCard>()
    private val discardPile = ArrayDeque<UnoCard>()
    private val hands = List(playerCount) { mutableListOf<UnoCard>() }
    private val declaredUno = BooleanArray(playerCount)
    private var nextCardId = 0

    var currentPlayer: Int = 0
        private set
    var direction: Int = 1
        private set
    var currentColor: UnoColor = UnoColor.WILD
        private set
    var winner: Int? = null
        private set
    var hasDrawnThisTurn: Boolean = false
        private set
    var lastAction: String = ""
        private set

    init {
        buildDeck()
        dealInitialHands()
        chooseStartingCard()
    }

    val discardTop: UnoCard? get() = discardPile.lastOrNull()

    fun handOf(player: Int): List<UnoCard> =
        if (player in hands.indices) hands[player].toList() else emptyList()

    fun playableIndices(player: Int = currentPlayer): List<Int> {
        if (player !in hands.indices || winner != null) return emptyList()
        val top = discardTop ?: return emptyList()
        return hands[player].mapIndexedNotNull { index, card ->
            if (canPlay(card, top, hands[player])) index else null
        }
    }

    fun callUno(player: Int = currentPlayer): Boolean {
        if (player !in hands.indices || winner != null) return false
        if (player != currentPlayer || hands[player].size != 2) return false
        declaredUno[player] = true
        lastAction = "${playerLabel(player)}喊了 UNO！"
        return true
    }

    fun playCard(index: Int, chosenColor: UnoColor? = null): Boolean {
        if (winner != null) return false
        val hand = hands.getOrNull(currentPlayer) ?: return false
        val card = hand.getOrNull(index) ?: return false
        val top = discardTop ?: return false
        if (!canPlay(card, top, hand)) return false
        if (card.isWild && (chosenColor == null || chosenColor == UnoColor.WILD)) return false

        val player = currentPlayer
        hand.removeAt(index)
        discardPile.addLast(card)
        currentColor = if (card.isWild) chosenColor!! else card.color
        declaredUno[player] = if (hand.size == 1) declaredUno[player] else false
        hasDrawnThisTurn = false
        lastAction = "${playerLabel(player)}打出 ${cardLabel(card)}"

        if (hand.isEmpty()) {
            winner = player
            lastAction += "，获胜！"
            return true
        }
        if (hand.size == 1 && !declaredUno[player]) {
            drawCardsTo(player, 2)
            lastAction += "，未喊 UNO 罚抽 2 张"
        }

        when (card.kind) {
            UnoKind.SKIP -> {
                lastAction += "，下一位被跳过"
                advance(2)
            }
            UnoKind.REVERSE -> {
                direction = -direction
                lastAction += "，方向反转"
                advance(1)
            }
            UnoKind.DRAW_TWO -> {
                val target = nextIndex(1)
                drawCardsTo(target, 2)
                lastAction += "，${playerLabel(target)}抽 2 张并跳过"
                advance(2)
            }
            UnoKind.WILD_DRAW_FOUR -> {
                val target = nextIndex(1)
                drawCardsTo(target, 4)
                lastAction += "，${playerLabel(target)}抽 4 张并跳过"
                advance(2)
            }
            else -> advance(1)
        }
        return true
    }

    fun drawForCurrent(): UnoCard? {
        if (winner != null || hasDrawnThisTurn) return null
        val card = drawOne()
        if (card == null) {
            lastAction = "牌堆已空，轮到下一位"
            advance(1)
            return null
        }
        hands[currentPlayer].add(card)
        declaredUno[currentPlayer] = false
        hasDrawnThisTurn = true
        lastAction = "${playerLabel(currentPlayer)}抽了一张牌"
        return card
    }

    fun endTurnAfterDraw() {
        if (winner != null || !hasDrawnThisTurn) return
        hasDrawnThisTurn = false
        advance(1)
    }

    fun advanceTurn(steps: Int = 1) {
        if (winner == null) advance(steps)
    }

    fun aiChoice(): Int? {
        val indices = playableIndices()
        if (indices.isEmpty()) return null
        return indices.maxByOrNull { index ->
            val card = hands[currentPlayer][index]
            when (card.kind) {
                UnoKind.WILD_DRAW_FOUR -> 100
                UnoKind.DRAW_TWO -> 90
                UnoKind.SKIP -> 80
                UnoKind.REVERSE -> 70
                UnoKind.WILD -> 60
                UnoKind.NUMBER -> 20 + (card.number ?: 0)
            }
        }
    }

    fun aiColorChoice(): UnoColor {
        val counts = mutableMapOf<UnoColor, Int>()
        hands[currentPlayer].forEach { card ->
            if (!card.isWild) counts[card.color] = (counts[card.color] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: UnoColor.RED
    }

    fun snapshot(): UnoSnapshot = UnoSnapshot(
        hands = hands.map { it.toList() },
        discardTop = discardTop,
        drawPileSize = drawPile.size,
        currentColor = currentColor,
        currentPlayer = currentPlayer,
        direction = direction,
        winner = winner,
        hasDrawnThisTurn = hasDrawnThisTurn,
        unoDeclared = declaredUno.getOrElse(currentPlayer) { false },
        lastAction = lastAction,
        playableIndices = playableIndices(),
    )

    fun playerLabel(player: Int): String =
        if (player == 0) "你" else "电脑$player"

    private fun canPlay(card: UnoCard, top: UnoCard, hand: List<UnoCard>): Boolean {
        if (card.kind == UnoKind.WILD_DRAW_FOUR) {
            val hasColor = hand.any { !it.isWild && it.color == currentColor }
            if (hasColor) return false
        }
        if (card.isWild) return true
        if (card.color == currentColor) return true
        if (card.kind != top.kind) return false
        return card.kind != UnoKind.NUMBER || card.number == top.number
    }

    private fun advance(steps: Int) {
        if (playerCount <= 1) return
        var next = currentPlayer
        repeat(steps) {
            next = Math.floorMod(next + direction, playerCount)
        }
        currentPlayer = next
        hasDrawnThisTurn = false
    }

    private fun nextIndex(steps: Int): Int =
        Math.floorMod(currentPlayer + direction * steps, playerCount)

    private fun drawCardsTo(player: Int, count: Int) {
        repeat(count) {
            val card = drawOne() ?: return@repeat
            hands[player].add(card)
            declaredUno[player] = false
        }
    }

    private fun drawOne(): UnoCard? {
        if (drawPile.isEmpty()) refillDrawPile()
        return drawPile.removeLastOrNull()
    }

    private fun refillDrawPile() {
        val top = discardPile.removeLastOrNull() ?: return
        while (discardPile.isNotEmpty()) {
            drawPile.addLast(discardPile.removeFirst())
        }
        discardPile.addLast(top)
        drawPile.shuffle(random)
    }

    private fun buildDeck() {
        val colors = listOf(UnoColor.RED, UnoColor.YELLOW, UnoColor.GREEN, UnoColor.BLUE)
        colors.forEach { color ->
            addCard(UnoCard(nextCardId++, color, UnoKind.NUMBER, 0))
            for (number in 1..9) {
                repeat(2) { addCard(UnoCard(nextCardId++, color, UnoKind.NUMBER, number)) }
            }
            repeat(2) {
                addCard(UnoCard(nextCardId++, color, UnoKind.SKIP))
                addCard(UnoCard(nextCardId++, color, UnoKind.REVERSE))
                addCard(UnoCard(nextCardId++, color, UnoKind.DRAW_TWO))
            }
        }
        repeat(4) {
            addCard(UnoCard(nextCardId++, UnoColor.WILD, UnoKind.WILD))
            addCard(UnoCard(nextCardId++, UnoColor.WILD, UnoKind.WILD_DRAW_FOUR))
        }
        drawPile.shuffle(random)
    }

    private fun addCard(card: UnoCard) {
        drawPile.addLast(card)
    }

    private fun dealInitialHands() {
        repeat(UNO_HAND_SIZE) {
            for (player in 0 until playerCount) {
                drawOne()?.let { hands[player].add(it) }
            }
        }
    }

    private fun chooseStartingCard() {
        var candidate = drawPile.removeLastOrNull()
        while (candidate != null && candidate.kind != UnoKind.NUMBER) {
            drawPile.addFirst(candidate)
            candidate = drawPile.removeLastOrNull()
        }
        if (candidate == null) {
            candidate = UnoCard(nextCardId++, UnoColor.RED, UnoKind.NUMBER, 0)
        }
        discardPile.addLast(candidate)
        currentColor = candidate.color
        currentPlayer = 0
        hasDrawnThisTurn = false
        lastAction = "起始牌：${cardLabel(candidate)}，轮到你"
    }

    private fun cardLabel(card: UnoCard): String = when (card.kind) {
        UnoKind.NUMBER -> "${colorLabel(card.color)}${card.number}"
        UnoKind.SKIP -> "${colorLabel(card.color)}跳过"
        UnoKind.REVERSE -> "${colorLabel(card.color)}反转"
        UnoKind.DRAW_TWO -> "${colorLabel(card.color)}+2"
        UnoKind.WILD -> "万能牌"
        UnoKind.WILD_DRAW_FOUR -> "万能+4"
    }

    private fun colorLabel(color: UnoColor): String = when (color) {
        UnoColor.RED -> "红"
        UnoColor.YELLOW -> "黄"
        UnoColor.GREEN -> "绿"
        UnoColor.BLUE -> "蓝"
        UnoColor.WILD -> "万能"
    }
}
