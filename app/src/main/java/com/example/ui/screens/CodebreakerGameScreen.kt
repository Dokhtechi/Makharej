package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entertainment.CodebreakerGenerator
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

private data class CodebreakerGuessRow(
    val guess: String,
    val exactHits: Int,
    val misplacedHits: Int
)

private data class RestoredCodebreakerState(
    val guesses: List<CodebreakerGuessRow>,
    val eliminatedDigits: Set<Int>,
    val candidateDigits: Set<Int>,
    val revealedHintIndices: Set<Int>,
    val showClueNote: Boolean,
    val elapsedSeconds: Int
)

@Composable
fun CodebreakerGameScreen(
    puzzleEntity: EntertainmentPuzzleEntity,
    onBack: () -> Unit,
    onSaveProgress: (String, Int) -> Unit,
    onMarkCompleted: (String, Int, Int) -> Unit,
    onNextLevel: (() -> Unit)?,
    onShowGuide: (() -> Unit)? = null
) {
    val puzzle = remember(puzzleEntity.id, puzzleEntity.payloadJson) {
        CodebreakerGenerator.parsePayload(puzzleEntity.payloadJson)
    }
    if (puzzle == null) {
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("خطا در بارگذاری معمای کدشکن", color = appTextColor(MaterialTheme.colorScheme.onBackground))
        }
        return
    }

    val restored = remember(puzzleEntity.id) {
        val loadedGuesses = mutableListOf<CodebreakerGuessRow>()
        val loadedElim = mutableSetOf<Int>()
        val loadedCand = mutableSetOf<Int>()
        val loadedHints = mutableSetOf<Int>()
        var loadedShowNote = false
        var loadedSecs = 0

        try {
            if (puzzleEntity.savedProgressJson.isNotBlank()) {
                val obj = JSONObject(puzzleEntity.savedProgressJson)
                loadedSecs = obj.optInt("elapsedSeconds", 0).coerceAtLeast(0)
                loadedShowNote = obj.optBoolean("showClueNote", false)
                val gArr = obj.optJSONArray("guesses") ?: JSONArray()
                for (i in 0 until gArr.length()) {
                    val g = gArr.optString(i)
                    if (g.length == puzzle.codeLength) {
                        val (ex, mis) = CodebreakerGenerator.evaluateGuess(puzzle.secretCode, g)
                        loadedGuesses.add(CodebreakerGuessRow(g, ex, mis))
                    }
                }
                val eArr = obj.optJSONArray("eliminated") ?: JSONArray()
                for (i in 0 until eArr.length()) loadedElim.add(eArr.optInt(i))
                val cArr = obj.optJSONArray("candidates") ?: JSONArray()
                for (i in 0 until cArr.length()) loadedCand.add(cArr.optInt(i))
                val hArr = obj.optJSONArray("hintIndices") ?: JSONArray()
                for (i in 0 until hArr.length()) loadedHints.add(hArr.optInt(i))
            }
        } catch (_: Exception) {
        }

        RestoredCodebreakerState(
            guesses = loadedGuesses,
            eliminatedDigits = loadedElim,
            candidateDigits = loadedCand,
            revealedHintIndices = loadedHints,
            showClueNote = loadedShowNote,
            elapsedSeconds = loadedSecs
        )
    }

    var guesses by remember(puzzleEntity.id) { mutableStateOf(restored.guesses) }
    var currentInput by remember(puzzleEntity.id) { mutableStateOf("") }
    var eliminatedDigits by remember(puzzleEntity.id) { mutableStateOf(restored.eliminatedDigits) }
    var candidateDigits by remember(puzzleEntity.id) { mutableStateOf(restored.candidateDigits) }
    var revealedHintIndices by remember(puzzleEntity.id) { mutableStateOf(restored.revealedHintIndices) }
    var showClueNote by remember(puzzleEntity.id) { mutableStateOf(restored.showClueNote) }
    var scratchpadMode by remember(puzzleEntity.id) { mutableStateOf(false) }
    var elapsedSeconds by remember(puzzleEntity.id) { mutableIntStateOf(restored.elapsedSeconds) }
    var statusBanner by remember(puzzleEntity.id) { mutableStateOf<String?>(null) }

    val isWon = remember(guesses, puzzle.secretCode) {
        guesses.any { it.exactHits == puzzle.codeLength }
    }
    val isLost = remember(guesses, isWon, puzzle.maxAttempts) {
        !isWon && guesses.size >= puzzle.maxAttempts
    }
    var showWinDialog by remember(puzzleEntity.id) { mutableStateOf(false) }
    var showLoseDialog by remember(puzzleEntity.id) { mutableStateOf(false) }

    fun buildProgressJson(): String {
        val obj = JSONObject()
        obj.put("elapsedSeconds", elapsedSeconds)
        obj.put("showClueNote", showClueNote)
        obj.put("guesses", JSONArray(guesses.map { it.guess }))
        obj.put("eliminated", JSONArray(eliminatedDigits.toList()))
        obj.put("candidates", JSONArray(candidateDigits.toList()))
        obj.put("hintIndices", JSONArray(revealedHintIndices.toList()))
        return obj.toString()
    }

    val latestSave by rememberUpdatedState {
        if (!isWon && !isLost && (guesses.isNotEmpty() || elapsedSeconds > 0 || revealedHintIndices.isNotEmpty())) {
            onSaveProgress(buildProgressJson(), guesses.size)
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

    fun submitCurrentGuess() {
        if (isWon || isLost) return
        if (currentInput.length < puzzle.codeLength) {
            statusBanner = "لطفاً هر ${puzzle.codeLength} رقم رمز را وارد کنید."
            return
        }
        if (!puzzle.allowRepeats && currentInput.toSet().size < currentInput.length) {
            statusBanner = "در این سطح ارقام رمز غیرتکراری هستند."
            return
        }
        val (exact, misplaced) = CodebreakerGenerator.evaluateGuess(puzzle.secretCode, currentInput)
        val updated = guesses + CodebreakerGuessRow(currentInput, exact, misplaced)
        guesses = updated
        currentInput = ""
        statusBanner = null

        val progressJson = JSONObject().apply {
            put("elapsedSeconds", elapsedSeconds)
            put("showClueNote", showClueNote)
            put("guesses", JSONArray(updated.map { it.guess }))
            put("eliminated", JSONArray(eliminatedDigits.toList()))
            put("candidates", JSONArray(candidateDigits.toList()))
            put("hintIndices", JSONArray(revealedHintIndices.toList()))
        }.toString()

        if (exact == puzzle.codeLength) {
            onMarkCompleted(progressJson, elapsedSeconds, updated.size)
            showWinDialog = true
        } else if (updated.size >= puzzle.maxAttempts) {
            onSaveProgress(progressJson, updated.size)
            showLoseDialog = true
        } else {
            onSaveProgress(progressJson, updated.size)
        }
    }

    fun resetGame() {
        guesses = emptyList()
        currentInput = ""
        eliminatedDigits = emptySet()
        candidateDigits = emptySet()
        revealedHintIndices = emptySet()
        showClueNote = false
        scratchpadMode = false
        elapsedSeconds = 0
        statusBanner = null
        showWinDialog = false
        showLoseDialog = false
        onSaveProgress("", 0)
    }

    val attemptsLeft = (puzzle.maxAttempts - guesses.size).coerceAtLeast(0)
    val allowedDigits = (puzzle.minDigit..puzzle.maxDigit).toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("codebreaker_game_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    latestSave()
                    onBack()
                },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("codebreaker_back_button")
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
                    text = enDigits("${puzzle.themeTitle} • ارقام ${puzzle.minDigit} تا ${puzzle.maxDigit}" +
                            if (puzzle.allowRepeats) " (تکرار مجاز)" else " (بدون تکرار)"),
                    fontSize = 11.sp,
                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onShowGuide != null) {
                IconButton(
                    onClick = onShowGuide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("codebreaker_guide_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "راهنمای بازی کدشکن",
                        tint = Color(0xFFBE123C)
                    )
                }
            }

            // Timer badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = formatMmSs(elapsedSeconds),
                        fontSize = 12.sp,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }

            IconButton(
                onClick = { resetGame() },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("codebreaker_reset_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "شروع مجدد")
            }
        }

        // Vault Status Banner + Secret Slots (reveals hinted positions)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1B4B), Color(0xFF312E81), Color(0xFF4C1D95))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isWon) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isWon) Color(0xFF34D399) else Color(0xFFFDE047),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = enDigits("فرصت باقی‌مانده: $attemptsLeft از ${puzzle.maxAttempts}"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Smart Hint Button
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color(0xFFFBBF24).copy(alpha = 0.65f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isWon && !isLost) {
                                if (!showClueNote) {
                                    showClueNote = true
                                } else {
                                    val unrevealed = (0 until puzzle.codeLength).filter { it !in revealedHintIndices }
                                    if (unrevealed.size > 1) {
                                        revealedHintIndices = revealedHintIndices + unrevealed.first()
                                    } else {
                                        statusBanner = "حداکثر راهنمایی مجاز استفاده شده است."
                                    }
                                }
                            }
                            .testTag("codebreaker_hint_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = "راهنمایی هوشمند",
                                tint = Color(0xFFFDE047),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (!showClueNote) "سرنخ منطقی" else "کشف ۱ رقم",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFEF3C7)
                            )
                        }
                    }
                }

                // Secret vault slots (LTR for mathematical digit positions)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        for (idx in 0 until puzzle.codeLength) {
                            val showSecretChar = isWon || isLost || idx in revealedHintIndices
                            val displayChar = if (showSecretChar) puzzle.secretCode[idx].toString() else "?"
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isWon -> Color(0xFF10B981)
                                    idx in revealedHintIndices -> Color(0xFFF59E0B).copy(alpha = 0.30f)
                                    else -> Color.White.copy(alpha = 0.14f)
                                },
                                border = BorderStroke(
                                    1.2.dp,
                                    if (showSecretChar) Color(0xFFFDE047) else Color.White.copy(alpha = 0.28f)
                                ),
                                modifier = Modifier.size(width = 44.dp, height = 44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayChar,
                                        fontSize = 19.sp,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                if (showClueNote && puzzle.hintText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = enDigits("سرنخ: ${puzzle.hintText}"),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFEF08A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Peg Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Text("رقم و جایگاه درست", fontSize = 10.5.sp, color = Color(0xFFD1FAE5))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                        Text("رقم درست، جایگاه غلط", fontSize = 10.5.sp, color = Color(0xFFFEF3C7))
                    }
                }
            }
        }

        // Guess History List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (guesses.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = enDigits("اولین حدس ${puzzle.codeLength} رقمی خود را وارد کنید. از «حالت یادداشت» زیر صفحه‌کلید می‌توانید برای خط زدن ارقام نامحتمل استفاده کنید."),
                            fontSize = 12.sp,
                            lineHeight = 19.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(guesses) { idx, row ->
                    CodebreakerHistoryItem(
                        attemptNumber = idx + 1,
                        row = row,
                        codeLength = puzzle.codeLength
                    )
                }
            }
        }

        if (!statusBanner.isNullOrBlank()) {
            Text(
                text = enDigits(statusBanner!!),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Current Input Row + Submit
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until puzzle.codeLength) {
                            val ch = currentInput.getOrNull(i)?.toString().orEmpty()
                            val isActiveSlot = i == currentInput.length && !isWon && !isLost
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 40.dp, height = 42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (ch.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    )
                                    .border(
                                        width = if (isActiveSlot) 2.dp else 1.dp,
                                        color = if (isActiveSlot) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Text(
                                    text = ch,
                                    fontSize = 18.sp,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = {
                            if (currentInput.isNotEmpty()) currentInput = currentInput.dropLast(1)
                            statusBanner = null
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("codebreaker_backspace_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "پاک کردن")
                    }

                    Button(
                        onClick = { submitCurrentGuess() },
                        enabled = !isWon && !isLost && currentInput.length == puzzle.codeLength,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("codebreaker_submit_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("بررسی", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }

        // Scratchpad Mode Toggle + Tactile Digit Pad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (scratchpadMode) "حالت یادداشت: لمس رقم = خط‌زدن / نشانه سبز" else "صفحه‌کلید ورود رمز",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (scratchpadMode) Color(0xFFD97706) else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (scratchpadMode) Color(0xFFD97706) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { scratchpadMode = !scratchpadMode }
                    .testTag("codebreaker_scratchpad_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = null,
                        tint = if (scratchpadMode) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (scratchpadMode) "حالت یادداشت فعال" else "خط‌زدن ارقام",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (scratchpadMode) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        }

        // Digit Keypad (2 clean rows, >= 48dp touch targets)
        val keypadRows = remember(allowedDigits) {
            val split = (allowedDigits.size + 1) / 2
            listOf(allowedDigits.take(split), allowedDigits.drop(split)).filter { it.isNotEmpty() }
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                keypadRows.forEach { digitRow ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        digitRow.forEach { digit ->
                            val isElim = digit in eliminatedDigits
                            val isCand = digit in candidateDigits
                            val alreadyUsed = !puzzle.allowRepeats && digit.toString() in currentInput
                            val bgColor = when {
                                isElim -> Color(0xFFEF4444).copy(alpha = 0.16f)
                                isCand -> Color(0xFF10B981).copy(alpha = 0.22f)
                                alreadyUsed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                            val borderColor = when {
                                isElim -> Color(0xFFEF4444).copy(alpha = 0.6f)
                                isCand -> Color(0xFF10B981)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bgColor,
                                border = BorderStroke(1.2.dp, borderColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isWon && !isLost) {
                                        if (scratchpadMode) {
                                            when {
                                                !isElim && !isCand -> eliminatedDigits = eliminatedDigits + digit
                                                isElim -> {
                                                    eliminatedDigits = eliminatedDigits - digit
                                                    candidateDigits = candidateDigits + digit
                                                }
                                                else -> candidateDigits = candidateDigits - digit
                                            }
                                        } else {
                                            if (currentInput.length < puzzle.codeLength) {
                                                if (!puzzle.allowRepeats && digit.toString() in currentInput) {
                                                    statusBanner = "در این سطح ارقام غیرتکراری هستند."
                                                } else {
                                                    currentInput += digit.toString()
                                                    statusBanner = null
                                                }
                                            }
                                        }
                                    }
                                    .testTag("codebreaker_digit_$digit")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = digit.toString(),
                                        fontSize = 18.sp,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Black,
                                        textDecoration = if (isElim) TextDecoration.LineThrough else TextDecoration.None,
                                        color = when {
                                            isElim -> Color(0xFFDC2626)
                                            isCand -> Color(0xFF047857)
                                            alreadyUsed -> appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.5f)
                                            else -> appTextColor(MaterialTheme.colorScheme.onSurface)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
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
                Text("قفل گاوصندوق باز شد!", fontWeight = FontWeight.Black, fontSize = 17.sp)
            },
            text = {
                ScaledDialogContent {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = enDigits("آفرین! رمز ${puzzle.secretCode} را در ${guesses.size} تلاش و زمان ${formatMmSs(elapsedSeconds)} کشف کردید."),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            },
            confirmButton = {
                if (onNextLevel != null) {
                    Button(
                        onClick = {
                            showWinDialog = false
                            onNextLevel()
                        },
                        modifier = Modifier.testTag("codebreaker_next_level_button")
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
                    Text("مشاهده جدول")
                }
            }
        )
    }

    if (showLoseDialog) {
        AlertDialog(
            onDismissRequest = { showLoseDialog = false },
            title = { Text("فرصت‌ها به پایان رسید", fontWeight = FontWeight.Black, fontSize = 17.sp) },
            text = {
                ScaledDialogContent {
                    Text(
                        text = enDigits("رمز صحیح این مرحله ${puzzle.secretCode} بود. می‌توانید دوباره تلاش کنید یا به مرحله بعدی بروید."),
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
                if (onNextLevel != null) {
                    OutlinedButton(onClick = { showLoseDialog = false; onNextLevel() }) {
                        Text("مرحله بعدی")
                    }
                }
            }
        )
    }
}

@Composable
private fun CodebreakerHistoryItem(
    attemptNumber: Int,
    row: CodebreakerGuessRow,
    codeLength: Int
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (row.exactHits == codeLength) Color(0xFF10B981) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = attemptNumber.toString(),
                        fontSize = 11.sp,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    row.guess.forEach { ch ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(width = 32.dp, height = 34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = ch.toString(),
                                    fontSize = 16.sp,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }
            }

            // Visual Peg Feedback + Numeric Summary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(row.exactHits) {
                    Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(Color(0xFF10B981)))
                }
                repeat(row.misplacedHits) {
                    Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                }
                val misses = (codeLength - row.exactHits - row.misplacedHits).coerceAtLeast(0)
                repeat(misses) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    )
                }
            }
        }
    }
}

private fun formatMmSs(totalSeconds: Int): String {
    val mins = (totalSeconds / 60).coerceAtLeast(0)
    val secs = (totalSeconds % 60).coerceAtLeast(0)
    return "%02d:%02d".format(mins, secs)
}
