package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.EntertainmentDifficulty
import com.example.data.model.EntertainmentDownloadState
import com.example.data.model.EntertainmentGameType
import com.example.data.model.EntertainmentPackMetaEntity
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.ui.viewmodel.ExpenseViewModel

@Composable
fun EntertainmentScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allPuzzles by viewModel.entertainmentPuzzles.collectAsStateWithLifecycle()
    val packMeta by viewModel.entertainmentPackMeta.collectAsStateWithLifecycle()
    val downloadState by viewModel.entertainmentDownloadState.collectAsStateWithLifecycle()

    var selectedGameKey by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDifficultyKey by rememberSaveable { mutableStateOf(EntertainmentDifficulty.BEGINNER.key) }
    var activePuzzleId by rememberSaveable { mutableStateOf<String?>(null) }

    // Ensure full offline Room database is ready on first open
    LaunchedEffect(Unit) {
        viewModel.ensureEntertainmentDatabaseReady()
    }

    LaunchedEffect(downloadState.lastMessage) {
        val msg = downloadState.lastMessage
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearEntertainmentDownloadMessage()
        }
    }

    val activePuzzle = remember(activePuzzleId, allPuzzles) {
        activePuzzleId?.let { id -> allPuzzles.firstOrNull { it.id == id } }
    }

    // 1. Active Game Play View
    if (activePuzzle != null) {
        val sameBucket = remember(activePuzzle.gameType, activePuzzle.difficulty, allPuzzles) {
            allPuzzles
                .filter { it.gameType == activePuzzle.gameType && it.difficulty == activePuzzle.difficulty }
                .sortedBy { it.levelNumber }
        }
        val currentIdx = sameBucket.indexOfFirst { it.id == activePuzzle.id }
        val nextPuzzle = if (currentIdx in 0 until sameBucket.lastIndex) sameBucket[currentIdx + 1] else null

        val openNext: (() -> Unit)? = nextPuzzle?.let { next -> { activePuzzleId = next.id } }

        when (EntertainmentGameType.fromKey(activePuzzle.gameType)) {
            EntertainmentGameType.CROSSWORD -> {
                CrosswordGameScreen(
                    puzzleEntity = activePuzzle,
                    onBack = { activePuzzleId = null },
                    onSaveProgress = { json, mistakes -> viewModel.saveEntertainmentProgress(activePuzzle.id, json, mistakes) },
                    onMarkCompleted = { json, secs, mistakes -> viewModel.markEntertainmentCompleted(activePuzzle.id, json, secs, mistakes) },
                    onNextLevel = openNext
                )
            }
            EntertainmentGameType.SUDOKU -> {
                SudokuGameScreen(
                    puzzleEntity = activePuzzle,
                    onBack = { activePuzzleId = null },
                    onSaveProgress = { json, mistakes -> viewModel.saveEntertainmentProgress(activePuzzle.id, json, mistakes) },
                    onMarkCompleted = { json, secs, mistakes -> viewModel.markEntertainmentCompleted(activePuzzle.id, json, secs, mistakes) },
                    onNextLevel = openNext
                )
            }
            EntertainmentGameType.TRIVIA -> {
                TriviaGameScreen(
                    puzzleEntity = activePuzzle,
                    onBack = { activePuzzleId = null },
                    onSaveProgress = { json, mistakes -> viewModel.saveEntertainmentProgress(activePuzzle.id, json, mistakes) },
                    onMarkCompleted = { json, secs, mistakes -> viewModel.markEntertainmentCompleted(activePuzzle.id, json, secs, mistakes) },
                    onNextLevel = openNext
                )
            }
        }
        return
    }

    // 2. Game Level Selector View (Beginner -> Intermediate -> Advanced -> Master)
    if (selectedGameKey != null) {
        val gameType = EntertainmentGameType.fromKey(selectedGameKey!!)
        val selectedDiff = EntertainmentDifficulty.fromKey(selectedDifficultyKey)
        BackHandler { selectedGameKey = null }

        GameLevelSelectorScreen(
            gameType = gameType,
            selectedDifficulty = selectedDiff,
            allGamePuzzles = allPuzzles.filter { it.gameType == gameType.key },
            onSelectDifficulty = { selectedDifficultyKey = it.key },
            onSelectPuzzle = { activePuzzleId = it.id },
            onBack = { selectedGameKey = null }
        )
        return
    }

    // 3. Entertainment Main Hub
    val activeGames = remember { EntertainmentGameType.entries.map { it.key }.toSet() }
    val validPuzzles = remember(allPuzzles) { allPuzzles.filter { it.gameType in activeGames } }
    val totalCount = validPuzzles.size
    val solvedCount = validPuzzles.count { it.isCompleted }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = scaledDp(16.dp))
            .testTag("entertainment_screen"),
        verticalArrangement = Arrangement.spacedBy(scaledDp(12.dp))
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        // Offline Database Banner & One-Tap Download/Update Card
        item {
            OfflineDatabaseHeaderCard(
                totalPuzzles = totalCount,
                solvedPuzzles = solvedCount,
                packMeta = packMeta,
                downloadState = downloadState,
                onDownloadFullDatabase = { viewModel.downloadFullEntertainmentDatabase() }
            )
        }

        // Section Title
        item {
            Text(
                text = "بازی‌ها و سرگرمی‌های فکری (مبتدی تا استاد)",
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Black,
                color = appTextColor(MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 3 Game Cards (Crossword 15x15, Sudoku, Trivia — Persian Wordle removed)
        val gameStyles = listOf(
            GameCardStyle(
                type = EntertainmentGameType.CROSSWORD,
                icon = Icons.Default.GridOn,
                gradient = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
                badgeText = "مربع 15×15 • فارسی"
            ),
            GameCardStyle(
                type = EntertainmentGameType.SUDOKU,
                icon = Icons.Default.Grid4x4,
                gradient = listOf(Color(0xFF0284C7), Color(0xFF0D9488)),
                badgeText = "6×6 و 9×9 • 3 جان"
            ),
            GameCardStyle(
                type = EntertainmentGameType.TRIVIA,
                icon = Icons.Default.Psychology,
                gradient = listOf(Color(0xFF059669), Color(0xFF10B981)),
                badgeText = "چیستان و اطلاعات عمومی"
            )
        )

        gameStyles.forEach { style ->
            item(key = style.type.key) {
                val gamePuzzles = validPuzzles.filter { it.gameType == style.type.key }
                val gameSolved = gamePuzzles.count { it.isCompleted }
                EntertainmentGameCard(
                    style = style,
                    totalStages = gamePuzzles.size,
                    solvedStages = gameSolved,
                    onClick = {
                        selectedDifficultyKey = EntertainmentDifficulty.BEGINNER.key
                        selectedGameKey = style.type.key
                    }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

private data class GameCardStyle(
    val type: EntertainmentGameType,
    val icon: ImageVector,
    val gradient: List<Color>,
    val badgeText: String
)

@Composable
private fun OfflineDatabaseHeaderCard(
    totalPuzzles: Int,
    solvedPuzzles: Int,
    packMeta: EntertainmentPackMetaEntity?,
    downloadState: EntertainmentDownloadState,
    onDownloadFullDatabase: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF1E1B4B),
                            Color(0xFF312E81),
                            Color(0xFF0F766E)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SportsEsports,
                            contentDescription = null,
                            tint = Color(0xFFFDE047),
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "باشگاه سرگرمی آفلاین",
                        fontSize = 16.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = enDigits("ذخیره‌شده در گوشی: $totalPuzzles مرحله ($solvedPuzzles حل‌شده)"),
                        fontSize = 11.sp,
                        color = Color(0xFFE0E7FF),
                        maxLines = 2
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = Color(0xFF6EE7B7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "آفلاین",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFD1FAE5),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (packMeta != null && packMeta.lastUpdatedShamsi.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "آخرین بروزرسانی:",
                        fontSize = 11.sp,
                        color = Color(0xFFC7D2FE),
                        maxLines = 1,
                        softWrap = false
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = enDigits(packMeta.lastUpdatedShamsi),
                            fontSize = 11.5.sp,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE047),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (downloadState.isDownloading) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = enDigits(downloadState.statusText),
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${downloadState.progressPercent}%",
                                fontSize = 12.sp,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFDE047),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (downloadState.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF34D399),
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                }
            } else {
                Button(
                    onClick = onDownloadFullDatabase,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("download_entertainment_db_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = enDigits("بروزرسانی دیتابیس آفلاین (360 مرحله)"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EntertainmentGameCard(
    style: GameCardStyle,
    totalStages: Int,
    solvedStages: Int,
    onClick: () -> Unit
) {
    val progress = if (totalStages > 0) solvedStages.toFloat() / totalStages else 0f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("entertainment_game_card_${style.type.key.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(style.gradient))
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = style.type.titleFa,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Title on its own dedicated full-width line (prevents any vertical character stacking)
                Text(
                    text = style.type.titleFa,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Badge pill on its own crisp horizontal line
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = style.gradient.first().copy(alpha = 0.13f)
                ) {
                    Text(
                        text = enDigits(style.badgeText),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = style.gradient.first(),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = enDigits(style.type.subtitleFa),
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = style.gradient.first(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = enDigits("$solvedStages / $totalStages"),
                            fontSize = 11.sp,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = style.gradient.first(),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "شروع بازی",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GameLevelSelectorScreen(
    gameType: EntertainmentGameType,
    selectedDifficulty: EntertainmentDifficulty,
    allGamePuzzles: List<EntertainmentPuzzleEntity>,
    onSelectDifficulty: (EntertainmentDifficulty) -> Unit,
    onSelectPuzzle: (EntertainmentPuzzleEntity) -> Unit,
    onBack: () -> Unit
) {
    val levelList = remember(allGamePuzzles, selectedDifficulty) {
        allGamePuzzles
            .filter { it.difficulty == selectedDifficulty.key }
            .sortedBy { it.levelNumber }
    }

    val diffColor = when (selectedDifficulty) {
        EntertainmentDifficulty.BEGINNER -> Color(0xFF10B981)
        EntertainmentDifficulty.INTERMEDIATE -> Color(0xFF0EA5E9)
        EntertainmentDifficulty.ADVANCED -> Color(0xFFF59E0B)
        EntertainmentDifficulty.MASTER -> Color(0xFFE11D48)
    }

    val diffDescription = when (selectedDifficulty) {
        EntertainmentDifficulty.BEGINNER -> "مناسب شروع و گرم کردن ذهن با راهنمایی بیشتر"
        EntertainmentDifficulty.INTERMEDIATE -> "چالش متعادل برای تقویت تمرکز و دایره لغات"
        EntertainmentDifficulty.ADVANCED -> "سطح حرفه‌ای با معماها و چیدمان پیچیده‌تر"
        EntertainmentDifficulty.MASTER -> "سخت‌ترین چالش‌ها مخصوص بازیکنان باتجربه"
    }

    val inProgress = remember(levelList) {
        levelList.firstOrNull { !it.isCompleted && it.savedProgressJson.isNotBlank() }
    }
    val nextUnsolved = remember(levelList) {
        levelList.firstOrNull { !it.isCompleted } ?: levelList.firstOrNull()
    }
    val primaryTarget = inProgress ?: nextUnsolved
    val solvedInSelected = remember(levelList) { levelList.count { it.isCompleted } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .testTag("entertainment_level_selector"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("level_selector_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gameType.titleFa,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onBackground),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = enDigits("انتخاب سطح بازی • ${allGamePuzzles.count { it.isCompleted }} بازی حل‌شده"),
                        fontSize = 11.5.sp,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4 Clean Difficulty Cards (مبتدی / متوسط / پیشرفته / استاد) — NO 1..30 stage grid
        item {
            val tierRows = remember { EntertainmentDifficulty.entries.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tierRows.forEach { rowDiffs ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowDiffs.forEach { diff ->
                            val isSelected = diff == selectedDifficulty
                            val tierColor = when (diff) {
                                EntertainmentDifficulty.BEGINNER -> Color(0xFF10B981)
                                EntertainmentDifficulty.INTERMEDIATE -> Color(0xFF0EA5E9)
                                EntertainmentDifficulty.ADVANCED -> Color(0xFFF59E0B)
                                EntertainmentDifficulty.MASTER -> Color(0xFFE11D48)
                            }
                            val solvedInTier = allGamePuzzles.count { it.difficulty == diff.key && it.isCompleted }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) tierColor else MaterialTheme.colorScheme.surfaceVariant,
                                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 60.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onSelectDifficulty(diff) }
                                    .testTag("difficulty_tab_${diff.key.lowercase()}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = diff.titleFa,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) Color.White else appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (solvedInTier > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surface
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else tierColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        text = solvedInTier.toString(),
                                                        fontSize = 11.sp,
                                                        fontFamily = OutfitFontFamily,
                                                        fontWeight = FontWeight.Black,
                                                        color = if (isSelected) Color.White else tierColor,
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

        // Selected Difficulty Action Card (Play / Resume + Random New Game)
        item {
            if (levelList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (primaryTarget != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.5.dp, diffColor.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "سطح ${selectedDifficulty.titleFa}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = diffColor
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = diffColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = enDigits("حل‌شده: $solvedInSelected"),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = diffColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = diffDescription,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                        )

                        Button(
                            onClick = { onSelectPuzzle(primaryTarget) },
                            colors = ButtonDefaults.buttonColors(containerColor = diffColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("play_next_unsolved_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (inProgress != null) {
                                    "ادامه بازی نیمه‌تمام (${selectedDifficulty.titleFa})"
                                } else {
                                    "شروع بازی (${selectedDifficulty.titleFa})"
                                },
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val alternatePuzzles = remember(levelList, primaryTarget) {
                            levelList.filter { it.id != primaryTarget.id && !it.isCompleted }
                                .ifEmpty { levelList.filter { it.id != primaryTarget.id } }
                        }
                        if (alternatePuzzles.isNotEmpty()) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { onSelectPuzzle(alternatePuzzles.random()) },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.2.dp, diffColor.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("play_random_new_button")
                            ) {
                                Text(
                                    text = "شروع یک بازی جدید دیگر در سطح ${selectedDifficulty.titleFa}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    color = diffColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
