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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Square
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entertainment.NonogramGenerator
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import kotlinx.coroutines.delay
import org.json.JSONObject

private const val NONOGRAM_MAX_LIVES = 3

// Cell states: 0 = unknown, 1 = filled (■), 2 = marked empty (×)
private data class RestoredNonogramState(
    val cells: List<Int>,
    val livesLeft: Int,
    val mistakes: Int,
    val elapsedSeconds: Int
)

@Composable
fun NonogramGameScreen(
    puzzleEntity: EntertainmentPuzzleEntity,
    onBack: () -> Unit,
    onSaveProgress: (String, Int) -> Unit,
    onMarkCompleted: (String, Int, Int) -> Unit,
    onNextLevel: (() -> Unit)?,
    onShowGuide: (() -> Unit)? = null
) {
    val puzzle = remember(puzzleEntity.id, puzzleEntity.payloadJson) {
        NonogramGenerator.parsePayload(puzzleEntity.payloadJson)
    }
    if (puzzle == null) {
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("خطا در بارگذاری جدول نونگرام", color = appTextColor(MaterialTheme.colorScheme.onBackground))
        }
        return
    }

    val size = puzzle.size
    val totalCells = size * size
    val accentColor = remember(puzzle.accentHex) { Color(puzzle.accentHex) }
    val rowClues = remember(puzzle.solutionRows) { NonogramGenerator.computeRowClues(puzzle.solutionRows) }
    val colClues = remember(puzzle.solutionRows) { NonogramGenerator.computeColClues(puzzle.solutionRows) }
    val totalTargetFilled = remember(puzzle.solutionRows) {
        puzzle.solutionRows.sumOf { row -> row.count { it == '1' } }
    }

    val restored = remember(puzzleEntity.id) {
        val base = MutableList(totalCells) { 0 }
        var lives = NONOGRAM_MAX_LIVES
        var errs = 0
        var secs = 0
        try {
            if (puzzleEntity.savedProgressJson.isNotBlank()) {
                val obj = JSONObject(puzzleEntity.savedProgressJson)
                val boardStr = obj.optString("board", "")
                lives = obj.optInt("livesLeft", NONOGRAM_MAX_LIVES).coerceIn(0, NONOGRAM_MAX_LIVES)
                errs = obj.optInt("mistakes", 0).coerceAtLeast(0)
                secs = obj.optInt("elapsedSeconds", 0).coerceAtLeast(0)
                if (boardStr.length == totalCells) {
                    for (i in 0 until totalCells) {
                        val v = boardStr[i] - '0'
                        if (v in 0..2) base[i] = v
                    }
                }
            }
        } catch (_: Exception) {
        }
        RestoredNonogramState(base.toList(), lives, errs, secs)
    }

    var cells by remember(puzzleEntity.id) { mutableStateOf(restored.cells) }
    var livesLeft by remember(puzzleEntity.id) { mutableIntStateOf(restored.livesLeft) }
    var mistakes by remember(puzzleEntity.id) { mutableIntStateOf(restored.mistakes) }
    var elapsedSeconds by remember(puzzleEntity.id) { mutableIntStateOf(restored.elapsedSeconds) }
    var fillMode by remember(puzzleEntity.id) { mutableStateOf(true) } // true = ■ Fill, false = × Cross
    var feedbackMessage by remember(puzzleEntity.id) { mutableStateOf<String?>(null) }

    val filledCorrectCount = remember(cells, puzzle.solutionRows) {
        var count = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (puzzle.solutionRows[r][c] == '1' && cells[r * size + c] == 1) count++
            }
        }
        count
    }

    val isWon = filledCorrectCount == totalTargetFilled && totalTargetFilled > 0
    val isLost = !isWon && livesLeft <= 0
    var showWinDialog by remember(puzzleEntity.id) { mutableStateOf(false) }
    var showLoseDialog by remember(puzzleEntity.id) { mutableStateOf(false) }

    fun serializeProgress(boardList: List<Int>, curLives: Int, curMistakes: Int): String {
        return JSONObject().apply {
            put("board", boardList.joinToString(""))
            put("livesLeft", curLives)
            put("mistakes", curMistakes)
            put("elapsedSeconds", elapsedSeconds)
        }.toString()
    }

    val latestSave by rememberUpdatedState {
        if (!isWon && !isLost && (cells.any { it != 0 } || elapsedSeconds > 0)) {
            onSaveProgress(serializeProgress(cells, livesLeft, mistakes), mistakes)
        }
    }

    LaunchedEffect(puzzleEntity.id, isWon, isLost) {
        while (!isWon && !isLost) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    DisposableEffect(puzzleEntity.id) {
        onDispose { latestSave() }
    }

    BackHandler {
        latestSave()
        onBack()
    }

    fun autoCompleteFinishedLines(mutable: MutableList<Int>) {
        // If all target '1's in a row are filled, auto-mark remaining unknown cells as '×'
        for (r in 0 until size) {
            val allFilled = (0 until size).all { c ->
                puzzle.solutionRows[r][c] != '1' || mutable[r * size + c] == 1
            }
            if (allFilled) {
                for (c in 0 until size) {
                    if (mutable[r * size + c] == 0) mutable[r * size + c] = 2
                }
            }
        }
        for (c in 0 until size) {
            val allFilled = (0 until size).all { r ->
                puzzle.solutionRows[r][c] != '1' || mutable[r * size + c] == 1
            }
            if (allFilled) {
                for (r in 0 until size) {
                    if (mutable[r * size + c] == 0) mutable[r * size + c] = 2
                }
            }
        }
    }

    fun checkAndPersist(updated: List<Int>, newLives: Int, newMistakes: Int) {
        cells = updated
        livesLeft = newLives
        mistakes = newMistakes

        var solvedCount = 0
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (puzzle.solutionRows[r][c] == '1' && updated[r * size + c] == 1) solvedCount++
            }
        }
        val json = serializeProgress(updated, newLives, newMistakes)
        if (solvedCount == totalTargetFilled) {
            onMarkCompleted(json, elapsedSeconds, newMistakes)
            showWinDialog = true
        } else if (newLives <= 0) {
            onSaveProgress(json, newMistakes)
            showLoseDialog = true
        } else {
            onSaveProgress(json, newMistakes)
        }
    }

    fun onCellTap(r: Int, c: Int) {
        if (isWon || isLost) return
        val idx = r * size + c
        val current = cells[idx]
        if (current == 1) return // Already correctly filled

        val shouldBeFilled = puzzle.solutionRows[r][c] == '1'
        val next = cells.toMutableList()

        if (fillMode) {
            if (shouldBeFilled) {
                next[idx] = 1
                autoCompleteFinishedLines(next)
                feedbackMessage = null
                checkAndPersist(next, livesLeft, mistakes)
            } else {
                next[idx] = 2 // Auto-mark as empty X on wrong fill
                val nextLives = (livesLeft - 1).coerceAtLeast(0)
                val nextMistakes = mistakes + 1
                feedbackMessage = "این خانه خالی است! یک جان کم شد."
                checkAndPersist(next, nextLives, nextMistakes)
            }
        } else {
            // Cross (×) mode toggles empty mark without losing lives
            next[idx] = if (current == 2) 0 else 2
            feedbackMessage = null
            checkAndPersist(next, livesLeft, mistakes)
        }
    }

    fun applySmartHint() {
        if (isWon || isLost) return
        val unrevealedIdx = (0 until totalCells).firstOrNull { idx ->
            val r = idx / size
            val c = idx % size
            puzzle.solutionRows[r][c] == '1' && cells[idx] != 1
        } ?: return
        val next = cells.toMutableList()
        next[unrevealedIdx] = 1
        autoCompleteFinishedLines(next)
        feedbackMessage = "یک خانه صحیح با راهنمایی هوشمند آشکار شد."
        checkAndPersist(next, livesLeft, mistakes)
    }

    fun resetGame() {
        cells = List(totalCells) { 0 }
        livesLeft = NONOGRAM_MAX_LIVES
        mistakes = 0
        elapsedSeconds = 0
        feedbackMessage = null
        showWinDialog = false
        showLoseDialog = false
        onSaveProgress("", 0)
    }

    val rowSatisfied = remember(cells, puzzle.solutionRows) {
        (0 until size).map { r ->
            (0 until size).all { c -> puzzle.solutionRows[r][c] != '1' || cells[r * size + c] == 1 }
        }
    }
    val colSatisfied = remember(cells, puzzle.solutionRows) {
        (0 until size).map { c ->
            (0 until size).all { r -> puzzle.solutionRows[r][c] != '1' || cells[r * size + c] == 1 }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("nonogram_game_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = {
                    latestSave()
                    onBack()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nonogram_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enDigits(puzzleEntity.title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = appTextColor(MaterialTheme.colorScheme.onBackground),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = enDigits(
                        if (isWon) "طرح کشف‌شده: ${puzzle.artworkTitle}"
                        else "شبکه ${size}×${size} • پیشرفت: $filledCorrectCount از $totalTargetFilled"
                    ),
                    fontSize = 11.sp,
                    color = if (isWon) Color(0xFF059669) else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    fontWeight = if (isWon) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onShowGuide != null) {
                IconButton(
                    onClick = onShowGuide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nonogram_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "راهنمای بازی نونگرام",
                        tint = Color(0xFFD97706)
                    )
                }
            }

            // 3 Hearts
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(NONOGRAM_MAX_LIVES) { idx ->
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = if (idx < livesLeft) Color(0xFFE11D48) else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
                        fontSize = 11.5.sp,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Revealed Artwork Celebration Banner on Win
        if (isWon) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.14f)),
                border = BorderStroke(1.5.dp, accentColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تصویر پنهان آشکار شد: ${puzzle.artworkTitle}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = accentColor
                        )
                        Text(
                            text = enDigits("زمان حل: %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)),
                            fontSize = 11.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }

        // Responsive LTR Nonogram Puzzle Board
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val maxRowClueCount = rowClues.maxOf { it.size }.coerceAtLeast(1)
                    val maxColClueCount = colClues.maxOf { it.size }.coerceAtLeast(1)
                    val rowClueAreaWidth = (maxRowClueCount * 15 + 14).dp.coerceIn(44.dp, 76.dp)
                    val availableGridWidth = (maxWidth - rowClueAreaWidth - 6.dp).coerceAtLeast(160.dp)
                    val cellDp = (availableGridWidth / size).coerceIn(22.dp, 46.dp)
                    val colClueAreaHeight = (maxColClueCount * 14 + 12).dp.coerceIn(36.dp, 74.dp)
                    val clueFontSize = if (size >= 10) 9.5.sp else if (size >= 8) 10.5.sp else 11.5.sp

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Top Column Clues Row
                        Row(verticalAlignment = Alignment.Bottom) {
                            Spacer(modifier = Modifier.width(rowClueAreaWidth))
                            for (c in 0 until size) {
                                val satisfied = colSatisfied[c]
                                Box(
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier
                                        .width(cellDp)
                                        .height(colClueAreaHeight)
                                        .padding(bottom = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (satisfied) Color(0xFF10B981).copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Bottom,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        colClues[c].forEach { num ->
                                            Text(
                                                text = num.toString(),
                                                fontSize = clueFontSize,
                                                lineHeight = clueFontSize,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Black,
                                                color = if (satisfied) Color(0xFF047857)
                                                else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Grid Rows (Row Clue + Cells)
                        for (r in 0 until size) {
                            val satisfied = rowSatisfied[r]
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Left Row Clues
                                Box(
                                    contentAlignment = Alignment.CenterEnd,
                                    modifier = Modifier
                                        .width(rowClueAreaWidth)
                                        .height(cellDp)
                                        .padding(end = 5.dp, top = 1.dp, bottom = 1.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (satisfied) Color(0xFF10B981).copy(alpha = 0.16f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .padding(horizontal = 5.dp)
                                ) {
                                    Text(
                                        text = rowClues[r].joinToString(" "),
                                        fontSize = clueFontSize,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Black,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        color = if (satisfied) Color(0xFF047857)
                                        else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }

                                // Interactive Grid Cells
                                for (c in 0 until size) {
                                    val cellVal = cells[r * size + c]
                                    val thickRight = size >= 8 && (c + 1) % 5 == 0 && c != size - 1
                                    val thickBottom = size >= 8 && (r + 1) % 5 == 0 && r != size - 1
                                    val cellColor = when {
                                        cellVal == 1 -> if (isWon) accentColor else Color(0xFF1E293B)
                                        cellVal == 2 -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.background
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(cellDp)
                                            .padding(
                                                end = if (thickRight) 2.dp else 0.5.dp,
                                                bottom = if (thickBottom) 2.dp else 0.5.dp
                                            )
                                            .clip(RoundedCornerShape(if (size >= 10) 3.dp else 5.dp))
                                            .background(cellColor)
                                            .border(
                                                width = 0.8.dp,
                                                color = if (cellVal == 1) cellColor
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
                                                shape = RoundedCornerShape(if (size >= 10) 3.dp else 5.dp)
                                            )
                                            .clickable(enabled = !isWon && !isLost) { onCellTap(r, c) }
                                            .testTag("nonogram_cell_${r}_${c}")
                                    ) {
                                        if (cellVal == 2 && !isWon) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "خالی",
                                                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                modifier = Modifier.size((cellDp * 0.55f).coerceAtLeast(11.dp))
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

        if (!feedbackMessage.isNullOrBlank()) {
            Text(
                text = enDigits(feedbackMessage!!),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (feedbackMessage!!.contains("جان")) MaterialTheme.colorScheme.error else Color(0xFF059669),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Fill (■) vs Mark Empty (×) Mode Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { fillMode = true },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (fillMode) Color(0xFF1E293B) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (fillMode) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("nonogram_mode_fill")
            ) {
                Icon(Icons.Default.Square, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("پر کردن خانه", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }

            Button(
                onClick = { fillMode = false },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!fillMode) Color(0xFF0284C7) else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (!fillMode) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp)
                    .testTag("nonogram_mode_cross")
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("علامت خالی (×)", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }

        // Hint & Reset Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { applySmartHint() },
                enabled = !isWon && !isLost,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("nonogram_hint_button")
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("راهنمایی هوشمند", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }

            OutlinedButton(
                onClick = { resetGame() },
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("nonogram_reset_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("شروع مجدد", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }

        // Brief Rule Tip Card
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "راهنما: اعداد کنار هر سطر و بالای هر ستون، طول دسته‌های متوالی خانه‌های پُر را نشان می‌دهند (مثلاً «2 1» یعنی دو خانه پُر، حداقل یک خانه خالی و سپس یک خانه پُر). با تکمیل هر سطر یا ستون، اعداد آن سبز می‌شوند.",
                fontSize = 11.sp,
                lineHeight = 18.sp,
                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { showWinDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text("تصویر کشف شد!", fontWeight = FontWeight.Black, fontSize = 17.sp)
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits("تبریک! طرح «${puzzle.artworkTitle}» را در زمان %02d:%02d با $mistakes خطا کامل کردید.".format(elapsedSeconds / 60, elapsedSeconds % 60)),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                if (onNextLevel != null) {
                    Button(
                        onClick = {
                            showWinDialog = false
                            onNextLevel()
                        },
                        modifier = Modifier.testTag("nonogram_next_level_button")
                    ) {
                        Text("مرحله بعدی", fontWeight = FontWeight.Black)
                    }
                } else {
                    Button(onClick = { showWinDialog = false; onBack() }) {
                        Text("بازگشت", fontWeight = FontWeight.Black)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWinDialog = false }) {
                    Text("مشاهده طرح")
                }
            }
        )
    }

    if (showLoseDialog) {
        AlertDialog(
            onDismissRequest = { showLoseDialog = false },
            title = { Text("جان‌ها تمام شد", fontWeight = FontWeight.Black, fontSize = 17.sp) },
            text = {
                ScaledDialogContent {
                    Text(
                        text = "اشکالی ندارد! می‌توانید جدول را دوباره از ابتدا حل کنید یا از حالت «علامت خالی ×» برای تحلیل دقیق‌تر بهره ببرید.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { resetGame() }) {
                    Text("تلاش مجدد", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLoseDialog = false; onBack() }) {
                    Text("بازگشت")
                }
            }
        )
    }
}
