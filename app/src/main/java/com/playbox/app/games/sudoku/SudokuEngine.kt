package com.playbox.app.games.sudoku

import kotlinx.serialization.Serializable

@Serializable
enum class SudokuDifficulty(
    val label: String,
    val puzzle: String,
    val solution: String,
    val baseScore: Int,
) {
    EASY(
        "简单",
        "900307154017005060300000820670000038009740010082006095193800500000004000854613079",
        "968327154217485963345961827671259438539748612482136795193872546726594381854613279",
        1200,
    ),
    MEDIUM(
        "中等",
        "020047081708600040056003070100050060060000300037206900003024090600000720000760000",
        "329547681718692543456183279142359867965478312837216954573824196684931725291765438",
        1800,
    ),
    HARD(
        "困难",
        "470060000005001800068090052090040000500007080004318000000054906000000000000780000",
        "472865139935271864168493752893546217516927483724318695387154926241639578659782341",
        2600,
    ),
}

@Serializable
data class SudokuSnapshot(
    val difficulty: SudokuDifficulty,
    val cells: List<SudokuCell>,
    val mistakes: Int,
    val completed: Boolean,
)

@Serializable
data class SudokuCell(
    val value: Int = 0,
    val given: Boolean = false,
    val notes: List<Int> = emptyList(),
)

enum class SudokuInputResult {
    IGNORED,
    WRONG,
    PLACED,
    COMPLETED,
}

/**
 * 9×9 数独引擎。题目内置且均唯一解；输入错误会保留并可被覆盖，同时计入错误次数。
 */
class SudokuEngine private constructor(
    val difficulty: SudokuDifficulty,
    private var cells: MutableList<SudokuCell>,
    private var mistakes: Int,
    private var completed: Boolean,
) {
    val currentCells: List<SudokuCell> get() = cells.toList()
    val currentMistakes: Int get() = mistakes
    val isCompleted: Boolean get() = completed
    val solution: String get() = difficulty.solution
    val givenCount: Int get() = cells.count { it.given }

    fun setValue(row: Int, column: Int, value: Int): SudokuInputResult {
        if (completed || value !in 1..9) return SudokuInputResult.IGNORED
        val index = index(row, column)
        if (index !in cells.indices) return SudokuInputResult.IGNORED
        val cell = cells[index]
        if (cell.given) return SudokuInputResult.IGNORED
        val correct = difficulty.solution[index].digitToInt() == value
        cells[index] = cell.copy(value = value, notes = emptyList())
        if (!correct) {
            mistakes += 1
            return SudokuInputResult.WRONG
        }
        if (isSolved()) {
            completed = true
            return SudokuInputResult.COMPLETED
        }
        return SudokuInputResult.PLACED
    }

    fun toggleNote(row: Int, column: Int, value: Int): Boolean {
        if (completed || value !in 1..9) return false
        val index = index(row, column)
        if (index !in cells.indices) return false
        val cell = cells[index]
        if (cell.given || cell.value != 0) return false
        val notes = cell.notes.toMutableSet()
        if (!notes.add(value)) notes.remove(value)
        cells[index] = cell.copy(notes = notes.sorted())
        return true
    }

    fun erase(row: Int, column: Int): Boolean {
        if (completed) return false
        val index = index(row, column)
        if (index !in cells.indices) return false
        val cell = cells[index]
        if (cell.given) return false
        if (cell.value == 0 && cell.notes.isEmpty()) return false
        cells[index] = cell.copy(value = 0, notes = emptyList())
        return true
    }

    fun snapshot(): SudokuSnapshot = SudokuSnapshot(
        difficulty = difficulty,
        cells = cells.toList(),
        mistakes = mistakes,
        completed = completed,
    )

    fun isCorrect(row: Int, column: Int): Boolean {
        val index = index(row, column)
        val cell = cells.getOrNull(index) ?: return false
        return cell.value == 0 || cell.value == difficulty.solution[index].digitToInt()
    }

    private fun isSolved(): Boolean = cells.indices.all { index ->
        cells[index].value == difficulty.solution[index].digitToInt()
    }

    private fun index(row: Int, column: Int): Int = row * 9 + column

    companion object {
        fun restore(snapshot: SudokuSnapshot): SudokuEngine = SudokuEngine(
            difficulty = snapshot.difficulty,
            cells = snapshot.cells.toMutableList(),
            mistakes = snapshot.mistakes,
            completed = snapshot.completed,
        )

        fun newGame(difficulty: SudokuDifficulty = SudokuDifficulty.EASY): SudokuEngine {
            val cells = difficulty.puzzle.mapIndexed { index, character ->
                val value = character.digitToInt()
                SudokuCell(value = value, given = value != 0)
            }.toMutableList()
            return SudokuEngine(
                difficulty = difficulty,
                cells = cells,
                mistakes = 0,
                completed = false,
            )
        }
    }
}
