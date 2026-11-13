package com.playbox.app.games.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    private fun move(row: Int, column: Int, toRow: Int, toColumn: Int): ChessMove =
        ChessMove(ChessSquare(row, column), ChessSquare(toRow, toColumn))

    @Test
    fun `initial position has twenty legal moves`() {
        val engine = ChessEngine()
        assertEquals(20, engine.legalMoves().size)
    }

    @Test
    fun `fools mate is a checkmate`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move(6, 5, 5, 5))) // f3
        assertTrue(engine.makeMove(move(1, 4, 3, 4))) // e5
        assertTrue(engine.makeMove(move(6, 6, 4, 6))) // g4
        assertTrue(engine.makeMove(move(0, 3, 4, 7))) // Qh4#
        assertEquals(ChessOutcome.CHECKMATE, engine.status.outcome)
        assertEquals(ChessColor.BLACK, engine.status.winner)
        assertNull(engine.chooseAiMove(1))
    }

    @Test
    fun `white can castle kingside after clearing the back rank`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move(6, 4, 4, 4))) // e4
        assertTrue(engine.makeMove(move(1, 4, 3, 4))) // e5
        assertTrue(engine.makeMove(move(7, 6, 5, 5))) // Nf3
        assertTrue(engine.makeMove(move(0, 1, 2, 2))) // Nc6
        assertTrue(engine.makeMove(move(7, 5, 4, 2))) // Bc4
        assertTrue(engine.makeMove(move(0, 6, 2, 5))) // Nf6
        val castle = engine.legalMoves().firstOrNull { it.isCastling && it.to.column == 6 }
        assertNotNull(castle)
        assertTrue(engine.makeMove(castle!!))
        assertEquals(ChessPieceType.KING, engine.pieceAt(7, 6)?.type)
        assertEquals(ChessPieceType.ROOK, engine.pieceAt(7, 5)?.type)
    }

    @Test
    fun `legal move generation handles castling on both sides`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move(6, 4, 4, 4))) // e4
        assertTrue(engine.makeMove(move(1, 4, 3, 4))) // e5
        assertTrue(engine.makeMove(move(7, 6, 5, 5))) // Nf3
        assertTrue(engine.makeMove(move(0, 1, 2, 2))) // Nc6
        assertTrue(engine.makeMove(move(7, 5, 4, 2))) // Bc4
        assertTrue(engine.makeMove(move(0, 6, 2, 5))) // Nf6
        assertTrue(engine.makeMove(move(6, 3, 5, 3))) // d3
        assertTrue(engine.makeMove(move(0, 5, 1, 4))) // Be7
        val moves = engine.legalMoves()
        assertTrue(moves.any { it.isCastling && it.from == ChessSquare(7, 4) && it.to == ChessSquare(7, 6) })
    }

    @Test
    fun `en passant removes the passed pawn`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move(6, 4, 4, 4))) // e4
        assertTrue(engine.makeMove(move(1, 0, 2, 0))) // a6
        assertTrue(engine.makeMove(move(4, 4, 3, 4))) // e5
        assertTrue(engine.makeMove(move(1, 3, 3, 3))) // d5
        val ep = engine.legalMoves().first { it.isEnPassant }
        assertTrue(engine.makeMove(ep))
        assertEquals(ChessPieceType.PAWN, engine.pieceAt(2, 3)?.type)
        assertNull(engine.pieceAt(3, 3))
    }

    @Test
    fun `undo restores the previous position`() {
        val engine = ChessEngine()
        assertTrue(engine.makeMove(move(6, 4, 4, 4)))
        assertEquals(ChessPieceType.PAWN, engine.pieceAt(4, 4)?.type)
        assertTrue(engine.undo())
        assertEquals(0, engine.fullmoveNumber - 1)
        assertEquals(ChessPieceType.PAWN, engine.pieceAt(6, 4)?.type)
        assertNull(engine.pieceAt(4, 4))
    }

    @Test
    fun `ai returns a legal move`() {
        val engine = ChessEngine()
        val aiMove = engine.chooseAiMove(2)
        assertNotNull(aiMove)
        assertTrue(aiMove in engine.legalMoves())
        assertTrue(engine.makeMove(aiMove!!))
    }
}
