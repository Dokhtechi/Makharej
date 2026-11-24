package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import com.example.data.network.TgjuChartAsset
import com.example.data.network.TgjuDateSearchResult
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.utils.PersianDateHelper

@Composable
internal fun HistoricalDateSearchBar(
    accentColor: Color,
    availableYears: List<Int>,
    selectedYear: Int,
    selectedMonth: Int,
    selectedDay: Int,
    oldestDateStr: String,
    newestDateStr: String,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onSearchClick: () -> Unit,
    searchResult: TgjuDateSearchResult?,
    asset: TgjuChartAsset,
    onDismissResult: () -> Unit
) {
    var yearMenuExpanded by remember { mutableStateOf(false) }
    var monthMenuExpanded by remember { mutableStateOf(false) }
    var dayMenuExpanded by remember { mutableStateOf(false) }

    val maxDaysInMonth = remember(selectedYear, selectedMonth) {
        PersianDateHelper.getDaysInMonth(selectedYear, selectedMonth)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.32f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("tgju_historical_date_search_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "جستجوی تاریخی قیمت:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${PersianDateHelper.toEnglishDigits(oldestDateStr)} - ${PersianDateHelper.toEnglishDigits(newestDateStr)}",
                        fontFamily = OutfitFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1.05f)) {
                    DatePartDropdownChip(
                        label = "سال",
                        value = selectedYear.toString(),
                        accentColor = accentColor,
                        useLtrValue = true,
                        testTagName = "tgju_search_year_picker",
                        onClick = { yearMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = yearMenuExpanded,
                        onDismissRequest = { yearMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        availableYears.forEach { yr ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = yr.toString(),
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = if (yr == selectedYear) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (yr == selectedYear) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onYearChange(yr)
                                    yearMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1.3f)) {
                    DatePartDropdownChip(
                        label = "ماه",
                        value = PersianDateHelper.getMonthName(selectedMonth).ifBlank { selectedMonth.toString() },
                        accentColor = accentColor,
                        useLtrValue = false,
                        testTagName = "tgju_search_month_picker",
                        onClick = { monthMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = monthMenuExpanded,
                        onDismissRequest = { monthMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        PersianDateHelper.PERSIAN_MONTH_NAMES.forEachIndexed { idx, name ->
                            val monthNum = idx + 1
                            val monthStr = monthNum.toString().padStart(2, '0')
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = enDigits("$monthStr - $name"),
                                        fontWeight = if (monthNum == selectedMonth) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (monthNum == selectedMonth) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onMonthChange(monthNum)
                                    monthMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(0.85f)) {
                    DatePartDropdownChip(
                        label = "روز",
                        value = selectedDay.toString().padStart(2, '0'),
                        accentColor = accentColor,
                        useLtrValue = true,
                        testTagName = "tgju_search_day_picker",
                        onClick = { dayMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = dayMenuExpanded,
                        onDismissRequest = { dayMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        (1..maxDaysInMonth).forEach { dayNum ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = dayNum.toString().padStart(2, '0'),
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = if (dayNum == selectedDay) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (dayNum == selectedDay) accentColor else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    onDayChange(dayNum)
                                    dayMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor,
                    modifier = Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSearchClick() }
                        .testTag("tgju_date_search_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "جستجوی تاریخ",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "جستجو",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            if (searchResult != null) {
                val bannerColor = when {
                    searchResult.isExactMatch -> Color(0xFF059669)
                    searchResult.isOutOfRange -> Color(0xFFDC2626)
                    else -> Color(0xFFD97706)
                }
                val unitSuffix = if (asset.isUsdCurrency) "" else " ${asset.unitFa}"
                val formattedPrice = formatChartPrice(searchResult.point.price, asset)
                val bannerText = when {
                    searchResult.isExactMatch ->
                        "قیمت دقیق ${searchResult.point.weekdayFa} ${searchResult.point.shamsiDate}: $formattedPrice$unitSuffix"
                    searchResult.isOutOfRange ->
                        "تاریخ ${searchResult.requestedShamsiDate} خارج از آرشیو ثبت‌شده از ۱۳۹۰ است؛ نزدیک‌ترین روز (${searchResult.point.shamsiDate}: $formattedPrice$unitSuffix)"
                    else ->
                        "در ${searchResult.requestedShamsiDate} معامله ثبت نشده؛ نزدیک‌ترین روز کاری (${searchResult.point.weekdayFa} ${searchResult.point.shamsiDate}: $formattedPrice$unitSuffix)"
                }

                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = bannerColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tgju_date_search_result_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (searchResult.isExactMatch) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = bannerColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = enDigits(bannerText),
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "بستن پیام جستجو",
                            tint = bannerColor,
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { onDismissResult() }
                                .padding(3.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DatePartDropdownChip(
    label: String,
    value: String,
    accentColor: Color,
    useLtrValue: Boolean,
    testTagName: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                    maxLines = 1,
                    softWrap = false
                )
                if (useLtrValue) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = value,
                            fontFamily = OutfitFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = appTextColor(MaterialTheme.colorScheme.onSurface),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = value,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "انتخاب $label",
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
