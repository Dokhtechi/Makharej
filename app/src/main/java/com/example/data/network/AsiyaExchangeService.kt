package com.example.data.network

import android.content.Context
import com.example.data.model.MarketRateItem
import com.example.utils.PersianDateHelper
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Fetches the live Tether (USDT) price in Tomans exclusively from https://asiyaexchange.com
 * using a multi-stage extractor:
 *  1. Cached discovered JSON API endpoint (instant refresh once learned)
 *  2. Direct OkHttp fetch of https://asiyaexchange.com (+ www) parsing JSON, embedded scripts,
 *     clean block-to-line HTML text, and external JS-bundle discovered API endpoints
 *  3. Headless offscreen Android WebView extractor (handles React/Vue/Next SPAs, WebSockets,
 *     dynamic XHR/Fetch calls, JS challenges, and Crypto tabs automatically)
 */
object AsiyaExchangeService {

    const val BASE_URL = "https://asiyaexchange.com"
    const val SOURCE_LABEL = "Asiya Exchange (asiyaexchange.com)"

    private const val PREFS_NAME = "asiya_exchange_prefs"
    private const val KEY_DISCOVERED_API_URL = "discovered_api_url"

    private val moshiAnyAdapter = Moshi.Builder().build().adapter(Any::class.java).lenient()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val SCRIPT_TAG_REGEX = Regex("""<script\b[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)
    private val SCRIPT_SRC_REGEX = Regex("""<script\b[^>]*\bsrc\s*=\s*["']([^"']+\.js(?:\?[^"']*)?)["']""", RegexOption.IGNORE_CASE)
    private val STYLE_OR_SVG_REGEX = Regex("""<(?:style|svg|noscript|head)\b[^>]*>[\s\S]*?</(?:style|svg|noscript|head)>""", RegexOption.IGNORE_CASE)
    private val BLOCK_BREAK_REGEX = Regex("""</?(?:div|tr|li|p|br|section|article|header|footer|h[1-6]|ul|ol|table|tbody|thead|dl|dt|dd)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val CELL_SEP_REGEX = Regex("""</?(?:td|th)\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
    private val HTML_ENTITY_REGEX = Regex("""&(?:nbsp|zwnj|rlm|lrm|thinsp|#160|#8204|#x200c);""", RegexOption.IGNORE_CASE)
    private val PERCENT_REGEX = Regex("""\(?([+-]?\d+(?:\.\d+)?)\s*%\)?""")
    private val NUMBER_REGEX = Regex("""\d[\d,.\u00A0 ]*(?:\.\d+)?""")
    private val DOT_THOUSANDS_REGEX = Regex("""^\d{1,3}(?:\.\d{3})+$""")

    private val API_URL_DISCOVERY_REGEX = Regex(
        """["']((?:https?://(?:[\w.-]+\.)?asiyaexchange\.com)?/(?:api|wp-json|v\d+|data|rates|prices|market)[^"'\s<>]{0,140})["']""",
        RegexOption.IGNORE_CASE
    )
    private val SUBDOMAIN_API_REGEX = Regex(
        """["'](https?://(?:api|back|backend|app|panel|ws|data|rate|rates|price|prices)\.asiyaexchange\.com[^"'\s<>]{0,140})["']""",
        RegexOption.IGNORE_CASE
    )

    private val PAIR_EXCLUSION_REGEX = Regex(
        """(?:btc|eth|bnb|sol|xrp|ada|doge|trx|ton|ltc|shib|avax|dot|matic|link|not|pepe|بیت\s*کوین|اتریوم|سولانا|ریپل|تون|دوج|ترون)\s*(?:[/\-_]|به|to)\s*(?:usdt|tether|تتر)""",
        RegexOption.IGNORE_CASE
    )

    private val OTHER_ASSET_BOUNDARY_REGEX = Regex(
        """(?:دلار\s*آمریکا|دلار\s*آزاد|دلار\s*کانادا|دلار\s*استرالیا|یورو|پوند|درهم|لیر|فرانک|ین|یوان|دینار|بیت[\s\u200c]*کوین|اتریوم|سولانا|ریپل|دوج[\s\u200c]*کوین|ترون|تون[\s\u200c]*کوین|سکه|ربع\s*سکه|نیم\s*سکه|طلای|مثقال|انس\s*جهانی|\b(?:usd|eur|gbp|aed|try|cad|aud|chf|btc|eth|bnb|sol|xrp|trx|ton|doge)\b)""",
        RegexOption.IGNORE_CASE
    )

    private const val MIN_TOMAN_USDT = 20_000L
    private const val MAX_TOMAN_USDT = 650_000L
    private const val MIN_RIAL_USDT = 200_000L
    private const val MAX_RIAL_USDT = 6_500_000L

    /**
     * Fetches live Tether (USDT) price exclusively from https://asiyaexchange.com.
     */
    suspend fun fetchTetherRate(
        context: Context? = null,
        previousCachedPriceTomans: Long = 0L,
        referenceDollarTomans: Long = 0L
    ): MarketRateItem? = withContext(Dispatchers.IO) {
        val prefs = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedApiUrl = prefs?.getString(KEY_DISCOVERED_API_URL, null)?.takeIf { it.isNotBlank() }

        // 0. Fast path: try previously discovered Asiya Exchange API endpoint first
        if (!cachedApiUrl.isNullOrBlank()) {
            val apiBody = downloadText(cachedApiUrl, referer = BASE_URL)
            if (!apiBody.isNullOrBlank()) {
                parseTetherFromResponse(
                    rawText = apiBody,
                    previousCachedPriceTomans = previousCachedPriceTomans,
                    referenceDollarTomans = referenceDollarTomans
                )?.let { return@withContext it }
            }
        }

        val discoveredApiUrls = LinkedHashSet<String>()
        val discoveredBundleUrls = LinkedHashSet<String>()

        // 1. Fetch https://asiyaexchange.com and https://www.asiyaexchange.com directly via OkHttp
        val landingUrls = listOf(BASE_URL, "https://www.asiyaexchange.com")
        for (url in landingUrls) {
            val body = downloadText(url, referer = BASE_URL) ?: continue
            if (body.isBlank()) continue

            parseTetherFromResponse(
                rawText = body,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )?.let { return@withContext it }

            discoverApiUrls(body).forEach { discoveredApiUrls.add(it) }
            discoverScriptBundles(body).forEach { discoveredBundleUrls.add(it) }
        }

        // 2. Scan main frontend JS bundles (if SPA) to uncover real internal API endpoints
        for (bundleUrl in discoveredBundleUrls.take(3)) {
            val jsText = downloadText(bundleUrl, referer = BASE_URL, maxBytes = 650_000) ?: continue
            discoverApiUrls(jsText).forEach { discoveredApiUrls.add(it) }
        }

        // 3. Query any discovered Asiya Exchange API endpoints directly
        for (apiUrl in discoveredApiUrls.take(10)) {
            val apiBody = downloadText(apiUrl, referer = BASE_URL) ?: continue
            if (apiBody.isBlank()) continue
            val parsed = parseTetherFromResponse(
                rawText = apiBody,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )
            if (parsed != null) {
                prefs?.edit()?.putString(KEY_DISCOVERED_API_URL, apiUrl)?.apply()
                return@withContext parsed
            }
        }

        // 4. Headless Android WebView extractor (renders JS SPA, captures XHR/Fetch/WebSocket & Crypto tabs)
        if (context != null) {
            val webViewResult = try {
                AsiyaWebViewExtractor.extractTether(
                    context = context,
                    previousCachedPriceTomans = previousCachedPriceTomans,
                    referenceDollarTomans = referenceDollarTomans
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (webViewResult != null) {
                val learnedUrl = webViewResult.discoveredApiUrl
                if (!learnedUrl.isNullOrBlank() && learnedUrl.contains("asiyaexchange.com", ignoreCase = true)) {
                    prefs?.edit()?.putString(KEY_DISCOVERED_API_URL, learnedUrl)?.apply()
                }
                return@withContext webViewResult.item
            }
        }

        // 5. Lightweight secondary subpath check
        val subpaths = listOf(
            "$BASE_URL/crypto",
            "$BASE_URL/tether",
            "$BASE_URL/usdt",
            "$BASE_URL/fa"
        )
        for (subUrl in subpaths) {
            val body = downloadText(subUrl, referer = BASE_URL) ?: continue
            if (body.isBlank()) continue
            parseTetherFromResponse(
                rawText = body,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )?.let { return@withContext it }
        }

        null
    }

    /**
     * Parses Tether (USDT) price in Tomans from either a JSON payload or an HTML page from asiyaexchange.com.
     */
    fun parseTetherFromResponse(
        rawText: String,
        previousCachedPriceTomans: Long = 0L,
        referenceDollarTomans: Long = 0L
    ): MarketRateItem? {
        if (rawText.isBlank()) return null
        val trimmed = rawText.trim()

        // 1. Direct JSON object or JSON array response
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            parseTetherFromJsonText(trimmed, previousCachedPriceTomans, referenceDollarTomans)?.let { return it }
        }

        // 2. Embedded JSON inside <script> tags (Next.js __NEXT_DATA__, Nuxt, inline API state)
        for (match in SCRIPT_TAG_REGEX.findAll(rawText)) {
            val scriptBody = match.groupValues[1].trim()
            if (scriptBody.isBlank() || !containsTetherKeyword(scriptBody)) continue

            if (scriptBody.startsWith("{") || scriptBody.startsWith("[")) {
                parseTetherFromJsonText(scriptBody, previousCachedPriceTomans, referenceDollarTomans)?.let { return it }
            }
            extractEmbeddedJsonTether(scriptBody, previousCachedPriceTomans, referenceDollarTomans)?.let { return it }
        }

        // 3. Clean block-to-line rendered HTML text (works for <div> cards, <tr> tables, <li> lists without CSS bloat)
        val cleanText = htmlToCleanLines(rawText)
        parseTetherFromCleanText(cleanText, previousCachedPriceTomans, referenceDollarTomans)?.let { return it }

        return null
    }

    /**
     * Converts raw HTML into clean newline-delimited rendered lines so Tailwind/Bootstrap classes never truncate prices.
     */
    fun htmlToCleanLines(html: String): String {
        val noScripts = STYLE_OR_SVG_REGEX.replace(SCRIPT_TAG_REGEX.replace(html, "\n"), "\n")
        val withCellSeps = CELL_SEP_REGEX.replace(noScripts, " | ")
        val withLineBreaks = BLOCK_BREAK_REGEX.replace(withCellSeps, "\n")
        val stripped = HTML_TAG_REGEX.replace(withLineBreaks, " ")
        return HTML_ENTITY_REGEX.replace(stripped, " ")
    }

    /**
     * Extracts Tether (USDT) price from clean multi-line rendered text (HTML-converted or WebView innerText).
     */
    fun parseTetherFromCleanText(
        plainText: String,
        previousCachedPriceTomans: Long = 0L,
        referenceDollarTomans: Long = 0L
    ): MarketRateItem? {
        if (plainText.isBlank() || !containsTetherKeyword(plainText)) return null

        val normalizedDoc = PersianDateHelper.toEnglishDigits(plainText)
            .replace("٬", ",")
            .replace("،", ",")
            .replace("٪", "%")
            .replace("\u200c", " ")
        val globalHintRial = isRialHint(normalizedDoc) && !isTomanHint(normalizedDoc)
        val globalHintToman = isTomanHint(normalizedDoc) && !isRialHint(normalizedDoc)

        val lines = normalizedDoc
            .split('\n', '\r')
            .map { it.replace(Regex("[\\t ]+"), " ").trim() }
            .filter { it.isNotEmpty() }

        for (i in lines.indices) {
            val line = lines[i]
            if (!containsTetherKeyword(line)) continue
            if (PAIR_EXCLUSION_REGEX.containsMatchIn(line)) continue

            // Build a focused local block around line i (stopping if another currency starts)
            val blockLines = mutableListOf(line)
            for (next in (i + 1)..minOf(lines.lastIndex, i + 6)) {
                val nextLine = lines[next]
                if (OTHER_ASSET_BOUNDARY_REGEX.containsMatchIn(nextLine) && !containsTetherKeyword(nextLine)) {
                    break
                }
                if (PAIR_EXCLUSION_REGEX.containsMatchIn(nextLine)) break
                blockLines.add(nextLine)
            }

            // Try forward block first (standard: label -> price), then include line i-1 (price above label)
            val candidateBlocks = mutableListOf(blockLines.joinToString(" | "))
            if (i > 0) {
                val prevLine = lines[i - 1]
                if (!OTHER_ASSET_BOUNDARY_REGEX.containsMatchIn(prevLine) && !PAIR_EXCLUSION_REGEX.containsMatchIn(prevLine)) {
                    candidateBlocks.add("$prevLine | ${blockLines.joinToString(" | ")}")
                }
            }

            for (block in candidateBlocks) {
                val hintRial = isRialHint(block) || (!isTomanHint(block) && globalHintRial)
                val hintToman = isTomanHint(block) || (!isRialHint(block) && globalHintToman)

                val priceFromSell = extractLabeledPrice(
                    plainText = block,
                    labels = listOf("قیمت فروش", "فروش", "sell", "نرخ فروش", "قیمت لحظه", "قیمت"),
                    hintRial = hintRial,
                    hintToman = hintToman,
                    referenceDollarTomans = referenceDollarTomans
                )
                val priceFromBuy = if (priceFromSell == null) {
                    extractLabeledPrice(
                        plainText = block,
                        labels = listOf("قیمت خرید", "خرید", "buy"),
                        hintRial = hintRial,
                        hintToman = hintToman,
                        referenceDollarTomans = referenceDollarTomans
                    )
                } else null
                val fallbackPrice = if (priceFromSell == null && priceFromBuy == null) {
                    extractBestPlausibleTetherPrice(
                        plainText = block,
                        hintRial = hintRial,
                        hintToman = hintToman,
                        referenceDollarTomans = referenceDollarTomans
                    )
                } else null

                val finalPrice = priceFromSell ?: priceFromBuy ?: fallbackPrice ?: continue

                val pctMatch = PERCENT_REGEX.find(block)
                val pctSigned = pctMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val absPct = abs(pctSigned).takeIf { it <= 50.0 } ?: 0.0
                val isNegative = pctSigned < 0.0 || block.contains("کاهش") || block.contains("▼") || block.contains("↓")
                val diffTomans = if (absPct > 0.0) (finalPrice * (absPct / 100.0)).roundToLong() else 0L

                return buildTetherItem(
                    priceTomans = finalPrice,
                    changeTomans = diffTomans,
                    changePercent = absPct,
                    isPositive = !isNegative,
                    previousCachedPriceTomans = previousCachedPriceTomans
                )
            }
        }
        return null
    }

    private fun parseTetherFromJsonText(
        jsonText: String,
        previousCachedPriceTomans: Long,
        referenceDollarTomans: Long
    ): MarketRateItem? {
        return try {
            val root = moshiAnyAdapter.fromJson(jsonText.trim()) ?: return null
            val parsed = findTetherInJsonNode(root, parentKey = "", referenceDollarTomans = referenceDollarTomans) ?: return null
            buildTetherItem(
                priceTomans = parsed.priceTomans,
                changeTomans = parsed.changeTomans,
                changePercent = parsed.changePercent,
                isPositive = parsed.isPositive,
                previousCachedPriceTomans = previousCachedPriceTomans
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class ParsedTetherRaw(
        val priceTomans: Long,
        val changeTomans: Long = 0L,
        val changePercent: Double = 0.0,
        val isPositive: Boolean = true
    )

    private fun findTetherInJsonNode(
        node: Any?,
        parentKey: String,
        referenceDollarTomans: Long
    ): ParsedTetherRaw? {
        when (node) {
            is Map<*, *> -> {
                // Case A: The object itself represents Tether
                val isParentTether = isPureTetherIdentifier(parentKey)
                val idFields = listOf(
                    "symbol", "code", "slug", "key", "id", "currency", "coin",
                    "name", "title", "en_name", "fa_name", "pair", "market", "asset"
                )
                val hasTetherIdField = idFields.any { f ->
                    val v = node[f]?.toString().orEmpty()
                    v.isNotBlank() && isPureTetherIdentifier(v)
                }

                if (isParentTether || hasTetherIdField) {
                    extractPriceFromTetherJsonMap(node, referenceDollarTomans)?.let { return it }
                }

                // Case B: Flat keyed map like {"usdt_sell": 84500, "usdt_buy": 84100} or {"usdt": 84500}
                extractFlatTetherKeysFromMap(node, referenceDollarTomans)?.let { return it }

                // Case C: Recurse into child objects / arrays
                for ((rawK, child) in node) {
                    val k = rawK?.toString().orEmpty()
                    if (child is Map<*, *> || child is List<*>) {
                        findTetherInJsonNode(child, parentKey = k, referenceDollarTomans = referenceDollarTomans)?.let { return it }
                    }
                }
            }

            is List<*> -> {
                for (child in node) {
                    if (child is Map<*, *> || child is List<*>) {
                        findTetherInJsonNode(child, parentKey = parentKey, referenceDollarTomans = referenceDollarTomans)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private fun extractFlatTetherKeysFromMap(
        node: Map<*, *>,
        referenceDollarTomans: Long
    ): ParsedTetherRaw? {
        val sellCandidates = mutableListOf<Long>()
        val generalCandidates = mutableListOf<Long>()
        val buyCandidates = mutableListOf<Long>()

        for ((rawK, v) in node) {
            val k = rawK?.toString() ?: continue
            val lower = k.lowercase()
            if (PAIR_EXCLUSION_REGEX.containsMatchIn(lower)) continue
            val isTetherKey = isPureTetherIdentifier(lower) ||
                ((lower.contains("usdt") || lower.contains("tether") || lower.contains("تتر")) &&
                    !lower.contains("btc") && !lower.contains("eth") && !lower.contains("bnb"))
            if (!isTetherKey) continue

            if (v is Map<*, *>) {
                extractPriceFromTetherJsonMap(v, referenceDollarTomans)?.let { return it }
                continue
            }

            val num = when (v) {
                is Number -> v.toDouble()
                is String -> parseCleanDouble(v)
                else -> null
            } ?: continue

            val normalized = normalizeRawTetherValue(
                raw = num,
                hintRial = isRialHint(lower) || (v is String && isRialHint(v)),
                hintToman = isTomanHint(lower) || (v is String && isTomanHint(v)),
                referenceDollarTomans = referenceDollarTomans
            ) ?: continue

            when {
                lower.contains("sell") || lower.contains("sale") || lower.contains("فروش") -> sellCandidates.add(normalized)
                lower.contains("buy") || lower.contains("خرید") -> buyCandidates.add(normalized)
                else -> generalCandidates.add(normalized)
            }
        }

        val best = sellCandidates.firstOrNull()
            ?: generalCandidates.firstOrNull()
            ?: buyCandidates.firstOrNull()
            ?: return null
        return ParsedTetherRaw(priceTomans = best)
    }

    private fun extractPriceFromTetherJsonMap(
        obj: Map<*, *>,
        referenceDollarTomans: Long
    ): ParsedTetherRaw? {
        val unitText = listOf(
            obj["unit"]?.toString().orEmpty(),
            obj["currency_unit"]?.toString().orEmpty(),
            obj["quote"]?.toString().orEmpty(),
            obj["base"]?.toString().orEmpty()
        ).joinToString(" ")
        val objHintRial = isRialHint(unitText)
        val objHintToman = isTomanHint(unitText)

        val priceKeys = listOf(
            "sell_price", "sellPrice", "sell_toman", "sellToman", "sell_irt", "sell",
            "sale_price", "salePrice", "sale",
            "price_toman", "priceToman", "toman_price", "tomanPrice", "toman", "irt",
            "price", "rate", "last", "last_price", "lastPrice", "current", "value", "amount", "p",
            "buy_price", "buyPrice", "buy_toman", "buyToman", "buy_irt", "buy",
            "sell_rial", "price_rial", "rial", "irr"
        )

        var chosenToman: Long? = null
        for (k in priceKeys) {
            if (!obj.containsKey(k)) continue
            val rawVal = obj[k]
            val num = when (rawVal) {
                is Number -> rawVal.toDouble()
                is String -> parseCleanDouble(rawVal)
                else -> null
            } ?: continue
            val normalized = normalizeRawTetherValue(
                raw = num,
                hintRial = objHintRial || isRialHint(k) || (rawVal is String && isRialHint(rawVal)),
                hintToman = objHintToman || isTomanHint(k) || (rawVal is String && isTomanHint(rawVal)),
                referenceDollarTomans = referenceDollarTomans
            )
            if (normalized != null) {
                chosenToman = normalized
                break
            }
        }

        val finalPrice = chosenToman ?: return null

        val pctKeys = listOf("change_percent", "changePercent", "change_24h", "change24h", "percent", "dp", "pct", "change")
        var rawPct = 0.0
        var hasExplicitPct = false
        for (k in pctKeys) {
            if (!obj.containsKey(k)) continue
            val v = obj[k]
            val n = when (v) {
                is Number -> v.toDouble()
                is String -> parseCleanDouble(v)
                else -> null
            }
            if (n != null && abs(n) <= 50.0) {
                rawPct = n
                hasExplicitPct = true
                break
            }
        }

        val diffKeys = listOf("diff", "change_amount", "changeAmount", "d")
        var rawDiff = 0.0
        for (k in diffKeys) {
            if (!obj.containsKey(k)) continue
            val v = obj[k]
            val n = when (v) {
                is Number -> v.toDouble()
                is String -> parseCleanDouble(v)
                else -> null
            }
            if (n != null && abs(n) < finalPrice * 0.5) {
                rawDiff = n
                break
            }
        }

        val dt = (obj["dt"] ?: obj["trend"] ?: obj["direction"])?.toString()?.lowercase().orEmpty()
        val isNegative = dt in listOf("low", "down", "negative", "decrease", "red") ||
            rawPct < 0.0 ||
            rawDiff < 0.0

        val absPct = abs(rawPct)
        val absDiffTomans = if (abs(rawDiff) >= 1.0) {
            val d = abs(rawDiff)
            if (objHintRial && d > 10_000.0) (d / 10.0).roundToLong() else d.roundToLong()
        } else if (hasExplicitPct && absPct > 0.0) {
            (finalPrice * (absPct / 100.0)).roundToLong()
        } else {
            0L
        }

        return ParsedTetherRaw(
            priceTomans = finalPrice,
            changeTomans = absDiffTomans,
            changePercent = absPct,
            isPositive = !isNegative
        )
    }

    private fun extractEmbeddedJsonTether(
        scriptBody: String,
        previousCachedPriceTomans: Long,
        referenceDollarTomans: Long
    ): MarketRateItem? {
        val tetherRegex = Regex("""(?:"(?:usdt|tether|usdt_irt|usdt-irt|usdt_irr|تتر)"|تتر)""", RegexOption.IGNORE_CASE)
        for (m in tetherRegex.findAll(scriptBody)) {
            val start = (m.range.first - 140).coerceAtLeast(0)
            val end = (m.range.last + 360).coerceAtMost(scriptBody.length)
            val snippet = scriptBody.substring(start, end)
            if (PAIR_EXCLUSION_REGEX.containsMatchIn(snippet)) continue

            val openBrace = snippet.lastIndexOf('{', (m.range.first - start).coerceAtLeast(0))
            val closeBrace = snippet.indexOf('}', (m.range.last - start).coerceAtLeast(0))
            if (openBrace >= 0 && closeBrace > openBrace) {
                val candidateObj = snippet.substring(openBrace, closeBrace + 1)
                parseTetherFromJsonText(candidateObj, previousCachedPriceTomans, referenceDollarTomans)?.let { return it }
            }
        }
        return null
    }

    private fun extractLabeledPrice(
        plainText: String,
        labels: List<String>,
        hintRial: Boolean,
        hintToman: Boolean,
        referenceDollarTomans: Long
    ): Long? {
        for (label in labels) {
            val idx = plainText.indexOf(label, ignoreCase = true)
            if (idx < 0) continue
            val after = plainText.substring(idx + label.length).take(75)
            for (m in NUMBER_REGEX.findAll(after)) {
                val raw = parseNumberToken(m.value) ?: continue
                normalizeRawTetherValue(
                    raw = raw,
                    hintRial = hintRial || isRialHint(after),
                    hintToman = hintToman || isTomanHint(after),
                    referenceDollarTomans = referenceDollarTomans
                )?.let { return it }
            }
        }
        return null
    }

    private fun extractBestPlausibleTetherPrice(
        plainText: String,
        hintRial: Boolean,
        hintToman: Boolean,
        referenceDollarTomans: Long
    ): Long? {
        val withoutPct = PERCENT_REGEX.replace(plainText, " ")
        val candidates = mutableListOf<Long>()
        for (m in NUMBER_REGEX.findAll(withoutPct)) {
            val raw = parseNumberToken(m.value) ?: continue
            normalizeRawTetherValue(
                raw = raw,
                hintRial = hintRial,
                hintToman = hintToman,
                referenceDollarTomans = referenceDollarTomans
            )?.let { candidates.add(it) }
        }
        if (candidates.isEmpty()) return null
        if (referenceDollarTomans > 10_000L && candidates.size > 1) {
            return candidates.minByOrNull { abs(it - referenceDollarTomans) }
        }
        return candidates.first()
    }

    private fun parseNumberToken(token: String): Double? {
        val compact = token.replace("\u00A0", "").replace(" ", "").trim()
        if (compact.isEmpty()) return null
        val cleaned = if (DOT_THOUSANDS_REGEX.matches(compact)) {
            compact.replace(".", "")
        } else {
            compact.replace(",", "")
        }
        return cleaned.toDoubleOrNull()
    }

    private fun normalizeRawTetherValue(
        raw: Double,
        hintRial: Boolean = false,
        hintToman: Boolean = false,
        referenceDollarTomans: Long = 0L
    ): Long? {
        if (raw <= 0.0) return null
        val rounded = raw.roundToLong()
        if (rounded in 1380L..1420L || rounded in 2010L..2040L) return null

        // Dynamic Dollar-anchored Rial vs Toman disambiguation when TGJU Dollar is known
        if (referenceDollarTomans >= MIN_TOMAN_USDT) {
            val minT = (referenceDollarTomans * 0.55).roundToLong()
            val maxT = (referenceDollarTomans * 1.65).roundToLong()
            if (hintToman && rounded in minT..maxT) return rounded
            if (hintRial && (rounded / 10L) in minT..maxT) return (raw / 10.0).roundToLong()
            if (rounded in minT..maxT) return rounded
            if ((rounded / 10L) in minT..maxT) return (raw / 10.0).roundToLong()
        }

        return when {
            hintToman && rounded in MIN_TOMAN_USDT..MAX_TOMAN_USDT -> rounded
            hintRial && rounded in MIN_RIAL_USDT..MAX_RIAL_USDT -> (raw / 10.0).roundToLong()
            rounded in MIN_TOMAN_USDT..MAX_TOMAN_USDT -> rounded
            rounded in (MAX_TOMAN_USDT + 1)..MAX_RIAL_USDT -> (raw / 10.0).roundToLong()
            else -> null
        }
    }

    private fun buildTetherItem(
        priceTomans: Long,
        changeTomans: Long,
        changePercent: Double,
        isPositive: Boolean,
        previousCachedPriceTomans: Long
    ): MarketRateItem {
        var finalDiff = changeTomans
        var finalPct = changePercent
        var finalPos = isPositive

        if (finalDiff == 0L && finalPct == 0.0 && previousCachedPriceTomans >= MIN_TOMAN_USDT && previousCachedPriceTomans != priceTomans) {
            val delta = priceTomans - previousCachedPriceTomans
            finalDiff = abs(delta)
            finalPct = (finalDiff.toDouble() / previousCachedPriceTomans.toDouble()) * 100.0
            finalPos = delta >= 0L
        }

        return MarketRateItem(
            key = "asiya-usdt",
            title = "تتر (USDT)",
            priceTomans = priceTomans,
            changePercent = finalPct,
            changeTomans = finalDiff,
            isPositiveChange = finalPos,
            timeFormatted = PersianDateHelper.getTimeFormatted(),
            unitLabel = "تومان",
            displayPriceFormatted = null,
            displayDiffFormatted = null,
            sourceLabel = SOURCE_LABEL
        )
    }

    private fun isPureTetherIdentifier(raw: String): Boolean {
        val norm = raw.trim().lowercase()
        if (norm.isBlank() || PAIR_EXCLUSION_REGEX.containsMatchIn(norm)) return false
        return norm == "usdt" ||
            norm == "tether" ||
            norm == "usdt_irt" ||
            norm == "usdt-irt" ||
            norm == "usdtirt" ||
            norm == "usdt_irr" ||
            norm == "usdt-irr" ||
            norm == "usdtirr" ||
            norm == "usdt_toman" ||
            norm == "usdt_tmn" ||
            norm == "tether-irr" ||
            norm == "tether_irt" ||
            norm == "تتر" ||
            norm == "دلار تتر" ||
            norm.contains("تتر") ||
            norm == "usdt/irt" ||
            norm == "usdt/irr" ||
            norm == "usdt/tmn"
    }

    private fun containsTetherKeyword(text: String): Boolean {
        return text.contains("تتر") ||
            text.contains("usdt", ignoreCase = true) ||
            text.contains("tether", ignoreCase = true)
    }

    private fun isRialHint(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("ریال") || lower.contains("rial") || Regex("""\birr\b""").containsMatchIn(lower)
    }

    private fun isTomanHint(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("تومان") || lower.contains("تومن") || lower.contains("toman") || Regex("""\b(?:irt|tmn)\b""").containsMatchIn(lower)
    }

    private fun parseCleanDouble(raw: String?): Double? {
        if (raw.isNullOrBlank()) return null
        val norm = PersianDateHelper.toEnglishDigits(HTML_TAG_REGEX.replace(raw, " "))
            .replace("٬", "")
            .replace("،", "")
            .replace("٪", "")
            .replace("%", "")
            .trim()
        val match = Regex("""[+-]?\d[\d,.]*""").find(norm)?.value ?: return null
        val sign = if (match.startsWith("-")) -1.0 else 1.0
        val unsigned = match.removePrefix("-").removePrefix("+")
        val parsed = parseNumberToken(unsigned) ?: return null
        return sign * parsed
    }

    private fun discoverApiUrls(content: String): List<String> {
        val results = LinkedHashSet<String>()
        for (m in SUBDOMAIN_API_REGEX.findAll(content)) {
            val u = m.groupValues[1].trim()
            if (isValidDiscoveredApiUrl(u)) results.add(u)
        }
        for (m in API_URL_DISCOVERY_REGEX.findAll(content)) {
            val raw = m.groupValues[1].trim()
            if (raw.isBlank()) continue
            val full = if (raw.startsWith("http", ignoreCase = true)) raw else "$BASE_URL$raw"
            if (isValidDiscoveredApiUrl(full)) results.add(full)
        }
        return results.take(10)
    }

    private fun isValidDiscoveredApiUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".png") ||
            lower.endsWith(".svg") || lower.endsWith(".jpg") || lower.endsWith(".woff2")
        ) return false
        return lower.contains("rate") || lower.contains("price") || lower.contains("currenc") ||
            lower.contains("crypto") || lower.contains("usdt") || lower.contains("tether") ||
            lower.contains("market") || lower.contains("ticker") || lower.contains("/api")
    }

    private fun discoverScriptBundles(html: String): List<String> {
        val results = LinkedHashSet<String>()
        for (m in SCRIPT_SRC_REGEX.findAll(html)) {
            val src = m.groupValues[1].trim()
            val lower = src.lowercase()
            if (lower.contains("google") || lower.contains("analytics") || lower.contains("gtag") ||
                lower.contains("clarity") || lower.contains("hotjar") || lower.contains("jquery")
            ) continue
            val full = when {
                src.startsWith("http", ignoreCase = true) -> src
                src.startsWith("//") -> "https:$src"
                src.startsWith("/") -> "$BASE_URL$src"
                else -> "$BASE_URL/$src"
            }
            if (full.contains("asiyaexchange.com", ignoreCase = true)) {
                results.add(full)
            }
        }
        return results.take(4)
    }

    private fun downloadText(urlString: String, referer: String, maxBytes: Int = 1_500_000): String? {
        return try {
            val req = Request.Builder()
                .url(urlString)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
                .header("Referer", referer)
                .header("Accept", "application/json,text/html,application/xhtml+xml,text/plain,*/*;q=0.8")
                .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body ?: return null
                val bytes = body.source().let { src ->
                    src.request(maxBytes.toLong())
                    src.buffer.clone().readUtf8(minOf(src.buffer.size, maxBytes.toLong()))
                }
                bytes
            }
        } catch (_: Exception) {
            null
        }
    }
}
