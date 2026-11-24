package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.network.TgjuChartAsset
import com.example.data.network.TgjuChartRange
import com.example.data.network.TgjuDateSearchResult
import com.example.data.network.TgjuFifteenYearExtremes
import com.example.data.network.TgjuHistoryPoint
import com.example.data.network.TgjuHistoryService
import com.example.data.network.TgjuHistorySource
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.utils.PersianDateHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Native interactive 10-year TGJU historical price line chart dialog for all market assets
 * (Dollar, Euro, 18k Gold, Sekeh Emami, Rob Sekeh, Gold Ounce, Bitcoin, Tether, Bourse, Brent Oil).
 *
 * Features:
 * - Persistent on-device 10-year offline cache (`filesDir`) & incremental-only updates
 * - Shamsi historical date search (Year / Month / Day) right at the top that immediately snaps
 *   the vertical crosshair line to the searched date and displays its price right atop the line
 * - Selectable timeframes: 1 Week, 1 Month, 6 Months, 1 Year, 10 Years
 * - Interactive Zoom In / Zoom Out & Day-by-Day Left/Right arrow steppers
 * - Tap or drag ANYWHERE along the chart curve to inspect exact Price, Weekday, and Shamsi date
 */
@Composable
fun TgjuHistoricalChartDialog(
    asset: TgjuChartAsset,
    liveLatestPrice: Double?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accentColor = Color(asset.accentHex)
    val todayShamsi = remember { PersianDateHelper.getShamsiDate() }

    var selectedRange by remember(asset) { mutableStateOf(TgjuChartRange.MONTH_1) }
    var allPoints by remember(asset) { mutableStateOf<List<TgjuHistoryPoint>>(emptyList()) }
    var dataSource by remember(asset) { mutableStateOf<TgjuHistorySource?>(null) }
    var newlyAddedDays by remember(asset) { mutableIntStateOf(0) }
    var isLoading by remember(asset) { mutableStateOf(true) }
    var isSyncingInBackground by remember(asset) { mutableStateOf(false) }
    var errorMsg by remember(asset) { mutableStateOf<String?>(null) }
    var refreshTrigger by remember(asset) { mutableIntStateOf(0) }

    // Historical Shamsi date search state (Year / Month / Day)
    var searchYear by remember(asset) { mutableIntStateOf(todayShamsi.year) }
    var searchMonth by remember(asset) { mutableIntStateOf(todayShamsi.month) }
    var searchDay by remember(asset) { mutableIntStateOf(todayShamsi.day) }
    var searchResultBanner by remember(asset) { mutableStateOf<TgjuDateSearchResult?>(null) }

    // Zoom window & active selection — keyed ONLY on `asset` so date-search range switches NEVER reset selection!
    var visibleStartIdx by remember(asset) { mutableIntStateOf(0) }
    var visibleEndIdx by remember(asset) { mutableIntStateOf(-1) }
    var selectedPointIdx by remember(asset) { mutableStateOf<Int?>(null) }

    BackHandler { onDismiss() }

    // Cache-first load + incremental-only network sync (offline-ready!)
    LaunchedEffect(asset, refreshTrigger) {
        val isManualRefresh = refreshTrigger > 0
        errorMsg = null

        // On initial open, peek persistent device cache immediately for instant offline display
        if (!isManualRefresh && allPoints.isEmpty()) {
            val cachedPeek = TgjuHistoryService.peekCachedHistory(
                context = context,
                asset = asset,
                liveLatestPrice = liveLatestPrice
            )
            if (cachedPeek != null && cachedPeek.points.isNotEmpty()) {
                val initRangePts = TgjuHistoryService.filterPointsForRange(cachedPeek.points, selectedRange)
                allPoints = cachedPeek.points
                visibleStartIdx = 0
                visibleEndIdx = (initRangePts.size - 1).coerceAtLeast(0)
                selectedPointIdx = null
                dataSource = cachedPeek.source
                isLoading = false
                isSyncingInBackground = true
                cachedPeek.points.lastOrNull()?.let { latest ->
                    searchYear = latest.shamsiYear
                    searchMonth = latest.shamsiMonth
                    searchDay = latest.shamsiDay
                }
            } else {
                isLoading = true
            }
        } else if (allPoints.isNotEmpty()) {
            isSyncingInBackground = true
        } else {
            isLoading = true
        }

        val result = TgjuHistoryService.fetchHistoryFrom1390(
            context = context,
            asset = asset,
            liveLatestPrice = liveLatestPrice,
            forceRefresh = isManualRefresh
        )
        result.fold(
            onSuccess = { loadResult ->
                val hadPointsBefore = allPoints.isNotEmpty()
                val activeSearchedDate = searchResultBanner?.point?.shamsiDate
                allPoints = loadResult.points
                dataSource = loadResult.source
                newlyAddedDays = loadResult.newlyAddedDaysCount
                isLoading = false
                isSyncingInBackground = false
                errorMsg = null

                val updatedRangePts = TgjuHistoryService.filterPointsForRange(loadResult.points, selectedRange)
                if (!hadPointsBefore) {
                    visibleStartIdx = 0
                    visibleEndIdx = (updatedRangePts.size - 1).coerceAtLeast(0)
                    selectedPointIdx = null
                    loadResult.points.lastOrNull()?.let { latest ->
                        searchYear = latest.shamsiYear
                        searchMonth = latest.shamsiMonth
                        searchDay = latest.shamsiDay
                    }
                } else if (activeSearchedDate == null && selectedPointIdx == null) {
                    // Keep full view up to the newly appended latest day if user wasn't inspecting a searched/selected historical date
                    visibleStartIdx = 0
                    visibleEndIdx = (updatedRangePts.size - 1).coerceAtLeast(0)
                }
            },
            onFailure = { err ->
                isLoading = false
                isSyncingInBackground = false
                if (allPoints.isEmpty()) {
                    errorMsg = err.message ?: "خطا در دریافت داده‌های نمودار از tgju.org"
                }
            }
        )
    }

    val availableYears = remember(allPoints, todayShamsi) {
        if (allPoints.isNotEmpty()) {
            val minY = allPoints.first().shamsiYear
            val maxY = allPoints.last().shamsiYear
            (maxY downTo minY).toList()
        } else {
            (todayShamsi.year downTo TgjuHistoryService.MIN_SHAMSI_YEAR).toList()
        }
    }

    val rangePoints = remember(allPoints, selectedRange) {
        TgjuHistoryService.filterPointsForRange(allPoints, selectedRange)
    }

    val rangeExtremes = remember(rangePoints) {
        TgjuHistoryService.findRangeExtremes(rangePoints)
    }

    val displayedPoints = remember(rangePoints, visibleStartIdx, visibleEndIdx) {
        if (rangePoints.isEmpty()) {
            emptyList()
        } else {
            val safeStart = visibleStartIdx.coerceIn(0, rangePoints.lastIndex)
            val safeEnd = (if (visibleEndIdx < 0) rangePoints.lastIndex else visibleEndIdx)
                .coerceIn(safeStart, rangePoints.lastIndex)
            rangePoints.subList(safeStart, safeEnd + 1)
        }
    }

    val activeIndex = selectedPointIdx?.coerceIn(0, (displayedPoints.size - 1).coerceAtLeast(0))
    val activePoint = if (displayedPoints.isNotEmpty()) {
        displayedPoints[activeIndex ?: displayedPoints.lastIndex]
    } else null

    val periodFirstPrice = displayedPoints.firstOrNull()?.price ?: 0.0
    val activePrice = activePoint?.price ?: 0.0
    val periodDiff = activePrice - periodFirstPrice
    val periodPct = if (periodFirstPrice > 0.0) (periodDiff / periodFirstPrice) * 100.0 else 0.0
    val isPositiveTrend = periodDiff >= 0.0
    val trendColor = if (isPositiveTrend) Color(0xFF059669) else Color(0xFFDC2626)

    // Detect if the visible window is short enough (~14 days or less) to show Persian weekdays
    val spanDays = remember(displayedPoints) {
        if (displayedPoints.size < 2) 0L
        else ((displayedPoints.last().epochMs - displayedPoints.first().epochMs) / (24L * 60L * 60L * 1000L)).coerceAtLeast(1L)
    }
    val isWeeklyGranularity = selectedRange == TgjuChartRange.WEEK_1 || spanDays <= 14L

    fun selectTimeRange(newRange: TgjuChartRange) {
        val newRangePts = TgjuHistoryService.filterPointsForRange(allPoints, newRange)
        searchResultBanner = null
        selectedRange = newRange
        visibleStartIdx = 0
        visibleEndIdx = (newRangePts.size - 1).coerceAtLeast(0)
        selectedPointIdx = null
    }

    /**
     * Synchronously focuses the chart window and snaps the vertical crosshair line & price callout
     * directly onto the exact given historical point on the very same frame.
     */
    fun focusPointOnChart(
        targetPoint: TgjuHistoryPoint,
        banner: TgjuDateSearchResult? = null,
        syncDatePickers: Boolean = false
    ) {
        if (allPoints.isEmpty()) return

        var targetRange = TgjuHistoryService.bestRangeForPoint(allPoints, targetPoint)
        var targetRangePts = TgjuHistoryService.filterPointsForRange(allPoints, targetRange)
        var foundIdx = targetRangePts.indexOfFirst { it.shamsiDate == targetPoint.shamsiDate }
        if (foundIdx < 0 && targetRange != TgjuChartRange.FROM_1390) {
            targetRange = TgjuChartRange.FROM_1390
            targetRangePts = TgjuHistoryService.filterPointsForRange(allPoints, targetRange)
            foundIdx = targetRangePts.indexOfFirst { it.shamsiDate == targetPoint.shamsiDate }
        }
        val safeFoundIdx = if (foundIdx >= 0) foundIdx else targetRangePts.lastIndex.coerceAtLeast(0)

        val (startIdx, endIdx, localSelIdx) = if (targetRangePts.size > 40) {
            // Focus a crisp ±14-day window centered right around the target date
            val halfWindow = 14
            var start = (safeFoundIdx - halfWindow).coerceAtLeast(0)
            var end = (safeFoundIdx + halfWindow).coerceAtMost(targetRangePts.lastIndex)
            val desiredSize = halfWindow * 2 + 1
            if (end - start + 1 < desiredSize) {
                if (start == 0) {
                    end = (desiredSize - 1).coerceAtMost(targetRangePts.lastIndex)
                } else if (end == targetRangePts.lastIndex) {
                    start = (targetRangePts.lastIndex - desiredSize + 1).coerceAtLeast(0)
                }
            }
            Triple(start, end, (safeFoundIdx - start).coerceIn(0, (end - start).coerceAtLeast(0)))
        } else {
            Triple(0, targetRangePts.lastIndex.coerceAtLeast(0), safeFoundIdx)
        }

        if (syncDatePickers) {
            searchYear = targetPoint.shamsiYear
            searchMonth = targetPoint.shamsiMonth
            searchDay = targetPoint.shamsiDay
        }
        searchResultBanner = banner
        selectedRange = targetRange
        visibleStartIdx = startIdx
        visibleEndIdx = endIdx
        selectedPointIdx = localSelIdx
    }

    /**
     * Immediately searches the archive and SYNCHRONOUSLY snaps the chart window and
     * vertical crosshair line directly onto the found date on the very same frame!
     */
    fun executeHistoricalDateSearch() {
        if (allPoints.isEmpty()) return
        val result = TgjuHistoryService.findPointByShamsiDate(
            allPoints = allPoints,
            year = searchYear,
            month = searchMonth,
            day = searchDay
        ) ?: return
        focusPointOnChart(targetPoint = result.point, banner = result, syncDatePickers = false)
    }

    /**
     * Snaps the vertical crosshair directly onto the exact highest or lowest point inside the
     * currently selected timeframe WITHOUT switching away from `selectedRange`.
     */
    fun focusPointInCurrentRange(targetPoint: TgjuHistoryPoint) {
        if (rangePoints.isEmpty()) return
        val foundIdx = rangePoints.indexOfFirst { it.shamsiDate == targetPoint.shamsiDate }
        if (foundIdx < 0) {
            focusPointOnChart(targetPoint = targetPoint, banner = null, syncDatePickers = true)
            return
        }
        searchYear = targetPoint.shamsiYear
        searchMonth = targetPoint.shamsiMonth
        searchDay = targetPoint.shamsiDay
        searchResultBanner = null
        visibleStartIdx = 0
        visibleEndIdx = rangePoints.lastIndex
        selectedPointIdx = foundIdx
    }

    fun zoomInWindow() {
        if (displayedPoints.size <= 5) return
        val curCount = displayedPoints.size
        val targetCount = (curCount / 2).coerceAtLeast(5)
        val anchorInFull = visibleStartIdx + (activeIndex ?: displayedPoints.lastIndex)
        val half = targetCount / 2
        var newStart = (anchorInFull - half).coerceAtLeast(0)
        var newEnd = (newStart + targetCount - 1).coerceAtMost(rangePoints.lastIndex)
        if (newEnd - newStart + 1 < targetCount) {
            newStart = (newEnd - targetCount + 1).coerceAtLeast(0)
        }
        visibleStartIdx = newStart
        visibleEndIdx = newEnd
        selectedPointIdx = (anchorInFull - newStart).coerceIn(0, (newEnd - newStart).coerceAtLeast(0))
    }

    fun zoomOutWindow() {
        if (rangePoints.isEmpty()) return
        val curCount = displayedPoints.size
        if (curCount >= rangePoints.size) {
            val nextOrdinal = (selectedRange.ordinal + 1).coerceAtMost(TgjuChartRange.entries.lastIndex)
            selectTimeRange(TgjuChartRange.entries[nextOrdinal])
            return
        }
        val anchorInFull = visibleStartIdx + (activeIndex ?: displayedPoints.lastIndex)
        val targetCount = (curCount * 2).coerceAtMost(rangePoints.size)
        val center = anchorInFull
        var newStart = (center - targetCount / 2).coerceAtLeast(0)
        var newEnd = (newStart + targetCount - 1).coerceAtMost(rangePoints.lastIndex)
        if (newEnd - newStart + 1 < targetCount) {
            newStart = (newEnd - targetCount + 1).coerceAtLeast(0)
        }
        visibleStartIdx = newStart
        visibleEndIdx = newEnd
        selectedPointIdx = (anchorInFull - newStart).coerceIn(0, (newEnd - newStart).coerceAtLeast(0))
    }

    val safeStartIdx = visibleStartIdx.coerceIn(0, (rangePoints.size - 1).coerceAtLeast(0))
    val safeEndIdx = (if (visibleEndIdx < 0) rangePoints.lastIndex else visibleEndIdx)
        .coerceIn(safeStartIdx, (rangePoints.size - 1).coerceAtLeast(0))
    val currentGlobalIdx = if (rangePoints.isEmpty()) 0 else {
        (safeStartIdx + (activeIndex ?: displayedPoints.lastIndex)).coerceIn(0, rangePoints.lastIndex)
    }
    val canStepPrev = rangePoints.size >= 2 && currentGlobalIdx > 0
    val canStepNext = rangePoints.size >= 2 && currentGlobalIdx < rangePoints.lastIndex

    fun stepDay(delta: Int) {
        if (rangePoints.size < 2) return
        searchResultBanner = null
        val nextGlobal = (currentGlobalIdx + delta).coerceIn(0, rangePoints.lastIndex)
        var newStart = safeStartIdx
        var newEnd = safeEndIdx
        if (nextGlobal < newStart) {
            val shift = newStart - nextGlobal
            newStart = (newStart - shift).coerceAtLeast(0)
            newEnd = (newEnd - shift).coerceAtLeast(newStart)
        } else if (nextGlobal > newEnd) {
            val shift = nextGlobal - newEnd
            newEnd = (newEnd + shift).coerceAtMost(rangePoints.lastIndex)
            newStart = (newStart + shift).coerceAtMost(newEnd)
        }
        visibleStartIdx = newStart
        visibleEndIdx = newEnd
        selectedPointIdx = (nextGlobal - newStart).coerceIn(0, (newEnd - newStart).coerceAtLeast(0))
    }

    val sourceDomainName = if (asset == TgjuChartAsset.GRAM) "nobitex.ir" else "tgju.org"
    val cacheStatusSubtitle = remember(dataSource, allPoints.size, newlyAddedDays, isSyncingInBackground, sourceDomainName) {
        when {
            isSyncingInBackground && allPoints.isNotEmpty() ->
                "در حال همگام‌سازی روزهای جدید (کش از ۱۳۹۰: ${allPoints.size} روز)..."
            dataSource == TgjuHistorySource.DISK_CACHE_OFFLINE ->
                "حالت آفلاین • بارگذاری از کش گوشی (${allPoints.size} روز • از ۱۳۹۰)"
            dataSource == TgjuHistorySource.INCREMENTAL_UPDATE && newlyAddedDays > 0 ->
                "به‌روزرسانی افزایشی (+$newlyAddedDays روز جدید) • کش از ۱۳۹۰ (${allPoints.size} روز)"
            dataSource == TgjuHistorySource.INCREMENTAL_UPDATE ->
                "به‌روز است (دیتای گذشته از کش گوشی • ${allPoints.size} روز)"
            allPoints.isNotEmpty() ->
                "ذخیره آفلاین از سال ۱۳۹۰ در کش گوشی (${allPoints.size} روز) • $sourceDomainName"
            else ->
                "منبع: $sourceDomainName  •  آرشیو از سال 1390"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ScaledDialogContent {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .heightIn(max = 780.dp)
                    .testTag("tgju_historical_chart_dialog"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.5.dp, accentColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "نمودار قیمت ${asset.titleFa}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (allPoints.isNotEmpty()) {
                                        Icon(
                                            imageVector = if (dataSource == TgjuHistorySource.DISK_CACHE_OFFLINE) {
                                                Icons.Default.WifiOff
                                            } else {
                                                Icons.Default.OfflinePin
                                            },
                                            contentDescription = null,
                                            tint = if (dataSource == TgjuHistorySource.DISK_CACHE_OFFLINE) {
                                                Color(0xFFD97706)
                                            } else {
                                                Color(0xFF059669)
                                            },
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    Text(
                                        text = enDigits(cacheStatusSubtitle),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dataSource == TgjuHistorySource.DISK_CACHE_OFFLINE) {
                                            Color(0xFFB45309)
                                        } else {
                                            appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { if (!isSyncingInBackground) refreshTrigger++ },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("tgju_chart_refresh_button")
                            ) {
                                if (isSyncingInBackground) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        strokeWidth = 2.2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "به‌روزرسانی افزایشی نمودار",
                                        tint = accentColor
                                    )
                                }
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("tgju_chart_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "بستن نمودار",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Historical Shamsi Date Search Panel right at the top (Year / Month / Day)
                    if (!isLoading && allPoints.isNotEmpty()) {
                        HistoricalDateSearchBar(
                            accentColor = accentColor,
                            availableYears = availableYears,
                            selectedYear = searchYear,
                            selectedMonth = searchMonth,
                            selectedDay = searchDay,
                            oldestDateStr = allPoints.first().shamsiDate,
                            newestDateStr = allPoints.last().shamsiDate,
                            onYearChange = { newY ->
                                searchYear = newY
                                val maxD = PersianDateHelper.getDaysInMonth(newY, searchMonth)
                                if (searchDay > maxD) searchDay = maxD
                            },
                            onMonthChange = { newM ->
                                searchMonth = newM
                                val maxD = PersianDateHelper.getDaysInMonth(searchYear, newM)
                                if (searchDay > maxD) searchDay = maxD
                            },
                            onDayChange = { newD ->
                                searchDay = newD
                            },
                            onSearchClick = { executeHistoricalDateSearch() },
                            searchResult = searchResultBanner,
                            asset = asset,
                            onDismissResult = { searchResultBanner = null }
                        )
                    }

                    // 8 Timeframe Selector Chips in a symmetric 4x2 grid with vibrant, cheerful jewel-tone colors
                    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val topRanges = remember {
                        listOf(
                            TgjuChartRange.WEEK_1,
                            TgjuChartRange.MONTH_1,
                            TgjuChartRange.MONTH_6,
                            TgjuChartRange.YEAR_1
                        )
                    }
                    val bottomRanges = remember {
                        listOf(
                            TgjuChartRange.YEAR_3,
                            TgjuChartRange.YEAR_5,
                            TgjuChartRange.YEAR_10,
                            TgjuChartRange.FROM_1390
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(topRanges, bottomRanges).forEach { rowRanges ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                rowRanges.forEach { range ->
                                    val isSelected = range == selectedRange
                                    val (chipAccent, chipDarkText) = when (range) {
                                        TgjuChartRange.WEEK_1 -> Color(0xFF10B981) to Color(0xFF065F46)     // Radiant Emerald
                                        TgjuChartRange.MONTH_1 -> Color(0xFF0EA5E9) to Color(0xFF0369A1)    // Luminous Sky Blue
                                        TgjuChartRange.MONTH_6 -> Color(0xFF6366F1) to Color(0xFF3730A3)    // Vivid Indigo
                                        TgjuChartRange.YEAR_1 -> Color(0xFFF59E0B) to Color(0xFF92400E)     // Cheerful Amber Gold
                                        TgjuChartRange.YEAR_3 -> Color(0xFFF43F5E) to Color(0xFF9F1239)     // Vibrant Coral Rose
                                        TgjuChartRange.YEAR_5 -> Color(0xFFA855F7) to Color(0xFF6B21A8)     // Bright Orchid Purple
                                        TgjuChartRange.YEAR_10 -> Color(0xFFF97316) to Color(0xFF9A3412)    // Energetic Sunset Orange
                                        TgjuChartRange.FROM_1390 -> Color(0xFF14B8A6) to Color(0xFF115E59)  // Sparkling Ocean Teal
                                    }
                                    val unselectedBg = chipAccent.copy(alpha = if (isDarkTheme) 0.22f else 0.14f)
                                    val chipBorderColor = if (isSelected) {
                                        chipAccent
                                    } else {
                                        chipAccent.copy(alpha = if (isDarkTheme) 0.72f else 0.62f)
                                    }
                                    val chipTextColor = when {
                                        isSelected -> Color.White
                                        isDarkTheme -> Color.White
                                        else -> chipDarkText
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(11.dp),
                                        color = if (isSelected) chipAccent else unselectedBg,
                                        shadowElevation = if (isSelected) 4.dp else 0.dp,
                                        border = BorderStroke(
                                            width = if (isSelected) 1.6.dp else 1.2.dp,
                                            color = chipBorderColor
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .clickable { selectTimeRange(range) }
                                            .testTag("tgju_chart_range_${range.name}")
                                    ) {
                                        Text(
                                            text = enDigits(range.labelFa),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.ExtraBold,
                                            color = chipTextColor,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            softWrap = false,
                                            modifier = Modifier.padding(vertical = 7.5.dp, horizontal = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active Price & Touched Date Readout Card
                    if (activePoint != null && !isLoading) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = accentColor.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Exact weekday + Shamsi date
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(7.dp),
                                            color = accentColor
                                        ) {
                                            Text(
                                                text = activePoint.weekdayFa.ifBlank { "تاریخ" },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                            )
                                        }
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = PersianDateHelper.toEnglishDigits(activePoint.shamsiDate),
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // Period change pill
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = trendColor.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, trendColor.copy(alpha = 0.4f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPositiveTrend) {
                                                    Icons.AutoMirrored.Filled.TrendingUp
                                                } else {
                                                    Icons.AutoMirrored.Filled.TrendingDown
                                                },
                                                contentDescription = null,
                                                tint = trendColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                val sign = if (isPositiveTrend) "+" else "-"
                                                Text(
                                                    text = "$sign${String.format(Locale.US, "%.2f", abs(periodPct))}%",
                                                    fontFamily = OutfitFontFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = trendColor,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = formatChartPrice(activePoint.price, asset),
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Black,
                                                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        if (!asset.isUsdCurrency) {
                                            Text(
                                                text = asset.unitFa,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.padding(bottom = 2.dp)
                                            )
                                        }
                                    }

                                    // Zoom controls (+ / -)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { zoomInWindow() }
                                                .testTag("tgju_chart_zoom_in")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomIn,
                                                    contentDescription = "بزرگ‌نمایی نمودار",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { zoomOutWindow() }
                                                .testTag("tgju_chart_zoom_out")
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.ZoomOut,
                                                    contentDescription = "کوچک‌نمایی نمودار",
                                                    tint = accentColor,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Daily High / Low summary
                                if (activePoint.high > activePoint.low) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = enDigits("بیشترین روز: ${formatChartPrice(activePoint.high, asset)}"),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = enDigits("کمترین روز: ${formatChartPrice(activePoint.low, asset)}"),
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFDC2626),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Single-line interactive Highest & Lowest price strip for the currently selected timeframe
                    if (rangeExtremes != null && !isLoading) {
                        RangeExtremesRow(
                            extremes = rangeExtremes,
                            rangeLabelFa = selectedRange.labelFa,
                            asset = asset,
                            selectedDateStr = if (selectedPointIdx != null) activePoint?.shamsiDate else null,
                            onHighestClick = {
                                focusPointInCurrentRange(rangeExtremes.highestPoint)
                            },
                            onLowestClick = {
                                focusPointInCurrentRange(rangeExtremes.lowestPoint)
                            }
                        )
                    }

                    // Main Interactive Line Chart Area (with top crosshair price callout pill!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(275.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isLoading -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "در حال دریافت و ذخیره داده‌ها از سال ۱۳۹۰ در کش گوشی...",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            errorMsg != null && displayedPoints.isEmpty() -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = errorMsg ?: "خطا در دریافت نمودار",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                        textAlign = TextAlign.Center
                                    )
                                    Button(onClick = { refreshTrigger++ }) {
                                        Text("تلاش مجدد", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            displayedPoints.size >= 2 -> {
                                InteractiveTgjuLineCanvas(
                                    points = displayedPoints,
                                    asset = asset,
                                    lineColor = trendColor,
                                    accentColor = accentColor,
                                    selectedIndex = activeIndex,
                                    isWeeklyGranularity = isWeeklyGranularity,
                                    range = selectedRange,
                                    onSelectIndex = { idx ->
                                        searchResultBanner = null
                                        selectedPointIdx = idx
                                    }
                                )
                            }

                            else -> {
                                Text(
                                    text = "داده کافی برای این بازه زمانی یافت نشد",
                                    fontSize = 12.sp,
                                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }

                    // Day-by-Day Left / Right Stepper Controls directly below the chart
                    if (displayedPoints.size >= 2 && !isLoading) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Left arrow -> Older / Previous day
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (canStepPrev) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = canStepPrev) { stepDay(-1) }
                                            .testTag("tgju_chart_prev_day")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                contentDescription = "روز قبل",
                                                tint = if (canStepPrev) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "روز قبل",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (canStepPrev) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // Center active day indicator & counter
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp)
                                    ) {
                                        if (activePoint != null) {
                                            Text(
                                                text = PersianDateHelper.toEnglishDigits(activePoint.shamsiDate),
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Text(
                                                text = enDigits("${activePoint.weekdayFa}  (${currentGlobalIdx + 1}/${rangePoints.size})"),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = accentColor,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Right arrow -> Newer / Next day
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (canStepNext) accentColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable(enabled = canStepNext) { stepDay(+1) }
                                            .testTag("tgju_chart_next_day")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = "روز بعد",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = if (canStepNext) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "روز بعد",
                                                tint = if (canStepNext) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Touch hint footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "از سرچ تاریخی بالا، فلش‌های روزانه یا لمس نمودار برای مشاهده قیمت استفاده کنید",
                            fontSize = 9.8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeExtremesRow(
    extremes: TgjuFifteenYearExtremes,
    rangeLabelFa: String,
    asset: TgjuChartAsset,
    selectedDateStr: String?,
    onHighestClick: () -> Unit,
    onLowestClick: () -> Unit
) {
    val isHighestFocused = selectedDateStr != null && selectedDateStr == extremes.highestPoint.shamsiDate
    val isLowestFocused = selectedDateStr != null && selectedDateStr == extremes.lowestPoint.shamsiDate
    val highColor = Color(0xFF059669)
    val lowColor = Color(0xFFDC2626)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tgju_chart_range_extremes_row"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExtremePriceChip(
            label = "بیشترین $rangeLabelFa",
            point = extremes.highestPoint,
            asset = asset,
            tintColor = highColor,
            isFocused = isHighestFocused,
            isHigh = true,
            testTagName = "tgju_chart_range_highest_button",
            onClick = onHighestClick,
            modifier = Modifier.weight(1f)
        )
        ExtremePriceChip(
            label = "کمترین $rangeLabelFa",
            point = extremes.lowestPoint,
            asset = asset,
            tintColor = lowColor,
            isFocused = isLowestFocused,
            isHigh = false,
            testTagName = "tgju_chart_range_lowest_button",
            onClick = onLowestClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ExtremePriceChip(
    label: String,
    point: TgjuHistoryPoint,
    asset: TgjuChartAsset,
    tintColor: Color,
    isFocused: Boolean,
    isHigh: Boolean,
    testTagName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val priceFormatted = formatChartPrice(point.price, asset)
    val dateFormatted = PersianDateHelper.toEnglishDigits(point.shamsiDate)

    Surface(
        shape = RoundedCornerShape(11.dp),
        color = tintColor.copy(alpha = if (isFocused) 0.18f else 0.08f),
        border = BorderStroke(
            width = if (isFocused) 1.6.dp else 1.dp,
            color = tintColor.copy(alpha = if (isFocused) 0.95f else 0.38f)
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .testTag(testTagName)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = if (isHigh) {
                            Icons.AutoMirrored.Filled.TrendingUp
                        } else {
                            Icons.AutoMirrored.Filled.TrendingDown
                        },
                        contentDescription = label,
                        tint = tintColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = enDigits(label),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        color = tintColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = dateFormatted,
                        fontFamily = OutfitFontFamily,
                        fontSize = 8.8.sp,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = priceFormatted,
                        fontFamily = OutfitFontFamily,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (!asset.isUsdCurrency) {
                    Text(
                        text = asset.unitFa,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}


