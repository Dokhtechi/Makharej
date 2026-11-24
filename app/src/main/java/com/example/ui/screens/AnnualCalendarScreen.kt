package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ExpenseEntity
import com.example.data.model.MonthSummary
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.utils.PersianDateHelper

private val AnnualCalendarCurrentColor = Color(0xFF059669) // Emerald green for هزینه جاری
private val AnnualCalendarTotalColor = Color(0xFF7C3AED)   // Royal violet for هزینه کل

@Composable
fun AnnualCalendarScreen(
    viewModel: ExpenseViewModel,
    onAddExpenseInMonth: (monthIndex: Int) -> Unit,
    onEditExpense: (ExpenseEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }

    if (selectedMonthIndex != null) {
        MonthDetailScreen(
            viewModel = viewModel,
            onBackClick = { selectedMonthIndex = null },
            onAddExpenseInMonth = onAddExpenseInMonth,
            onEditExpense = onEditExpense
        )
    } else {
        val currentYear by viewModel.currentYear.collectAsStateWithLifecycle()
        val monthSummaries by viewModel.yearMonthSummaries.collectAsStateWithLifecycle()
        val clockState by viewModel.clockState.collectAsStateWithLifecycle()
        val currentMonth = clockState.shamsiDate.month

        val highestMonthExpense = monthSummaries.maxOfOrNull { it.totalExpense }?.coerceAtLeast(1L) ?: 1L
        val totalYearDirectExpenses = monthSummaries.sumOf { it.directExpenseTotal }
        val totalYearExpenses = monthSummaries.sumOf { it.totalExpense }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
                .testTag("annual_calendar_screen")
        ) {
            // Year Navigation Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF10B981)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(scaledDp(12f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Year Button (Opposing Arrow on the right)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(scaledDp(40f))
                                .clip(CircleShape)
                                .clickable { viewModel.setYear(currentYear - 1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "سال قبل",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(scaledDp(22f))
                                )
                            }
                        }

                        // Current Year Title & Annual Total
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(scaledDp(18f))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = enDigits("سال $currentYear"),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = enDigits(PersianDateHelper.formatCurrency(totalYearDirectExpenses, includeUnit = false)),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AnnualCalendarCurrentColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    text = "|",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = enDigits(PersianDateHelper.formatCurrency(totalYearExpenses, includeUnit = false)),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = AnnualCalendarTotalColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Next Year Button (Opposing Arrow on the left)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(scaledDp(40f))
                                .clip(CircleShape)
                                .clickable { viewModel.setYear(currentYear + 1) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "سال بعد",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(scaledDp(22f))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Selectable Year Pills (allow jumping to any past year easily)
                    val currentCalendarYear = clockState.shamsiDate.year
                    val quickYears = listOf(
                        currentCalendarYear - 3,
                        currentCalendarYear - 2,
                        currentCalendarYear - 1,
                        currentCalendarYear,
                        currentCalendarYear + 1
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickYears.forEach { yr ->
                            val isSelected = yr == currentYear
                            val isThisYear = yr == currentCalendarYear
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isThisYear -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        }
                                    )
                                    .clickable { viewModel.setYear(yr) }
                                    .padding(horizontal = scaledDp(8f), vertical = scaledDp(4f))
                            ) {
                                Text(
                                    text = enDigits(if (isThisYear && !isSelected) "$yr (امسال)" else yr.toString()),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                    color = when {
                                        isSelected -> Color.White
                                        isThisYear -> MaterialTheme.colorScheme.primary
                                        else -> appTextColor(MaterialTheme.colorScheme.onSurface)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Compact 1-line color legend: Green = هزینه جاری | Violet = هزینه کل
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("annual_calendar_color_legend"),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AnnualCalendarCurrentColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "هزینه جاری",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = AnnualCalendarCurrentColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(AnnualCalendarTotalColor)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "هزینه کل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = AnnualCalendarTotalColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(تومان)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 12 Persian Months Grid - 3 columns x 4 rows fits completely on screen
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(monthSummaries) { monthSummary ->
                    val isCurrent = monthSummary.monthIndex == currentMonth && currentYear == clockState.shamsiDate.year
                    val progress = (monthSummary.totalExpense.toFloat() / highestMonthExpense.toFloat()).coerceIn(0f, 1f)

                    CompactMonthCard(
                        summary = monthSummary,
                        isCurrent = isCurrent,
                        relativeProgress = progress,
                        onClick = {
                            viewModel.setSelectedMonth(monthSummary.monthIndex)
                            selectedMonthIndex = monthSummary.monthIndex
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CompactMonthCard(
    summary: MonthSummary,
    isCurrent: Boolean,
    relativeProgress: Float,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable { onClick() }
            .testTag("month_card_${summary.monthIndex}"),
        shape = RoundedCornerShape(13.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isCurrent) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981))
        } else if (summary.totalExpense > 0) {
            androidx.compose.foundation.BorderStroke(1.4.dp, Color(0xFF0284C7))
        } else {
            androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF94A3B8))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 2.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = scaledDp(8f), vertical = scaledDp(7f))
        ) {
            // Month name & number
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = enDigits("${summary.monthIndex}. ${summary.monthName}"),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                    softWrap = true,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "جاری",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Dual color-coded amounts: Green = هزینه جاری ماه | Violet = هزینه کل ماه (بدون متن اضافی)
            val directStr = PersianDateHelper.formatCurrency(summary.directExpenseTotal, includeUnit = false)
            val totalStr = PersianDateHelper.formatCurrency(summary.totalExpense, includeUnit = false)
            val maxLen = maxOf(directStr.length, totalStr.length)
            val amountFontSize = when {
                maxLen >= 11 -> 10.sp
                maxLen >= 9 -> 10.8.sp
                else -> 11.5.sp
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Row 1: هزینه جاری (Emerald Green)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(AnnualCalendarCurrentColor)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = directStr,
                            fontFamily = OutfitFontFamily,
                            fontSize = amountFontSize,
                            lineHeight = (amountFontSize.value + 3f).sp,
                            fontWeight = FontWeight.Black,
                            color = if (summary.directExpenseTotal > 0L) AnnualCalendarCurrentColor
                            else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Row 2: هزینه کل (Royal Violet)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(AnnualCalendarTotalColor)
                    )
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = totalStr,
                            fontFamily = OutfitFontFamily,
                            fontSize = amountFontSize,
                            lineHeight = (amountFontSize.value + 3f).sp,
                            fontWeight = FontWeight.Black,
                            color = if (summary.totalExpense > 0L) AnnualCalendarTotalColor
                            else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Transaction count
            Text(
                text = enDigits("${summary.transactionCount} تراکنش"),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                maxLines = 1,
                softWrap = false
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Mini progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (relativeProgress > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(relativeProgress)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isCurrent) MaterialTheme.colorScheme.primary else Color(0xFF10B981))
                    )
                }
            }
        }
    }
}
