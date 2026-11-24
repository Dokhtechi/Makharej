package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entertainment.PersianCrosswordGenerator
import com.example.data.model.CrosswordClue
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import kotlinx.coroutines.delay
import org.json.JSONObject

private val PERSIAN_KEYBOARD_ROWS = listOf(
    listOf("آ", "ا", "ب", "پ", "ت", "ث", "ج", "چ", "ح", "خ", "د"),
    listOf("ذ", "ر", "ز", "ژ", "س", "ش", "ص", "ض", "ط", "ظ", "ع"),
    listOf("غ", "ف", "ق", "ک", "گ", "ل", "م", "ن", "و", "ه", "ی")
)

private data class RestoredCrosswordState(
    val entries: Map<Pair<Int, Int>, Char>,
    val elapsedSeconds: Int,
    val hasActiveSavedSession: Boolean
)

@Composable
fun CrosswordGameScreen(
    puzzleEntity: EntertainmentPuzzleEntity,
    onBack: () -> Unit,
    onSaveProgress: (String, Int) -> Unit,
    onMarkCompleted: (String, Int, Int) -> Unit,
    onNextLevel: (() -> Unit)?
) {
    val puzzleData = remember(puzzleEntity.id, puzzleEntity.payloadJson) {
        PersianCrosswordGenerator.deserializeCrossword(puzzleEntity.payloadJson)
    }

    if (puzzleData == null) {
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("خطا در بارگذاری جدول", color = appTextColor(MaterialTheme.colorScheme.onBackground))
        }
        return
    }

    val restored = remember(puzzleEntity.id) {
        val map = mutableMapOf<Pair<Int, Int>, Char>()
        var savedElapsed = 0
        try {
            if (puzzleEntity.savedProgressJson.isNotBlank()) {
                val root = JSONObject(puzzleEntity.savedProgressJson)
                savedElapsed = root.optInt("elapsedSeconds", 0).coerceAtLeast(0)
                val entriesObj = root.optJSONObject("entries") ?: root
                val keys = entriesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "elapsedSeconds" || key == "entries") continue
                    val parts = key.split("_")
                    if (parts.size == 2) {
                        val r = parts[0].toIntOrNull()
                        val c = parts[1].toIntOrNull()
                        val ch = entriesObj.optString(key).firstOrNull()
                        if (r != null && c != null && ch != null &&
                            r in 0 until puzzleData.rows && c in 0 until puzzleData.cols &&
                            puzzleData.solutionGrid[r][c] != '#'
                        ) {
                            map[r to c] = ch
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        val hasSaved = (map.isNotEmpty() || savedElapsed > 0) && !puzzleEntity.isCompleted
        RestoredCrosswordState(map.toMap(), savedElapsed, hasSaved)
    }

    val acrossClues = remember(puzzleData) {
        puzzleData.clues
            .filter { it.direction == "ACROSS" }
            .sortedWith(compareBy<CrosswordClue> { it.row }.thenBy { it.col })
    }
    val downClues = remember(puzzleData) {
        puzzleData.clues
            .filter { it.direction == "DOWN" }
            .sortedWith(compareBy<CrosswordClue> { it.col }.thenBy { it.row })
    }
    val acrossCluesByRow = remember(acrossClues) { acrossClues.groupBy { it.row } }
    val downCluesByCol = remember(downClues) { downClues.groupBy { it.col } }

    // First playable white cell in each row (rightmost in RTL) & each column (topmost)
    val firstWhiteColPerRow = remember(puzzleData) {
        IntArray(puzzleData.rows) { r ->
            (0 until puzzleData.cols).firstOrNull { c -> puzzleData.solutionGrid[r][c] != '#' } ?: -1
        }
    }
    val firstWhiteRowPerCol = remember(puzzleData) {
        IntArray(puzzleData.cols) { c ->
            (0 until puzzleData.rows).firstOrNull { r -> puzzleData.solutionGrid[r][c] != '#' } ?: -1
        }
    }

    var userEntries by remember(puzzleEntity.id) { mutableStateOf(restored.entries) }
    var selectedClue by remember(puzzleEntity.id) {
        mutableStateOf(acrossClues.firstOrNull() ?: downClues.firstOrNull())
    }
    var selectedCell by remember(puzzleEntity.id) {
        val first = acrossClues.firstOrNull() ?: downClues.firstOrNull()
        mutableStateOf(if (first != null) first.row to first.col else 0 to 0)
    }
    var isZoomedIn by remember(puzzleEntity.id) { mutableStateOf(false) }
    var elapsedSeconds by remember(puzzleEntity.id) { mutableIntStateOf(restored.elapsedSeconds) }
    var hintsUsed by remember(puzzleEntity.id) { mutableIntStateOf(puzzleEntity.mistakesCount) }
    var isSolved by remember(puzzleEntity.id) { mutableStateOf(puzzleEntity.isCompleted) }
    var showWinDialog by remember(puzzleEntity.id) { mutableStateOf(false) }
    var showResumeDialog by remember(puzzleEntity.id) { mutableStateOf(restored.hasActiveSavedSession) }
    var showKeyboardPopup by remember(puzzleEntity.id) { mutableStateOf(false) }

    fun buildProgressJson(entries: Map<Pair<Int, Int>, Char>, elapsed: Int): String {
        val entriesObj = JSONObject()
        entries.forEach { (coord, ch) -> entriesObj.put("${coord.first}_${coord.second}", ch.toString()) }
        return JSONObject().apply {
            put("elapsedSeconds", elapsed)
            put("entries", entriesObj)
        }.toString()
    }

    fun persistProgress(
        entries: Map<Pair<Int, Int>, Char> = userEntries,
        elapsed: Int = elapsedSeconds,
        mistakes: Int = hintsUsed
    ) {
        if (isSolved) return
        onSaveProgress(buildProgressJson(entries, elapsed), mistakes)
    }

    val latestSaveAction by rememberUpdatedState {
        if (!isSolved && (userEntries.isNotEmpty() || elapsedSeconds > 0)) {
            persistProgress()
        }
    }

    DisposableEffect(puzzleEntity.id) {
        onDispose {
            latestSaveAction()
        }
    }

    fun handleExit() {
        latestSaveAction()
        onBack()
    }

    BackHandler {
        if (showKeyboardPopup) {
            showKeyboardPopup = false
        } else {
            handleExit()
        }
    }

    val isTimerPaused = isSolved || showResumeDialog

    LaunchedEffect(puzzleEntity.id, isTimerPaused) {
        while (!isTimerPaused) {
            delay(1000L)
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) {
                persistProgress(elapsed = elapsedSeconds)
            }
        }
    }

    fun resetGameFresh() {
        userEntries = emptyMap()
        isSolved = false
        elapsedSeconds = 0
        hintsUsed = 0
        showResumeDialog = false
        onSaveProgress("", 0)
    }

    val activeClueOrderedCoords = remember(selectedClue) {
        val clue = selectedClue ?: return@remember emptyList<Pair<Int, Int>>()
        clue.answer.indices.map { i ->
            if (clue.direction == "ACROSS") clue.row to (clue.col + i) else (clue.row + i) to clue.col
        }
    }

    val activeClueCells = remember(activeClueOrderedCoords) {
        activeClueOrderedCoords.toSet()
    }

    fun cluesCoveringCell(r: Int, c: Int): List<CrosswordClue> {
        return puzzleData.clues.filter { clue ->
            if (clue.direction == "ACROSS") {
                clue.row == r && c in clue.col until (clue.col + clue.answer.length)
            } else {
                clue.col == c && r in clue.row until (clue.row + clue.answer.length)
            }
        }
    }

    val matchingCluesForSelectedCell = remember(selectedCell, puzzleData) {
        cluesCoveringCell(selectedCell.first, selectedCell.second)
    }

    // Active Row (if ACROSS) or Column (if DOWN) clues — all clues on the current newspaper line!
    val activeDirection = selectedClue?.direction ?: "ACROSS"
    val activeLineNumber = if (activeDirection == "ACROSS") {
        (selectedClue?.row ?: selectedCell.first) + 1
    } else {
        (selectedClue?.col ?: selectedCell.second) + 1
    }
    val activeLineClues = remember(selectedClue, selectedCell, acrossCluesByRow, downCluesByCol) {
        if (activeDirection == "ACROSS") {
            acrossCluesByRow[selectedClue?.row ?: selectedCell.first].orEmpty()
        } else {
            downCluesByCol[selectedClue?.col ?: selectedCell.second].orEmpty()
        }
    }

    // Count all wrong letters currently typed on the board (instant live error feedback!)
    val wrongCoords = remember(userEntries, puzzleData) {
        userEntries.entries.mapNotNull { (coord, ch) ->
            val expected = puzzleData.solutionGrid.getOrNull(coord.first)?.getOrNull(coord.second)
            if (expected != null && expected != '#' && ch != expected) coord else null
        }
    }

    fun selectRowHeader(rowIndex: Int) {
        val rowClues = acrossCluesByRow[rowIndex].orEmpty()
        val firstClue = rowClues.firstOrNull()
        if (firstClue != null) {
            selectedClue = firstClue
            selectedCell = firstClue.row to firstClue.col
            if (!isSolved) showKeyboardPopup = true
        } else {
            val firstCol = firstWhiteColPerRow[rowIndex]
            if (firstCol >= 0) {
                selectedCell = rowIndex to firstCol
                selectedClue = cluesCoveringCell(rowIndex, firstCol).firstOrNull()
                if (!isSolved) showKeyboardPopup = true
            }
        }
    }

    fun selectColumnHeader(colIndex: Int) {
        val colClues = downCluesByCol[colIndex].orEmpty()
        val firstClue = colClues.firstOrNull()
        if (firstClue != null) {
            selectedClue = firstClue
            selectedCell = firstClue.row to firstClue.col
            if (!isSolved) showKeyboardPopup = true
        } else {
            val firstRow = firstWhiteRowPerCol[colIndex]
            if (firstRow >= 0) {
                selectedCell = firstRow to colIndex
                selectedClue = cluesCoveringCell(firstRow, colIndex).firstOrNull()
                if (!isSolved) showKeyboardPopup = true
            }
        }
    }

    fun handleCellTap(r: Int, c: Int) {
        val coord = r to c
        val matching = cluesCoveringCell(r, c)
        val acrossMatch = matching.firstOrNull { it.direction == "ACROSS" }
        val downMatch = matching.firstOrNull { it.direction == "DOWN" }

        val isRowStart = (c == firstWhiteColPerRow[r])
        val isColStart = (r == firstWhiteRowPerCol[c])

        if (selectedCell == coord && acrossMatch != null && downMatch != null) {
            // Re-tapping the same cell toggles cleanly between Row (Across) & Column (Down)
            selectedClue = if (selectedClue?.direction == "ACROSS") downMatch else acrossMatch
        } else if (isRowStart && !isColStart && acrossMatch != null) {
            // Tapped first cell of a Row -> immediately reveal this Row's Across questions!
            selectedClue = acrossMatch
        } else if (isColStart && !isRowStart && downMatch != null) {
            // Tapped first cell of a Column -> immediately reveal this Column's Down questions!
            selectedClue = downMatch
        } else if (isRowStart && isColStart) {
            // Top-right corner (1,1): prefer Across first, or toggle on repeat tap
            selectedClue = acrossMatch ?: downMatch
        } else {
            // Interior cell: preserve current direction when available
            val sameDir = matching.firstOrNull { it.direction == selectedClue?.direction }
            selectedClue = sameDir ?: acrossMatch ?: downMatch
        }

        selectedCell = coord
        if (!isSolved) {
            showKeyboardPopup = true
        }
    }

    fun checkSolved(entries: Map<Pair<Int, Int>, Char>): Boolean {
        for (r in 0 until puzzleData.rows) {
            val rowStr = puzzleData.solutionGrid[r]
            for (c in 0 until puzzleData.cols) {
                val expected = rowStr[c]
                if (expected == '#') continue
                val actual = entries[r to c] ?: return false
                if (actual != expected) return false
            }
        }
        return true
    }

    fun handleLetterInput(letter: Char) {
        if (isSolved || showResumeDialog) return
        val (r, c) = selectedCell
        if (r !in 0 until puzzleData.rows || c !in 0 until puzzleData.cols) return
        if (puzzleData.solutionGrid[r][c] == '#') return

        val updated = userEntries.toMutableMap()
        updated[r to c] = letter
        userEntries = updated

        if (checkSolved(updated)) {
            isSolved = true
            showKeyboardPopup = false
            onMarkCompleted(buildProgressJson(updated, elapsedSeconds), elapsedSeconds, hintsUsed)
            showWinDialog = true
            return
        }

        persistProgress(entries = updated)

        // Advance cursor to the next cell in the active word only if the typed letter is correct,
        // so when the player types a WRONG letter (shown in RED), they can immediately replace it!
        val isCorrectLetter = letter == puzzleData.solutionGrid[r][c]
        val clue = selectedClue
        if (isCorrectLetter && clue != null) {
            val clueCoords = clue.answer.indices.map { i ->
                if (clue.direction == "ACROSS") clue.row to (clue.col + i) else (clue.row + i) to clue.col
            }
            val currentIdx = clueCoords.indexOf(r to c)
            if (currentIdx in 0 until clueCoords.lastIndex) {
                selectedCell = clueCoords[currentIdx + 1]
            }
        }
    }

    fun handleBackspace() {
        if (isSolved || showResumeDialog) return
        val (r, c) = selectedCell
        val updated = userEntries.toMutableMap()
        if (updated.containsKey(r to c)) {
            updated.remove(r to c)
            userEntries = updated
            persistProgress(entries = updated)
        } else {
            val clue = selectedClue
            if (clue != null) {
                val clueCoords = clue.answer.indices.map { i ->
                    if (clue.direction == "ACROSS") clue.row to (clue.col + i) else (clue.row + i) to clue.col
                }
                val idx = clueCoords.indexOf(r to c)
                if (idx > 0) {
                    val prev = clueCoords[idx - 1]
                    selectedCell = prev
                    updated.remove(prev)
                    userEntries = updated
                    persistProgress(entries = updated)
                }
            }
        }
    }

    fun handleHint() {
        if (isSolved || showResumeDialog) return
        val (r, c) = selectedCell
        val target = if (r in 0 until puzzleData.rows && c in 0 until puzzleData.cols &&
            puzzleData.solutionGrid[r][c] != '#' && userEntries[r to c] != puzzleData.solutionGrid[r][c]
        ) {
            r to c
        } else {
            val activeMissing = activeClueCells.firstOrNull { (cr, cc) ->
                userEntries[cr to cc] != puzzleData.solutionGrid[cr][cc]
            }
            activeMissing ?: run {
                var found: Pair<Int, Int>? = null
                outer@ for (rr in 0 until puzzleData.rows) {
                    for (cc in 0 until puzzleData.cols) {
                        val expected = puzzleData.solutionGrid[rr][cc]
                        if (expected != '#' && userEntries[rr to cc] != expected) {
                            found = rr to cc
                            break@outer
                        }
                    }
                }
                found
            }
        } ?: return

        val expectedChar = puzzleData.solutionGrid[target.first][target.second]
        hintsUsed++
        selectedCell = target
        handleLetterInput(expectedChar)
    }

    fun jumpToNextWrongCell() {
        val target = wrongCoords.firstOrNull { it != selectedCell } ?: wrongCoords.firstOrNull() ?: return
        val matching = cluesCoveringCell(target.first, target.second)
        selectedClue = matching.firstOrNull { it.direction == selectedClue?.direction } ?: matching.firstOrNull()
        selectedCell = target
        if (!isSolved) showKeyboardPopup = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag("crossword_game_screen"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Bar
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
                            .testTag("crossword_back_button")
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
                        Text(
                            text = enDigits("جدول 15×15  •  زمان: ${formatSecondsClock(elapsedSeconds)}  •  راهنما: $hintsUsed"),
                            fontSize = 11.sp,
                            fontFamily = OutfitFontFamily,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false
                        )
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
                } else if (wrongCoords.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFDC2626).copy(alpha = 0.14f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { jumpToNextWrongCell() }
                            .testTag("crossword_wrong_count_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "خطاها", tint = Color(0xFFDC2626), modifier = Modifier.size(15.dp))
                            Text(
                                text = enDigits("${wrongCoords.size} غلط"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Active Row / Column Questions Card — shows ALL clues of the selected Row (افقی) or Column (عمودی)!
            if (activeLineClues.isNotEmpty()) {
                val isAcrossLine = activeDirection == "ACROSS"
                val lineTitle = if (isAcrossLine) "افقی — ردیف $activeLineNumber" else "عمودی — ستون $activeLineNumber"
                val switchLabel = if (isAcrossLine) "ستون ${selectedCell.second + 1}" else "ردیف ${selectedCell.first + 1}"
                val canSwitchAxis = matchingCluesForSelectedCell.size > 1

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("crossword_active_clue_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4F46E5).copy(alpha = 0.10f)),
                    border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF4F46E5)
                                ) {
                                    Text(
                                        text = enDigits(lineTitle),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (canSwitchAxis) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, Color(0xFF4F46E5).copy(alpha = 0.35f)),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val other = matchingCluesForSelectedCell.firstOrNull { it != selectedClue }
                                                if (other != null) selectedClue = other
                                            }
                                            .testTag("crossword_banner_switch_axis")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SwapHoriz,
                                                contentDescription = "تغییر جهت",
                                                tint = Color(0xFF4338CA),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = enDigits(switchLabel),
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4338CA),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF4F46E5).copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { if (!isSolved) showKeyboardPopup = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = "باز کردن کیبورد",
                                        tint = Color(0xFF4338CA),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "کیبورد",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4338CA),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        // List every clue in this Row/Column — active word highlighted, tap any to jump straight to it
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            activeLineClues.forEachIndexed { idx, clue ->
                                val isCurrent = clue == selectedClue
                                val clueSolved = clue.answer.indices.all { i ->
                                    val cr = if (clue.direction == "ACROSS") clue.row else clue.row + i
                                    val cc = if (clue.direction == "ACROSS") clue.col + i else clue.col
                                    userEntries[cr to cc] == clue.answer[i]
                                }
                                Surface(
                                    shape = RoundedCornerShape(9.dp),
                                    color = when {
                                        isCurrent -> MaterialTheme.colorScheme.surface
                                        clueSolved -> Color(0xFF10B981).copy(alpha = 0.10f)
                                        else -> Color.Transparent
                                    },
                                    border = if (isCurrent) BorderStroke(1.2.dp, Color(0xFF4F46E5)) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable {
                                            selectedClue = clue
                                            selectedCell = clue.row to clue.col
                                            if (!isSolved) showKeyboardPopup = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = enDigits("${idx + 1}."),
                                            fontSize = 11.5.sp,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Black,
                                            color = if (isCurrent) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = enDigits("${clue.clue} (${clue.answer.length} حرف)"),
                                            fontSize = 12.5.sp,
                                            lineHeight = 20.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (clueSolved) Color(0xFF047857) else appTextColor(MaterialTheme.colorScheme.onSurface),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (clueSolved) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "حل شده",
                                                tint = Color(0xFF059669),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Classic Iranian Newspaper 15×15 Grid — Zero wasted gaps, true 1:1 square cells, slimmer 1..15 headers & crisp Persian letters
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val headerRatio = 0.78f
                val availableBoardWidth = (maxWidth - 4.dp).coerceAtMost(456.dp)
                val fitCell = ((availableBoardWidth - 4.dp) / (puzzleData.cols + headerRatio)).coerceAtLeast(18.dp)
                val cellSize = if (isZoomedIn) 36.dp else fitCell
                val headerThickness = (cellSize * headerRatio).coerceAtLeast(16.dp)
                val letterFontSize = (cellSize.value * 0.56f).coerceIn(12.5f, 20.5f).sp
                val headerFontSize = (headerThickness.value * 0.54f).coerceIn(8.5f, 12.5f).sp
                val needsHorizontalScroll = isZoomedIn || (cellSize * puzzleData.cols + headerThickness + 6.dp) > maxWidth

                val activeRowIdx = if (activeDirection == "ACROSS") (selectedClue?.row ?: selectedCell.first) else -1
                val activeColIdx = if (activeDirection == "DOWN") (selectedClue?.col ?: selectedCell.second) else -1

                val gridContent: @Composable () -> Unit = {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        shadowElevation = 5.dp,
                        border = BorderStroke(1.8.dp, Color(0xFF0F172A)),
                        modifier = Modifier.testTag("crossword_board_frame")
                    ) {
                        Column(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // TOP HEADER ROW: Corner + Columns 1..15 (RTL -> 1 is rightmost, 15 is leftmost)
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(width = headerThickness, height = headerThickness)
                                        .background(Color(0xFFE2E8F0))
                                        .border(0.5.dp, Color(0xFF94A3B8)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "×",
                                        style = TextStyle(
                                            fontSize = headerFontSize,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF475569),
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                                for (c in 0 until puzzleData.cols) {
                                    val isColActive = c == activeColIdx
                                    Box(
                                        modifier = Modifier
                                            .size(width = cellSize, height = headerThickness)
                                            .background(if (isColActive) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                                            .border(0.5.dp, Color(0xFF94A3B8))
                                            .clickable { selectColumnHeader(c) }
                                            .testTag("crossword_col_header_${c + 1}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (c + 1).toString(),
                                            style = TextStyle(
                                                fontSize = headerFontSize,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isColActive) Color.White else Color(0xFF1E293B),
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // 15 ROWS: Right row header (1..15) + 15 flush 1:1 newspaper cells
                            for (r in 0 until puzzleData.rows) {
                                val isRowActive = r == activeRowIdx
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    // Right-edge Row Number (1..15) — clickable to show all Across questions of this row
                                    Box(
                                        modifier = Modifier
                                            .size(width = headerThickness, height = cellSize)
                                            .background(if (isRowActive) Color(0xFF4F46E5) else Color(0xFFF1F5F9))
                                            .border(0.5.dp, Color(0xFF94A3B8))
                                            .clickable { selectRowHeader(r) }
                                            .testTag("crossword_row_header_${r + 1}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (r + 1).toString(),
                                            style = TextStyle(
                                                fontSize = headerFontSize,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRowActive) Color.White else Color(0xFF1E293B),
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            ),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    // 15 Square Playable / Blocked Cells (true 1:1 aspect ratio)
                                    for (c in 0 until puzzleData.cols) {
                                        val expected = puzzleData.solutionGrid[r][c]
                                        val isBlocked = expected == '#'
                                        if (isBlocked) {
                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .background(Color(0xFF0F172A))
                                                    .border(0.4.dp, Color(0xFF0F172A))
                                            )
                                        } else {
                                            val coord = r to c
                                            val isSelected = selectedCell == coord
                                            val isInActiveWord = coord in activeClueCells
                                            val isInActiveLine = (isRowActive || c == activeColIdx)
                                            val userChar = userEntries[coord]
                                            // ALWAYS highlight wrong letters in RED immediately!
                                            val isWrong = userChar != null && userChar != expected
                                            val isCorrectSolved = isSolved && userChar == expected

                                            val cellBg = when {
                                                isSelected && isWrong -> Color(0xFFDC2626)
                                                isSelected -> Color(0xFF4F46E5)
                                                isWrong -> Color(0xFFFEE2E2)
                                                isCorrectSolved -> Color(0xFFD1FAE5)
                                                isInActiveWord -> Color(0xFFDBEAFE)
                                                isInActiveLine -> Color(0xFFF1F5F9)
                                                else -> Color.White
                                            }
                                            val borderColor = when {
                                                isSelected -> Color(0xFFFACC15)
                                                isWrong -> Color(0xFFEF4444)
                                                else -> Color(0xFF475569)
                                            }
                                            val textColor = when {
                                                isSelected -> Color.White
                                                isWrong -> Color(0xFFDC2626)
                                                isCorrectSolved -> Color(0xFF047857)
                                                else -> Color(0xFF0F172A)
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(cellSize)
                                                    .background(cellBg)
                                                    .border(
                                                        width = if (isSelected) 1.6.dp else if (isWrong) 1.1.dp else 0.55.dp,
                                                        color = borderColor
                                                    )
                                                    .clickable { handleCellTap(r, c) }
                                                    .testTag("crossword_cell_${r}_${c}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (userChar != null) {
                                                    Text(
                                                        text = userChar.toString(),
                                                        style = TextStyle(
                                                            fontSize = letterFontSize,
                                                            lineHeight = letterFontSize * 1.08f,
                                                            fontFamily = FontFamily.SansSerif,
                                                            fontWeight = FontWeight.Bold,
                                                            color = textColor,
                                                            textAlign = TextAlign.Center,
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
                            }
                        }
                    }
                }

                if (needsHorizontalScroll) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        gridContent()
                    }
                } else {
                    gridContent()
                }
            }

            // Helpful 1-line guide chip right under the board
            Text(
                text = enDigits("راهنما: لمس اولین خانه یا شماره هر ردیف/ستون (1 تا 15) سوالات همان ردیف/ستون را نشان می‌دهد؛ حروف اشتباه فوراً قرمز می‌شوند."),
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            // Clean Icon-Above-Label Action Buttons (Zero vertical letter wrapping!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                GameToolButton(
                    label = if (isZoomedIn) "کوچک" else "بزرگ‌نمایی",
                    icon = if (isZoomedIn) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                    bgColor = if (isZoomedIn) Color(0xFF4F46E5) else Color(0xFF4F46E5).copy(alpha = 0.14f),
                    contentColor = if (isZoomedIn) Color.White else Color(0xFF4338CA),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crossword_zoom_button"),
                    onClick = { isZoomedIn = !isZoomedIn }
                )
                GameToolButton(
                    label = "راهنما",
                    icon = Icons.Default.Lightbulb,
                    bgColor = Color(0xFFF59E0B).copy(alpha = 0.16f),
                    contentColor = Color(0xFFB45309),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crossword_hint_button"),
                    onClick = { handleHint() }
                )
                GameToolButton(
                    label = if (wrongCoords.isNotEmpty()) "خطا (${wrongCoords.size})" else "بدون خطا",
                    icon = Icons.Default.ErrorOutline,
                    bgColor = if (wrongCoords.isNotEmpty()) Color(0xFFDC2626).copy(alpha = 0.15f) else Color(0xFF10B981).copy(alpha = 0.14f),
                    contentColor = if (wrongCoords.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF047857),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crossword_check_button"),
                    onClick = { jumpToNextWrongCell() }
                )
                GameToolButton(
                    label = "پاک",
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    bgColor = Color(0xFFEF4444).copy(alpha = 0.14f),
                    contentColor = Color(0xFFDC2626),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crossword_backspace_button"),
                    onClick = { handleBackspace() }
                )
                GameToolButton(
                    label = "از نو",
                    icon = Icons.Default.Refresh,
                    bgColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("crossword_reset_button"),
                    onClick = { resetGameFresh() }
                )
            }

            // Full Classic Newspaper Clues Grouped by Row (1..15 افقی) & Column (1..15 عمودی)
            NewspaperGroupedClueSection(
                title = "سوالات افقی (ردیف 1 تا 15)",
                groupedClues = acrossCluesByRow,
                selectedClue = selectedClue,
                onSelectClue = { clue ->
                    selectedClue = clue
                    selectedCell = clue.row to clue.col
                    if (!isSolved) showKeyboardPopup = true
                }
            )
            NewspaperGroupedClueSection(
                title = "سوالات عمودی (ستون 1 تا 15)",
                groupedClues = downCluesByCol,
                selectedClue = selectedClue,
                onSelectClue = { clue ->
                    selectedClue = clue
                    selectedCell = clue.row to clue.col
                    if (!isSolved) showKeyboardPopup = true
                }
            )

            Spacer(modifier = Modifier.height(if (showKeyboardPopup) 320.dp else 16.dp))
        }

        // Floating Popup Persian Keyboard — opens immediately on clicking any cell, header, or clue!
        AnimatedVisibility(
            visible = showKeyboardPopup && !isSolved && !showResumeDialog,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            CrosswordPopupKeyboardPanel(
                selectedClue = selectedClue,
                selectedCell = selectedCell,
                activeLineNumber = activeLineNumber,
                activeLineClues = activeLineClues,
                activeClueCoords = activeClueOrderedCoords,
                userEntries = userEntries,
                solutionGrid = puzzleData.solutionGrid,
                canToggleDirection = matchingCluesForSelectedCell.size > 1,
                onToggleDirection = {
                    if (matchingCluesForSelectedCell.size > 1) {
                        val nextClue = matchingCluesForSelectedCell.firstOrNull { it != selectedClue }
                            ?: matchingCluesForSelectedCell.first()
                        selectedClue = nextClue
                    }
                },
                onSelectClue = { clue ->
                    selectedClue = clue
                    selectedCell = clue.row to clue.col
                },
                onSelectCell = { coord -> selectedCell = coord },
                onLetterClick = { ch -> handleLetterInput(ch) },
                onBackspace = { handleBackspace() },
                onHint = { handleHint() },
                onClose = { showKeyboardPopup = false }
            )
        }
    }

    // Resume Saved Game Dialog (preserves exact elapsed timer!)
    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = {
                ScaledDialogContent {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4F46E5), modifier = Modifier.size(26.dp))
                        Text("ادامه جدول قبلی؟", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits(
                            "شما یک جدول نیمه‌تمام در این مرحله دارید (زمان سپری‌شده: ${formatSecondsClock(elapsedSeconds)}).\n\nآیا می‌خواهید از ادامه همین جدول و زمان قبلی ادامه دهید؟"
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
                        modifier = Modifier.testTag("crossword_resume_confirm_button")
                    ) {
                        Text("ادامه بازی قبلی", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                ScaledDialogContent {
                    OutlinedButton(
                        onClick = { resetGameFresh() },
                        modifier = Modifier.testTag("crossword_restart_fresh_button")
                    ) {
                        Text("شروع از صفر")
                    }
                }
            }
        )
    }

    if (showWinDialog) {
        AlertDialog(
            onDismissRequest = { showWinDialog = false },
            title = {
                ScaledDialogContent {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(28.dp))
                        Text("آفرین! جدول 15×15 کامل حل شد", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits("شما این جدول کلمات متقاطع کلاسیک 15×15 را در مدت ${formatSecondsClock(elapsedSeconds)} با موفقیت تکمیل کردید."),
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
                            Text("جدول بعدی", fontWeight = FontWeight.Bold)
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
private fun NewspaperGroupedClueSection(
    title: String,
    groupedClues: Map<Int, List<CrosswordClue>>,
    selectedClue: CrosswordClue?,
    onSelectClue: (CrosswordClue) -> Unit
) {
    if (groupedClues.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = enDigits(title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            groupedClues.keys.sorted().forEach { lineIdx ->
                val lineClues = groupedClues[lineIdx].orEmpty()
                if (lineClues.isEmpty()) return@forEach
                val lineContainsSelected = lineClues.any { it == selectedClue }
                Surface(
                    shape = RoundedCornerShape(11.dp),
                    color = if (lineContainsSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (lineContainsSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (lineIdx + 1).toString(),
                                    fontSize = 11.sp,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (lineContainsSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            lineClues.forEach { clue ->
                                val isSel = clue == selectedClue
                                Text(
                                    text = enDigits("• ${clue.clue} (${clue.answer.length} حرف)"),
                                    fontSize = 12.5.sp,
                                    lineHeight = 20.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else appTextColor(MaterialTheme.colorScheme.onSurface),
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSelectClue(clue) }
                                        .padding(vertical = 3.dp, horizontal = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CrosswordPopupKeyboardPanel(
    selectedClue: CrosswordClue?,
    selectedCell: Pair<Int, Int>,
    activeLineNumber: Int,
    activeLineClues: List<CrosswordClue>,
    activeClueCoords: List<Pair<Int, Int>>,
    userEntries: Map<Pair<Int, Int>, Char>,
    solutionGrid: List<String>,
    canToggleDirection: Boolean,
    onToggleDirection: () -> Unit,
    onSelectClue: (CrosswordClue) -> Unit,
    onSelectCell: (Pair<Int, Int>) -> Unit,
    onLetterClick: (Char) -> Unit,
    onBackspace: () -> Unit,
    onHint: () -> Unit,
    onClose: () -> Unit
) {
    val selectedExpected = solutionGrid.getOrNull(selectedCell.first)?.getOrNull(selectedCell.second)
    val selectedActual = userEntries[selectedCell]
    val isSelectedCellWrong = selectedActual != null && selectedExpected != null &&
        selectedExpected != '#' && selectedActual != selectedExpected

    val activeWordHasWrong = activeClueCoords.any { coord ->
        val exp = solutionGrid.getOrNull(coord.first)?.getOrNull(coord.second)
        val act = userEntries[coord]
        act != null && exp != null && exp != '#' && act != exp
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("crossword_popup_keyboard"),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        border = BorderStroke(
            1.2.dp,
            if (isSelectedCellWrong || activeWordHasWrong) Color(0xFFEF4444) else Color(0xFF4F46E5).copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Popup Top Bar: Row/Column badge + direction toggle + error indicator + quick actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (selectedClue != null) {
                        val badgeText = if (selectedClue.direction == "ACROSS") {
                            "ردیف $activeLineNumber افقی"
                        } else {
                            "ستون $activeLineNumber عمودی"
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4F46E5)
                        ) {
                            Text(
                                text = enDigits(badgeText),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                    if (canToggleDirection) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF4F46E5).copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleDirection() }
                                .testTag("crossword_popup_toggle_direction")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = "تغییر جهت افقی/عمودی",
                                    tint = Color(0xFF4338CA),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "تغییر جهت",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4338CA),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    if (isSelectedCellWrong || activeWordHasWrong) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFDC2626).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "حرف غلط!",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFDC2626),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onHint() }
                            .testTag("crossword_popup_hint")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "راهنما",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onBackspace() }
                            .testTag("crossword_popup_backspace")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "پاک کردن",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onClose() }
                            .testTag("crossword_popup_close")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "بستن کیبورد",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Quick switcher chips when this Row or Column has multiple words (e.g., 1..3 clues on the same line)
            if (activeLineClues.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    activeLineClues.forEachIndexed { idx, clue ->
                        val isCurrent = clue == selectedClue
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) Color(0xFF4F46E5) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectClue(clue) }
                        ) {
                            Text(
                                text = enDigits("${idx + 1}) ${clue.clue} (${clue.answer.length})"),
                                fontSize = 10.5.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) Color.White else appTextColor(MaterialTheme.colorScheme.onSurface),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            } else if (selectedClue != null) {
                Text(
                    text = enDigits("${selectedClue.clue} (${selectedClue.answer.length} حرف)"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }

            // Live Word Slot Preview Boxes — immediately turns RED on wrong letters!
            if (activeClueCoords.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                ) {
                    activeClueCoords.forEach { coord ->
                        val isSel = coord == selectedCell
                        val expected = solutionGrid.getOrNull(coord.first)?.getOrNull(coord.second)
                        val ch = userEntries[coord]
                        val isWrong = ch != null && expected != null && expected != '#' && ch != expected
                        val boxBg = when {
                            isSel && isWrong -> Color(0xFFDC2626)
                            isSel -> Color(0xFF4F46E5)
                            isWrong -> Color(0xFFFEE2E2)
                            ch != null -> Color(0xFFE0E7FF)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        }
                        val txtColor = when {
                            isSel -> Color.White
                            isWrong -> Color(0xFFDC2626)
                            else -> Color(0xFF0F172A)
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(boxBg)
                                .border(
                                    width = if (isSel || isWrong) 1.5.dp else 0.75.dp,
                                    color = when {
                                        isSel -> Color(0xFFFACC15)
                                        isWrong -> Color(0xFFEF4444)
                                        else -> Color(0xFF94A3B8)
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectCell(coord) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ch?.toString() ?: "",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.Bold,
                                    color = txtColor,
                                    textAlign = TextAlign.Center,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // 33-letter Persian Keyboard (3 clean rows × 11 keys)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PERSIAN_KEYBOARD_ROWS.forEach { rowLetters ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        rowLetters.forEach { letter ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                shadowElevation = 1.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onLetterClick(letter.first()) }
                                    .testTag("crossword_popup_key_$letter")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter,
                                        style = TextStyle(
                                            fontSize = 15.5.sp,
                                            fontFamily = FontFamily.SansSerif,
                                            fontWeight = FontWeight.SemiBold,
                                            color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                            textAlign = TextAlign.Center,
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
            }
        }
    }
}

internal fun formatSecondsClock(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val mins = safe / 60
    val secs = safe % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}

