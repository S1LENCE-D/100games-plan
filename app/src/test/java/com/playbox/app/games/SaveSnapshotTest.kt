package com.playbox.app.games

import com.playbox.app.games.chess.ChessColor
import com.playbox.app.games.chess.ChessEngine
import com.playbox.app.games.chess.ChessMove
import com.playbox.app.games.chess.ChessSnapshot
import com.playbox.app.games.chess.ChessSquare
import com.playbox.app.games.gomoku.GomokuEngine
import com.playbox.app.games.gomoku.GomokuSnapshot
import com.playbox.app.games.sudoku.SudokuEngine
import com.playbox.app.games.sudoku.SudokuSnapshot
import com.playbox.app.games.xiangqi.XiangqiEngine
import com.playbox.app.games.xiangqi.XiangqiMove
import com.playbox.app.games.xiangqi.XiangqiPoint
import com.playbox.app.games.xiangqi.XiangqiSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveSnapshotTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `gomoku snapshot round trips`() {
        val engine = GomokuEngine()
        engine.place(7, 7)
        val restored = GomokuEngine.restore(
            json.decodeFromString<GomokuSnapshot>(json.encodeToString(engine.snapshot())),
        )
        assertEquals(engine.board, restored.board)
        assertEquals(engine.currentPlayer, restored.currentPlayer)
    }

    @Test
    fun `chess snapshot restores the side to move`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(ChessMove(ChessSquare(6, 4), ChessSquare(4, 4))))
        val restored = ChessEngine.restore(
            json.decodeFromString<ChessSnapshot>(json.encodeToString(engine.snapshot())),
        )
        assertEquals(ChessColor.BLACK, restored.sideToMove)
        assertEquals(engine.pieceAt(4, 4), restored.pieceAt(4, 4))
    }

    @Test
    fun `xiangqi snapshot restores the position`() {
        val engine = XiangqiEngine()
        assertTrue(engine.makeMove(XiangqiMove(XiangqiPoint(6, 4), XiangqiPoint(5, 4))))
        val restored = XiangqiEngine.restore(
            json.decodeFromString<XiangqiSnapshot>(json.encodeToString(engine.snapshot())),
        )
        assertEquals(engine.sideToMove, restored.sideToMove)
        assertEquals(engine.pieceAt(5, 4), restored.pieceAt(5, 4))
    }

    @Test
    fun `sudoku snapshot restores cells`() {
        val engine = SudokuEngine.newGame()
        val restored = SudokuEngine.restore(
            json.decodeFromString<SudokuSnapshot>(json.encodeToString(engine.snapshot())),
        )
        assertEquals(engine.currentCells, restored.currentCells)
    }
}
