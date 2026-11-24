package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entertainment.PersianWordleAndTriviaBank
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale

private data class SavedTriviaState(
    val selectedOption: Int?,
    val elapsedSeconds: Int,
    val wrongAttempts: Int
)

private fun parseSavedTriviaState(json: String, fallbackMistakes: Int): SavedTriviaState {
    if (json.isBlank()) return SavedTriviaState(null, 0, fallbackMistakes)
    return try {
        val obj = JSONObject(json)
        val sel = if (obj.has("selected") && !obj.isNull("selected")) obj.optInt("selected", -1).takeIf { it >= 0 } else null
        val secs = obj.optInt("elapsedSeconds", 0).coerceAtLeast(0)
        val wrong = obj.optInt("wrongAttempts", fallbackMistakes).coerceAtLeast(0)
        SavedTriviaState(sel, secs, wrong)
    } catch (_: Exception) {
        SavedTriviaState(null, 0, fallbackMistakes)
    }
}

private fun formatTriviaTime(secs: Int): String =
    String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)

@Composable
fun TriviaGameScreen(
    puzzleEntity: EntertainmentPuzzleEntity,
    onBack: () -> Unit,
    onSaveProgress: (String, Int) -> Unit,
    onMarkCompleted: (String, Int, Int) -> Unit,
    onNextLevel: (() -> Unit)?
) {
    val data = remember(puzzleEntity.id, puzzleEntity.payloadJson) {
        PersianWordleAndTriviaBank.deserializeTrivia(puzzleEntity.payloadJson)
    }
    if (data == null) {
        BackHandler { onBack() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("خطا در بارگذاری معما")
        }
        return
    }

    val initialSaved = remember(puzzleEntity.id) {
        parseSavedTriviaState(puzzleEntity.savedProgressJson, puzzleEntity.mistakesCount)
    }
    val hasResumeSession = remember(puzzleEntity.id) {
        !puzzleEntity.isCompleted && (initialSaved.elapsedSeconds > 0 || initialSaved.selectedOption != null)
    }

    var showResumeDialog by remember(puzzleEntity.id) { mutableStateOf(hasResumeSession) }
    var selectedOption by remember(puzzleEntity.id) {
        mutableStateOf(if (puzzleEntity.isCompleted) data.correctIndex else initialSaved.selectedOption)
    }
    var wrongAttempts by remember(puzzleEntity.id) { mutableIntStateOf(initialSaved.wrongAttempts) }
    var elapsedSeconds by remember(puzzleEntity.id) {
        mutableIntStateOf(
            if (puzzleEntity.isCompleted && puzzleEntity.bestTimeSeconds > 0) {
                puzzleEntity.bestTimeSeconds
            } else {
                initialSaved.elapsedSeconds
            }
        )
    }
    val isAnsweredCorrectly = selectedOption == data.correctIndex || puzzleEntity.isCompleted

    fun buildProgressJson(): String = JSONObject().apply {
        if (selectedOption != null) put("selected", selectedOption)
        put("elapsedSeconds", elapsedSeconds)
        put("wrongAttempts", wrongAttempts)
    }.toString()

    fun persistProgress() {
        if (!isAnsweredCorrectly && (elapsedSeconds > 0 || selectedOption != null)) {
            onSaveProgress(buildProgressJson(), wrongAttempts)
        }
    }

    fun handleExit() {
        persistProgress()
        onBack()
    }

    BackHandler { handleExit() }

    DisposableEffect(puzzleEntity.id) {
        onDispose {
            persistProgress()
        }
    }

    LaunchedEffect(puzzleEntity.id, isAnsweredCorrectly, showResumeDialog) {
        while (!isAnsweredCorrectly && !showResumeDialog) {
            delay(1000L)
            elapsedSeconds++
            if (elapsedSeconds % 5 == 0) {
                onSaveProgress(buildProgressJson(), wrongAttempts)
            }
        }
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = {
                Text(
                    text = "ادامه معمای قبلی؟",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = enDigits(
                        "شما قبلاً این معما را نیمه‌تمام گذاشته بودید (زمان ثبت‌شده: ${formatTriviaTime(elapsedSeconds)}). آیا می‌خواهید از همان‌جا با ادامه تایمر بازی کنید؟"
                    ),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showResumeDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("ادامه بازی", fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        selectedOption = null
                        wrongAttempts = 0
                        elapsedSeconds = 0
                        showResumeDialog = false
                        onSaveProgress("", 0)
                    }
                ) {
                    Text("شروع از نو", fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("trivia_game_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
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
                        .testTag("trivia_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = enDigits(puzzleEntity.title),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onBackground),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = data.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "زمان بازی",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = formatTriviaTime(elapsedSeconds),
                                fontSize = 11.5.sp,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                color = appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                if (isAnsweredCorrectly) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "درست",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF047857),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Question Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ),
            border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = data.category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    text = enDigits(data.question),
                    fontSize = 16.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = appTextColor(MaterialTheme.colorScheme.onPrimaryContainer)
                )
            }
        }

        // 4 Answer Options
        val optionLabels = listOf("1", "2", "3", "4")
        data.options.forEachIndexed { idx, optText ->
            val isChosen = selectedOption == idx
            val isCorrectOpt = idx == data.correctIndex

            val cardBg = when {
                isAnsweredCorrectly && isCorrectOpt -> Color(0xFF10B981).copy(alpha = 0.16f)
                isChosen && !isCorrectOpt -> Color(0xFFEF4444).copy(alpha = 0.14f)
                else -> MaterialTheme.colorScheme.surface
            }
            val borderCol = when {
                isAnsweredCorrectly && isCorrectOpt -> Color(0xFF10B981)
                isChosen && !isCorrectOpt -> Color(0xFFEF4444)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = !isAnsweredCorrectly) {
                        selectedOption = idx
                        if (idx == data.correctIndex) {
                            val json = JSONObject().apply {
                                put("selected", idx)
                                put("elapsedSeconds", elapsedSeconds)
                                put("wrongAttempts", wrongAttempts)
                            }.toString()
                            onMarkCompleted(json, elapsedSeconds, wrongAttempts)
                        } else {
                            wrongAttempts++
                            onSaveProgress(buildProgressJson(), wrongAttempts)
                        }
                    }
                    .testTag("trivia_option_$idx"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(1.4.dp, borderCol)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isAnsweredCorrectly && isCorrectOpt -> Color(0xFF10B981)
                            isChosen && !isCorrectOpt -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when {
                                isAnsweredCorrectly && isCorrectOpt -> Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                isChosen && !isCorrectOpt -> Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> Text(
                                    text = optionLabels.getOrElse(idx) { "${idx + 1}" },
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    Text(
                        text = enDigits(optText),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Instant Explanation & Next Stage Button
        if (isAnsweredCorrectly) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "آفرین! پاسخ کاملاً درست است",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF047857)
                        )
                    }
                    Text(
                        text = enDigits(data.explanation),
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF065F46)
                    )
                    if (onNextLevel != null) {
                        Button(
                            onClick = onNextLevel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("trivia_next_button")
                        ) {
                            Text("معمای بعدی", fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        } else if (selectedOption != null) {
            Text(
                text = "پاسخ نادرست بود؛ گزینه دیگری را امتحان کنید!",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFDC2626),
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
