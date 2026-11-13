package com.playbox.app.games.chess

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBoardFrame
import com.playbox.app.ui.components.game.GameButtonContent
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameSaveChoiceDialog
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.hypot
import kotlin.math.min

private val ChessHelp = listOf(
    "标准 8×8 国际象棋，白棋先手，目标是将死对方的王。",
    "点击自己的棋子查看合法落点，再点击目标格落子。",
    "支持王车易位、吃过路兵、兵升变、将军、将死、逼和、五十回合和三次重复局面。",
    "人机模式悔棋会同时撤回 AI 的上一手；双人同屏只撤回一手。",
)

private fun pieceSymbol(piece: ChessPiece): String = when (piece.color) {
    ChessColor.WHITE -> when (piece.type) {
        ChessPieceType.KING -> "♔"
        ChessPieceType.QUEEN -> "♕"
        ChessPieceType.ROOK -> "♖"
        ChessPieceType.BISHOP -> "♗"
        ChessPieceType.KNIGHT -> "♘"
        ChessPieceType.PAWN -> "♙"
    }
    ChessColor.BLACK -> when (piece.type) {
        ChessPieceType.KING -> "♚"
        ChessPieceType.QUEEN -> "♛"
        ChessPieceType.ROOK -> "♜"
        ChessPieceType.BISHOP -> "♝"
        ChessPieceType.KNIGHT -> "♞"
        ChessPieceType.PAWN -> "♟"
    }
}

@Composable
fun ChessRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: ChessViewModel = viewModel(
        factory = ChessViewModel.factory(container.gameStatsRepository, container.gameSaveRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val statusText = when {
        !state.hasStarted -> "准备开始"
        state.outcome == ChessOutcome.CHECKMATE -> "将死"
        state.outcome == ChessOutcome.STALEMATE -> "逼和"
        state.outcome != ChessOutcome.ONGOING -> "和棋"
        state.isAiThinking -> "AI 思考中…"
        state.mode == ChessMode.AI && state.sideToMove == state.humanColor -> "轮到你（${if (state.humanColor == ChessColor.WHITE) "白" else "黑"}）"
        state.mode == ChessMode.AI -> "AI 回合"
        state.sideToMove == ChessColor.WHITE -> "白方回合"
        else -> "黑方回合"
    }

    GameScaffold(
        gameName = "国际象棋",
        score = state.score,
        bestScore = bestScore,
        canPause = false,
        isPaused = false,
        onBackRequest = {
            if (state.hasStarted && !state.showResult) {
                showExitConfirmation = true
            } else {
                onExit()
            }
        },
        onTogglePause = {},
        extraStats = listOf(
            GameStatusItem("状态", statusText),
            GameStatusItem("步数", state.moveCount.toString()),
        ),
        onHelpRequest = { showHelp = true },
        onSaveRequest = if (state.hasStarted && !state.isGameOver && !state.isAiThinking) {
            viewModel::saveGame
        } else {
            null
        },
    ) {
        GameContentContainer {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val boardSize = minOf(
                    maxWidth,
                    760.dp,
                    (maxHeight - 140.dp).coerceAtLeast(280.dp),
                )
                Column(
                    modifier = Modifier.width(boardSize),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            state.mode == ChessMode.AI -> "人机对战 · 你执${if (state.humanColor == ChessColor.WHITE) "白" else "黑"}"
                            else -> "双人同屏 · 白棋先手"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boardSize),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        ChessBoard(
                            state = state,
                            onSquareTap = viewModel::tapSquare,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::undo,
                            enabled = state.moveCount > 0 &&
                                !state.isAiThinking &&
                                !state.isGameOver &&
                                !state.showResult,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            GameButtonContent(Icons.Filled.KeyboardArrowLeft, "悔棋")
                        }
                        FilledTonalButton(
                            onClick = viewModel::restart,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) {
                            GameButtonContent(Icons.Filled.Refresh, "重新开局")
                        }
                    }
                }
            }
        }

        if (!state.hasStarted && !state.showSaveChoice) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "国际象棋",
                description = "标准 8×8 国际象棋，白棋先手。挑战 AI，或与身边的人双人同屏。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = ChessMode.entries,
                    selected = state.mode,
                    label = {
                        when (it) {
                            ChessMode.AI -> "人机对战"
                            ChessMode.LOCAL -> "双人同屏"
                        }
                    },
                    onSelect = viewModel::selectMode,
                )
                if (state.mode == ChessMode.AI) {
                    GameSegmentedOptions(
                        options = listOf(ChessColor.WHITE, ChessColor.BLACK),
                        selected = state.humanColor,
                        label = { if (it == ChessColor.WHITE) "执白先手" else "执黑后手" },
                        onSelect = viewModel::selectHumanColor,
                    )
                }
            }
        }
    }

    if (showHelp) {
        GameHelpSheet("国际象棋", ChessHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            autoSave = true,
            onConfirm = {
                showExitConfirmation = false
                viewModel.saveAndExit(onExit)
            },
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (state.showSaveChoice) {
        GameSaveChoiceDialog(
            gameName = "国际象棋",
            onResume = viewModel::resumeSavedGame,
            onNewGame = viewModel::startNewGameFromSave,
            onExit = onExit,
        )
    }
    if (state.pendingPromotion.isNotEmpty()) {
        PromotionDialog(
            options = state.pendingPromotion.mapNotNull { it.promotion }.distinct(),
            onSelect = viewModel::choosePromotion,
            onDismiss = viewModel::cancelPromotion,
        )
    }
    if (state.showResult) {
        GameResultDialog(
            gameName = "国际象棋",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            details = listOf(
                outcomeText(state),
                "共 ${state.moveCount} 步",
                if (state.mode == ChessMode.AI) "人机对战 · 你执${if (state.humanColor == ChessColor.WHITE) "白" else "黑"}" else "双人同屏",
            ),
        )
    }
}

@Composable
private fun PromotionDialog(
    options: List<ChessPieceType>,
    onSelect: (ChessPieceType) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择升变棋子") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GameButtonContent(
                            icon = Icons.Filled.Check,
                            text = when (type) {
                                ChessPieceType.QUEEN -> "后（Queen）"
                                ChessPieceType.ROOK -> "车（Rook）"
                                ChessPieceType.BISHOP -> "象（Bishop）"
                                ChessPieceType.KNIGHT -> "马（Knight）"
                                else -> type.name
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun outcomeText(state: ChessUiState): String = when (state.outcome) {
    ChessOutcome.CHECKMATE -> when {
        state.mode == ChessMode.AI && state.winner == state.humanColor -> "将死 · 你赢了"
        state.mode == ChessMode.AI -> "将死 · AI 获胜"
        state.winner == ChessColor.WHITE -> "将死 · 白方获胜"
        else -> "将死 · 黑方获胜"
    }
    ChessOutcome.STALEMATE -> "逼和"
    ChessOutcome.DRAW_FIFTY -> "五十回合和棋"
    ChessOutcome.DRAW_REPETITION -> "三次重复局面和棋"
    ChessOutcome.DRAW_MATERIAL -> "子力不足和棋"
    ChessOutcome.ONGOING -> "对局进行中"
}

@Composable
internal fun ChessBoard(
    state: ChessUiState,
    onSquareTap: (ChessSquare) -> Unit,
    modifier: Modifier = Modifier,
    flippedOverride: Boolean? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lightSquare = Color(0xFFC7B18C)
    val darkSquare = Color(0xFF67503D)
    val coordinateColor = scheme.onSurfaceVariant.copy(alpha = 0.7f)
    val flipped = flippedOverride ?: (state.mode == ChessMode.AI && state.humanColor == ChessColor.BLACK)

    Canvas(
        modifier = modifier.pointerInput(flipped, state.board, state.isAiThinking, state.isGameOver) {
            detectTapGestures { offset ->
                val padding = size.width * 0.075f
                val cell = (size.width - padding * 2f) / 8f
                val screenColumn = ((offset.x - padding) / cell).toInt()
                val screenRow = ((offset.y - padding) / cell).toInt()
                if (screenRow !in 0..7 || screenColumn !in 0..7) return@detectTapGestures
                val center = Offset(
                    padding + (screenColumn + 0.5f) * cell,
                    padding + (screenRow + 0.5f) * cell,
                )
                if (hypot(offset.x - center.x, offset.y - center.y) > cell * 0.62f) return@detectTapGestures
                val row = if (flipped) 7 - screenRow else screenRow
                val column = if (flipped) 7 - screenColumn else screenColumn
                onSquareTap(ChessSquare(row, column))
            }
        },
    ) {
        drawRect(scheme.surfaceContainerLow)
        val padding = size.width * 0.075f
        val cell = (size.width - padding * 2f) / 8f
        val textStyle = TextStyle(
            fontSize = with(density) { (cell * 0.72f).toSp() },
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
        )
        for (screenRow in 0..7) {
            for (screenColumn in 0..7) {
                val row = if (flipped) 7 - screenRow else screenRow
                val column = if (flipped) 7 - screenColumn else screenColumn
                val topLeft = Offset(padding + screenColumn * cell, padding + screenRow * cell)
                drawRect(
                    color = if ((row + column) % 2 == 0) lightSquare else darkSquare,
                    topLeft = topLeft,
                    size = Size(cell, cell),
                )
                if (state.lastMove?.from == ChessSquare(row, column) ||
                    state.lastMove?.to == ChessSquare(row, column)
                ) {
                    drawRect(
                        color = scheme.tertiary.copy(alpha = 0.22f),
                        topLeft = topLeft,
                        size = Size(cell, cell),
                    )
                }
                if (state.selected == ChessSquare(row, column)) {
                    drawRect(
                        color = scheme.tertiary.copy(alpha = 0.45f),
                        topLeft = topLeft,
                        size = Size(cell, cell),
                    )
                }
                if (state.legalTargets.any { it.row == row && it.column == column }) {
                    drawCircle(
                        color = scheme.tertiary.copy(alpha = 0.72f),
                        radius = cell * 0.13f,
                        center = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f),
                    )
                }
                val piece = state.board[row * 8 + column]
                if (piece != null) {
                    val symbol = pieceSymbol(piece)
                    val measured = textMeasurer.measure(symbol, textStyle)
                    val textTopLeft = Offset(
                        topLeft.x + (cell - measured.size.width) / 2f,
                        topLeft.y + (cell - measured.size.height) / 2f,
                    )
                    val shadow = if (piece.color == ChessColor.WHITE) {
                        Color.Black.copy(alpha = 0.7f)
                    } else {
                        Color.White.copy(alpha = 0.55f)
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = symbol,
                        topLeft = Offset(textTopLeft.x + 1.4f, textTopLeft.y + 1.8f),
                        style = textStyle.copy(color = shadow),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = symbol,
                        topLeft = textTopLeft,
                        style = textStyle.copy(
                            color = if (piece.color == ChessColor.WHITE) Color(0xFFF7F0E2) else Color(0xFF0C0E12),
                        ),
                    )
                }
                if (state.inCheck) {
                    val king = findKing(state.board, state.sideToMove)
                    if (king == ChessSquare(row, column)) {
                        drawCircle(
                            color = scheme.error.copy(alpha = 0.7f),
                            radius = cell * 0.46f,
                            center = Offset(topLeft.x + cell / 2f, topLeft.y + cell / 2f),
                            style = Stroke(width = 3f),
                        )
                    }
                }
            }
        }
        // 坐标
        val coordStyle = TextStyle(
            color = coordinateColor,
            fontSize = with(density) { (cell * 0.16f).toSp() },
            fontFamily = FontFamily.Monospace,
        )
        for (index in 0..7) {
            val file = ('a' + index).toString()
            val rank = (if (flipped) index + 1 else 8 - index).toString()
            val fileMeasured = textMeasurer.measure(file, coordStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = file,
                topLeft = Offset(
                    padding + (index + 0.5f) * cell - fileMeasured.size.width / 2f,
                    size.height - padding * 0.62f,
                ),
                style = coordStyle,
            )
            val rankMeasured = textMeasurer.measure(rank, coordStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = rank,
                topLeft = Offset(
                    padding * 0.2f,
                    padding + (index + 0.5f) * cell - rankMeasured.size.height / 2f,
                ),
                style = coordStyle,
            )
        }
    }
}

private fun findKing(board: List<ChessPiece?>, color: ChessColor): ChessSquare? {
    for (row in 0..7) {
        for (column in 0..7) {
            val piece = board[row * 8 + column]
            if (piece?.type == ChessPieceType.KING && piece.color == color) {
                return ChessSquare(row, column)
            }
        }
    }
    return null
}
