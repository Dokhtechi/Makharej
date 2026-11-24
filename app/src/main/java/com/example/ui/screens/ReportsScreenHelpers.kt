package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ExpenseEntity
import com.example.data.model.FolderSummary
import com.example.data.model.ResolvedExpense
import com.example.ui.components.CurrentMonthExpenseGrouper
import com.example.ui.components.ExpenseItemCard
import com.example.ui.components.GroupedExpenseDropdownCard
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.ui.viewmodel.MonthlyBreakdownItem
import com.example.utils.PersianDateHelper

@Composable
internal fun MonthBreakdownRow(
    item: MonthlyBreakdownItem,
    onEditExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit
) {
    val progress = (item.percentageOfTotal / 100f).coerceIn(0f, 1f)
    var isMonthExpanded by rememberSaveable(item.yearMonthLabel) { mutableStateOf(false) }
    var expandedInnerGroups by rememberSaveable(item.yearMonthLabel) { mutableStateOf(listOf<String>()) }
    val groupedMonthItems = remember(item.transactions) {
        val sorted = item.transactions.sortedWith(
            compareByDescending<ResolvedExpense> { it.entity.shamsiDateString }
                .thenByDescending { it.entity.timestamp }
                .thenByDescending { it.entity.id }
        )
        CurrentMonthExpenseGrouper.groupExpenses(sorted)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = item.transactions.isNotEmpty()) {
                isMonthExpanded = !isMonthExpanded
            }
            .testTag("month_breakdown_row_${item.year}_${item.month}"),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.3.dp, Color(0xFFF43F5E).copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = enDigits(item.yearMonthLabel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = scaledDp(8.dp), vertical = scaledDp(3.dp))
                        )
                    }

                    Text(
                        text = enDigits("${item.transactionCount} تراکنش"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = enDigits(PersianDateHelper.formatCurrency(item.totalAmount)),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = if (item.totalAmount > 0) appTextColor(MaterialTheme.colorScheme.onSurface) else appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            maxLines = 1,
                            softWrap = false
                        )
                        if (item.totalAmount > 0) {
                            Text(
                                text = enDigits("${item.percentageOfTotal.toInt()}% از کل بازه"),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    if (item.transactions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isMonthExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isMonthExpanded) "بستن ریز هزینه‌های ماه" else "مشاهده ریز هزینه‌های ماه",
                            tint = Color(0xFFF43F5E),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (item.totalAmount > 0) MaterialTheme.colorScheme.primary else Color.Transparent)
                )
            }

            AnimatedVisibility(visible = isMonthExpanded && groupedMonthItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFF43F5E).copy(alpha = 0.25f))
                    Text(
                        text = enDigits("ریز هزینه‌های ${item.yearMonthLabel} (${groupedMonthItems.size} دسته):"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = true
                    )

                    groupedMonthItems.forEach { group ->
                        if (!group.isAnnual && group.items.size == 1) {
                            val singleItem = group.items.first()
                            ExpenseItemCard(
                                resolvedExpense = singleItem,
                                onDeleteClick = { onDeleteExpense(singleItem.entity) },
                                onEditClick = { onEditExpense(singleItem.entity) }
                            )
                        } else {
                            val isInnerExpanded = expandedInnerGroups.contains(group.groupKey)
                            GroupedExpenseDropdownCard(
                                group = group,
                                isExpanded = isInnerExpanded,
                                onToggleExpand = {
                                    expandedInnerGroups = if (isInnerExpanded) {
                                        expandedInnerGroups - group.groupKey
                                    } else {
                                        expandedInnerGroups + group.groupKey
                                    }
                                },
                                onEditItem = onEditExpense,
                                onDeleteItem = onDeleteExpense
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun shareCsv(context: Context, csvContent: String) {
    try {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvContent)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "اشتراک‌گذاری گزارش هزینه‌ها")
        context.startActivity(shareIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "خطا در اشتراک‌گذاری گزارش", Toast.LENGTH_SHORT).show()
    }
}

object AnnualCommitmentsReportHelper {
    private val PIE_COLOR_HEXES = longArrayOf(
        0xFF10B981, 0xFF0EA5E9, 0xFFF59E0B, 0xFFF43F5E, 0xFF8B5CF6, 0xFF14B8A6,
        0xFFF97316, 0xFF6366F1, 0xFFEC4899, 0xFF84CC16, 0xFF06B6D4, 0xFFEAB308,
        0xFF2563EB, 0xFFDC2626, 0xFF059669, 0xFFD946EF, 0xFF7C3AED, 0xFF0284C7
    )

    /**
     * Builds accurate, cost-proportional Pie Chart summaries strictly from registered annual commitments
     * (`isAnnual == true && amount > 0L`). Never includes non-annual expenses such as supermarket purchases.
     */
    fun buildAnnualCommitmentSummaries(annualExpenses: List<ExpenseEntity>): List<FolderSummary> {
        val valid = annualExpenses
            .filter { it.isAnnual && it.amount > 0L }
            .sortedWith(compareByDescending<ExpenseEntity> { it.amount }.thenBy { it.title })
        if (valid.isEmpty()) return emptyList()

        val total = valid.sumOf { it.amount }.coerceAtLeast(1L)
        val titleCounts = valid.groupingBy { it.title.trim() }.eachCount()

        return valid.mapIndexed { idx, entity ->
            val rawTitle = entity.title.trim().ifBlank { "تعهد سالانه ${idx + 1}" }
            val label = if ((titleCounts[rawTitle] ?: 0) > 1 && entity.shamsiDateString.isNotBlank()) {
                "$rawTitle (${entity.shamsiDateString})"
            } else {
                rawTitle
            }
            val pct = ((entity.amount.toDouble() / total.toDouble()) * 100.0).toFloat()
            FolderSummary(
                folderName = label,
                totalAmount = entity.amount,
                itemCount = 1,
                percentage = pct,
                colorHex = PIE_COLOR_HEXES[idx % PIE_COLOR_HEXES.size]
            )
        }
    }

    /**
     * Builds Pie Chart breakdown summaries strictly for the expenses inside a selected non-annual folder.
     */
    fun buildSelectedFolderItemSummaries(folderExpenses: List<ResolvedExpense>): List<FolderSummary> {
        val valid = folderExpenses.filter { it.entity.amount > 0L }
        if (valid.isEmpty()) return emptyList()
        val total = valid.sumOf { it.entity.amount }.coerceAtLeast(1L)

        val grouped = valid.groupBy { item ->
            item.entity.title.trim().ifBlank { item.effectiveFolder }
        }

        return grouped.entries
            .map { (title, list) ->
                val sum = list.sumOf { it.entity.amount }
                Triple(title, sum, list.size)
            }
            .sortedByDescending { it.second }
            .mapIndexed { idx, (title, sum, count) ->
                FolderSummary(
                    folderName = title,
                    totalAmount = sum,
                    itemCount = count,
                    percentage = ((sum.toDouble() / total.toDouble()) * 100.0).toFloat(),
                    colorHex = PIE_COLOR_HEXES[idx % PIE_COLOR_HEXES.size]
                )
            }
    }
}
