package com.example.data.network

import com.example.data.model.MarketRateItem
import com.example.utils.PersianDateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Isolated public market data extractor for Nobitex (`https://nobitex.ir/price/gram`).
 *
 * Extracts live rates for:
 * - Tether (USDT / `usdt-rls` / `USDTIRT`) in **Tomans** (Rials ÷ 10)
 * - GRAM (`gram-usdt` / `GRAMUSDT`, with `gram-rls ÷ usdt-rls` cross-rate fallback) in **USD (دلار)**
 *
 * Uses Nobitex's official public REST endpoints (`apiv2.nobitex.ir` & `api.nobitex.ir`) with
 * direct `https://nobitex.ir/price/gram` HTML/SSR fallback.
 */
object NobitexMarketService {

    const val GRAM_PAGE_URL = "https://nobitex.ir/price/gram"
    const val USDT_PAGE_URL = "https://nobitex.ir/price/usdt"
    const val SOURCE_LABEL = "Nobitex (nobitex.ir/price/gram)"

    private val API_HOSTS = listOf(
        "https://apiv2.nobitex.ir",
        "https://api.nobitex.ir"
    )

    private val HTML_TAG_REGEX = Regex("<[^>]*>")
    private val USD_PREFIX_REGEX = Regex("""\$\s*([0-9][0-9,]*(?:\.[0-9]+)?)""")
    private val USD_SUFFIX_REGEX = Regex(
        """([0-9][0-9,]*(?:\.[0-9]+)?)\s*(?:دلار|USDT|USD|تتر)""",
        RegexOption.IGNORE_CASE
    )

    data class NobitexExtractedRates(
        val tether: MarketRateItem? = null,
        val gram: MarketRateItem? = null
    ) {
        fun hasAny(): Boolean = tether != null || gram != null
        fun isComplete(): Boolean = tether != null && gram != null
        fun mergeMissing(other: NobitexExtractedRates): NobitexExtractedRates = NobitexExtractedRates(
            tether = tether ?: other.tether,
            gram = gram ?: other.gram
        )
    }

    /**
     * Fetches Tether (USDT in Tomans) and GRAM (in USD / دلار) from Nobitex.
     */
    suspend fun fetchTetherAndGram(
        previousTetherTomans: Long = 0L,
        previousGramUsd: Double = 0.0
    ): NobitexExtractedRates = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var merged = NobitexExtractedRates()

        // 1. Primary: Official Nobitex /market/stats (usdt-rls + gram-usdt + gram-rls cross-rate)
        for (host in API_HOSTS) {
            if (merged.isComplete()) break
            val combinedQuery = "srcCurrency=usdt,gram&dstCurrency=rls,usdt"
            runCatching {
                val getJson = downloadGet("$host/market/stats?$combinedQuery&_t=$now", GRAM_PAGE_URL)
                if (!getJson.isNullOrBlank()) {
                    merged = merged.mergeMissing(parseStatsJson(getJson, now))
                }
            }
            if (merged.isComplete()) break
            runCatching {
                val postJson = downloadPostForm("$host/market/stats", combinedQuery, GRAM_PAGE_URL)
                if (!postJson.isNullOrBlank()) {
                    merged = merged.mergeMissing(parseStatsJson(postJson, now))
                }
            }
        }

        // 1b. Dedicated single-pair /market/stats calls if either market is still missing
        if (merged.gram == null) {
            for (host in API_HOSTS) {
                if (merged.gram != null) break
                val query = "srcCurrency=gram&dstCurrency=usdt"
                runCatching {
                    val json = downloadGet("$host/market/stats?$query&_t=$now", GRAM_PAGE_URL)
                    if (!json.isNullOrBlank()) {
                        parseStatsJson(json, now).gram?.let { merged = merged.copy(gram = it) }
                    }
                }
            }
        }
        if (merged.tether == null) {
            for (host in API_HOSTS) {
                if (merged.tether != null) break
                val query = "srcCurrency=usdt&dstCurrency=rls"
                runCatching {
                    val json = downloadGet("$host/market/stats?$query&_t=$now", GRAM_PAGE_URL)
                    if (!json.isNullOrBlank()) {
                        parseStatsJson(json, now).tether?.let { merged = merged.copy(tether = it) }
                    }
                }
            }
        }

        // 2. Secondary: Official Nobitex public orderbook endpoints (v3 then v2)
        if (merged.tether == null) {
            merged = merged.copy(
                tether = fetchOrderbookTomanRate(
                    symbol = "USDTIRT",
                    key = "nobitex-usdt",
                    title = "تتر (USDT)",
                    minTomans = 10_000L,
                    previousTomans = previousTetherTomans,
                    nowMs = now
                )
            )
        }
        if (merged.gram == null) {
            merged = merged.copy(
                gram = fetchOrderbookUsdRate(
                    symbol = "GRAMUSDT",
                    key = "nobitex-gram",
                    title = "گرام (GRAM)",
                    previousUsd = previousGramUsd,
                    nowMs = now
                )
            )
        }

        // 3. Cross-rate fallback for GRAM in USD via GRAMIRT ÷ USDTIRT if GRAMUSDT was unavailable
        if (merged.gram == null) {
            val usdtToman = merged.tether?.priceTomans?.takeIf { it > 0L } ?: previousTetherTomans
            if (usdtToman > 0L) {
                val gramTomanItem = fetchOrderbookTomanRate(
                    symbol = "GRAMIRT",
                    key = "nobitex-gram",
                    title = "گرام (GRAM)",
                    minTomans = 10L,
                    previousTomans = 0L,
                    nowMs = now
                )
                if (gramTomanItem != null && gramTomanItem.priceTomans > 0L) {
                    val gramUsd = gramTomanItem.priceTomans.toDouble() / usdtToman.toDouble()
                    merged = merged.copy(
                        gram = buildUsdRateItem(
                            key = "nobitex-gram",
                            title = "گرام (GRAM)",
                            priceUsd = gramUsd,
                            diffUsd = if (previousGramUsd > 0.0) abs(gramUsd - previousGramUsd) else 0.0,
                            changePct = if (previousGramUsd > 0.0) abs((gramUsd - previousGramUsd) / previousGramUsd) * 100.0 else 0.0,
                            isPositive = previousGramUsd <= 0.0 || gramUsd >= previousGramUsd,
                            nowMs = now
                        )
                    )
                }
            }
        }

        // 4. Direct HTML / embedded SSR JSON fallback from https://nobitex.ir/price/gram (and /price/usdt)
        if (!merged.isComplete()) {
            val refUsdt = merged.tether?.priceTomans?.takeIf { it > 0L } ?: previousTetherTomans
            runCatching {
                val gramHtml = downloadGet("$GRAM_PAGE_URL?_t=$now", "https://nobitex.ir/")
                if (!gramHtml.isNullOrBlank()) {
                    if (merged.tether == null) {
                        parseTetherFromPageHtml(gramHtml, now)?.let { merged = merged.copy(tether = it) }
                    }
                    if (merged.gram == null) {
                        val effectiveUsdt = merged.tether?.priceTomans?.takeIf { it > 0L } ?: refUsdt
                        parseGramFromPageHtml(gramHtml, effectiveUsdt, now)?.let { merged = merged.copy(gram = it) }
                    }
                }
            }
        }
        if (merged.tether == null) {
            runCatching {
                val usdtHtml = downloadGet("$USDT_PAGE_URL?_t=$now", GRAM_PAGE_URL)
                if (!usdtHtml.isNullOrBlank()) {
                    parseTetherFromPageHtml(usdtHtml, now)?.let { merged = merged.copy(tether = it) }
                }
            }
        }

        merged
    }

    /**
     * Ultra-fast dedicated refresh path for Tether (USDT) ONLY from Nobitex (in Tomans).
     */
    suspend fun fetchTetherOnly(previousTetherTomans: Long = 0L): MarketRateItem? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // 1. Primary: Nobitex /market/stats for usdt-rls
        for (host in API_HOSTS) {
            val query = "srcCurrency=usdt&dstCurrency=rls"
            runCatching {
                val getJson = downloadGet("$host/market/stats?$query&_t=$now", GRAM_PAGE_URL)
                if (!getJson.isNullOrBlank()) {
                    parseStatsJson(getJson, now).tether?.let { return@withContext it }
                }
            }
            runCatching {
                val postJson = downloadPostForm("$host/market/stats", query, GRAM_PAGE_URL)
                if (!postJson.isNullOrBlank()) {
                    parseStatsJson(postJson, now).tether?.let { return@withContext it }
                }
            }
        }

        // 2. Secondary: Nobitex orderbook USDTIRT
        fetchOrderbookTomanRate(
            symbol = "USDTIRT",
            key = "nobitex-usdt",
            title = "تتر (USDT)",
            minTomans = 10_000L,
            previousTomans = previousTetherTomans,
            nowMs = now
        )?.let { return@withContext it }

        // 3. Direct HTML fallback from nobitex.ir/price/gram and nobitex.ir/price/usdt
        for (pageUrl in listOf(GRAM_PAGE_URL, USDT_PAGE_URL)) {
            runCatching {
                val html = downloadGet("$pageUrl?_t=$now", "https://nobitex.ir/")
                if (!html.isNullOrBlank()) {
                    parseTetherFromPageHtml(html, now)?.let { return@withContext it }
                }
            }
        }

        null
    }

    private fun fetchOrderbookTomanRate(
        symbol: String,
        key: String,
        title: String,
        minTomans: Long,
        previousTomans: Long,
        nowMs: Long
    ): MarketRateItem? {
        for (host in API_HOSTS) {
            for (version in listOf("v3", "v2")) {
                runCatching {
                    val json = downloadGet("$host/$version/orderbook/$symbol?_t=$nowMs", GRAM_PAGE_URL)
                    if (!json.isNullOrBlank()) {
                        parseOrderbookJson(
                            jsonText = json,
                            key = key,
                            title = title,
                            minTomans = minTomans,
                            previousTomans = previousTomans,
                            nowMs = nowMs
                        )?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun fetchOrderbookUsdRate(
        symbol: String,
        key: String,
        title: String,
        previousUsd: Double,
        nowMs: Long
    ): MarketRateItem? {
        for (host in API_HOSTS) {
            for (version in listOf("v3", "v2")) {
                runCatching {
                    val json = downloadGet("$host/$version/orderbook/$symbol?_t=$nowMs", GRAM_PAGE_URL)
                    if (!json.isNullOrBlank()) {
                        parseOrderbookUsdJson(
                            jsonText = json,
                            key = key,
                            title = title,
                            previousUsd = previousUsd,
                            nowMs = nowMs
                        )?.let { return it }
                    }
                }
            }
        }
        return null
    }

    /**
     * Parses Nobitex `/market/stats` JSON response:
     * - `usdt-rls`: Tether in Tomans (Rials ÷ 10)
     * - `gram-usdt` (or cross-rate `gram-rls ÷ usdt-rls`): GRAM in USD (دلار)
     */
    fun parseStatsJson(jsonText: String, nowMs: Long = System.currentTimeMillis()): NobitexExtractedRates {
        if (jsonText.isBlank()) return NobitexExtractedRates()
        val statsBody = extractJsonObjectBody(jsonText, "stats") ?: return NobitexExtractedRates()

        val tetherBody = extractJsonObjectBody(statsBody, "usdt-rls")
        val gramUsdBody = extractJsonObjectBody(statsBody, "gram-usdt")
            ?: extractJsonObjectBody(statsBody, "1000gram-usdt")
        val gramRlsBody = extractJsonObjectBody(statsBody, "gram-rls")
            ?: extractJsonObjectBody(statsBody, "1000gram-rls")

        val tether = tetherBody?.let {
            buildTomanItemFromStatsBody(
                objBody = it,
                key = "nobitex-usdt",
                title = "تتر (USDT)",
                minTomans = 10_000L,
                nowMs = nowMs
            )
        }

        val gram = gramUsdBody?.let {
            buildUsdItemFromStatsBody(
                objBody = it,
                key = "nobitex-gram",
                title = "گرام (GRAM)",
                nowMs = nowMs
            )
        } ?: if (gramRlsBody != null && tether != null && tether.priceTomans > 0L) {
            buildUsdItemFromRlsCrossRate(
                gramRlsBody = gramRlsBody,
                usdtPriceTomans = tether.priceTomans,
                nowMs = nowMs
            )
        } else {
            null
        }

        return NobitexExtractedRates(tether = tether, gram = gram)
    }

    private fun buildUsdItemFromRlsCrossRate(
        gramRlsBody: String,
        usdtPriceTomans: Long,
        nowMs: Long
    ): MarketRateItem? {
        if (usdtPriceTomans <= 0L) return null
        if (extractJsonField(gramRlsBody, "isClosed").equals("true", ignoreCase = true)) return null
        val gramRials = parseDouble(extractJsonField(gramRlsBody, "latest"))
            ?: parseDouble(extractJsonField(gramRlsBody, "bestSell"))
            ?: parseDouble(extractJsonField(gramRlsBody, "bestBuy"))
            ?: return null
        if (gramRials <= 0.0) return null

        val gramTomans = gramRials / 10.0
        val priceUsd = gramTomans / usdtPriceTomans.toDouble()
        if (priceUsd <= 0.0) return null

        val dayChangePct = parseDouble(extractJsonField(gramRlsBody, "dayChange")) ?: 0.0
        val dayOpenRials = parseDouble(extractJsonField(gramRlsBody, "dayOpen"))
        val rawDiffUsd = if (dayOpenRials != null && dayOpenRials > 0.0) {
            ((gramRials - dayOpenRials) / 10.0) / usdtPriceTomans.toDouble()
        } else if (abs(dayChangePct) > 0.0) {
            priceUsd * (dayChangePct / 100.0)
        } else {
            0.0
        }
        val isPositive = when {
            dayChangePct > 0.0 -> true
            dayChangePct < 0.0 -> false
            else -> rawDiffUsd >= 0.0
        }
        val absPct = if (abs(dayChangePct) > 0.0) {
            abs(dayChangePct)
        } else if (dayOpenRials != null && dayOpenRials > 0.0) {
            abs((gramRials - dayOpenRials) / dayOpenRials) * 100.0
        } else {
            0.0
        }

        return buildUsdRateItem(
            key = "nobitex-gram",
            title = "گرام (GRAM)",
            priceUsd = priceUsd,
            diffUsd = abs(rawDiffUsd),
            changePct = absPct,
            isPositive = isPositive,
            nowMs = nowMs
        )
    }

    private fun buildUsdItemFromStatsBody(
        objBody: String,
        key: String,
        title: String,
        nowMs: Long
    ): MarketRateItem? {
        if (extractJsonField(objBody, "isClosed").equals("true", ignoreCase = true)) return null
        val latestUsd = parseDouble(extractJsonField(objBody, "latest"))
            ?: parseDouble(extractJsonField(objBody, "bestSell"))
            ?: parseDouble(extractJsonField(objBody, "bestBuy"))
            ?: return null
        if (latestUsd <= 0.0) return null

        val dayOpenUsd = parseDouble(extractJsonField(objBody, "dayOpen"))
        val dayChangePct = parseDouble(extractJsonField(objBody, "dayChange")) ?: 0.0

        val rawDiffUsd = if (dayOpenUsd != null && dayOpenUsd > 0.0) {
            latestUsd - dayOpenUsd
        } else if (abs(dayChangePct) > 0.0) {
            latestUsd * (dayChangePct / 100.0)
        } else {
            0.0
        }

        val isPositive = when {
            dayChangePct > 0.0 -> true
            dayChangePct < 0.0 -> false
            else -> rawDiffUsd >= 0.0
        }
        val absPct = if (abs(dayChangePct) > 0.0) {
            abs(dayChangePct)
        } else if (dayOpenUsd != null && dayOpenUsd > 0.0) {
            abs((latestUsd - dayOpenUsd) / dayOpenUsd) * 100.0
        } else {
            0.0
        }

        return buildUsdRateItem(
            key = key,
            title = title,
            priceUsd = latestUsd,
            diffUsd = abs(rawDiffUsd),
            changePct = absPct,
            isPositive = isPositive,
            nowMs = nowMs
        )
    }

    private fun buildTomanItemFromStatsBody(
        objBody: String,
        key: String,
        title: String,
        minTomans: Long,
        nowMs: Long
    ): MarketRateItem? {
        if (extractJsonField(objBody, "isClosed").equals("true", ignoreCase = true)) return null
        val latestRials = parseDouble(extractJsonField(objBody, "latest"))
            ?: parseDouble(extractJsonField(objBody, "bestSell"))
            ?: parseDouble(extractJsonField(objBody, "bestBuy"))
            ?: return null
        if (latestRials <= 0.0) return null

        val priceTomans = (latestRials / 10.0).roundToLong()
        if (priceTomans < minTomans) return null

        val dayOpenRials = parseDouble(extractJsonField(objBody, "dayOpen"))
        val dayChangePct = parseDouble(extractJsonField(objBody, "dayChange")) ?: 0.0

        val rawDiffRials = if (dayOpenRials != null && dayOpenRials > 0.0) {
            latestRials - dayOpenRials
        } else if (abs(dayChangePct) > 0.0) {
            latestRials * (dayChangePct / 100.0)
        } else {
            0.0
        }

        val isPositive = when {
            dayChangePct > 0.0 -> true
            dayChangePct < 0.0 -> false
            else -> rawDiffRials >= 0.0
        }
        val absDiffTomans = (abs(rawDiffRials) / 10.0).roundToLong()
        val absPct = if (abs(dayChangePct) > 0.0) {
            abs(dayChangePct)
        } else if (dayOpenRials != null && dayOpenRials > 0.0) {
            abs((latestRials - dayOpenRials) / dayOpenRials) * 100.0
        } else {
            0.0
        }

        return MarketRateItem(
            key = key,
            title = title,
            priceTomans = priceTomans,
            changePercent = absPct,
            changeTomans = absDiffTomans,
            isPositiveChange = isPositive,
            timeFormatted = PersianDateHelper.getTimeFormatted(nowMs),
            unitLabel = "تومان",
            displayPriceFormatted = null,
            displayDiffFormatted = null,
            sourceLabel = SOURCE_LABEL
        )
    }

    /**
     * Parses Nobitex `/v2/orderbook/{SYMBOL}` or `/v3/orderbook/{SYMBOL}` JSON for `*IRT` pairs
     * (returned in Iranian Rials, converted ÷ 10 -> Tomans).
     */
    fun parseOrderbookJson(
        jsonText: String,
        key: String,
        title: String,
        minTomans: Long = 10L,
        previousTomans: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): MarketRateItem? {
        val priceRials = extractOrderbookPriceValue(jsonText) ?: return null
        val priceTomans = (priceRials / 10.0).roundToLong()
        if (priceTomans < minTomans) return null

        val diffTomans = if (previousTomans > 0L) abs(priceTomans - previousTomans) else 0L
        val pct = if (previousTomans > 0L) (diffTomans.toDouble() / previousTomans.toDouble()) * 100.0 else 0.0
        val isPositive = previousTomans <= 0L || priceTomans >= previousTomans

        return MarketRateItem(
            key = key,
            title = title,
            priceTomans = priceTomans,
            changePercent = pct,
            changeTomans = diffTomans,
            isPositiveChange = isPositive,
            timeFormatted = PersianDateHelper.getTimeFormatted(nowMs),
            unitLabel = "تومان",
            displayPriceFormatted = null,
            displayDiffFormatted = null,
            sourceLabel = SOURCE_LABEL
        )
    }

    /**
     * Parses Nobitex `/v2/orderbook/{SYMBOL}` or `/v3/orderbook/{SYMBOL}` JSON for `*USDT` pairs
     * (e.g. `GRAMUSDT`), where prices are already denominated in USD (no ÷ 10).
     */
    fun parseOrderbookUsdJson(
        jsonText: String,
        key: String,
        title: String,
        previousUsd: Double = 0.0,
        nowMs: Long = System.currentTimeMillis()
    ): MarketRateItem? {
        val priceUsd = extractOrderbookPriceValue(jsonText) ?: return null
        if (priceUsd <= 0.0) return null

        val diffUsd = if (previousUsd > 0.0) abs(priceUsd - previousUsd) else 0.0
        val pct = if (previousUsd > 0.0) (diffUsd / previousUsd) * 100.0 else 0.0
        val isPositive = previousUsd <= 0.0 || priceUsd >= previousUsd

        return buildUsdRateItem(
            key = key,
            title = title,
            priceUsd = priceUsd,
            diffUsd = diffUsd,
            changePct = pct,
            isPositive = isPositive,
            nowMs = nowMs
        )
    }

    private fun extractOrderbookPriceValue(jsonText: String): Double? {
        if (jsonText.isBlank()) return null
        if (!extractJsonField(jsonText, "status").equals("ok", ignoreCase = true)) return null

        var priceVal = parseDouble(extractJsonField(jsonText, "lastTradePrice"))
        if (priceVal == null || priceVal <= 0.0) {
            val ask0 = extractOrderbookTopPrice(jsonText, "asks")
            val bid0 = extractOrderbookTopPrice(jsonText, "bids")
            priceVal = when {
                ask0 != null && bid0 != null && ask0 > 0.0 && bid0 > 0.0 -> (ask0 + bid0) / 2.0
                ask0 != null && ask0 > 0.0 -> ask0
                bid0 != null && bid0 > 0.0 -> bid0
                else -> null
            }
        }
        return priceVal?.takeIf { it > 0.0 }
    }

    /**
     * Fallback extractor for GRAM in USD from `https://nobitex.ir/price/gram` HTML / embedded SSR JSON.
     */
    fun parseGramFromPageHtml(
        html: String,
        referenceUsdtTomans: Long = 0L,
        nowMs: Long = System.currentTimeMillis()
    ): MarketRateItem? {
        if (html.isBlank()) return null

        // 1. Check embedded stats JSON block ("gram-usdt":{...})
        extractJsonObjectBody(html, "gram-usdt")?.let { objBody ->
            buildUsdItemFromStatsBody(objBody, "nobitex-gram", "گرام (GRAM)", nowMs = nowMs)
                ?.let { return it }
        }

        // 2. Check visible USD/USDT price on the nobitex.ir/price/gram page ($0.962, 0.962 دلار, 0.962 USDT)
        val cleanText = PersianDateHelper.toEnglishDigits(HTML_TAG_REGEX.replace(html, " "))
        val usdMatch = USD_PREFIX_REGEX.find(cleanText) ?: USD_SUFFIX_REGEX.find(cleanText)
        val rawUsd = usdMatch?.groupValues?.get(1)?.let(::parseDouble)
        if (rawUsd != null && rawUsd > 0.0 && rawUsd < 100_000.0) {
            val pctMatch = Regex("""([+-]?\d+(?:\.\d+)?)\s*%""").find(cleanText)
            val pctVal = pctMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val diffUsd = rawUsd * (abs(pctVal) / 100.0)
            return buildUsdRateItem(
                key = "nobitex-gram",
                title = "گرام (GRAM)",
                priceUsd = rawUsd,
                diffUsd = diffUsd,
                changePct = abs(pctVal),
                isPositive = pctVal >= 0.0,
                nowMs = nowMs
            )
        }

        // 3. Fallback: embedded "gram-rls" converted to USD using embedded "usdt-rls" or referenceUsdtTomans
        val gramRlsBody = extractJsonObjectBody(html, "gram-rls")
        if (gramRlsBody != null) {
            val usdtFromPage = extractJsonObjectBody(html, "usdt-rls")
                ?.let { buildTomanItemFromStatsBody(it, "nobitex-usdt", "تتر (USDT)", 10_000L, nowMs) }
                ?.priceTomans ?: 0L
            val effectiveUsdtTomans = if (usdtFromPage > 0L) usdtFromPage else referenceUsdtTomans
            if (effectiveUsdtTomans > 0L) {
                buildUsdItemFromRlsCrossRate(gramRlsBody, effectiveUsdtTomans, nowMs)?.let { return it }
            }
        }

        return null
    }

    /**
     * Fallback extractor for Tether (USDT) in Tomans from Nobitex HTML / embedded SSR JSON.
     */
    fun parseTetherFromPageHtml(html: String, nowMs: Long = System.currentTimeMillis()): MarketRateItem? {
        if (html.isBlank()) return null

        // 1. Check embedded stats JSON block ("usdt-rls":{...})
        extractJsonObjectBody(html, "usdt-rls")?.let { objBody ->
            buildTomanItemFromStatsBody(objBody, "nobitex-usdt", "تتر (USDT)", minTomans = 10_000L, nowMs = nowMs)
                ?.let { return it }
        }

        // 2. Check visible Toman price near "تتر" / "USDT"
        val cleanText = PersianDateHelper.toEnglishDigits(HTML_TAG_REGEX.replace(html, " "))
        val usdtContextRegex = Regex("""(?:تتر|USDT)[^0-9]{0,45}([0-9][0-9,٬]{4,12})\s*تومان""", RegexOption.IGNORE_CASE)
        val m = usdtContextRegex.find(cleanText)
        val rawVal = m?.groupValues?.get(1)?.let(::parseDouble)
        if (rawVal != null && rawVal in 10_000.0..5_000_000.0) {
            return MarketRateItem(
                key = "nobitex-usdt",
                title = "تتر (USDT)",
                priceTomans = rawVal.roundToLong(),
                changePercent = 0.0,
                changeTomans = 0L,
                isPositiveChange = true,
                timeFormatted = PersianDateHelper.getTimeFormatted(nowMs),
                unitLabel = "تومان",
                displayPriceFormatted = null,
                displayDiffFormatted = null,
                sourceLabel = SOURCE_LABEL
            )
        }
        return null
    }

    private fun buildUsdRateItem(
        key: String,
        title: String,
        priceUsd: Double,
        diffUsd: Double,
        changePct: Double,
        isPositive: Boolean,
        nowMs: Long
    ): MarketRateItem {
        return MarketRateItem(
            key = key,
            title = title,
            priceTomans = priceUsd.roundToLong(),
            changePercent = abs(changePct),
            changeTomans = diffUsd.roundToLong(),
            isPositiveChange = isPositive,
            timeFormatted = PersianDateHelper.getTimeFormatted(nowMs),
            unitLabel = "دلار",
            displayPriceFormatted = formatUsdPrice(priceUsd),
            displayDiffFormatted = if (diffUsd > 0.0) formatUsdDiff(diffUsd) else null,
            sourceLabel = SOURCE_LABEL
        )
    }

    /**
     * Adaptive USD price formatter for cryptocurrencies (supports both ~$0.962 and micro-cent tokens like $0.00245).
     */
    fun formatUsdPrice(value: Double): String {
        return when {
            value >= 100.0 -> "$" + String.format(Locale.US, "%,.2f", value)
            value >= 1.0 -> "$" + trimDecimals(String.format(Locale.US, "%.4f", value), minFractionDigits = 2)
            value >= 0.01 -> "$" + trimDecimals(String.format(Locale.US, "%.4f", value), minFractionDigits = 3)
            else -> "$" + trimDecimals(String.format(Locale.US, "%.6f", value), minFractionDigits = 4)
        }
    }

    fun formatUsdDiff(value: Double): String {
        val absVal = abs(value)
        return when {
            absVal >= 1.0 -> "$" + trimDecimals(String.format(Locale.US, "%,.3f", absVal), minFractionDigits = 2)
            absVal >= 0.01 -> "$" + trimDecimals(String.format(Locale.US, "%.4f", absVal), minFractionDigits = 3)
            else -> "$" + trimDecimals(String.format(Locale.US, "%.6f", absVal), minFractionDigits = 4)
        }
    }

    private fun trimDecimals(formatted: String, minFractionDigits: Int): String {
        val dotIdx = formatted.indexOf('.')
        if (dotIdx < 0) return formatted
        var end = formatted.length
        while (end > dotIdx + 1 + minFractionDigits && formatted[end - 1] == '0') {
            end--
        }
        return formatted.substring(0, end)
    }

    /**
     * Pure-Kotlin balanced-brace JSON object extractor for `"key": { ... }`.
     * Works identically in Android runtime and local JVM unit tests without `org.json` stubs.
     */
    private fun extractJsonObjectBody(source: String, key: String): String? {
        val headerMatch = Regex(""""${Regex.escape(key)}"\s*:\s*\{""").find(source) ?: return null
        val startBrace = headerMatch.range.last
        var depth = 0
        var inString = false
        var escaped = false
        for (i in startBrace until source.length) {
            val ch = source[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return source.substring(startBrace, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractJsonField(objBody: String, field: String): String? {
        val match = Regex(""""${Regex.escape(field)}"\s*:\s*(?:"([^"]*)"|([+-]?[0-9.eE-]+|true|false|null))""")
            .find(objBody) ?: return null
        val raw = match.groupValues[1].ifEmpty { match.groupValues[2] }
        return if (raw.equals("null", ignoreCase = true)) null else raw
    }

    private fun extractOrderbookTopPrice(jsonText: String, sideKey: String): Double? {
        val match = Regex(""""${Regex.escape(sideKey)}"\s*:\s*\[\s*\[\s*"?([0-9]+(?:\.[0-9]+)?)"?""")
            .find(jsonText) ?: return null
        return parseDouble(match.groupValues[1])
    }

    private fun parseDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val cleaned = PersianDateHelper.toEnglishDigits(raw)
            .replace("٬", "")
            .replace(",", "")
            .replace("%", "")
            .replace("٪", "")
            .trim()
        return cleaned.toDoubleOrNull()
    }

    private fun downloadGet(urlString: String, referer: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Referer", referer)
            setRequestProperty("Origin", "https://nobitex.ir")
            setRequestProperty("Accept", "application/json, text/html, */*")
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadPostForm(urlString: String, formBody: String, referer: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            setRequestProperty("Referer", referer)
            setRequestProperty("Origin", "https://nobitex.ir")
            setRequestProperty("Accept", "application/json, */*")
        }
        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(formBody)
                writer.flush()
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
