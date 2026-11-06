package com.playbox.app.games.minesweeper

import kotlin.random.Random
import kotlinx.serialization.Serializable

@Serializable
enum class MinesweeperDifficulty(
    val label: String,
    val rows: Int,
    val columns: Int,
    val mines: Int,
    val baseScore: Int,
) {
    EASY("初级", 9, 9, 10, 1000),
    MEDIUM("中级", 12, 12, 25, 2200),
    HARD("高级", 16, 16, 45, 4000),
}

@Serializable
data class MineCell(
    val row: Int,
    val column: Int,
    val isMine: Boolean = false,
    val adjacentMines: Int = 0,
    val revealed: Boolean = false,
    val flagged: Boolean = false,
)

@Serializable
data class MinesweeperSnapshot(
    val cells: List<MineCell>,
    val difficulty: MinesweeperDifficulty,
    val initialized: Boolean,
    val isGameOver: Boolean,
    val isWon: Boolean,
)

enum class RevealOutcome {
    IGNORED,
    REVEALED,
    MINE,
    WIN,
}

/**
 * 扫雷纯逻辑引擎。首次翻格之后再布置地雷，并排除首格及其周围八格。
 */
class MinesweeperEngine private constructor(
    private val random: Random,
    private val difficulty: MinesweeperDifficulty,
    private var cells: MutableList<MineCell>,
    private var initialized: Boolean,
    private var gameOver: Boolean,
    private var won: Boolean,
) {
    val currentCells: List<MineCell> get() = cells.toList()
    val currentDifficulty: MinesweeperDifficulty get() = difficulty
    val isGameOver: Boolean get() = gameOver
    val isWon: Boolean get() = won
    val isInitialized: Boolean get() = initialized
    val minesRemaining: Int get() = difficulty.mines - cells.count { it.flagged }

    fun reveal(row: Int, column: Int): RevealOutcome {
        if (gameOver || row !in 0 until difficulty.rows || column !in 0 until difficulty.columns) {
            return RevealOutcome.IGNORED
        }
        if (!initialized) ensureMines(row, column)
        val index = index(row, column)
        val cell = cells[index]
        if (cell.revealed || cell.flagged) return RevealOutcome.IGNORED

        if (cell.isMine) {
            revealAllMines()
            gameOver = true
            return RevealOutcome.MINE
        }

        floodReveal(row, column)
        if (checkWin()) {
            won = true
            gameOver = true
            return RevealOutcome.WIN
        }
        return RevealOutcome.REVEALED
    }

    fun toggleFlag(row: Int, column: Int): Boolean {
        if (gameOver || row !in 0 until difficulty.rows || column !in 0 until difficulty.columns) {
            return false
        }
        val index = index(row, column)
        val cell = cells[index]
        if (cell.revealed) return false
        cells[index] = cell.copy(flagged = !cell.flagged)
        return true
    }

    private fun ensureMines(safeRow: Int, safeColumn: Int) {
        val forbidden = buildSet {
            for (dr in -1..1) {
                for (dc in -1..1) {
                    val r = safeRow + dr
                    val c = safeColumn + dc
                    if (r in 0 until difficulty.rows && c in 0 until difficulty.columns) {
                        add(index(r, c))
                    }
                }
            }
        }
        val candidates = cells.indices.filterNot { it in forbidden }.toMutableList()
        if (candidates.size < difficulty.mines) {
            cells.indices.filterNot { it == index(safeRow, safeColumn) }.forEach { if (it !in candidates) candidates += it }
        }
        candidates.shuffle(random)
        val mines = candidates.take(difficulty.mines).toSet()
        cells = cells.mapIndexed { cellIndex, cell ->
            val mine = cellIndex in mines
            cell.copy(isMine = mine)
        }.toMutableList()
        for (cell in cells.toList()) {
            if (cell.isMine) continue
            var adjacent = 0
            forEachNeighbor(cell.row, cell.column) { neighbor ->
                if (cells[index(neighbor.row, neighbor.column)].isMine) adjacent += 1
            }
            cells[index(cell.row, cell.column)] = cell.copy(adjacentMines = adjacent)
        }
        initialized = true
    }

    private fun floodReveal(startRow: Int, startColumn: Int) {
        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.add(startRow to startColumn)
        val visited = HashSet<Int>()
        while (stack.isNotEmpty()) {
            val (row, column) = stack.removeLast()
            val cellIndex = index(row, column)
            if (!visited.add(cellIndex)) continue
            val cell = cells[cellIndex]
            if (cell.revealed || cell.flagged) continue
            cells[cellIndex] = cell.copy(revealed = true)
            if (cell.adjacentMines == 0) {
                forEachNeighbor(row, column) { neighbor ->
                    val neighborCell = cells[index(neighbor.row, neighbor.column)]
                    if (!neighborCell.revealed && !neighborCell.flagged) {
                        stack.add(neighbor.row to neighbor.column)
                    }
                }
            }
        }
    }

    private fun checkWin(): Boolean {
        return cells.all { it.isMine || it.revealed }
    }

    private fun revealAllMines() {
        cells = cells.map { cell ->
            if (cell.isMine) cell.copy(revealed = true) else cell
        }.toMutableList()
    }

    private fun forEachNeighbor(row: Int, column: Int, action: (MineCell) -> Unit) {
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = row + dr
                val c = column + dc
                if (r in 0 until difficulty.rows && c in 0 until difficulty.columns) {
                    action(cells[index(r, c)])
                }
            }
        }
    }

    private fun index(row: Int, column: Int): Int = row * difficulty.columns + column

    fun snapshot(): MinesweeperSnapshot = MinesweeperSnapshot(
        cells = cells.toList(),
        difficulty = difficulty,
        initialized = initialized,
        isGameOver = gameOver,
        isWon = won,
    )

    companion object {
        fun newGame(
            difficulty: MinesweeperDifficulty = MinesweeperDifficulty.EASY,
            random: Random = Random.Default,
        ): MinesweeperEngine {
            val cells = buildList {
                repeat(difficulty.rows) { row ->
                    repeat(difficulty.columns) { column ->
                        add(MineCell(row = row, column = column))
                    }
                }
            }
            return MinesweeperEngine(
                random = random,
                difficulty = difficulty,
                cells = cells.toMutableList(),
                initialized = false,
                gameOver = false,
                won = false,
            )
        }

        fun restore(snapshot: MinesweeperSnapshot, random: Random = Random.Default): MinesweeperEngine =
            MinesweeperEngine(
                random = random,
                difficulty = snapshot.difficulty,
                cells = snapshot.cells.toMutableList(),
                initialized = snapshot.initialized,
                gameOver = snapshot.isGameOver,
                won = snapshot.isWon,
            )
    }
}
