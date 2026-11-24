package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MarketRateItem
import com.example.data.model.MarketRatesState
import com.example.ui.theme.LocalAppFontScale
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.utils.PersianDateHelper
import java.util.Locale

@Composable
fun MarketRatesCard(
    ratesState: MarketRatesState,
    onRefresh: () -> Unit,
    onRefreshTetherOnly: () -> Unit = onRefresh,
    modifier: Modifier = Modifier
) {
    var activeTgjuHistoryAsset by remember { mutableStateOf<Pair<com.example.data.network.TgjuChartAsset, Double?>?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, Color(0xFFF59E0B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(scaledDp(14f))) {
            // Header: Title, Live Indicator, and Refresh Button
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
                        color = Color(0xFFFEF3C7),
                        modifier = Modifier.size(scaledDp(36f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CurrencyExchange,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(scaledDp(19f))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = enDigits("قیمت روز ارز، طلا و سکه"),
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = appTextColor(MaterialTheme.colorScheme.onSurface)
                        )
                        val displayUpdateTime = ratesState.lastUpdateTime.trim()
                        if (displayUpdateTime.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "به‌روزرسانی:",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appTextColor(MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    softWrap = false
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = PersianDateHelper.toEnglishDigits(displayUpdateTime),
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = appTextColor(MaterialTheme.colorScheme.onSurface),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "نرخ لحظه‌ای بازار",
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("refresh_market_rates_button")
                ) {
                    if (ratesState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "به‌روزرسانی قیمت‌ها",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!ratesState.errorMessage.isNullOrBlank() && ratesState.dollar.priceTomans <= 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRefresh() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "خطا در اتصال؛ برای دریافت مجدد لمس کنید",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "تلاش مجدد",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1: Dollar and Euro
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRateColumnItem(
                    item = ratesState.dollar,
                    symbol = "$",
                    badgeColor = Color(0xFF047857),
                    gradientColors = listOf(Color(0xFFE8FAF2), Color(0xFFD1F4E6)),
                    borderColor = Color(0xFF6EE7B7),
                    titleColor = Color(0xFF065F46),
                    chartTestTag = "market_rate_dollar_chart",
                    onChartClick = {
                        val live = ratesState.dollar.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.DOLLAR to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                MarketRateColumnItem(
                    item = ratesState.euro,
                    symbol = "€",
                    badgeColor = Color(0xFF1D4ED8),
                    gradientColors = listOf(Color(0xFFEEF5FF), Color(0xFFDBEAFE)),
                    borderColor = Color(0xFF93C5FD),
                    titleColor = Color(0xFF1E40AF),
                    chartTestTag = "market_rate_euro_chart",
                    onChartClick = {
                        val live = ratesState.euro.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.EURO to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 2: Sekeh Emami and Rob Sekeh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRateColumnItem(
                    item = ratesState.sekehEmami,
                    symbol = "",
                    badgeColor = Color(0xFFD97706),
                    gradientColors = listOf(Color(0xFFFFFBEB), Color(0xFFFDE68A)),
                    borderColor = Color(0xFFFBBF24),
                    titleColor = Color(0xFF854D0E),
                    chartTestTag = "market_rate_sekeh_emami_chart",
                    onChartClick = {
                        val live = ratesState.sekehEmami.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.SEKEH_EMAMI to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                MarketRateColumnItem(
                    item = ratesState.robSekeh,
                    symbol = "",
                    badgeColor = Color(0xFFE11D48),
                    gradientColors = listOf(Color(0xFFFFF1F2), Color(0xFFFFE4E6)),
                    borderColor = Color(0xFFFDA4AF),
                    titleColor = Color(0xFF9F1239),
                    chartTestTag = "market_rate_rob_sekeh_chart",
                    onChartClick = {
                        val live = ratesState.robSekeh.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.ROB_SEKEH to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 3: Gold Ounce and 18k Gold
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRateColumnItem(
                    item = ratesState.goldOunce,
                    symbol = "",
                    badgeColor = Color(0xFF7C3AED),
                    gradientColors = listOf(Color(0xFFF5F3FF), Color(0xFFEDE9FE)),
                    borderColor = Color(0xFFC4B5FD),
                    titleColor = Color(0xFF5B21B6),
                    chartTestTag = "market_rate_gold_ounce_chart",
                    onChartClick = {
                        val liveStr = ratesState.goldOunce.displayPriceFormatted
                            ?.replace("$", "")?.replace(",", "")?.trim()
                        val live = liveStr?.toDoubleOrNull()
                            ?: ratesState.goldOunce.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.GOLD_OUNCE to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                MarketRateColumnItem(
                    item = ratesState.gold18k,
                    symbol = "",
                    badgeColor = Color(0xFFCA8A04),
                    gradientColors = listOf(Color(0xFFFEFCE8), Color(0xFFFEF08A)),
                    borderColor = Color(0xFFFACC15),
                    titleColor = Color(0xFF854D0E),
                    chartTestTag = "market_rate_gold_18k_chart",
                    onChartClick = {
                        val live = ratesState.gold18k.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.GOLD_18K to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 4: Bitcoin and Tether USDT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRateColumnItem(
                    item = ratesState.bitcoin,
                    symbol = "",
                    badgeColor = Color(0xFFEA580C),
                    gradientColors = listOf(Color(0xFFFFF7ED), Color(0xFFFFEDD5)),
                    borderColor = Color(0xFFFDBA74),
                    titleColor = Color(0xFF9A3412),
                    chartTestTag = "market_rate_bitcoin_chart",
                    onChartClick = {
                        val liveStr = ratesState.bitcoin.displayPriceFormatted
                            ?.replace("$", "")?.replace(",", "")?.replace("دلار", "")?.trim()
                        val live = liveStr?.let { PersianDateHelper.toEnglishDigits(it).toDoubleOrNull() }
                            ?: ratesState.bitcoin.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.BITCOIN to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                MarketRateColumnItem(
                    item = ratesState.tether,
                    symbol = "₮",
                    badgeColor = Color(0xFF0D9488),
                    gradientColors = listOf(Color(0xFFE6FFFA), Color(0xFFCCFBF1)),
                    borderColor = Color(0xFF2DD4BF),
                    titleColor = Color(0xFF0F766E),
                    chartTestTag = null,
                    onChartClick = null,
                    isLoading = ratesState.isLoading || ratesState.isTetherRefreshing,
                    onRetryClick = onRefreshTetherOnly,
                    onItemRefreshClick = onRefreshTetherOnly,
                    isItemRefreshing = ratesState.isTetherRefreshing,
                    itemRefreshTestTag = "refresh_tether_only_button",
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 5: GRAM Cryptocurrency (Nobitex) and Brent Crude Oil
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRateColumnItem(
                    item = ratesState.gram,
                    symbol = "",
                    badgeColor = Color(0xFF0284C7),
                    gradientColors = listOf(Color(0xFFF0F9FF), Color(0xFFE0F2FE)),
                    borderColor = Color(0xFF7DD3FC),
                    titleColor = Color(0xFF0369A1),
                    chartTestTag = "market_rate_gram_chart",
                    onChartClick = {
                        val liveStr = ratesState.gram.displayPriceFormatted
                            ?.replace("$", "")?.replace(",", "")?.replace("دلار", "")?.trim()
                        val live = liveStr?.let { PersianDateHelper.toEnglishDigits(it).toDoubleOrNull() }
                            ?: ratesState.gram.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.GRAM to live
                    },
                    isLoading = ratesState.isLoading,
                    onRetryClick = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                MarketRateColumnItem(
                    item = ratesState.crudeOil,
                    symbol = "",
                    badgeColor = Color(0xFF0F766E),
                    gradientColors = listOf(Color(0xFFF0FDFA), Color(0xFFCCFBF1)),
                    borderColor = Color(0xFF5EEAD4),
                    titleColor = Color(0xFF115E59),
                    chartTestTag = "market_rate_brent_oil_chart",
                    onChartClick = {
                        val liveStr = ratesState.crudeOil.displayPriceFormatted
                            ?.replace("$", "")?.replace(",", "")?.replace("دلار", "")?.trim()
                        val live = liveStr?.let { PersianDateHelper.toEnglishDigits(it).toDoubleOrNull() }
                            ?: ratesState.crudeOil.priceTomans.takeIf { it > 0L }?.toDouble()
                        activeTgjuHistoryAsset = com.example.data.network.TgjuChartAsset.BRENT_OIL to live
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        }
    }

    activeTgjuHistoryAsset?.let { (asset, livePrice) ->
        TgjuHistoricalChartDialog(
            asset = asset,
            liveLatestPrice = livePrice,
            onDismiss = { activeTgjuHistoryAsset = null }
        )
    }
}

@Composable
private fun MarketRateColumnItem(
    item: MarketRateItem,
    symbol: String,
    badgeColor: Color,
    gradientColors: List<Color>,
    borderColor: Color,
    titleColor: Color,
    modifier: Modifier = Modifier,
    chartTestTag: String? = null,
    onChartClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    onRetryClick: (() -> Unit)? = null,
    onItemRefreshClick: (() -> Unit)? = null,
    isItemRefreshing: Boolean = false,
    itemRefreshTestTag: String? = null
) {
    val uiScale = LocalAppFontScale.current
    val isGreen = item.isPositiveChange
    val changeColor = if (isGreen) Color(0xFF065F46) else Color(0xFF991B1B)
    val changeBg = if (isGreen) Color(0xFFD1FAE5) else Color(0xFFFEE2E2)
    val changeBorder = if (isGreen) Color(0xFF10B981) else Color(0xFFEF4444)
    val isPendingSource = item.priceTomans <= 0L && item.displayPriceFormatted.isNullOrBlank()

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = scaledDp(96f))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(gradientColors))
            .border(BorderStroke(1.3.dp, borderColor), RoundedCornerShape(14.dp))
            .then(
                when {
                    onChartClick != null -> Modifier
                        .then(if (chartTestTag != null) Modifier.testTag(chartTestTag) else Modifier)
                        .clickable { onChartClick() }
                    isPendingSource && !isLoading && onRetryClick != null -> Modifier
                        .clickable { onRetryClick() }
                    else -> Modifier
                }
            )
            .padding(horizontal = scaledDp(8f), vertical = (8f * uiScale).coerceIn(6f, 12f).dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Title + optional Chart pill (kept cleanly separated)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (symbol.isNotBlank()) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = symbol,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 13.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = enDigits(item.title),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = titleColor,
                            maxLines = 2,
                            softWrap = true
                        )
                    }

                    if (onChartClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeColor.copy(alpha = 0.16f),
                            border = BorderStroke(0.9.dp, badgeColor.copy(alpha = 0.55f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = "مشاهده چارت ${item.title}",
                                    tint = badgeColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "نمودار",
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = badgeColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else if (onItemRefreshClick != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = badgeColor.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .then(if (itemRefreshTestTag != null) Modifier.testTag(itemRefreshTestTag) else Modifier)
                                .clickable(enabled = !isItemRefreshing) { onItemRefreshClick() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (isItemRefreshing) {
                                    CircularProgressIndicator(
                                        color = badgeColor,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "به‌روزرسانی مستقل ${item.title}",
                                        tint = badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Row 2: Live Price — strictly separated LTR numeric value & Persian unit
                if (isPendingSource) {
                    Text(
                        text = if (isLoading) "در حال دریافت..." else "نامشخص · تلاش مجدد",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLoading) Color(0xFF475569) else Color(0xFFB91C1C),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val priceNumberStr = when {
                        item.unitLabel == "دلار" -> {
                            val raw = item.displayPriceFormatted
                                ?.replace("دلار", "")
                                ?.replace("\u00A0", "")
                                ?.trim()
                            if (!raw.isNullOrBlank()) {
                                val normalized = PersianDateHelper.toEnglishDigits(raw)
                                if (normalized.startsWith("$")) normalized else "$$normalized"
                            } else {
                                "$" + PersianDateHelper.formatCurrency(item.priceTomans, includeUnit = false)
                            }
                        }
                        item.unitLabel == "واحد" -> {
                            val raw = item.displayPriceFormatted
                                ?.replace("واحد", "")
                                ?.replace("\u00A0", "")
                                ?.trim()
                            if (!raw.isNullOrBlank()) {
                                PersianDateHelper.toEnglishDigits(raw)
                            } else {
                                PersianDateHelper.formatCurrency(item.priceTomans, includeUnit = false)
                            }
                        }
                        else -> PersianDateHelper.formatCurrency(item.priceTomans, includeUnit = false)
                    }

                    val priceUnitStr = when (item.unitLabel) {
                        "دلار" -> ""
                        "واحد" -> "واحد"
                        else -> "تومان"
                    }

                    val numFontSize = when {
                        priceNumberStr.length >= 11 -> 12.sp
                        priceNumberStr.length >= 9 -> 13.sp
                        else -> 14.sp
                    }

                    val stackPriceUnit = priceUnitStr.isNotEmpty() &&
                        ((uiScale >= 1.16f && priceNumberStr.length >= 8) || priceNumberStr.length >= 11)

                    if (stackPriceUnit) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = priceNumberStr,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = numFontSize,
                                    lineHeight = (numFontSize.value + 3f).sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = priceUnitStr,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF334155),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = priceNumberStr,
                                    fontFamily = OutfitFontFamily,
                                    fontSize = numFontSize,
                                    lineHeight = (numFontSize.value + 3f).sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (priceUnitStr.isNotEmpty()) {
                                Text(
                                    text = priceUnitStr,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF334155),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Row 3: Bottom Increase / Decrease Amount Pill — strictly separated words & LTR numbers
            val isLoaded = item.priceTomans > 0L || !item.displayPriceFormatted.isNullOrBlank()
            if (isLoaded) {
                Spacer(modifier = Modifier.height(6.dp))

                val cleanDiffNumber = when (item.unitLabel) {
                    "دلار" -> {
                        val raw = item.displayDiffFormatted
                            ?.replace("+", "")
                            ?.replace("-", "")
                            ?.replace("دلار", "")
                            ?.replace("\u00A0", "")
                            ?.trim()
                        if (!raw.isNullOrBlank()) {
                            val norm = PersianDateHelper.toEnglishDigits(raw)
                            if (norm.startsWith("$")) norm else "$$norm"
                        } else if (item.changeTomans > 0L) {
                            "$" + PersianDateHelper.formatCurrency(item.changeTomans, includeUnit = false)
                        } else {
                            ""
                        }
                    }
                    "واحد" -> {
                        val raw = item.displayDiffFormatted
                            ?.replace("+", "")
                            ?.replace("-", "")
                            ?.replace("واحد", "")
                            ?.replace("\u00A0", "")
                            ?.trim()
                        if (!raw.isNullOrBlank()) {
                            PersianDateHelper.toEnglishDigits(raw)
                        } else if (item.changeTomans > 0L) {
                            PersianDateHelper.formatCurrency(item.changeTomans, includeUnit = false)
                        } else {
                            ""
                        }
                    }
                    else -> {
                        if (item.changeTomans > 0L) {
                            PersianDateHelper.formatCurrency(item.changeTomans, includeUnit = false)
                        } else {
                            ""
                        }
                    }
                }

                val diffUnitStr = when (item.unitLabel) {
                    "دلار" -> ""
                    "واحد" -> "واحد"
                    else -> "تومان"
                }

                val hasAmountDiff = cleanDiffNumber.isNotEmpty()
                val hasPercent = item.changePercent >= 0.005
                val hasAnyDelta = hasAmountDiff || hasPercent
                val sign = if (isGreen) "+" else "-"
                val percentText = if (hasPercent) {
                    "$sign${String.format(Locale.US, "%.2f", item.changePercent)}%"
                } else {
                    ""
                }

                if (!hasAnyDelta) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Text(
                            text = "بدون تغییر",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = scaledDp(6f), vertical = scaledDp(3f))
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = changeBg,
                        border = BorderStroke(1.dp, changeBorder)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = scaledDp(6f), vertical = scaledDp(4f)),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Top line of change badge: Direction word ("افزایش"/"کاهش") + optional percentage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGreen) {
                                            Icons.AutoMirrored.Filled.TrendingUp
                                        } else {
                                            Icons.AutoMirrored.Filled.TrendingDown
                                        },
                                        contentDescription = if (isGreen) "افزایش قیمت" else "کاهش قیمت",
                                        tint = changeColor,
                                        modifier = Modifier.size(scaledDp(12f))
                                    )
                                    Text(
                                        text = if (isGreen) "افزایش" else "کاهش",
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = changeColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }

                                if (percentText.isNotEmpty()) {
                                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                        Text(
                                            text = percentText,
                                            fontFamily = OutfitFontFamily,
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = changeColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            // Bottom line of change badge: Exact numeric delta + separate unit label
                            if (hasAmountDiff) {
                                val signedDiff = "$sign$cleanDiffNumber"
                                val diffFontSize = if (signedDiff.length >= 10) 10.sp else 11.sp
                                val stackDiffUnit = diffUnitStr.isNotEmpty() &&
                                    ((uiScale >= 1.16f && signedDiff.length >= 8) || signedDiff.length >= 11)

                                if (stackDiffUnit) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = signedDiff,
                                                fontFamily = OutfitFontFamily,
                                                fontSize = diffFontSize,
                                                lineHeight = (diffFontSize.value + 3f).sp,
                                                fontWeight = FontWeight.Black,
                                                color = changeColor,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = diffUnitStr,
                                            fontSize = 9.5.sp,
                                            lineHeight = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = changeColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = signedDiff,
                                                fontFamily = OutfitFontFamily,
                                                fontSize = diffFontSize,
                                                lineHeight = (diffFontSize.value + 3f).sp,
                                                fontWeight = FontWeight.Black,
                                                color = changeColor,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (diffUnitStr.isNotEmpty()) {
                                            Text(
                                                text = diffUnitStr,
                                                fontSize = 9.5.sp,
                                                lineHeight = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = changeColor,
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
