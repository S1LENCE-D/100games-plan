package com.playbox.app.games.uno

import androidx.compose.runtime.Immutable

enum class UnoColor {
    RED,
    YELLOW,
    GREEN,
    BLUE,
    WILD,
}

enum class UnoKind {
    NUMBER,
    SKIP,
    REVERSE,
    DRAW_TWO,
    WILD,
    WILD_DRAW_FOUR,
}

@Immutable
data class UnoCard(
    val id: Int,
    val color: UnoColor,
    val kind: UnoKind,
    val number: Int? = null,
) {
    val isWild: Boolean get() = color == UnoColor.WILD

    fun label(): String = when (kind) {
        UnoKind.NUMBER -> number?.toString() ?: "?"
        UnoKind.SKIP -> "⊘"
        UnoKind.REVERSE -> "⇄"
        UnoKind.DRAW_TWO -> "+2"
        UnoKind.WILD -> "W"
        UnoKind.WILD_DRAW_FOUR -> "+4"
    }
}
