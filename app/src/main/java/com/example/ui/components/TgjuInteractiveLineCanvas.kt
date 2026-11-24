package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.network.TgjuChartAsset
import com.example.data.network.TgjuChartRange
import com.example.data.network.TgjuHistoryPoint
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.enDigits
import com.example.utils.PersianDateHelper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
internal fun InteractiveTgjuLineCanvas(
    points: List<TgjuHistoryPoint>,
    asset: TgjuChartAsset,
    lineColor: Color,
    accentColor: Color,
    selectedIndex: Int?,
    isWeeklyGranularity: Boolean,
    range: TgjuChartRange,
    onSelectIndex: (Int) -> Unit
) {
    val minPrice = remember(points) { points.minOf { it.price } }
    val maxPrice = remember(points) { points.maxOf { it.price } }
    val paddedMin = if (maxPrice == minPrice) minPrice * 0.98 else minPrice - (maxPrice - minPrice) * 0.08
    val paddedMax = if (maxPrice == minPrice) maxPrice * 1.02 else maxPrice + (maxPrice - minPrice) * 0.08
    val priceSpan = (paddedMax - paddedMin).coerceAtLeast(1e-6)

    val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val labelTextColor = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val midPrice = (paddedMin + paddedMax) / 2.0
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(top = 28.dp, bottom = 6.dp, end = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatCompactYPrice(paddedMax, asset),
                        fontFamily = OutfitFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatCompactYPrice(midPrice, asset),
                        fontFamily = OutfitFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = labelTextColor.copy(alpha = 0.85f),
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = formatCompactYPrice(paddedMin, asset),
                        fontFamily = OutfitFontFamily,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = labelTextColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 52.dp, top = 2.dp, bottom = 6.dp, end = 8.dp)
                        .pointerInput(points) {
                            detectTapGestures { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val frac = (offset.x / w).coerceIn(0f, 1f)
                                val idx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                                onSelectIndex(idx)
                            }
                        }
                        .pointerInput(points) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val frac = (change.position.x / w).coerceIn(0f, 1f)
                                val idx = (frac * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)
                                onSelectIndex(idx)
                            }
                        }
                        .testTag("tgju_line_chart_canvas")
                ) {
                    val w = size.width
                    val h = size.height
                    val plotTop = 26.dp.toPx()
                    val plotH = (h - plotTop).coerceAtLeast(1f)
                    if (w <= 0f || plotH <= 1f || points.size < 2) return@Canvas

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    for (step in 0..2) {
                        val y = plotTop + plotH * (step / 2f)
                        drawLine(
                            color = gridLineColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                    }

                    fun xFor(i: Int): Float = (i.toFloat() / (points.size - 1).toFloat()) * w
                    fun yFor(price: Double): Float {
                        val norm = ((price - paddedMin) / priceSpan).toFloat().coerceIn(0f, 1f)
                        return plotTop + plotH - (norm * plotH)
                    }

                    val linePath = Path()
                    val fillPath = Path()

                    points.forEachIndexed { i, pt ->
                        val x = xFor(i)
                        val y = yFor(pt.price)
                        if (i == 0) {
                            linePath.moveTo(x, y)
                            fillPath.moveTo(x, h)
                            fillPath.lineTo(x, y)
                        } else {
                            linePath.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }
                    fillPath.lineTo(xFor(points.lastIndex), h)
                    fillPath.close()

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(lineColor.copy(alpha = 0.26f), lineColor.copy(alpha = 0.01f)),
                            startY = plotTop,
                            endY = h
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = lineColor,
                        style = Stroke(
                            width = 2.4.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    if (points.size <= 14) {
                        points.forEachIndexed { i, pt ->
                            val cx = xFor(i)
                            val cy = yFor(pt.price)
                            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(cx, cy))
                            drawCircle(color = lineColor, radius = 2.6.dp.toPx(), center = Offset(cx, cy))
                        }
                    }

                    val activeIdx = selectedIndex ?: points.lastIndex
                    if (activeIdx in points.indices) {
                        val activePt = points[activeIdx]
                        val ax = xFor(activeIdx)
                        val ay = yFor(activePt.price)

                        val pillPriceText = formatCrosshairTopPrice(activePt.price, asset)
                        val textLayout = textMeasurer.measure(
                            text = pillPriceText,
                            style = TextStyle(
                                fontFamily = OutfitFontFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            ),
                            maxLines = 1
                        )

                        val padH = 6.dp.toPx()
                        val padV = 3.dp.toPx()
                        val pillW = textLayout.size.width + padH * 2f
                        val pillH = textLayout.size.height + padV * 2f
                        val pointerH = 4.dp.toPx()
                        val pointerHalfW = 4.dp.toPx()
                        val pillTopY = 0f
                        val pillBottomY = pillTopY + pillH
                        val pillLeftX = (ax - pillW / 2f).coerceIn(0f, (w - pillW).coerceAtLeast(0f))

                        drawLine(
                            color = accentColor.copy(alpha = 0.85f),
                            start = Offset(ax, pillBottomY + pointerH - 1.dp.toPx()),
                            end = Offset(ax, h),
                            strokeWidth = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )

                        drawRoundRect(
                            color = accentColor,
                            topLeft = Offset(pillLeftX, pillTopY),
                            size = Size(pillW, pillH),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        val clampedPointerX = ax.coerceIn(
                            pillLeftX + pointerHalfW + 2.dp.toPx(),
                            (pillLeftX + pillW - pointerHalfW - 2.dp.toPx()).coerceAtLeast(pillLeftX + pointerHalfW + 2.dp.toPx())
                        )
                        val pointerPath = Path().apply {
                            moveTo(clampedPointerX - pointerHalfW, pillBottomY - 0.5f)
                            lineTo(clampedPointerX + pointerHalfW, pillBottomY - 0.5f)
                            lineTo(ax.coerceIn(pillLeftX + 2.dp.toPx(), pillLeftX + pillW - 2.dp.toPx()), pillBottomY + pointerH)
                            close()
                        }
                        drawPath(path = pointerPath, color = accentColor, style = Fill)

                        drawText(
                            textLayoutResult = textLayout,
                            color = Color.White,
                            topLeft = Offset(pillLeftX + padH, pillTopY + padV)
                        )

                        drawCircle(color = accentColor.copy(alpha = 0.24f), radius = 10.dp.toPx(), center = Offset(ax, ay))
                        drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = Offset(ax, ay))
                        drawCircle(color = accentColor, radius = 3.8.dp.toPx(), center = Offset(ax, ay))
                    }
                }
            }

            HorizontalDivider(
                color = gridLineColor,
                modifier = Modifier.padding(start = 52.dp, end = 8.dp)
            )

            Spacer(modifier = Modifier.height(5.dp))

            val tickIndices = remember(points, isWeeklyGranularity) {
                buildXAxisTickIndices(points.size, maxTicks = if (isWeeklyGranularity) minOf(points.size, 7) else 5)
            }
            val activeIdx = selectedIndex ?: points.lastIndex

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tickIndices.forEach { idx ->
                    val pt = points[idx]
                    val isNearSelected = selectedIndex != null && abs(idx - activeIdx) == 0
                    val label = formatXAxisTickLabel(pt, isWeeklyGranularity, range)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isNearSelected) accentColor else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onSelectIndex(idx) }
                    ) {
                        Text(
                            text = enDigits(label),
                            fontSize = if (isWeeklyGranularity) 10.sp else 9.5.sp,
                            fontWeight = if (isNearSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isNearSelected) Color.White else labelTextColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (selectedIndex != null && activeIdx !in tickIndices && activeIdx in points.indices) {
                val touchedPt = points[activeIdx]
                val touchedLabel = if (isWeeklyGranularity) {
                    "${touchedPt.weekdayFa} ${touchedPt.monthDayFa}"
                } else {
                    "${touchedPt.weekdayFa} • ${touchedPt.shamsiDate}"
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor
                    ) {
                        Text(
                            text = enDigits(touchedLabel),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

internal fun buildXAxisTickIndices(size: Int, maxTicks: Int): List<Int> {
    if (size <= 0) return emptyList()
    if (size <= maxTicks) return (0 until size).toList()
    val ticks = LinkedHashSet<Int>()
    val count = maxTicks.coerceAtLeast(2)
    for (i in 0 until count) {
        val idx = ((i.toFloat() / (count - 1).toFloat()) * (size - 1)).roundToInt().coerceIn(0, size - 1)
        ticks.add(idx)
    }
    return ticks.toList()
}

internal fun formatXAxisTickLabel(
    point: TgjuHistoryPoint,
    isWeeklyGranularity: Boolean,
    range: TgjuChartRange
): String {
    return when {
        isWeeklyGranularity -> point.weekdayFa.ifBlank { point.shortWeekdayFa }
        range == TgjuChartRange.MONTH_1 -> point.monthDayFa
        range == TgjuChartRange.MONTH_6 -> PersianDateHelper.getMonthName(point.shamsiMonth).ifBlank { point.monthDayFa }
        range == TgjuChartRange.YEAR_1 -> {
            val mName = PersianDateHelper.getMonthName(point.shamsiMonth)
            val yrShort = (point.shamsiYear % 100).toString().padStart(2, '0')
            "$mName $yrShort"
        }
        else -> {
            val m = point.shamsiMonth.toString().padStart(2, '0')
            "${point.shamsiYear}/$m"
        }
    }
}

internal fun formatChartPrice(price: Double, asset: TgjuChartAsset): String {
    return when {
        asset.isUsdCurrency && asset.showDecimals -> "$" + String.format(Locale.US, "%,.2f", price)
        asset.isUsdCurrency -> "$" + String.format(Locale.US, "%,.0f", price)
        else -> PersianDateHelper.formatCurrency(price.roundToLong(), includeUnit = false)
    }
}

internal fun formatCrosshairTopPrice(price: Double, asset: TgjuChartAsset): String {
    val num = formatChartPrice(price, asset)
    return if (asset.isUsdCurrency) {
        num
    } else {
        "$num ${asset.unitFa}"
    }
}

internal fun formatCompactYPrice(price: Double, asset: TgjuChartAsset): String {
    return when {
        asset.isUsdCurrency && asset.showDecimals && price < 500.0 -> "$" + String.format(Locale.US, "%.1f", price)
        asset.isUsdCurrency && price >= 10_000.0 -> "$" + String.format(Locale.US, "%.0fK", price / 1_000.0)
        asset.isUsdCurrency -> "$" + String.format(Locale.US, "%,.0f", price)
        else -> {
            val millions = price / 1_000_000.0
            if (millions >= 1.0) {
                String.format(Locale.US, "%.1fM", millions)
            } else {
                PersianDateHelper.formatCurrency(price.roundToLong(), includeUnit = false)
            }
        }
    }
}
