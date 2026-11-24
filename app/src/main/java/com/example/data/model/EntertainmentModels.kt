package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class EntertainmentGameType(
    val key: String,
    val titleFa: String,
    val subtitleFa: String
) {
    CROSSWORD(
        key = "CROSSWORD",
        titleFa = "جدول کلمات متقاطع",
        subtitleFa = "جدول مربع 15×15 فارسی با راهنمای افقی و عمودی"
    ),
    SUDOKU(
        key = "SUDOKU",
        titleFa = "جدول سودوکو",
        subtitleFa = "معمای عددی منطقی 6×6 و 9×9 با ذخیره خودکار"
    ),
    TRIVIA(
        key = "TRIVIA",
        titleFa = "معما و اطلاعات عمومی",
        subtitleFa = "چیستان، هوش و دانستنی‌های ایران و جهان"
    );

    companion object {
        fun fromKey(key: String): EntertainmentGameType =
            entries.firstOrNull { it.key == key } ?: CROSSWORD
    }
}

enum class EntertainmentDifficulty(
    val key: String,
    val titleFa: String,
    val order: Int
) {
    BEGINNER("BEGINNER", "مبتدی", 1),
    INTERMEDIATE("INTERMEDIATE", "متوسط", 2),
    ADVANCED("ADVANCED", "پیشرفته", 3),
    MASTER("MASTER", "استاد", 4);

    companion object {
        fun fromKey(key: String): EntertainmentDifficulty =
            entries.firstOrNull { it.key == key } ?: BEGINNER
    }
}

@Entity(tableName = "entertainment_puzzles")
data class EntertainmentPuzzleEntity(
    @PrimaryKey val id: String,
    val gameType: String,
    val difficulty: String,
    val levelNumber: Int,
    val title: String,
    val payloadJson: String,
    val savedProgressJson: String = "",
    val isCompleted: Boolean = false,
    val bestTimeSeconds: Int = 0,
    val mistakesCount: Int = 0,
    val updatedAtEpochMs: Long = 0L
)

@Entity(tableName = "entertainment_pack_meta")
data class EntertainmentPackMetaEntity(
    @PrimaryKey val packId: String = "main_offline_pack",
    val versionCode: Int = 1,
    val totalPuzzles: Int = 0,
    val lastUpdatedShamsi: String = "",
    val lastUpdatedEpochMs: Long = 0L,
    val sourceLabel: String = "دیتابیس آفلاین کامل"
)

data class EntertainmentDownloadState(
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val statusText: String = "",
    val lastMessage: String? = null
)

data class CrosswordClue(
    val number: Int,
    val direction: String, // "ACROSS" or "DOWN"
    val row: Int,
    val col: Int,
    val answer: String,
    val clue: String
)

data class CrosswordPuzzleData(
    val rows: Int,
    val cols: Int,
    val solutionGrid: List<String>, // '#' for blocked cell, otherwise single Persian char
    val clues: List<CrosswordClue>
)

data class SudokuPuzzleData(
    val size: Int,        // 6 or 9
    val boxRows: Int,     // 2 or 3
    val boxCols: Int,     // 3
    val puzzle: String,   // '0' for empty, '1'..'9' for given
    val solution: String  // '1'..'9'
)

data class TriviaQuestionData(
    val question: String,
    val options: List<String>,
    val correctIndex: Int,
    val category: String,
    val explanation: String
)
