package com.playbox.app.games.xiangqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiangqiEngineTest {

    private fun move(row: Int, column: Int, toRow: Int, toColumn: Int): XiangqiMove =
        XiangqiMove(XiangqiPoint(row, column), XiangqiPoint(toRow, toColumn))

    @Test
    fun `initial red position has legal moves`() {
        val engine = XiangqiEngine()
        assertEquals(XiangqiColor.RED, engine.sideToMove)
        assertTrue(engine.legalMoves().isNotEmpty())
    }

    @Test
    fun `red soldier advances and cannot move sideways before the river`() {
        val engine = XiangqiEngine()
        assertTrue(engine.makeMove(move(6, 4, 5, 4)))
        assertTrue(engine.makeMove(move(0, 1, 2, 2)))
        val redMoves = engine.legalMoves().filter { it.from == XiangqiPoint(5, 4) }
        assertTrue(redMoves.isNotEmpty())
        assertTrue(redMoves.all { it.to.row < 5 })
    }

    @Test
    fun `undo restores the initial position`() {
        val engine = XiangqiEngine()
        assertTrue(engine.makeMove(move(6, 4, 5, 4)))
        assertEquals(XiangqiPieceType.SOLDIER, engine.pieceAt(5, 4)?.type)
        assertTrue(engine.undo())
        assertNull(engine.pieceAt(5, 4))
        assertEquals(XiangqiPieceType.SOLDIER, engine.pieceAt(6, 4)?.type)
        assertEquals(XiangqiColor.RED, engine.sideToMove)
    }

    @Test
    fun `ai returns a legal move`() {
        val engine = XiangqiEngine()
        val aiMove = engine.chooseAiMove(2)
        assertNotNull(aiMove)
        assertTrue(aiMove in engine.legalMoves())
        assertTrue(engine.makeMove(aiMove!!))
    }
}
