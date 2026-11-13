package com.playbox.app.games.xiangqi

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playbox.app.AppContainer
import com.playbox.app.ui.components.game.ExitGameConfirmDialog
import com.playbox.app.ui.components.game.GameBoardFrame
import com.playbox.app.ui.components.game.GameContentContainer
import com.playbox.app.ui.components.game.GameHelpSheet
import com.playbox.app.ui.components.game.GameReadyOverlay
import com.playbox.app.ui.components.game.GameResultDialog
import com.playbox.app.ui.components.game.GameScaffold
import com.playbox.app.ui.components.game.GameSegmentedOptions
import com.playbox.app.ui.components.game.GameStatusItem
import kotlin.math.min
import kotlin.math.roundToInt

private val XiangqiHelp = listOf(
    "标准 9×10 中国象棋，红方先手，目标是将死或困毙对方的将帅。",
    "点击自己的棋子查看合法落点，再点击目标点落子。",
    "马走日但受马腿阻挡；象走田且不能过河，受象眼阻挡；士和将帅不能离开九宫。",
    "炮吃子必须隔一个炮架；兵过河后可以左右移动；将帅不能在同一直线上照面。",
    "人机模式悔棋会同时撤回 AI 的上一手；双人同屏只撤回一手。",
)

private fun pieceLabel(piece: XiangqiPiece): String = when (piece.color) {
    XiangqiColor.RED -> when (piece.type) {
        XiangqiPieceType.GENERAL -> "帅"
        XiangqiPieceType.ADVISOR -> "仕"
        XiangqiPieceType.ELEPHANT -> "相"
        XiangqiPieceType.HORSE -> "马"
        XiangqiPieceType.CHARIOT -> "车"
        XiangqiPieceType.CANNON -> "炮"
        XiangqiPieceType.SOLDIER -> "兵"
    }
    XiangqiColor.BLACK -> when (piece.type) {
        XiangqiPieceType.GENERAL -> "将"
        XiangqiPieceType.ADVISOR -> "士"
        XiangqiPieceType.ELEPHANT -> "象"
        XiangqiPieceType.HORSE -> "马"
        XiangqiPieceType.CHARIOT -> "车"
        XiangqiPieceType.CANNON -> "炮"
        XiangqiPieceType.SOLDIER -> "卒"
    }
}

@Composable
fun XiangqiRouteScreen(
    container: AppContainer,
    onExit: () -> Unit,
) {
    val viewModel: XiangqiViewModel = viewModel(
        factory = XiangqiViewModel.factory(container.gameStatsRepository),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bestScore by viewModel.bestScore.collectAsStateWithLifecycle()
    val tutorialSeen by viewModel.tutorialSeen.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }

    val statusText = when {
        !state.hasStarted -> "准备开始"
        state.outcome == XiangqiOutcome.CHECKMATE -> "将死"
        state.outcome == XiangqiOutcome.STALEMATE -> "困毙"
        state.outcome != XiangqiOutcome.ONGOING -> "和棋"
        state.isAiThinking -> "AI 思考中…"
        state.mode == XiangqiMode.AI && state.sideToMove == state.humanColor -> "轮到你（${if (state.humanColor == XiangqiColor.RED) "红" else "黑"}）"
        state.mode == XiangqiMode.AI -> "AI 回合"
        state.sideToMove == XiangqiColor.RED -> "红方回合"
        else -> "黑方回合"
    }

    GameScaffold(
        gameName = "中国象棋",
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
    ) {
        GameContentContainer {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val maxBoardHeight = (maxHeight - 144.dp).coerceAtLeast(300.dp)
                val boardWidth = minOf(maxWidth, 700.dp, maxBoardHeight * 0.9f)
                val boardHeight = boardWidth * 10f / 9f
                Column(
                    modifier = Modifier.width(boardWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = when {
                            state.mode == XiangqiMode.AI -> "人机对战 · 你执${if (state.humanColor == XiangqiColor.RED) "红" else "黑"}"
                            else -> "双人同屏 · 红方先手"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    GameBoardFrame(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(boardHeight),
                        contentPadding = 0.dp,
                        clipContent = true,
                    ) {
                        XiangqiBoard(
                            state = state,
                            onPointTap = viewModel::tapPoint,
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
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("悔棋")
                        }
                        FilledTonalButton(
                            onClick = viewModel::restart,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("重新开局")
                        }
                    }
                }
            }
        }

        if (!state.hasStarted) {
            GameReadyOverlay(
                showTutorial = tutorialSeen == false,
                title = "中国象棋",
                description = "标准 9×10 中国象棋，红方先手。挑战 AI，或与身边的人双人同屏。",
                onStart = {
                    viewModel.markTutorialSeen()
                    viewModel.startGame()
                },
                onDismissRequest = onExit,
            ) {
                GameSegmentedOptions(
                    options = XiangqiMode.entries,
                    selected = state.mode,
                    label = {
                        when (it) {
                            XiangqiMode.AI -> "人机对战"
                            XiangqiMode.LOCAL -> "双人同屏"
                        }
                    },
                    onSelect = viewModel::selectMode,
                )
                if (state.mode == XiangqiMode.AI) {
                    GameSegmentedOptions(
                        options = listOf(XiangqiColor.RED, XiangqiColor.BLACK),
                        selected = state.humanColor,
                        label = { if (it == XiangqiColor.RED) "执红先手" else "执黑后手" },
                        onSelect = viewModel::selectHumanColor,
                    )
                }
            }
        }
    }

    if (showHelp) {
        GameHelpSheet("中国象棋", XiangqiHelp, onDismiss = { showHelp = false })
    }
    if (showExitConfirmation) {
        ExitGameConfirmDialog(
            onConfirm = onExit,
            onDismiss = { showExitConfirmation = false },
        )
    }
    if (state.showResult) {
        GameResultDialog(
            gameName = "中国象棋",
            score = state.score,
            bestScore = bestScore,
            isNewRecord = state.isNewRecord,
            onReplay = viewModel::restart,
            onExit = onExit,
            details = listOf(
                outcomeText(state),
                "共 ${state.moveCount} 步",
                if (state.mode == XiangqiMode.AI) "人机对战 · 你执${if (state.humanColor == XiangqiColor.RED) "红" else "黑"}" else "双人同屏",
            ),
        )
    }
}

private fun outcomeText(state: XiangqiUiState): String = when (state.outcome) {
    XiangqiOutcome.CHECKMATE -> when {
        state.mode == XiangqiMode.AI && state.winner == state.humanColor -> "将死 · 你赢了"
        state.mode == XiangqiMode.AI -> "将死 · AI 获胜"
        state.winner == XiangqiColor.RED -> "将死 · 红方获胜"
        else -> "将死 · 黑方获胜"
    }
    XiangqiOutcome.STALEMATE -> when {
        state.mode == XiangqiMode.AI && state.winner == state.humanColor -> "困毙 · 你赢了"
        state.mode == XiangqiMode.AI -> "困毙 · AI 获胜"
        state.winner == XiangqiColor.RED -> "困毙 · 红方获胜"
        else -> "困毙 · 黑方获胜"
    }
    XiangqiOutcome.DRAW_MOVE_LIMIT -> "六十回合无吃子和棋"
    XiangqiOutcome.DRAW_REPETITION -> "三次重复局面和棋"
    XiangqiOutcome.ONGOING -> "对局进行中"
}

@Composable
private fun XiangqiBoard(
    state: XiangqiUiState,
    onPointTap: (XiangqiPoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val boardColor = Color(0xFF2B211A)
    val lineColor = Color(0xFFC8A96E).copy(alpha = 0.72f)
    val flipped = state.mode == XiangqiMode.AI && state.humanColor == XiangqiColor.BLACK
    val redColor = Color(0xFFB73731)
    val blackColor = Color(0xFF17191E)
    val tokenColor = Color(0xFFE8DCC4)

    Canvas(
        modifier = modifier.pointerInput(flipped, state.board, state.isAiThinking, state.isGameOver) {
            detectTapGestures { offset ->
                val horizontalPadding = size.width * 0.075f
                val verticalPadding = size.height * 0.055f
                val cellX = (size.width - horizontalPadding * 2f) / 8f
                val cellY = (size.height - verticalPadding * 2f) / 9f
                // 以整格为命中区域，选择离触点最近的交叉点。
                // 手机上单格通常约 48dp，边缘和棋子外圈也能稳定点中。
                val minX = horizontalPadding - cellX * 0.55f
                val maxX = horizontalPadding + 8f * cellX + cellX * 0.55f
                val minY = verticalPadding - cellY * 0.55f
                val maxY = verticalPadding + 9f * cellY + cellY * 0.55f
                if (offset.x !in minX..maxX || offset.y !in minY..maxY) return@detectTapGestures
                val screenColumn = ((offset.x - horizontalPadding) / cellX).roundToInt().coerceIn(0, 8)
                val screenRow = ((offset.y - verticalPadding) / cellY).roundToInt().coerceIn(0, 9)
                val row = if (flipped) 9 - screenRow else screenRow
                val column = if (flipped) 8 - screenColumn else screenColumn
                onPointTap(XiangqiPoint(row, column))
            }
        },
    ) {
        drawRect(boardColor)
        val horizontalPadding = size.width * 0.075f
        val verticalPadding = size.height * 0.055f
        val cellX = (size.width - horizontalPadding * 2f) / 8f
        val cellY = (size.height - verticalPadding * 2f) / 9f
        fun point(row: Int, column: Int) = Offset(
            horizontalPadding + column * cellX,
            verticalPadding + row * cellY,
        )

        // 横线
        for (row in 0..9) {
            drawLine(
                color = lineColor,
                start = point(row, 0),
                end = point(row, 8),
                strokeWidth = 1.6f,
            )
        }
        // 竖线：河界处断开
        for (column in 0..8) {
            if (column == 0 || column == 8) {
                drawLine(lineColor, point(0, column), point(9, column), strokeWidth = 1.6f)
            } else {
                drawLine(lineColor, point(0, column), point(4, column), strokeWidth = 1.6f)
                drawLine(lineColor, point(5, column), point(9, column), strokeWidth = 1.6f)
            }
        }
        // 九宫
        listOf(0, 7).forEach { top ->
            drawLine(lineColor, point(top, 3), point(top + 2, 5), strokeWidth = 1.4f)
            drawLine(lineColor, point(top, 5), point(top + 2, 3), strokeWidth = 1.4f)
        }
        // 河界
        val riverStyle = TextStyle(
            color = lineColor.copy(alpha = 0.92f),
            fontSize = with(density) { (cellY * 0.48f).toSp() },
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
        )
        val riverLeft = textMeasurer.measure("楚 河", riverStyle)
        val riverRight = textMeasurer.measure("汉 界", riverStyle)
        drawText(
            textMeasurer = textMeasurer,
            text = "楚 河",
            topLeft = Offset(
                horizontalPadding + cellX * 1.8f - riverLeft.size.width / 2f,
                verticalPadding + cellY * 4.5f - riverLeft.size.height / 2f,
            ),
            style = riverStyle,
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "汉 界",
            topLeft = Offset(
                horizontalPadding + cellX * 6.2f - riverRight.size.width / 2f,
                verticalPadding + cellY * 4.5f - riverRight.size.height / 2f,
            ),
            style = riverStyle,
        )

        val tokenRadius = min(cellX, cellY) * 0.42f
        for (screenRow in 0..9) {
            for (screenColumn in 0..8) {
                val row = if (flipped) 9 - screenRow else screenRow
                val column = if (flipped) 8 - screenColumn else screenColumn
                val center = point(screenRow, screenColumn)
                if (state.lastMove?.from == XiangqiPoint(row, column) ||
                    state.lastMove?.to == XiangqiPoint(row, column)
                ) {
                    drawCircle(
                        color = scheme.tertiary.copy(alpha = 0.42f),
                        radius = tokenRadius * 1.28f,
                        center = center,
                        style = Stroke(width = 2f),
                    )
                }
                if (state.selected == XiangqiPoint(row, column)) {
                    drawCircle(
                        color = scheme.tertiary.copy(alpha = 0.5f),
                        radius = tokenRadius * 1.35f,
                        center = center,
                    )
                }
                if (state.legalTargets.any { it.row == row && it.column == column }) {
                    drawCircle(
                        color = scheme.tertiary.copy(alpha = 0.78f),
                        radius = tokenRadius * 0.28f,
                        center = center,
                    )
                }
                val piece = state.board[row * 9 + column]
                if (piece != null) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.28f),
                        radius = tokenRadius,
                        center = Offset(center.x + 2f, center.y + 3f),
                    )
                    drawCircle(tokenColor, tokenRadius, center)
                    drawCircle(
                        color = if (piece.color == XiangqiColor.RED) redColor else blackColor,
                        radius = tokenRadius,
                        center = center,
                        style = Stroke(width = 2.2f),
                    )
                    val style = TextStyle(
                        color = if (piece.color == XiangqiColor.RED) redColor else blackColor,
                        fontSize = with(density) { (tokenRadius * 1.25f).toSp() },
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                    )
                    val label = pieceLabel(piece)
                    val measured = textMeasurer.measure(label, style)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(
                            center.x - measured.size.width / 2f,
                            center.y - measured.size.height / 2f,
                        ),
                        style = style,
                    )
                }
            }
        }

        if (state.inCheck) {
            val general = findGeneral(state.board, state.sideToMove)
            if (general != null) {
                drawCircle(
                    color = scheme.error,
                    radius = tokenRadius * 1.08f,
                    center = point(
                        if (flipped) 9 - general.row else general.row,
                        if (flipped) 8 - general.column else general.column,
                    ),
                    style = Stroke(width = 3f),
                )
            }
        }
    }
}

private fun findGeneral(board: List<XiangqiPiece?>, color: XiangqiColor): XiangqiPoint? {
    for (row in 0..9) {
        for (column in 0..8) {
            val piece = board[row * 9 + column]
            if (piece?.type == XiangqiPieceType.GENERAL && piece.color == color) {
                return XiangqiPoint(row, column)
            }
        }
    }
    return null
}
