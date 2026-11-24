package com.example.data.network

import android.content.Context
import com.example.utils.PersianDateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

enum class TgjuChartAsset(
    val symbol: String,
    val fallbackSymbols: List<String>,
    val titleFa: String,
    val unitFa: String,
    val isRialToToman: Boolean,
    val isUsdCurrency: Boolean,
    val showDecimals: Boolean,
    val accentHex: Long,
    val minPlausiblePrice: Double,
    val maxPlausiblePrice: Double
) {
    DOLLAR(
        symbol = "price_dollar_rl",
        fallbackSymbols = emptyList(),
        titleFa = "دلار",
        unitFa = "تومان",
        isRialToToman = true,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFF047857,
        minPlausiblePrice = 500.0,
        maxPlausiblePrice = 500_000.0
    ),
    EURO(
        symbol = "price_eur",
        fallbackSymbols = emptyList(),
        titleFa = "یورو",
        unitFa = "تومان",
        isRialToToman = true,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFF1D4ED8,
        minPlausiblePrice = 700.0,
        maxPlausiblePrice = 600_000.0
    ),
    GOLD_18K(
        symbol = "geram18",
        fallbackSymbols = emptyList(),
        titleFa = "طلای ۱۸ عیار",
        unitFa = "تومان",
        isRialToToman = true,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFFCA8A04,
        minPlausiblePrice = 10_000.0,
        maxPlausiblePrice = 50_000_000.0
    ),
    SEKEH_EMAMI(
        symbol = "retail_sekee",
        fallbackSymbols = listOf("sekee"),
        titleFa = "سکه امامی (تک‌فروشی)",
        unitFa = "تومان",
        isRialToToman = true,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFFD97706,
        minPlausiblePrice = 100_000.0,
        maxPlausiblePrice = 500_000_000.0
    ),
    ROB_SEKEH(
        symbol = "retail_rob",
        fallbackSymbols = listOf("rob"),
        titleFa = "ربع سکه (تک‌فروشی)",
        unitFa = "تومان",
        isRialToToman = true,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFFE11D48,
        minPlausiblePrice = 30_000.0,
        maxPlausiblePrice = 200_000_000.0
    ),
    GOLD_OUNCE(
        symbol = "ons",
        fallbackSymbols = emptyList(),
        titleFa = "انس جهانی طلا",
        unitFa = "دلار",
        isRialToToman = false,
        isUsdCurrency = true,
        showDecimals = true,
        accentHex = 0xFF7C3AED,
        minPlausiblePrice = 500.0,
        maxPlausiblePrice = 25_000.0
    ),
    BITCOIN(
        symbol = "crypto-bitcoin",
        fallbackSymbols = emptyList(),
        titleFa = "بیت‌کوین",
        unitFa = "دلار",
        isRialToToman = false,
        isUsdCurrency = true,
        showDecimals = false,
        accentHex = 0xFFEA580C,
        minPlausiblePrice = 1.0,
        maxPlausiblePrice = 500_000.0
    ),
    BOURSE(
        symbol = "bourse",
        fallbackSymbols = listOf("index_overall"),
        titleFa = "شاخص بورس",
        unitFa = "واحد",
        isRialToToman = false,
        isUsdCurrency = false,
        showDecimals = false,
        accentHex = 0xFF2563EB,
        minPlausiblePrice = 10_000.0,
        maxPlausiblePrice = 50_000_000.0
    ),
    BRENT_OIL(
        symbol = "oil_brent",
        fallbackSymbols = listOf("energy-brent-oil"),
        titleFa = "نفت برنت",
        unitFa = "دلار",
        isRialToToman = false,
        isUsdCurrency = true,
        showDecimals = true,
        accentHex = 0xFF0F766E,
        minPlausiblePrice = 10.0,
        maxPlausiblePrice = 300.0
    )
}

enum class TgjuChartRange(
    val labelFa: String,
    val daysBack: Int
) {
    WEEK_1("۱ هفته", 7),
    MONTH_1("۱ ماه", 30),
    MONTH_6("۶ ماه", 180),
    YEAR_1("۱ سال", 365),
    FROM_1390("از ۱۳۹۰", 0)
}

data class TgjuHistoryPoint(
    val epochMs: Long,
    val price: Double,
    val high: Double,
    val low: Double,
    val shamsiDate: String,      // e.g. "1405/09/02"
    val shamsiYear: Int,
    val shamsiMonth: Int,
    val shamsiDay: Int,
    val weekdayFa: String,       // e.g. "سه‌شنبه"
    val shortWeekdayFa: String,  // e.g. "۳شنبه"
    val monthDayFa: String       // e.g. "2 آذر"
)

enum class TgjuHistorySource {
    FULL_NETWORK_FETCH,     // One-time full historical download since 1390/01/01
    INCREMENTAL_UPDATE,     // Incremental sync: fetched only missing recent days & merged into cache
    DISK_CACHE_FRESH,       // Served instantaneously from persistent device cache (already fresh)
    DISK_CACHE_OFFLINE      // Offline fallback: served directly from persistent since-1390 device cache
}

data class TgjuHistoryLoadResult(
    val points: List<TgjuHistoryPoint>,
    val source: TgjuHistorySource,
    val lastSyncEpochMs: Long,
    val newlyAddedDaysCount: Int = 0,
    val isFullFrom1390Cached: Boolean = false
)

data class TgjuDateSearchResult(
    val point: TgjuHistoryPoint,
    val indexInAllPoints: Int,
    val isExactMatch: Boolean,
    val isOutOfRange: Boolean,
    val requestedShamsiDate: String
)

/**
 * Extracts and persistently caches real daily historical price series since Shamsi year 1390
 * (`1390/01/01` ≈ 2011/03/21) directly from https://www.tgju.org for Dollar, Euro, 18k Gold,
 * Sekeh Emami, Rob Sekeh, Gold Ounce, Bitcoin, Tehran Bourse, and Brent Oil.
 *
 * Offline & Incremental Cache Architecture:
 * - On the first update for an asset, fetches the full archive back to 1390/01/01 (with automatic
 *   pagination if TGJU caps per-page rows) and saves it atomically to device storage (`filesDir`).
 * - On subsequent updates, NEVER re-downloads past historical data; calculates only the delta days
 *   since the latest cached record and fetches a small incremental window, merging new/updated days
 *   into the cached archive.
 * - Works seamlessly offline by reading the full archive since 1390 directly from device storage.
 */
object TgjuHistoryService {

    const val MIN_SHAMSI_YEAR = 1390

    internal data class CachedSeries(
        val fetchedAtMs: Long,
        val fullFrom1390Synced: Boolean,
        val points: List<TgjuHistoryPoint>
    )

    private val memoryCache = ConcurrentHashMap<String, CachedSeries>()
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val NUMBER_REGEX = Regex("""\d[\d,]*(?:\.\d+)?""")
    private val DATE_SLASH_OR_DASH_REGEX = Regex("""(\d{4})[/-](\d{1,2})[/-](\d{1,2})""")
    private val HIGHCHART_PAIR_REGEX = Regex("""\[\s*(\d{10,13})\s*,\s*(\d+(?:\.\d+)?)\s*]""")

    private const val CACHE_DIR_NAME_V4 = "tgju_history_cache_v4"
    private const val LEGACY_CACHE_DIR_NAME_V3 = "tgju_history_cache_v3"
    private const val LEGACY_CACHE_DIR_NAME_V2 = "tgju_history_cache_v2"
    private const val CACHE_HEADER_PREFIX_V4 = "TGJU_CACHE_V4"
    private const val CACHE_HEADER_PREFIX_V3 = "TGJU_CACHE_V3"
    private const val CACHE_HEADER_PREFIX_V2 = "TGJU_CACHE_V2"
    private const val CACHE_TTL_MS = 15 * 60_000L
    private const val ONE_DAY_MS = 24L * 60L * 60L * 1000L

    private val START_1390_EPOCH_MS: Long by lazy {
        PersianDateHelper.jalaliToTimestamp(MIN_SHAMSI_YEAR, 1, 1)
    }

    /**
     * Immediately peeks persistent device cache (or memory cache) without making any network call.
     * Lets the chart UI render the full cached history instantaneously, including offline.
     */
    suspend fun peekCachedHistory(
        context: Context,
        asset: TgjuChartAsset,
        liveLatestPrice: Double? = null
    ): TgjuHistoryLoadResult? = withContext(Dispatchers.IO) {
        val cached = loadExistingCache(context, asset) ?: return@withContext null
        if (cached.points.isEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        TgjuHistoryLoadResult(
            points = appendLivePointIfNewer(cached.points, liveLatestPrice, now),
            source = TgjuHistorySource.DISK_CACHE_FRESH,
            lastSyncEpochMs = cached.fetchedAtMs,
            newlyAddedDaysCount = 0,
            isFullFrom1390Cached = cached.fullFrom1390Synced
        )
    }

    /**
     * Loads historical prices since 1390/01/01 with persistent offline disk caching and incremental-only updates.
     *
     * - If the full since-1390 archive is already cached on disk:
     *   - When `!forceRefresh` and cache is fresh (< TTL): returns disk cache with zero network traffic.
     *   - When `forceRefresh` or cache expired: fetches ONLY the missing recent days (incremental delta),
     *     merges into the cached archive, and saves back to disk.
     *   - If offline / network fails: returns the full cached archive gracefully (`DISK_CACHE_OFFLINE`).
     * - If no since-1390 archive is cached yet (or upgrading from an older 10-year/5-year cache):
     *   - Fetches the full archive back to 1390/01/01 (using pagination if needed), merges with any
     *     existing cache, persists atomically to disk, and returns it.
     */
    suspend fun fetchHistoryFrom1390(
        context: Context,
        asset: TgjuChartAsset,
        liveLatestPrice: Double? = null,
        forceRefresh: Boolean = false
    ): Result<TgjuHistoryLoadResult> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = loadExistingCache(context, asset)

        // Case 1: Full archive since 1390 is already persisted in device cache
        if (cached != null && cached.fullFrom1390Synced && cached.points.size >= 5) {
            if (!forceRefresh && (now - cached.fetchedAtMs) < CACHE_TTL_MS) {
                return@withContext Result.success(
                    TgjuHistoryLoadResult(
                        points = appendLivePointIfNewer(cached.points, liveLatestPrice, now),
                        source = TgjuHistorySource.DISK_CACHE_FRESH,
                        lastSyncEpochMs = cached.fetchedAtMs,
                        newlyAddedDaysCount = 0,
                        isFullFrom1390Cached = true
                    )
                )
            }

            // Incremental-only update: calculate how many days elapsed since the latest cached date
            val latestCachedMs = cached.points.last().epochMs
            val daysSinceLast = ((now - latestCachedMs) / ONE_DAY_MS).toInt().coerceAtLeast(0)
            // Fetch only a tiny delta window (+7 days overlap to refresh recent OHLC corrections)
            val deltaLength = (daysSinceLast + 7).coerceIn(10, 400)

            val deltaPoints = try {
                withTimeoutOrNull(14_000L) {
                    fetchIncrementalDeltaPoints(asset, deltaLength, now)
                }.orEmpty()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                emptyList()
            }
            if (deltaPoints.isNotEmpty()) {
                val (merged, addedCount) = mergeHistoryPoints(cached.points, deltaPoints)
                val updatedCache = CachedSeries(
                    fetchedAtMs = now,
                    fullFrom1390Synced = true,
                    points = merged
                )
                saveCache(context, asset, updatedCache)
                return@withContext Result.success(
                    TgjuHistoryLoadResult(
                        points = appendLivePointIfNewer(merged, liveLatestPrice, now),
                        source = TgjuHistorySource.INCREMENTAL_UPDATE,
                        lastSyncEpochMs = now,
                        newlyAddedDaysCount = addedCount,
                        isFullFrom1390Cached = true
                    )
                )
            }

            // Offline / network unavailable -> seamlessly serve the full since-1390 device cache!
            return@withContext Result.success(
                TgjuHistoryLoadResult(
                    points = appendLivePointIfNewer(cached.points, liveLatestPrice, now),
                    source = TgjuHistorySource.DISK_CACHE_OFFLINE,
                    lastSyncEpochMs = cached.fetchedAtMs,
                    newlyAddedDaysCount = 0,
                    isFullFrom1390Cached = true
                )
            )
        }

        // Case 2: First-time full download back to 1390/01/01 (or upgrading legacy 10Y/5Y cache)
        val fetchedPoints = try {
            withTimeoutOrNull(28_000L) {
                fetchFullHistoryFrom1390FromNetwork(asset, now)
            }.orEmpty()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            emptyList()
        }

        if (fetchedPoints.isEmpty()) {
            // Fall back to any partial/legacy disk cache if offline
            if (cached != null && cached.points.isNotEmpty()) {
                return@withContext Result.success(
                    TgjuHistoryLoadResult(
                        points = appendLivePointIfNewer(cached.points, liveLatestPrice, now),
                        source = TgjuHistorySource.DISK_CACHE_OFFLINE,
                        lastSyncEpochMs = cached.fetchedAtMs,
                        newlyAddedDaysCount = 0,
                        isFullFrom1390Cached = cached.fullFrom1390Synced
                    )
                )
            }
            return@withContext Result.failure(
                IllegalStateException("اتصال اینترنت برقرار نیست و داده کش‌شده‌ای برای این نمودار روی گوشی موجود نیست.")
            )
        }

        val (merged, addedCount) = mergeHistoryPoints(cached?.points.orEmpty(), fetchedPoints)
        val savedCache = CachedSeries(
            fetchedAtMs = now,
            fullFrom1390Synced = true,
            points = merged
        )
        saveCache(context, asset, savedCache)

        Result.success(
            TgjuHistoryLoadResult(
                points = appendLivePointIfNewer(merged, liveLatestPrice, now),
                source = TgjuHistorySource.FULL_NETWORK_FETCH,
                lastSyncEpochMs = now,
                newlyAddedDaysCount = addedCount,
                isFullFrom1390Cached = true
            )
        )
    }

    /**
     * Fetches ONLY the latest `deltaLength` daily records (e.g. 10..30 rows) without re-downloading
     * past historical records from tgju.org.
     */
    private fun fetchIncrementalDeltaPoints(
        asset: TgjuChartAsset,
        deltaLength: Int,
        nowMs: Long
    ): List<TgjuHistoryPoint> {
        val symbolsToTry = listOf(asset.symbol) + asset.fallbackSymbols
        for (sym in symbolsToTry) {
            try {
                val url = "https://api.tgju.org/v1/market/indicator/summary-table-data/$sym?length=$deltaLength&start=0&draw=1&_t=$nowMs"
                val jsonText = downloadText(url, referer = "https://www.tgju.org/profile/$sym")
                if (!jsonText.isNullOrBlank()) {
                    val parsed = parseSummaryTableJson(jsonText, asset)
                    if (parsed.isNotEmpty()) return parsed
                }
            } catch (_: Exception) {
            }
        }
        return emptyList()
    }

    /**
     * One-time deep historical extraction from TGJU all the way back to Shamsi year 1390 (1390/01/01).
     * Requests up to 6,000 rows on page 1 and automatically paginates if TGJU caps per-page rows
     * until reaching 1390/01/01 or exhausting the symbol's available archive.
     */
    private fun fetchFullHistoryFrom1390FromNetwork(
        asset: TgjuChartAsset,
        nowMs: Long
    ): List<TgjuHistoryPoint> {
        val symbolsToTry = listOf(asset.symbol) + asset.fallbackSymbols
        val targetOldestMs = START_1390_EPOCH_MS
        var bestPoints: List<TgjuHistoryPoint> = emptyList()

        // 1. Primary official TGJU historical archive endpoint (supports start/length pagination)
        for (sym in symbolsToTry) {
            val collectedByDate = LinkedHashMap<String, TgjuHistoryPoint>()
            var startOffset = 0
            var page = 1
            val maxPages = 14

            while (page <= maxPages) {
                val reqLength = if (page == 1) 6000 else 2000
                val rawJson = try {
                    val url = "https://api.tgju.org/v1/market/indicator/summary-table-data/$sym?length=$reqLength&start=$startOffset&draw=$page&_t=$nowMs"
                    downloadText(url, referer = "https://www.tgju.org/profile/$sym")
                } catch (_: Exception) {
                    null
                }
                if (rawJson.isNullOrBlank()) break

                val pageRows = parseSummaryTableJson(rawJson, asset)
                if (pageRows.isEmpty()) break

                var newlyAddedThisPage = 0
                for (pt in pageRows) {
                    if (!collectedByDate.containsKey(pt.shamsiDate)) {
                        collectedByDate[pt.shamsiDate] = pt
                        newlyAddedThisPage++
                    }
                }

                val oldestSoFar = collectedByDate.values.minOfOrNull { it.epochMs } ?: nowMs
                // Stop once we have reached 1390/01/01, no new rows arrived, or server returned few rows
                if (oldestSoFar <= targetOldestMs || newlyAddedThisPage == 0 || pageRows.size < 100) {
                    break
                }
                if (page == 1 && pageRows.size < 450) {
                    break
                }

                startOffset += pageRows.size
                page++
            }

            val sortedCandidate = collectedByDate.values.sortedBy { it.epochMs }
            if (sortedCandidate.size > bestPoints.size) {
                bestPoints = sortedCandidate
            }
            if (bestPoints.size >= 30) break
        }

        // 2. Fallback: Try TGJU chart-summary-ajax or profile page embedded Highcharts data
        if (bestPoints.size < 10) {
            for (sym in symbolsToTry) {
                try {
                    val chartAjaxUrl = "https://www.tgju.org/chart-summary-ajax/$sym?_t=$nowMs"
                    val text = downloadText(chartAjaxUrl, referer = "https://www.tgju.org/profile/$sym")
                    if (!text.isNullOrBlank()) {
                        val parsed = parseChartSeriesFallback(text, asset)
                        if (parsed.size > bestPoints.size) {
                            bestPoints = parsed
                        }
                    }
                } catch (_: Exception) {
                }
                if (bestPoints.size >= 10) break

                try {
                    val profileHtml = downloadText("https://www.tgju.org/profile/$sym?_t=$nowMs", referer = "https://www.tgju.org/")
                    if (!profileHtml.isNullOrBlank()) {
                        val parsed = parseChartSeriesFallback(profileHtml, asset)
                        if (parsed.size > bestPoints.size) {
                            bestPoints = parsed
                        }
                    }
                } catch (_: Exception) {
                }
                if (bestPoints.size >= 10) break
            }
        }

        return bestPoints
    }

    /**
     * Merges incoming recent points into existing cached points by `shamsiDate`, preserves all past
     * history from Shamsi year 1390 onwards without duplication, and reports how many brand-new
     * dates were added.
     */
    fun mergeHistoryPoints(
        existing: List<TgjuHistoryPoint>,
        incoming: List<TgjuHistoryPoint>
    ): Pair<List<TgjuHistoryPoint>, Int> {
        val byDate = LinkedHashMap<String, TgjuHistoryPoint>(existing.size + incoming.size)
        for (pt in existing) {
            byDate[pt.shamsiDate] = pt
        }
        var newlyAdded = 0
        for (pt in incoming) {
            if (!byDate.containsKey(pt.shamsiDate)) {
                newlyAdded++
            }
            byDate[pt.shamsiDate] = pt
        }
        val sorted = byDate.values.sortedBy { it.epochMs }
        val from1390 = sorted.filter { it.shamsiYear >= MIN_SHAMSI_YEAR }
        val finalSeries = if (from1390.size >= 5) from1390 else sorted
        return finalSeries to newlyAdded
    }

    /**
     * Searches the loaded archive (since 1390) for an exact Shamsi date (Year / Month / Day).
     * If the market was closed on that date (e.g. Friday/holiday), returns the closest recorded
     * trading day with `isExactMatch = false`, and flags `isOutOfRange` if outside available history.
     */
    fun findPointByShamsiDate(
        allPoints: List<TgjuHistoryPoint>,
        year: Int,
        month: Int,
        day: Int
    ): TgjuDateSearchResult? {
        if (allPoints.isEmpty()) return null
        val safeMonth = month.coerceIn(1, 12)
        val maxDay = PersianDateHelper.getDaysInMonth(year, safeMonth)
        val safeDay = day.coerceIn(1, maxDay)
        val requestedStr = String.format(Locale.US, "%04d/%02d/%02d", year, safeMonth, safeDay)

        // 1. Exact Shamsi date match
        val exactIdx = allPoints.indexOfFirst {
            it.shamsiYear == year && it.shamsiMonth == safeMonth && it.shamsiDay == safeDay
        }
        if (exactIdx >= 0) {
            return TgjuDateSearchResult(
                point = allPoints[exactIdx],
                indexInAllPoints = exactIdx,
                isExactMatch = true,
                isOutOfRange = false,
                requestedShamsiDate = requestedStr
            )
        }

        // 2. Nearest recorded date by noon timestamp
        val (gy, gm, gd) = PersianDateHelper.jalaliToGregorian(year, safeMonth, safeDay)
        val cal = Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val targetMs = cal.timeInMillis
        val oldestMs = allPoints.first().epochMs
        val newestMs = allPoints.last().epochMs
        val isOutOfRange = targetMs < (oldestMs - ONE_DAY_MS) || targetMs > (newestMs + ONE_DAY_MS)

        var bestIdx = 0
        var bestDiff = Long.MAX_VALUE
        for (i in allPoints.indices) {
            val diff = abs(allPoints[i].epochMs - targetMs)
            if (diff < bestDiff) {
                bestDiff = diff
                bestIdx = i
            }
        }

        return TgjuDateSearchResult(
            point = allPoints[bestIdx],
            indexInAllPoints = bestIdx,
            isExactMatch = false,
            isOutOfRange = isOutOfRange,
            requestedShamsiDate = requestedStr
        )
    }

    /**
     * Picks the tightest `TgjuChartRange` that contains the given target point so the chart displays
     * it cleanly with optimal zoom context.
     */
    fun bestRangeForPoint(allPoints: List<TgjuHistoryPoint>, targetPoint: TgjuHistoryPoint): TgjuChartRange {
        if (allPoints.isEmpty()) return TgjuChartRange.FROM_1390
        val latestMs = allPoints.last().epochMs
        val ageDays = ((latestMs - targetPoint.epochMs) / ONE_DAY_MS).coerceAtLeast(0L)
        return when {
            ageDays <= TgjuChartRange.WEEK_1.daysBack -> TgjuChartRange.WEEK_1
            ageDays <= TgjuChartRange.MONTH_1.daysBack -> TgjuChartRange.MONTH_1
            ageDays <= TgjuChartRange.MONTH_6.daysBack -> TgjuChartRange.MONTH_6
            ageDays <= TgjuChartRange.YEAR_1.daysBack -> TgjuChartRange.YEAR_1
            else -> TgjuChartRange.FROM_1390
        }
    }

    // ---- Persistent Device Cache (Atomic File Storage in filesDir) ----

    private fun getCacheFile(context: Context, asset: TgjuChartAsset): File {
        val dir = File(context.applicationContext.filesDir, CACHE_DIR_NAME_V4)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${asset.symbol}.dat")
    }

    private fun getLegacyCacheFile(context: Context, dirName: String, asset: TgjuChartAsset): File {
        val dir = File(context.applicationContext.filesDir, dirName)
        return File(dir, "${asset.symbol}.dat")
    }

    private fun loadExistingCache(context: Context, asset: TgjuChartAsset): CachedSeries? {
        memoryCache[asset.symbol]?.let { return it }
        val v4File = getCacheFile(context, asset)
        if (v4File.exists()) {
            try {
                val parsed = deserializeCache(v4File.readText(Charsets.UTF_8))
                if (parsed != null) {
                    memoryCache[asset.symbol] = parsed
                    return parsed
                }
            } catch (_: Exception) {
            }
        }
        // Gracefully read legacy v3 (10Y) or v2 (5Y) cache (marked fullFrom1390Synced = false so offline works immediately & backfills back to 1390 once online)
        for (legacyDir in listOf(LEGACY_CACHE_DIR_NAME_V3, LEGACY_CACHE_DIR_NAME_V2)) {
            val legacyFile = getLegacyCacheFile(context, legacyDir, asset)
            if (legacyFile.exists()) {
                try {
                    val parsedLegacy = deserializeCache(legacyFile.readText(Charsets.UTF_8))
                    if (parsedLegacy != null) {
                        return parsedLegacy.copy(fullFrom1390Synced = false)
                    }
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private fun saveCache(context: Context, asset: TgjuChartAsset, series: CachedSeries) {
        memoryCache[asset.symbol] = series
        try {
            val file = getCacheFile(context, asset)
            val tmpFile = File(file.parentFile, "${asset.symbol}.dat.tmp")
            tmpFile.writeText(serializeCache(series.fetchedAtMs, series.fullFrom1390Synced, series.points), Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmpFile.renameTo(file)) {
                file.writeText(tmpFile.readText(Charsets.UTF_8), Charsets.UTF_8)
                tmpFile.delete()
            }
        } catch (_: Exception) {
        }
    }

    internal fun serializeCache(
        fetchedAtMs: Long,
        fullFrom1390Synced: Boolean,
        points: List<TgjuHistoryPoint>
    ): String {
        val sb = StringBuilder(points.size * 72 + 48)
        sb.append(CACHE_HEADER_PREFIX_V4)
            .append('|')
            .append(fetchedAtMs)
            .append('|')
            .append(if (fullFrom1390Synced) "1" else "0")
            .append('\n')
        for (p in points) {
            sb.append(p.epochMs).append('|')
                .append(p.price).append('|')
                .append(p.high).append('|')
                .append(p.low).append('|')
                .append(p.shamsiDate).append('|')
                .append(p.shamsiYear).append('|')
                .append(p.shamsiMonth).append('|')
                .append(p.shamsiDay).append('|')
                .append(p.weekdayFa.replace('|', ' ')).append('|')
                .append(p.shortWeekdayFa.replace('|', ' ')).append('|')
                .append(p.monthDayFa.replace('|', ' '))
                .append('\n')
        }
        return sb.toString()
    }

    internal fun deserializeCache(raw: String): CachedSeries? {
        if (raw.isBlank()) return null
        val lines = raw.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val header = lines.next().split('|')
        if (header.size < 3) return null
        val isV4 = header[0] == CACHE_HEADER_PREFIX_V4
        val isLegacy = header[0] == CACHE_HEADER_PREFIX_V3 || header[0] == CACHE_HEADER_PREFIX_V2
        if (!isV4 && !isLegacy) return null
        val fetchedAtMs = header[1].toLongOrNull() ?: return null
        val fullFrom1390Synced = isV4 && header[2] == "1"

        val points = ArrayList<TgjuHistoryPoint>(1536)
        while (lines.hasNext()) {
            val line = lines.next()
            if (line.isBlank()) continue
            val cols = line.split('|')
            if (cols.size < 11) continue
            val epochMs = cols[0].toLongOrNull() ?: continue
            val price = cols[1].toDoubleOrNull() ?: continue
            val high = cols[2].toDoubleOrNull() ?: price
            val low = cols[3].toDoubleOrNull() ?: price
            val shamsiDate = cols[4]
            val sYear = cols[5].toIntOrNull() ?: continue
            val sMonth = cols[6].toIntOrNull() ?: continue
            val sDay = cols[7].toIntOrNull() ?: continue
            points.add(
                TgjuHistoryPoint(
                    epochMs = epochMs,
                    price = price,
                    high = high,
                    low = low,
                    shamsiDate = shamsiDate,
                    shamsiYear = sYear,
                    shamsiMonth = sMonth,
                    shamsiDay = sDay,
                    weekdayFa = cols[8],
                    shortWeekdayFa = cols[9],
                    monthDayFa = cols[10]
                )
            )
        }
        if (points.isEmpty()) return null
        return CachedSeries(
            fetchedAtMs = fetchedAtMs,
            fullFrom1390Synced = fullFrom1390Synced,
            points = points.sortedBy { it.epochMs }
        )
    }

    /**
     * Parses TGJU's `api.tgju.org/v1/market/indicator/summary-table-data/{symbol}` JSON response.
     * Each row in `data` typically has: [open, low, high, close, changeHtml, pctHtml, gregorianDate, jalaliDate]
     * (or jalaliDate at index 6 and gregorianDate at index 7). Both orderings are handled automatically.
     */
    fun parseSummaryTableJson(jsonText: String, asset: TgjuChartAsset): List<TgjuHistoryPoint> {
        val rows = extractDataArrayRows(jsonText)
        if (rows.isEmpty()) return emptyList()
        val points = ArrayList<TgjuHistoryPoint>(rows.size)

        for (row in rows) {
            val point = parseSummaryRow(row, asset) ?: continue
            points.add(point)
        }

        return points
            .distinctBy { it.shamsiDate }
            .sortedBy { it.epochMs }
    }

    /**
     * Lightweight, zero-dependency, quote-aware extractor for TGJU's `"data": [[...], [...]]` JSON rows.
     */
    private fun extractDataArrayRows(jsonText: String): List<List<String>> {
        val dataKeyIdx = jsonText.indexOf("\"data\"")
        if (dataKeyIdx < 0) return emptyList()
        val outerOpen = jsonText.indexOf('[', dataKeyIdx + 6)
        if (outerOpen < 0) return emptyList()

        val rows = mutableListOf<List<String>>()
        var i = outerOpen + 1
        val n = jsonText.length
        var inString = false
        var escape = false
        var depth = 1
        var currentRow: MutableList<String>? = null
        val token = StringBuilder()
        var tokenStarted = false

        fun flushCell() {
            val row = currentRow ?: return
            if (tokenStarted) {
                row.add(token.toString().trim())
                token.clear()
                tokenStarted = false
            }
        }

        while (i < n && depth > 0) {
            val ch = jsonText[i]
            if (inString) {
                if (escape) {
                    when (ch) {
                        'n' -> token.append('\n')
                        't' -> token.append('\t')
                        'r' -> token.append('\r')
                        else -> token.append(ch)
                    }
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                } else {
                    token.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        inString = true
                        if (depth == 2) tokenStarted = true
                    }
                    '[' -> {
                        depth++
                        if (depth == 2) {
                            currentRow = mutableListOf()
                            token.clear()
                            tokenStarted = false
                        }
                    }
                    ']' -> {
                        if (depth == 2) {
                            flushCell()
                            currentRow?.let { if (it.isNotEmpty()) rows.add(it) }
                            currentRow = null
                        }
                        depth--
                    }
                    ',' -> {
                        if (depth == 2) {
                            flushCell()
                        }
                    }
                    else -> {
                        if (depth == 2 && !ch.isWhitespace()) {
                            token.append(ch)
                            tokenStarted = true
                        }
                    }
                }
            }
            i++
        }
        return rows
    }

    private fun parseSummaryRow(row: List<String>, asset: TgjuChartAsset): TgjuHistoryPoint? {
        if (row.size < 6) return null

        val openVal = parsePriceNumber(row.getOrNull(0).orEmpty())
        val lowVal = parsePriceNumber(row.getOrNull(1).orEmpty())
        val highVal = parsePriceNumber(row.getOrNull(2).orEmpty())
        val closeVal = parsePriceNumber(row.getOrNull(3).orEmpty()) ?: openVal ?: return null
        if (closeVal <= 0.0) return null

        val divisor = if (asset.isRialToToman) 10.0 else 1.0
        val price = closeVal / divisor
        val high = (highVal ?: closeVal) / divisor
        val low = (lowVal ?: closeVal) / divisor

        var gregTriple: Triple<Int, Int, Int>? = null
        var jalaliTriple: Triple<Int, Int, Int>? = null

        for (colIdx in 4 until row.size) {
            val cell = PersianDateHelper.toEnglishDigits(
                HTML_TAG_REGEX.replace(row[colIdx], " ").trim()
            )
            val m = DATE_SLASH_OR_DASH_REGEX.find(cell) ?: continue
            val y = m.groupValues[1].toIntOrNull() ?: continue
            val mo = m.groupValues[2].toIntOrNull() ?: continue
            val d = m.groupValues[3].toIntOrNull() ?: continue
            if (mo !in 1..12 || d !in 1..31) continue
            when {
                y in 1990..2100 -> gregTriple = Triple(y, mo, d)
                y in 1350..1500 -> jalaliTriple = Triple(y, mo, d)
            }
        }

        val (gy, gm, gd) = gregTriple ?: jalaliTriple?.let { (jy, jm, jd) ->
            PersianDateHelper.jalaliToGregorian(jy, jm, jd)
        } ?: return null

        val shamsi = PersianDateHelper.gregorianToJalali(gy, gm, gd)
        val cal = Calendar.getInstance().apply {
            set(gy, gm - 1, gd, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val shortWeek = weekdayShortFromCalendar(cal.get(Calendar.DAY_OF_WEEK))
        val monthDay = "${shamsi.day} ${shamsi.monthName}"

        return TgjuHistoryPoint(
            epochMs = cal.timeInMillis,
            price = price,
            high = maxOf(high, price),
            low = minOf(low, price),
            shamsiDate = shamsi.format(),
            shamsiYear = shamsi.year,
            shamsiMonth = shamsi.month,
            shamsiDay = shamsi.day,
            weekdayFa = shamsi.dayOfWeek,
            shortWeekdayFa = shortWeek,
            monthDayFa = monthDay
        )
    }

    /**
     * Fallback parser for embedded Highcharts `[timestamp, price]` pairs in TGJU profile HTML / AJAX responses.
     */
    fun parseChartSeriesFallback(rawText: String, asset: TgjuChartAsset): List<TgjuHistoryPoint> {
        val divisor = if (asset.isRialToToman) 10.0 else 1.0
        val points = mutableListOf<TgjuHistoryPoint>()
        for (match in HIGHCHART_PAIR_REGEX.findAll(rawText)) {
            val rawTs = match.groupValues[1].toLongOrNull() ?: continue
            val rawVal = match.groupValues[2].toDoubleOrNull() ?: continue
            if (rawVal <= 0.0) continue
            val epochMs = if (rawTs < 10_000_000_000L) rawTs * 1000L else rawTs
            // Accept timestamps starting from ~2010/11 (covers Shamsi 1390 = March 2011)
            if (epochMs < 1_290_000_000_000L || epochMs > 2_200_000_000_000L) continue

            val price = rawVal / divisor
            if (price < asset.minPlausiblePrice || price > asset.maxPlausiblePrice) continue

            val shamsi = PersianDateHelper.getShamsiDate(epochMs)
            if (shamsi.year < MIN_SHAMSI_YEAR) continue
            val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
            val shortWeek = weekdayShortFromCalendar(cal.get(Calendar.DAY_OF_WEEK))

            points.add(
                TgjuHistoryPoint(
                    epochMs = epochMs,
                    price = price,
                    high = price,
                    low = price,
                    shamsiDate = shamsi.format(),
                    shamsiYear = shamsi.year,
                    shamsiMonth = shamsi.month,
                    shamsiDay = shamsi.day,
                    weekdayFa = shamsi.dayOfWeek,
                    shortWeekdayFa = shortWeek,
                    monthDayFa = "${shamsi.day} ${shamsi.monthName}"
                )
            )
        }
        return points.distinctBy { it.shamsiDate }.sortedBy { it.epochMs }
    }

    fun filterPointsForRange(allPoints: List<TgjuHistoryPoint>, range: TgjuChartRange): List<TgjuHistoryPoint> {
        if (allPoints.isEmpty()) return emptyList()
        if (range == TgjuChartRange.FROM_1390) {
            val from1390 = allPoints.filter { it.shamsiYear >= MIN_SHAMSI_YEAR }
            return if (from1390.size >= 2) from1390 else allPoints
        }
        val latestEpoch = allPoints.last().epochMs
        val cutoff = latestEpoch - (range.daysBack.toLong() * ONE_DAY_MS)
        val filtered = allPoints.filter { it.epochMs >= cutoff }
        return when {
            filtered.size >= 2 -> filtered
            allPoints.size >= 2 -> allPoints.takeLast(minOf(allPoints.size, range.daysBack.coerceAtLeast(2)))
            else -> allPoints
        }
    }

    private fun appendLivePointIfNewer(
        series: List<TgjuHistoryPoint>,
        liveLatestPrice: Double?,
        nowMs: Long
    ): List<TgjuHistoryPoint> {
        if (liveLatestPrice == null || liveLatestPrice <= 0.0 || series.isEmpty()) return series
        val todayShamsi = PersianDateHelper.getShamsiDate(nowMs)
        val todayStr = todayShamsi.format()
        val last = series.last()
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val shortWeek = weekdayShortFromCalendar(cal.get(Calendar.DAY_OF_WEEK))

        // Guard against wildly mismatched units
        if (last.price > 0.0 && (liveLatestPrice > last.price * 3.0 || liveLatestPrice < last.price * 0.3)) {
            return series
        }

        val todayPoint = TgjuHistoryPoint(
            epochMs = nowMs,
            price = liveLatestPrice,
            high = maxOf(liveLatestPrice, if (last.shamsiDate == todayStr) last.high else liveLatestPrice),
            low = minOf(liveLatestPrice, if (last.shamsiDate == todayStr) last.low else liveLatestPrice),
            shamsiDate = todayStr,
            shamsiYear = todayShamsi.year,
            shamsiMonth = todayShamsi.month,
            shamsiDay = todayShamsi.day,
            weekdayFa = todayShamsi.dayOfWeek,
            shortWeekdayFa = shortWeek,
            monthDayFa = "${todayShamsi.day} ${todayShamsi.monthName}"
        )

        return if (last.shamsiDate == todayStr) {
            series.dropLast(1) + todayPoint
        } else if (nowMs > last.epochMs) {
            series + todayPoint
        } else {
            series
        }
    }

    private fun parsePriceNumber(raw: String): Double? {
        if (raw.isBlank()) return null
        val cleaned = PersianDateHelper.toEnglishDigits(HTML_TAG_REGEX.replace(raw, " "))
            .replace("٬", "")
            .replace(",", "")
            .trim()
        return NUMBER_REGEX.find(cleaned)?.value?.replace(",", "")?.toDoubleOrNull()
    }

    private fun weekdayShortFromCalendar(dayOfWeek: Int): String = when (dayOfWeek) {
        Calendar.SATURDAY -> "شنبه"
        Calendar.SUNDAY -> "۱شنبه"
        Calendar.MONDAY -> "۲شنبه"
        Calendar.TUESDAY -> "۳شنبه"
        Calendar.WEDNESDAY -> "۴شنبه"
        Calendar.THURSDAY -> "۵شنبه"
        Calendar.FRIDAY -> "جمعه"
        else -> ""
    }

    private fun downloadText(urlString: String, referer: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 25000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
            setRequestProperty("Referer", referer)
            setRequestProperty("Origin", "https://www.tgju.org")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
