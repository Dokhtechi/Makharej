package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.FolderSummary
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.utils.PersianDateHelper
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val VibrantPieColors = listOf(
    Color(0xFF10B981), // Vivid Emerald
    Color(0xFF0EA5E9), // Vivid Sky Blue
    Color(0xFFF59E0B), // Radiant Amber
    Color(0xFFF43F5E), // Coral Rose
    Color(0xFF8B5CF6), // Royal Purple
    Color(0xFF14B8A6), // Turquoise
    Color(0xFFF97316), // Bright Orange
    Color(0xFF6366F1), // Indigo
    Color(0xFFEC4899), // Pink
    Color(0xFF84CC16), // Lime Green
    Color(0xFF06B6D4), // Cyan
    Color(0xFFEAB308), // Yellow
    Color(0xFF2563EB), // Royal Blue
    Color(0xFFDC2626), // Crimson Red
    Color(0xFF059669), // Deep Emerald
    Color(0xFFD946EF), // Fuchsia
    Color(0xFF7C3AED), // Deep Violet
    Color(0xFF0284C7)  // Ocean Blue
)

fun formatPiePercentage(percentage: Double): String {
    if (percentage <= 0.0) return "0%"
    return if (percentage < 10.0) {
        String.format(Locale.US, "%.1f%%", percentage)
    } else {
        val rounded1 = (percentage * 10.0).roundToInt() / 10.0
        if (rounded1 % 1.0 == 0.0) "${rounded1.toInt()}%" else String.format(Locale.US, "%.1f%%", rounded1)
    }
}

private data class PieSliceCallout(
    val index: Int,
    val title: String,
    val pctText: String,
    val color: Color,
    val midAngleRad: Double,
    val isRightSide: Boolean,
    val anchorX: Float,
    val anchorY: Float,
    val elbowX: Float,
    val elbowY: Float,
    var labelY: Float
)

/**
 * Beautiful, interactive Pie/Donut Chart for expense distribution in the selected report period.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensePieChartCard(
    folderSummaries: List<FolderSummary>,
    totalAmount: Long,
    modifier: Modifier = Modifier,
    title: String = "نمودار سهم مخارج (پای‌چارت دایره‌ای)",
    subtitle: String = "توزیع درصد هزینه‌ها بر اساس پوشه‌ها در بازه انتخابی",
    legendTitle: String = "راهنمای پوشه‌ها و سهم درصدی:",
    centerLabel: String = "مجموع هزینه‌ها"
) {
    // Filter active items with amount > 0
    val activeItems = remember(folderSummaries) {
        folderSummaries.filter { it.totalAmount > 0 }.sortedByDescending { it.totalAmount }
    }

    val computedTotal = remember(activeItems, totalAmount) {
        val sum = activeItems.sumOf { it.totalAmount }
        if (sum > 0) sum else totalAmount
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("expense_pie_chart_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaledDp(16f)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(scaledDp(36f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(scaledDp(20f))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = enDigits(title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = appTextColor(MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = enDigits(subtitle),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            if (activeItems.isEmpty() || computedTotal <= 0) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "در این بازه زمانی هزینه‌ای برای رسم پای‌چارت ثبت نشده است",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Circular Donut/Pie Chart with Arrow Callout Labels pointing to each colored slice
                val textMeasurer = rememberTextMeasurer()
                val baseFontFamily = MaterialTheme.typography.bodySmall.fontFamily
                val labelBgColor = MaterialTheme.colorScheme.surface
                val labelTextDefaultColor = appTextColor(MaterialTheme.colorScheme.onSurface)

                val chartHeightDp = when {
                    activeItems.size >= 9 -> 320.dp
                    activeItems.size >= 6 -> 295.dp
                    else -> 270.dp
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeightDp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeightDp)
                    ) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val strokeWidth = 24.dp.toPx()
                        // Reserve generous left & right space for arrow callout titles
                        val outerRadius = minOf(size.width * 0.24f, size.height * 0.31f).coerceIn(50.dp.toPx(), 78.dp.toPx())
                        val ringRadius = outerRadius - strokeWidth / 2f
                        val ringDiameter = ringRadius * 2f
                        val topLeft = Offset(cx - ringRadius, cy - ringRadius)
                        val arcSize = Size(ringDiameter, ringDiameter)

                        val callouts = mutableListOf<PieSliceCallout>()
                        val maxLabeledSlices = 10

                        var startAngle = -90f
                        for ((idx, item) in activeItems.withIndex()) {
                            val sweepAngle = (item.totalAmount.toFloat() / computedTotal.toFloat()) * 360f
                            val sliceColor = VibrantPieColors[idx % VibrantPieColors.size]
                            val pct = (item.totalAmount.toDouble() / computedTotal.toDouble()) * 100.0

                            if (sweepAngle > 0.1f) {
                                val drawSweep = if (activeItems.size == 1) {
                                    360f
                                } else {
                                    val gap = if (sweepAngle > 3.5f) 1.2f else 0.25f
                                    (sweepAngle - gap).coerceAtLeast(0.75f)
                                }
                                drawArc(
                                    color = sliceColor,
                                    startAngle = startAngle,
                                    sweepAngle = drawSweep,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                )

                                // Add arrow callout for every visible top slice
                                if (callouts.size < maxLabeledSlices && (activeItems.size <= 8 || pct >= 2.2)) {
                                    val midDeg = startAngle + sweepAngle / 2f
                                    val midRad = Math.toRadians(midDeg.toDouble())
                                    val cosA = cos(midRad).toFloat()
                                    val sinA = sin(midRad).toFloat()
                                    val isRight = cosA >= 0f
                                    val anchorR = outerRadius + 2.dp.toPx()
                                    val elbowR = outerRadius + 15.dp.toPx()
                                    callouts.add(
                                        PieSliceCallout(
                                            index = idx,
                                            title = item.folderName,
                                            pctText = formatPiePercentage(pct),
                                            color = sliceColor,
                                            midAngleRad = midRad,
                                            isRightSide = isRight,
                                            anchorX = cx + cosA * anchorR,
                                            anchorY = cy + sinA * anchorR,
                                            elbowX = cx + cosA * elbowR,
                                            elbowY = cy + sinA * elbowR,
                                            labelY = cy + sinA * elbowR
                                        )
                                    )
                                }
                            }
                            startAngle += sweepAngle
                        }

                        // Resolve vertical collisions independently on Right and Left columns
                        val padTop = 16.dp.toPx()
                        val padBottom = size.height - 16.dp.toPx()
                        val minVerticalSpacing = 27.dp.toPx()

                        fun relaxColumn(sideItems: List<PieSliceCallout>) {
                            if (sideItems.isEmpty()) return
                            val sorted = sideItems.sortedBy { it.labelY }
                            // Downward push pass
                            sorted[0].labelY = sorted[0].labelY.coerceIn(padTop, padBottom)
                            for (i in 1 until sorted.size) {
                                val minAllowed = sorted[i - 1].labelY + minVerticalSpacing
                                if (sorted[i].labelY < minAllowed) {
                                    sorted[i].labelY = minAllowed
                                }
                            }
                            // Upward pull pass if bottom overflowed
                            if (sorted.last().labelY > padBottom) {
                                sorted.last().labelY = padBottom
                                for (i in sorted.lastIndex - 1 downTo 0) {
                                    val maxAllowed = sorted[i + 1].labelY - minVerticalSpacing
                                    if (sorted[i].labelY > maxAllowed) {
                                        sorted[i].labelY = maxAllowed
                                    }
                                }
                            }
                            sorted.forEach { it.labelY = it.labelY.coerceIn(padTop, padBottom) }
                        }

                        val rightCallouts = callouts.filter { it.isRightSide }
                        val leftCallouts = callouts.filter { !it.isRightSide }
                        relaxColumn(rightCallouts)
                        relaxColumn(leftCallouts)

                        val arrowLen = 6.dp.toPx()
                        val arrowHalfWidth = 3.2.dp.toPx()
                        val horizStub = 10.dp.toPx()
                        val sidePad = 2.dp.toPx()

                        (rightCallouts + leftCallouts).forEach { c ->
                            val isRight = c.isRightSide
                            val bendX = if (isRight) {
                                maxOf(c.elbowX, cx + outerRadius + 10.dp.toPx())
                            } else {
                                minOf(c.elbowX, cx - outerRadius - 10.dp.toPx())
                            }
                            val lineEndX = if (isRight) bendX + horizStub else bendX - horizStub

                            // 1. Colored elbow leader line from slice rim to label
                            val leaderPath = Path().apply {
                                moveTo(c.anchorX, c.anchorY)
                                lineTo(bendX, c.labelY)
                                lineTo(lineEndX, c.labelY)
                            }
                            drawPath(
                                path = leaderPath,
                                color = c.color,
                                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            )

                            // 2. Crisp filled arrowhead right at the slice edge pointing directly to the colored slice
                            val dirAngle = atan2((c.anchorY - c.labelY).toDouble(), (c.anchorX - bendX).toDouble())
                            val tipX = c.anchorX
                            val tipY = c.anchorY
                            val backX = tipX - cos(dirAngle).toFloat() * arrowLen
                            val backY = tipY - sin(dirAngle).toFloat() * arrowLen
                            val perpX = -sin(dirAngle).toFloat() * arrowHalfWidth
                            val perpY = cos(dirAngle).toFloat() * arrowHalfWidth

                            val arrowPath = Path().apply {
                                moveTo(tipX, tipY)
                                lineTo(backX + perpX, backY + perpY)
                                lineTo(backX - perpX, backY - perpY)
                                close()
                            }
                            drawPath(path = arrowPath, color = c.color, style = Fill)

                            // 3. Measure & draw crisp title + percentage right at the tip of the leader line
                            val availableW = if (isRight) {
                                (size.width - lineEndX - sidePad - 4.dp.toPx()).toInt().coerceAtLeast(36)
                            } else {
                                (lineEndX - sidePad - 4.dp.toPx()).toInt().coerceAtLeast(36)
                            }
                            val labelStr = enDigits("${c.title} (${c.pctText})")
                            val textLayout = textMeasurer.measure(
                                text = labelStr,
                                style = TextStyle(
                                    fontFamily = baseFontFamily,
                                    fontSize = 9.sp,
                                    lineHeight = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = labelTextDefaultColor,
                                    textDirection = TextDirection.Rtl,
                                    textAlign = if (isRight) TextAlign.Left else TextAlign.Right
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                constraints = Constraints(maxWidth = availableW)
                            )

                            val textX = if (isRight) {
                                (lineEndX + 3.dp.toPx()).coerceAtMost(size.width - textLayout.size.width - sidePad)
                            } else {
                                (lineEndX - 3.dp.toPx() - textLayout.size.width).coerceAtLeast(sidePad)
                            }
                            val textY = (c.labelY - textLayout.size.height / 2f)
                                .coerceIn(2.dp.toPx(), (size.height - textLayout.size.height - 2.dp.toPx()).coerceAtLeast(2.dp.toPx()))

                            // Subtle clean tag backdrop so text is ultra-legible
                            drawRoundRect(
                                color = labelBgColor.copy(alpha = 0.88f),
                                topLeft = Offset(textX - 2.dp.toPx(), textY - 1.dp.toPx()),
                                size = Size(textLayout.size.width + 4.dp.toPx(), textLayout.size.height + 2.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = textLayout,
                                color = c.color,
                                topLeft = Offset(textX, textY)
                            )
                        }
                    }

                    // Center Content (Total Amount & Label snugly inside inner donut hole)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = centerLabel,
                            fontSize = 9.5.sp,
                            color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        val centerNumStr = PersianDateHelper.formatCurrency(computedTotal, includeUnit = false)
                        Text(
                            text = enDigits(centerNumStr),
                            fontSize = if (centerNumStr.length >= 10) 11.sp else 12.5.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "تومان",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(12.dp))

                // Chart Legend (راهنمای رنگ‌ها، درصدها و مبالغ)
                Text(
                    text = enDigits(legendTitle),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((idx, item) in activeItems.withIndex()) {
                        val sliceColor = VibrantPieColors[idx % VibrantPieColors.size]
                        val percentage = if (computedTotal > 0) {
                            (item.totalAmount.toDouble() / computedTotal.toDouble()) * 100.0
                        } else {
                            0.0
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                sliceColor.copy(alpha = 0.35f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Color Indicator Dot
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(sliceColor)
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Folder Name
                                Text(
                                    text = enDigits(item.folderName),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                    softWrap = true
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                // Percentage Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = sliceColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = enDigits(formatPiePercentage(percentage)),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = sliceColor,
                                        modifier = Modifier.padding(horizontal = scaledDp(4f), vertical = scaledDp(1f))
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Amount
                                Text(
                                    text = enDigits(PersianDateHelper.formatCurrency(item.totalAmount)),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
