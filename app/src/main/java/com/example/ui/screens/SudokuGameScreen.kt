package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entertainment.SudokuGenerator
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val SUDOKU_MAX_MISTAKES = 3

private data class RestoredSudokuState(
    val board: List<Int>,
    val elapsedSeconds: Int,
    val mistakes: Int,
    val extraLifeUsed: Boolean,
    val isGameOver: Boolean,
    val hasActiveSavedSession: Boolean
)

@Composable
fun SudokuGameScreen(
    puzzleEntity: EntertainmentPuzzleEntity,
    onBack: () -> Unit,
    onSaveProgress: (String, Int) -> Unit,
    onMarkCompleted: (String, Int, Int) -> Unit,
    onNextLevel: (() -> Unit)?
) {
    val sudoku = remember(puzzleEntity.id, puzzleEntity.payloadJson) {
        SudokuGenerator.deserializeSudoku(puzzleEntity.payloadJson)
    }
    if (sudoku == null) {
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("خطا در بارگذاری جدول سودوکو", color = appTextColor(MaterialTheme.colorScheme.onBackground))
        }
        return
    }

    val size = sudoku.size
    val totalCells = size * size

    val restored = remember(puzzleEntity.id) {
        val base = sudoku.puzzle.map { it - '0' }.toMutableList()
        var savedElapsed = 0
        var savedMistakes = puzzleEntity.mistakesCount.coerceIn(0, SUDOKU_MAX_MISTAKES)
        var savedExtraLife = false
        var savedGameOver = false
        var hasSaved = false

        try {
            if (puzzleEntity.savedProgressJson.isNotBlank()) {
                val obj = JSONObject(puzzleEntity.savedProgressJson)
                val savedStr = obj.optString("board", "")
                savedElapsed = obj.optInt("elapsedSeconds", 0).coerceAtLeast(0)
                savedExtraLife = obj.optBoolean("extraLifeUsed", false)
                savedGameOver = obj.optBoolean("isGameOver", false)
                if (obj.has("mistakes")) {
                    savedMistakes = obj.optInt("mistakes", savedMistakes).coerceIn(0, SUDOKU_MAX_MISTAKES)
                }
                if (savedStr.length == totalCells) {
                    var anyUserCell = false
                    for (i in 0 until totalCells) {
                        if (sudoku.puzzle[i] == '0') {
                            val v = savedStr[i] - '0'
                            base[i] = v
                            if (v != 0) anyUserCell = true
                        }
                    }
                    hasSaved = (anyUserCell || savedElapsed > 0) && !puzzleEntity.isCompleted
                } else if (savedElapsed > 0 && !puzzleEntity.isCompleted) {
                    hasSaved = true
                }
            }
        } catch (_: Exception) {
        }

        RestoredSudokuState(
            board = base.toList(),
            elapsedSeconds = savedElapsed,
            mistakes = savedMistakes,
            extraLifeUsed = savedExtraLife,
            isGameOver = savedGameOver,
            hasActiveSavedSession = hasSaved
        )
    }

    var board by remember(puzzleEntity.id) { mutableStateOf(restored.board) }
    var notes by remember(puzzleEntity.id) { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    var undoStack by remember(puzzleEntity.id) { mutableStateOf<List<List<Int>>>(emptyList()) }
    var selectedIdx by remember(puzzleEntity.id) {
        mutableIntStateOf(sudoku.puzzle.indexOfFirst { it == '0' }.coerceAtLeast(0))
    }
    var isNotesMode by remember(puzzleEntity.id) { mutableStateOf(false) }
    var mistakes by remember(puzzleEntity.id) { mutableIntStateOf(restored.mistakes) }
    var extraLifeUsed by remember(puzzleEntity.id) { mutableStateOf(restored.extraLifeUsed) }
    var isGameOver by remember(puzzleEntity.id) { mutableStateOf(restored.isGameOver) }
    var elapsedSeconds by remember(puzzleEntity.id) { mutableIntStateOf(restored.elapsedSeconds) }
    var isSolved by remember(puzzleEntity.id) { mutableStateOf(puzzleEntity.isCompleted) }
    var showWinDialog by remember(puzzleEntity.id) { mutableStateOf(false) }
    var showResumeDialog by remember(puzzleEntity.id) { mutableStateOf(restored.hasActiveSavedSession && !restored.isGameOver) }

    fun buildProgressJson(
        currentBoard: List<Int>,
        currentElapsed: Int,
        currentMistakes: Int,
        currentExtraLife: Boolean,
        currentGameOver: Boolean
    ): String {
        return JSONObject().apply {
            put("board", currentBoard.joinToString(""))
            put("elapsedSeconds", currentElapsed)
            put("mistakes", currentMistakes)
            put("extraLifeUsed", currentExtraLife)
            put("isGameOver", currentGameOver)
        }.toString()
    }

    fun persistCurrentState(
        currentBoard: List<Int> = board,
        currentElapsed: Int = elapsedSeconds,
        currentMistakes: Int = mistakes,
        currentExtraLife: Boolean = extraLifeUsed,
        currentGameOver: Boolean = isGameOver
    ) {
        if (isSolved) return
        val json = buildProgressJson(
            currentBoard = currentBoard,
            currentElapsed = currentElapsed,
            currentMistakes = currentMistakes,
            currentExtraLife = currentExtraLife,
            currentGameOver = currentGameOver
        )
        onSaveProgress(json, currentMistakes)
    }

    val latestSaveAction by rememberUpdatedState {
        if (!isSolved && (elapsedSeconds > 0 || board.indices.any { sudoku.puzzle[it] == '0' && board[it] != 0 })) {
            persistCurrentState()
        }
    }

    // Automatically save timer & board state when leaving the game screen for any reason
    DisposableEffect(puzzleEntity.id) {
        onDispose {
            latestSaveAction()
        }
    }

    fun handleExit() {
        latestSaveAction()
        onBack()
    }

    BackHandler { handleExit() }

    val isTimerPaused = isSolved || isGameOver || showResumeDialog

    LaunchedEffect(puzzleEntity.id, isTimerPaused) {
        while (!isTimerPaused) {
            delay(1000L)
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) {
                persistCurrentState(currentElapsed = elapsedSeconds)
            }
        }
    }

    fun resetGameFresh() {
        val freshBoard = sudoku.puzzle.map { it - '0' }
        board = freshBoard
        notes = emptyMap()
        undoStack = emptyList()
        mistakes = 0
        extraLifeUsed = false
        isGameOver = false
        elapsedSeconds = 0
        isSolved = false
        showResumeDialog = false
        onSaveProgress("", 0)
    }

    fun grantSecondChance() {
        // Clear any currently wrong entries on the board and grant one extra life
        val cleanedBoard = board.mapIndexed { idx, value ->
            val expected = sudoku.solution[idx] - '0'
            if (sudoku.puzzle[idx] == '0' && value != 0 && value != expected) 0 else value
        }
        board = cleanedBoard
        mistakes = 0
        extraLifeUsed = true
        isGameOver = false
        persistCurrentState(
            currentBoard = cleanedBoard,
            currentMistakes = 0,
            currentExtraLife = true,
            currentGameOver = false
        )
    }

    fun isConflict(idx: Int, value: Int, grid: List<Int>): Boolean {
        if (value == 0) return false
        val r = idx / size
        val c = idx % size
        for (i in 0 until size) {
            val rowIdx = r * size + i
            if (rowIdx != idx && grid[rowIdx] == value) return true
            val colIdx = i * size + c
            if (colIdx != idx && grid[colIdx] == value) return true
        }
        val br = (r / sudoku.boxRows) * sudoku.boxRows
        val bc = (c / sudoku.boxCols) * sudoku.boxCols
        for (dr in 0 until sudoku.boxRows) {
            for (dc in 0 until sudoku.boxCols) {
                val bIdx = (br + dr) * size + (bc + dc)
                if (bIdx != idx && grid[bIdx] == value) return true
            }
        }
        return false
    }

    fun handleDigitInput(digit: Int) {
        if (isSolved || isGameOver || showResumeDialog) return
        if (selectedIdx !in 0 until totalCells) return
        if (sudoku.puzzle[selectedIdx] != '0') return

        if (isNotesMode) {
            val existing = notes[selectedIdx].orEmpty().toMutableSet()
            if (digit in existing) existing.remove(digit) else existing.add(digit)
            val updatedNotes = notes.toMutableMap()
            updatedNotes[selectedIdx] = existing
            notes = updatedNotes
            return
        }

        val updated = board.toMutableList()
        if (updated[selectedIdx] == digit) return
        undoStack = (undoStack + listOf(board)).takeLast(25)
        updated[selectedIdx] = digit
        board = updated

        if (notes.containsKey(selectedIdx)) {
            notes = notes - selectedIdx
        }

        val expectedDigit = sudoku.solution[selectedIdx] - '0'
        var nextMistakes = mistakes
        var lostNow = false
        if (digit != expectedDigit) {
            nextMistakes = (mistakes + 1).coerceAtMost(SUDOKU_MAX_MISTAKES)
            mistakes = nextMistakes
            if (nextMistakes >= SUDOKU_MAX_MISTAKES) {
                isGameOver = true
                lostNow = true
            }
        }

        val solvedNow = !lostNow && (
            updated.indices.all { i -> updated[i] == (sudoku.solution[i] - '0') } ||
                SudokuGenerator.isValidSolvedGrid(updated.joinToString(""), size, sudoku.boxRows, sudoku.boxCols)
            )

        if (solvedNow) {
            isSolved = true
            val json = buildProgressJson(updated, elapsedSeconds, nextMistakes, extraLifeUsed, false)
            onMarkCompleted(json, elapsedSeconds, nextMistakes)
            showWinDialog = true
        } else {
            persistCurrentState(
                currentBoard = updated,
                currentMistakes = nextMistakes,
                currentGameOver = lostNow
            )
        }
    }

    fun handleErase() {
        if (isSolved || isGameOver || showResumeDialog) return
        if (selectedIdx !in 0 until totalCells) return
        if (sudoku.puzzle[selectedIdx] != '0') return
        if (board[selectedIdx] != 0) {
            undoStack = (undoStack + listOf(board)).takeLast(25)
            val updated = board.toMutableList()
            updated[selectedIdx] = 0
            board = updated
            persistCurrentState(currentBoard = updated)
        }
        if (notes.containsKey(selectedIdx)) {
            notes = notes - selectedIdx
        }
    }

    fun handleHint() {
        if (isSolved || isGameOver || showResumeDialog) return
        val target = if (sudoku.puzzle[selectedIdx] == '0' && board[selectedIdx] != (sudoku.solution[selectedIdx] - '0')) {
            selectedIdx
        } else {
            board.indices.firstOrNull { i -> sudoku.puzzle[i] == '0' && board[i] != (sudoku.solution[i] - '0') }
        } ?: return

        selectedIdx = target
        val prevNotesMode = isNotesMode
        isNotesMode = false
        handleDigitInput(sudoku.solution[target] - '0')
        isNotesMode = prevNotesMode
    }

    val selectedVal = board.getOrElse(selectedIdx) { 0 }
    val selRow = selectedIdx / size
    val selCol = selectedIdx % size
    val selBoxR = selRow / sudoku.boxRows
    val selBoxC = selCol / sudoku.boxCols

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("sudoku_game_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header Bar (No vertical text wrapping)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                IconButton(
                    onClick = { handleExit() },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("sudoku_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = enDigits(puzzleEntity.title),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onBackground),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = enDigits("زمان: ${formatSecondsClock(elapsedSeconds)}"),
                            fontSize = 11.5.sp,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (mistakes >= 2) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = enDigits("خطا: $mistakes/$SUDOKU_MAX_MISTAKES"),
                                fontSize = 11.sp,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                color = if (mistakes > 0) Color(0xFFDC2626) else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (extraLifeUsed) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    text = "فرصت دوم",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (isSolved) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.16f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("حل شد", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857), maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // Refined Editorial Sudoku Board — crisp outer frame, alternating sub-grid tint, sharp overlay dividers & balanced digit weights
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val maxBoardSide = (maxWidth - 4.dp).coerceAtMost(388.dp)
                val cellSize = ((maxBoardSide - 4.dp) / size).coerceIn(33.dp, 58.dp)
                val digitFontSize = (cellSize.value * (if (size == 6) 0.50f else 0.56f)).coerceIn(17f, 26f).sp

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 6.dp,
                    border = BorderStroke(2.4.dp, Color(0xFF0F172A)),
                    modifier = Modifier.testTag("sudoku_board_frame")
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .drawWithContent {
                                drawContent()
                                val thinColor = Color(0xFFCBD5E1)
                                val thickColor = Color(0xFF1E293B)
                                val thinPx = 0.85.dp.toPx()
                                val thickPx = 2.2.dp.toPx()
                                val cellPx = this.size.width / size.toFloat()

                                // Draw crisp internal vertical & horizontal grid lines on top of cell fills
                                for (i in 1 until size) {
                                    val isVerticalBoxBorder = i % sudoku.boxCols == 0
                                    val x = cellPx * i
                                    drawLine(
                                        color = if (isVerticalBoxBorder) thickColor else thinColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, this.size.height),
                                        strokeWidth = if (isVerticalBoxBorder) thickPx else thinPx
                                    )

                                    val isHorizontalBoxBorder = i % sudoku.boxRows == 0
                                    val y = cellPx * i
                                    drawLine(
                                        color = if (isHorizontalBoxBorder) thickColor else thinColor,
                                        start = Offset(0f, y),
                                        end = Offset(this.size.width, y),
                                        strokeWidth = if (isHorizontalBoxBorder) thickPx else thinPx
                                    )
                                }
                            }
                    ) {
                        Column {
                            for (r in 0 until size) {
                                Row {
                                    for (c in 0 until size) {
                                        val idx = r * size + c
                                        val value = board[idx]
                                        val expected = sudoku.solution[idx] - '0'
                                        val isGiven = sudoku.puzzle[idx] != '0'
                                        val isSelected = idx == selectedIdx
                                        val boxR = r / sudoku.boxRows
                                        val boxC = c / sudoku.boxCols
                                        val isAltSubGrid = (boxR + boxC) % 2 == 1
                                        val isRelated = (r == selRow) || (c == selCol) ||
                                            (boxR == selBoxR && boxC == selBoxC)
                                        val isSameDigit = selectedVal != 0 && value == selectedVal && !isSelected
                                        val isWrongAnswer = !isGiven && value != 0 && value != expected
                                        val hasConflict = value != 0 && isConflict(idx, value, board)
                                        val showRedError = isWrongAnswer || hasConflict

                                        val cellBg = when {
                                            isSelected && showRedError -> Color(0xFFFECACA)
                                            isSelected -> Color(0xFFDBEAFE)
                                            showRedError -> Color(0xFFFEE2E2)
                                            isSameDigit -> Color(0xFFBFDBFE)
                                            isRelated -> Color(0xFFEFF6FF)
                                            isAltSubGrid -> Color(0xFFF8FAFC)
                                            else -> Color.White
                                        }

                                        val digitColor = when {
                                            showRedError -> Color(0xFFDC2626) // Vivid RED for wrong or conflicting digits
                                            isGiven -> Color(0xFF0F172A)      // Crisp architectural ink for given clues
                                            else -> Color(0xFF1D4ED8)         // Clean cobalt blue for player-entered digits
                                        }

                                        val digitWeight = when {
                                            showRedError -> FontWeight.Bold
                                            isGiven -> FontWeight.SemiBold
                                            else -> FontWeight.Medium
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(cellSize)
                                                .background(cellBg)
                                                .then(
                                                    when {
                                                        isSelected && showRedError -> Modifier.border(2.2.dp, Color(0xFFDC2626))
                                                        isSelected -> Modifier.border(2.2.dp, Color(0xFF2563EB))
                                                        else -> Modifier
                                                    }
                                                )
                                                .clickable { selectedIdx = idx }
                                                .testTag("sudoku_cell_${r}_${c}")
                                        ) {
                                            if (value != 0) {
                                                Text(
                                                    text = value.toString(),
                                                    style = TextStyle(
                                                        fontSize = digitFontSize,
                                                        fontFamily = OutfitFontFamily,
                                                        fontWeight = digitWeight,
                                                        color = digitColor,
                                                        textAlign = TextAlign.Center,
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                                    ),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            } else {
                                                val cellNotes = notes[idx].orEmpty()
                                                if (cellNotes.isNotEmpty()) {
                                                    val noteCols = if (size == 6) 3 else 3
                                                    val noteRows = if (size == 6) 2 else 3
                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(2.dp),
                                                        verticalArrangement = Arrangement.SpaceEvenly
                                                    ) {
                                                        for (nr in 0 until noteRows) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceEvenly
                                                            ) {
                                                                for (nc in 0 until noteCols) {
                                                                    val candidate = nr * noteCols + nc + 1
                                                                    Text(
                                                                        text = if (candidate in cellNotes) candidate.toString() else "",
                                                                        style = TextStyle(
                                                                            fontSize = 8.5.sp,
                                                                            lineHeight = 9.sp,
                                                                            fontFamily = OutfitFontFamily,
                                                                            fontWeight = FontWeight.Medium,
                                                                            color = Color(0xFF475569),
                                                                            textAlign = TextAlign.Center,
                                                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                                                        ),
                                                                        modifier = Modifier.weight(1f),
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clean Icon-Above-Label Action Buttons (Zero vertical letter stacking!)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GameToolButton(
                label = "یادداشت",
                icon = Icons.Default.Edit,
                bgColor = if (isNotesMode) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isNotesMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("sudoku_notes_toggle"),
                onClick = { isNotesMode = !isNotesMode }
            )
            GameToolButton(
                label = "برگشت",
                icon = Icons.AutoMirrored.Filled.Undo,
                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (undoStack.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                enabled = undoStack.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sudoku_undo_button"),
                onClick = {
                    val prev = undoStack.lastOrNull() ?: return@GameToolButton
                    undoStack = undoStack.dropLast(1)
                    board = prev
                    persistCurrentState(currentBoard = prev)
                }
            )
            GameToolButton(
                label = "پاک",
                icon = Icons.AutoMirrored.Filled.Backspace,
                bgColor = Color(0xFFEF4444).copy(alpha = 0.14f),
                contentColor = Color(0xFFDC2626),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sudoku_erase_button"),
                onClick = { handleErase() }
            )
            GameToolButton(
                label = "راهنما",
                icon = Icons.Default.Lightbulb,
                bgColor = Color(0xFFF59E0B).copy(alpha = 0.16f),
                contentColor = Color(0xFFB45309),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sudoku_hint_button"),
                onClick = { handleHint() }
            )
            GameToolButton(
                label = "از نو",
                icon = Icons.Default.Refresh,
                bgColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .testTag("sudoku_reset_button"),
                onClick = { resetGameFresh() }
            )
        }

        // Refined LTR Number Pad (1..6 or 1..9) with balanced digit weights & remaining counts
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                for (digit in 1..size) {
                    val countPlaced = board.indices.count { i ->
                        board[i] == digit && (sudoku.puzzle[i] != '0' || (sudoku.solution[i] - '0') == digit)
                    }
                    val isCompleteDigit = countPlaced >= size
                    val isActiveSelectedDigit = selectedVal == digit
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = when {
                            isCompleteDigit -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                            isActiveSelectedDigit -> Color(0xFFDBEAFE)
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shadowElevation = if (isCompleteDigit) 0.dp else 2.dp,
                        border = BorderStroke(
                            width = if (isActiveSelectedDigit) 1.5.dp else 1.dp,
                            color = when {
                                isCompleteDigit -> Color.Transparent
                                isActiveSelectedDigit -> Color(0xFF2563EB)
                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .clickable { handleDigitInput(digit) }
                            .testTag("sudoku_digit_$digit")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = digit.toString(),
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isCompleteDigit -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                                        isActiveSelectedDigit -> Color(0xFF1D4ED8)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${(size - countPlaced).coerceAtLeast(0)}",
                                style = TextStyle(
                                    fontSize = 9.5.sp,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Resume Saved Game Dialog (preserves exact elapsed timer!)
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = {
                ScaledDialogContent {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF0EA5E9), modifier = Modifier.size(26.dp))
                        Text("ادامه بازی قبلی؟", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits(
                            "شما یک بازی نیمه‌تمام در این مرحله دارید (زمان سپری‌شده: ${formatSecondsClock(elapsedSeconds)} • خطاها: $mistakes/$SUDOKU_MAX_MISTAKES).\n\nآیا می‌خواهید از ادامه همین بازی و زمان قبلی ادامه دهید؟"
                        ),
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                }
            },
            confirmButton = {
                ScaledDialogContent {
                    Button(
                        onClick = { showResumeDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.testTag("sudoku_resume_confirm_button")
                    ) {
                        Text("ادامه بازی قبلی", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ScaledDialogContent {
                    OutlinedButton(
                        onClick = { resetGameFresh() },
                        modifier = Modifier.testTag("sudoku_restart_fresh_button")
                    ) {
                        Text("شروع از صفر")
                    }
                }
            }
        )
    }

    // 3 Mistakes Game Over Dialog + One-Time Second Chance!
    if (isGameOver) {
        AlertDialog(
            onDismissRequest = { /* Require explicit user choice */ },
            title = {
                ScaledDialogContent {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(28.dp))
                        Text("باختید! (3 اشتباه)", fontWeight = FontWeight.Black, fontSize = 16.sp, color = Color(0xFFB91C1C))
                    }
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = if (!extraLifeUsed) {
                            enDigits("شما 3 بار عدد اشتباه وارد کردید و بازی متوقف شد.\n\nمی‌توانید یک بار دیگر فرصت ادامه بگیرید تا خانه‌های اشتباه پاک شوند و از همین‌جا ادامه دهید، یا بازی را از اول شروع کنید.")
                        } else {
                            enDigits("متأسفانه 3 اشتباه در فرصت دوم هم پر شد و این دور را باختید. برای تلاش مجدد می‌توانید بازی را از اول شروع کنید.")
                        },
                        fontSize = 13.sp,
                        lineHeight = 21.sp
                    )
                }
            },
            confirmButton = {
                ScaledDialogContent {
                    if (!extraLifeUsed) {
                        Button(
                            onClick = { grantSecondChance() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.testTag("sudoku_second_chance_button")
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("یک فرصت ادامه", fontWeight = FontWeight.Black)
                        }
                    } else {
                        Button(
                            onClick = { resetGameFresh() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            modifier = Modifier.testTag("sudoku_gameover_restart_button")
                        ) {
                            Text("شروع مجدد بازی", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                ScaledDialogContent {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!extraLifeUsed) {
                            OutlinedButton(onClick = { resetGameFresh() }) {
                                Text("شروع از اول")
                            }
                        }
                        OutlinedButton(onClick = { handleExit() }) {
                            Text("خروج")
                        }
                    }
                }
            }
        )
    }

    // Victory Dialog
    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { showWinDialog = false },
            title = {
                ScaledDialogContent {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(28.dp))
                        Text("تبریک! سودوکو حل شد", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits("جدول سودوکو در زمان ${formatSecondsClock(elapsedSeconds)} با $mistakes خطا تکمیل شد."),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                ScaledDialogContent {
                    if (onNextLevel != null) {
                        Button(
                            onClick = {
                                showWinDialog = false
                                onNextLevel()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Text("مرحله بعدی", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(onClick = { showWinDialog = false }) {
                            Text("عالی!")
                        }
                    }
                }
            },
            dismissButton = {
                ScaledDialogContent {
                    OutlinedButton(onClick = { showWinDialog = false }) {
                        Text("بستن")
                    }
                }
            }
        )
    }
}

@Composable
internal fun GameToolButton(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
