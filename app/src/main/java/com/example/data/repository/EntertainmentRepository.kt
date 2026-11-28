package com.example.data.repository

import android.content.Context
import com.example.data.db.EntertainmentDatabase
import com.example.data.entertainment.CodebreakerGenerator
import com.example.data.entertainment.NonogramGenerator
import com.example.data.entertainment.PersianCrosswordGenerator
import com.example.data.entertainment.PersianWordleAndTriviaBank
import com.example.data.entertainment.SudokuGenerator
import com.example.data.model.EntertainmentDownloadState
import com.example.data.model.EntertainmentGameType
import com.example.data.model.EntertainmentPackMetaEntity
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.utils.GitHubUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EntertainmentRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val dao = EntertainmentDatabase.getDatabase(appContext).entertainmentDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .build()

    val allPuzzles: Flow<List<EntertainmentPuzzleEntity>> = dao.observeAllPuzzles()
    val packMeta: Flow<EntertainmentPackMetaEntity?> = dao.observePackMeta()

    private val _downloadState = MutableStateFlow(EntertainmentDownloadState())
    val downloadState: StateFlow<EntertainmentDownloadState> = _downloadState.asStateFlow()

    /**
     * Ensures the local offline database has the latest v3 puzzle library (15x15 crosswords,
     * Sudokus, and Riddles/Trivia — with removed Wordle records cleaned up).
     */
    suspend fun ensureDatabaseReady() {
        withContext(Dispatchers.IO) {
            val meta = dao.getPackMetaOnce()
            val count = dao.getTotalPuzzleCount()
            if (meta == null || meta.versionCode < CURRENT_PACK_VERSION || count < EXPECTED_CORE_PUZZLES) {
                downloadAndSyncFullOfflineDatabase(checkRemoteGitHub = false)
            }
        }
    }

    /**
     * One-tap complete offline database download & update:
     * 1) Compiles & downloads all 360 stages across 15x15 Persian Crossword, Sudoku, and Trivia
     *    (Beginner -> Intermediate -> Advanced -> Master).
     * 2) Deletes deprecated Wordle entries if present from earlier builds.
     * 3) Preserves solved status, best times, and saved mid-game progress for compatible puzzles.
     */
    suspend fun downloadAndSyncFullOfflineDatabase(checkRemoteGitHub: Boolean = true) {
        if (_downloadState.value.isDownloading) return
        _downloadState.value = EntertainmentDownloadState(
            isDownloading = true,
            progressPercent = 10,
            statusText = "در حال آماده‌سازی دیتابیس آفلاین سرگرمی‌ها..."
        )

        withContext(Dispatchers.IO) {
            try {
                // Remove deprecated Wordle rows from phone database
                dao.deleteByGameType("WORDLE")

                val existingById = dao.getAllPuzzlesSnapshot().associateBy { it.id }

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 25,
                    statusText = "در حال ساخت 120 جدول کلمات متقاطع 15×15 (مبتدی تا استاد)..."
                )
                val crosswords = PersianCrosswordGenerator.generateAllLevels()

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 45,
                    statusText = "در حال دریافت 120 جدول سودوکو (6×6 و 9×9)..."
                )
                val sudokus = SudokuGenerator.generateAllLevels()

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 60,
                    statusText = "در حال دریافت 120 مرحله معما و اطلاعات عمومی..."
                )
                val trivias = PersianWordleAndTriviaBank.generateAllTriviaLevels()

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 72,
                    statusText = "در حال ساخت 120 مرحله کدشکن منطقی (مسترمایند)..."
                )
                val codebreakers = CodebreakerGenerator.generateAllLevels()

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 82,
                    statusText = "در حال ساخت 120 جدول تصویری نونگرام (5×5 تا 10×10)..."
                )
                val nonograms = NonogramGenerator.generateAllLevels()

                var remoteBonus = emptyList<EntertainmentPuzzleEntity>()
                var sourceLabel = "بسته کامل آفلاین (600 مرحله)"

                if (checkRemoteGitHub) {
                    _downloadState.value = _downloadState.value.copy(
                        progressPercent = 88,
                        statusText = "در حال بررسی بسته‌های تکمیلی از مخزن آنلاین..."
                    )
                    val repoInput = GitHubUpdateHelper.getSavedRepoInput(appContext)
                    val remoteResult = tryFetchRemotePackFromGitHub(repoInput)
                    if (remoteResult.isNotEmpty()) {
                        remoteBonus = remoteResult
                        sourceLabel = "همگام‌سازی کامل آنلاین + آفلاین"
                    }
                }

                _downloadState.value = _downloadState.value.copy(
                    progressPercent = 95,
                    statusText = "در حال ذخیره دائمی در حافظه گوشی..."
                )

                val allGenerated = (crosswords + sudokus + trivias + codebreakers + nonograms + remoteBonus)
                    .distinctBy { it.id }
                    .map { fresh ->
                        val prev = existingById[fresh.id]
                        // Only keep saved grid progress if payload hasn't structurally changed (e.g. old 6x6 -> new 15x15 crossword resets grid)
                        if (prev != null && prev.payloadJson == fresh.payloadJson) {
                            fresh.copy(
                                savedProgressJson = prev.savedProgressJson,
                                isCompleted = prev.isCompleted,
                                bestTimeSeconds = prev.bestTimeSeconds,
                                mistakesCount = prev.mistakesCount
                            )
                        } else {
                            fresh
                        }
                    }

                dao.upsertPuzzles(allGenerated)

                val nowMs = System.currentTimeMillis()
                val exactShamsi = NewsRepository.formatExactUpdateHeader(nowMs)
                dao.upsertPackMeta(
                    EntertainmentPackMetaEntity(
                        packId = "main_offline_pack",
                        versionCode = CURRENT_PACK_VERSION,
                        totalPuzzles = allGenerated.size,
                        lastUpdatedShamsi = exactShamsi,
                        lastUpdatedEpochMs = nowMs,
                        sourceLabel = sourceLabel
                    )
                )

                _downloadState.value = EntertainmentDownloadState(
                    isDownloading = false,
                    progressPercent = 100,
                    statusText = "",
                    lastMessage = "دیتابیس کامل سرگرمی‌ها (${allGenerated.size} مرحله با جداول 15×15) در گوشی ذخیره شد و بدون اینترنت قابل استفاده است."
                )
            } catch (_: Exception) {
                _downloadState.value = EntertainmentDownloadState(
                    isDownloading = false,
                    progressPercent = 0,
                    statusText = "",
                    lastMessage = "خطا در به‌روزرسانی دیتابیس سرگرمی‌ها؛ لطفاً مجدداً تلاش کنید."
                )
            }
        }
    }

    private fun tryFetchRemotePackFromGitHub(repoInput: String): List<EntertainmentPuzzleEntity> {
        val parsed = GitHubUpdateHelper.parseOwnerAndRepo(repoInput) ?: return emptyList()
        val (owner, repo) = parsed
        val urls = listOf(
            "https://raw.githubusercontent.com/$owner/$repo/main/entertainment_pack.json",
            "https://raw.githubusercontent.com/$owner/$repo/master/entertainment_pack.json"
        )
        for (url in urls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string().orEmpty()
                    if (body.isBlank()) return@use
                    return parseRemotePackJson(body)
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    private fun parseRemotePackJson(rawJson: String): List<EntertainmentPuzzleEntity> {
        val result = mutableListOf<EntertainmentPuzzleEntity>()
        val now = System.currentTimeMillis()
        val allowedTypes = EntertainmentGameType.entries.map { it.key }.toSet()
        try {
            val root = JSONObject(rawJson)
            val arr = root.optJSONArray("puzzles") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim()
                val gameType = obj.optString("gameType").trim()
                val difficulty = obj.optString("difficulty").trim()
                val levelNumber = obj.optInt("levelNumber", 0)
                val title = obj.optString("title").trim()
                val payload = obj.optJSONObject("payload")?.toString()
                    ?: obj.optString("payloadJson").trim()
                if (id.isNotBlank() && gameType in allowedTypes && difficulty.isNotBlank() &&
                    levelNumber > 0 && payload.isNotBlank()
                ) {
                    result.add(
                        EntertainmentPuzzleEntity(
                            id = id,
                            gameType = gameType,
                            difficulty = difficulty,
                            levelNumber = levelNumber,
                            title = title.ifBlank { "مرحله $levelNumber" },
                            payloadJson = payload,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    suspend fun saveProgress(puzzleId: String, progressJson: String, mistakesCount: Int) {
        withContext(Dispatchers.IO) {
            dao.saveProgress(
                id = puzzleId,
                progressJson = progressJson,
                mistakesCount = mistakesCount,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun markCompleted(
        puzzleId: String,
        progressJson: String,
        elapsedSeconds: Int,
        mistakesCount: Int
    ) {
        withContext(Dispatchers.IO) {
            dao.markCompleted(
                id = puzzleId,
                progressJson = progressJson,
                elapsedSeconds = elapsedSeconds,
                mistakesCount = mistakesCount,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun resetPuzzle(puzzleId: String) {
        withContext(Dispatchers.IO) {
            dao.resetPuzzleProgress(puzzleId)
        }
    }

    fun clearDownloadMessage() {
        if (_downloadState.value.lastMessage != null) {
            _downloadState.value = _downloadState.value.copy(lastMessage = null)
        }
    }

    companion object {
        const val CURRENT_PACK_VERSION = 5
        const val EXPECTED_CORE_PUZZLES = 600
    }
}
