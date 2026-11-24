package com.example.data.network

import android.content.Context
import com.example.data.model.MarketRateItem
import com.example.data.model.MarketRatesState
import com.example.utils.PersianDateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

class MarketRatesService(private val context: Context) {

    private val prefs = context.getSharedPreferences("market_rates_cache", Context.MODE_PRIVATE)

    /**
     * Fetches live market rates using strictly isolated per-source boundaries:
     * - TGJU (Dollar, Euro, Sekeh Emami, Rob Sekeh, Gold Ounce, 18K Gold, Bitcoin, Bourse, Brent Oil)
     * - Asiya Exchange (Tether USDT)
     * A network error, timeout, or layout change in either source is completely isolated
     * and gracefully falls back to the last cached rates without impacting the rest of the app.
     */
    suspend fun fetchRates(): MarketRatesState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val nonce = (now % 999983L).toString()

        var merged = TgjuExtractedRates()
        var tgjuSucceeded = false
        var asiyaSucceeded = false

        supervisorScope {
            // 1. ایزوله‌سازی کامل منبع TGJU با سقف زمانی مستقل
            val tgjuResult = try {
                withTimeoutOrNull(18_000L) {
                    fetchTgjuRatesIsolated(now = now, nonce = nonce)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (tgjuResult != null && tgjuResult.hasAny()) {
                merged = merged.mergeMissing(tgjuResult.copy(tether = null))
                tgjuSucceeded = true
            }

            // 2. ایزوله‌سازی کامل منبع Asiya Exchange با سقف زمانی مستقل (تتر فقط از asiyaexchange.com)
            val prevAsiyaTetherPrice = prefs.getLong("asiya_v1_tether_price", 0L)
            val referenceDollarTomans = merged.dollar?.priceTomans?.takeIf { it > 0L }
                ?: prefs.getLong("tgju_v5_dollar_price", 0L)
            val asiyaTether = try {
                withTimeoutOrNull(16_000L) {
                    AsiyaExchangeService.fetchTetherRate(
                        context = context,
                        previousCachedPriceTomans = prevAsiyaTetherPrice,
                        referenceDollarTomans = referenceDollarTomans
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (asiyaTether != null) {
                merged = merged.copy(tether = asiyaTether)
                asiyaSucceeded = true
            }
        }

        val fetchedAny = tgjuSucceeded || asiyaSucceeded || merged.hasAny()
        if (fetchedAny) {
            saveAllTgjuRates(merged, System.currentTimeMillis())
        }

        val err = if (!fetchedAny) {
            "خطا در دریافت نرخ‌ها از tgju.org و asiyaexchange.com (نمایش آخرین مقادیر ذخیره‌شده)"
        } else {
            null
        }
        buildCurrentState(errorMessage = err)
    }

    private fun fetchTgjuRatesIsolated(now: Long, nonce: String): TgjuExtractedRates {
        var merged = TgjuExtractedRates()

        // اولویت اول: ویجت لحظه‌ای رسمی api.tgju.org (تک‌فروشی سکه امامی و ربع سکه) — بدون تتر
        val allWidgetKeys = listOf(
            "price_dollar_rl", "price_eur", "retail_sekee",
            "retail_rob", "ons", "geram18",
            "crypto-bitcoin", "bourse", "oil_brent"
        )
        runCatching {
            val widgetUrl = "https://api.tgju.org/v1/widget/tmp?keys=${allWidgetKeys.joinToString(",")}&_t=$now&_r=$nonce"
            val widgetJson = downloadText(widgetUrl, TGJU_COIN_URL)
            if (!widgetJson.isNullOrBlank()) {
                val (parsed, _) = parseAllFromTgjuWidgetJson(widgetJson)
                merged = merged.mergeMissing(parsed.copy(tether = null))
            }
        }

        // اولویت دوم: استخراج مستقیم از HTML زنده صفحه https://www.tgju.org/coin
        if (!merged.isTgjuAssetsComplete()) {
            runCatching {
                val html = downloadText("$TGJU_COIN_URL?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html)
                    merged = merged.mergeMissing(parsed.copy(tether = null))
                }
            }
        }

        // اولویت سوم: سرورهای تیکر زنده TGJU
        if (!merged.isTgjuAssetsComplete()) {
            val ajaxEndpoints = listOf(
                "https://call5.tgju.org/ajax.json?_t=$now&_r=$nonce",
                "https://call4.tgju.org/ajax.json?_t=$now&_r=$nonce",
                "https://call3.tgju.org/ajax.json?_t=$now&_r=$nonce",
                "https://call2.tgju.org/ajax.json?_t=$now&_r=$nonce",
                "https://call1.tgju.org/ajax.json?_t=$now&_r=$nonce",
                "https://call.tgju.org/ajax.json?_t=$now&_r=$nonce"
            )
            for (endpoint in ajaxEndpoints) {
                if (merged.isTgjuAssetsComplete()) break
                runCatching {
                    val jsonText = downloadText(endpoint, TGJU_COIN_URL)
                    if (!jsonText.isNullOrBlank()) {
                        val (parsed, _) = parseAllFromTgjuAjaxJson(jsonText)
                        merged = merged.mergeMissing(parsed.copy(tether = null))
                    }
                }
            }
        }

        // اولویت چهارم: صفحات تکمیلی TGJU فقط برای دلار، یورو و بیت‌کوین
        if (merged.euro == null || merged.dollar == null) {
            runCatching {
                val html = downloadText("https://www.tgju.org/currency?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html)
                    merged = merged.mergeMissing(parsed.copy(tether = null))
                }
            }
        }
        if (merged.bitcoin == null) {
            runCatching {
                val html = downloadText("https://www.tgju.org/crypto?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html)
                    merged = merged.mergeMissing(parsed.copy(tether = null))
                }
            }
        }

        return merged
    }

    fun getCachedOrDefault(err: String? = null): MarketRatesState {
        return buildCurrentState(errorMessage = err)
    }

    fun hasAnyCachedTgjuRate(): Boolean {
        return prefs.getLong("tgju_v5_dollar_price", 0L) > 0L ||
            prefs.getLong("tgju_v6_retail_sekee_price", 0L) > 0L ||
            prefs.getLong("tgju_v5_gold18_price", 0L) > 0L ||
            prefs.getLong("tgju_v5_bourse_price", 0L) > 0L
    }

    private fun saveItem(editor: android.content.SharedPreferences.Editor, prefix: String, item: MarketRateItem?) {
        item ?: return
        editor.putLong("${prefix}_price", item.priceTomans)
        editor.putLong("${prefix}_diff", item.changeTomans)
        editor.putFloat("${prefix}_pct", item.changePercent.toFloat())
        editor.putBoolean("${prefix}_pos", item.isPositiveChange)
        editor.putString("${prefix}_price_str", item.displayPriceFormatted ?: "")
        editor.putString("${prefix}_diff_str", item.displayDiffFormatted ?: "")
        editor.putString("${prefix}_time", item.timeFormatted)
    }

    private fun loadItem(
        prefix: String,
        key: String,
        title: String,
        unitLabel: String,
        sourceLabel: String = TGJU_SOURCE_LABEL
    ): MarketRateItem {
        val price = prefs.getLong("${prefix}_price", 0L)
        val diff = prefs.getLong("${prefix}_diff", 0L)
        val pct = prefs.getFloat("${prefix}_pct", 0f).toDouble()
        val pos = prefs.getBoolean("${prefix}_pos", true)
        val priceStr = prefs.getString("${prefix}_price_str", "")?.takeIf { it.isNotBlank() }
        val diffStr = prefs.getString("${prefix}_diff_str", "")?.takeIf { it.isNotBlank() }
        val timeStr = prefs.getString("${prefix}_time", "") ?: ""

        return MarketRateItem(
            key = key,
            title = title,
            priceTomans = price,
            changePercent = pct,
            changeTomans = diff,
            isPositiveChange = pos,
            timeFormatted = timeStr,
            unitLabel = unitLabel,
            displayPriceFormatted = priceStr,
            displayDiffFormatted = diffStr,
            sourceLabel = sourceLabel
        )
    }

    private fun saveAllTgjuRates(rates: TgjuExtractedRates, fetchedAtMs: Long) {
        val shamsiUpdate = formatExactRefreshTimestamp(fetchedAtMs)
        prefs.edit().apply {
            saveItem(this, "tgju_v5_dollar", rates.dollar)
            saveItem(this, "tgju_v5_euro", rates.euro)
            saveItem(this, "tgju_v6_retail_sekee", rates.sekehEmami)
            saveItem(this, "tgju_v6_retail_rob", rates.robSekeh)
            saveItem(this, "tgju_v5_ons", rates.goldOunce)
            saveItem(this, "tgju_v5_gold18", rates.gold18k)
            saveItem(this, "tgju_v5_btc", rates.bitcoin)
            saveItem(this, "asiya_v1_tether", rates.tether)
            saveItem(this, "tgju_v5_bourse", rates.bourse)
            saveItem(this, "tgju_v5_oil", rates.crudeOil)
            putString("tgju_v5_last_update", shamsiUpdate)
            apply()
        }
    }

    private fun buildCurrentState(errorMessage: String? = null): MarketRatesState {
        val lastUpdate = prefs.getString("tgju_v5_last_update", "")?.takeIf { it.isNotBlank() } ?: ""

        return MarketRatesState(
            dollar = loadItem("tgju_v5_dollar", "price_dollar_rl", "دلار", "تومان"),
            euro = loadItem("tgju_v5_euro", "price_eur", "یورو", "تومان"),
            sekehEmami = loadItem("tgju_v6_retail_sekee", "retail_sekee", "سکه امامی (تک‌فروشی)", "تومان"),
            robSekeh = loadItem("tgju_v6_retail_rob", "retail_rob", "ربع سکه (تک‌فروشی)", "تومان"),
            goldOunce = loadItem("tgju_v5_ons", "ons", "انس جهانی طلا", "دلار"),
            gold18k = loadItem("tgju_v5_gold18", "geram18", "طلای 18 عیار", "تومان"),
            bitcoin = loadItem("tgju_v5_btc", "crypto-bitcoin", "بیت‌کوین", "دلار"),
            tether = loadItem(
                prefix = "asiya_v1_tether",
                key = "asiya-usdt",
                title = "تتر (USDT)",
                unitLabel = "تومان",
                sourceLabel = AsiyaExchangeService.SOURCE_LABEL
            ),
            bourse = loadItem("tgju_v5_bourse", "bourse", "شاخص کل بورس", "واحد"),
            crudeOil = loadItem("tgju_v5_oil", "oil_brent", "نفت برنت", "دلار"),
            lastUpdateTime = lastUpdate,
            isLoading = false,
            isSuccess = errorMessage == null,
            errorMessage = errorMessage
        )
    }

    private fun downloadText(urlString: String, referer: String): String? {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 14000
            readTimeout = 14000
            instanceFollowRedirects = true
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("Accept", "text/html,application/json,text/plain,*/*")
        }
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            return null
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    data class TgjuExtractedRates(
        val dollar: MarketRateItem? = null,
        val euro: MarketRateItem? = null,
        val sekehEmami: MarketRateItem? = null,
        val robSekeh: MarketRateItem? = null,
        val goldOunce: MarketRateItem? = null,
        val gold18k: MarketRateItem? = null,
        val bitcoin: MarketRateItem? = null,
        val tether: MarketRateItem? = null,
        val bourse: MarketRateItem? = null,
        val crudeOil: MarketRateItem? = null
    ) {
        fun hasAny(): Boolean =
            dollar != null || euro != null || sekehEmami != null || robSekeh != null ||
                goldOunce != null || gold18k != null || bitcoin != null || tether != null ||
                bourse != null || crudeOil != null

        fun isTgjuAssetsComplete(): Boolean =
            dollar != null && euro != null && sekehEmami != null && robSekeh != null &&
                goldOunce != null && gold18k != null && bitcoin != null &&
                bourse != null && crudeOil != null

        fun isComplete(): Boolean =
            isTgjuAssetsComplete() && tether != null

        fun mergeMissing(other: TgjuExtractedRates): TgjuExtractedRates = TgjuExtractedRates(
            dollar = dollar ?: other.dollar,
            euro = euro ?: other.euro,
            sekehEmami = sekehEmami ?: other.sekehEmami,
            robSekeh = robSekeh ?: other.robSekeh,
            goldOunce = goldOunce ?: other.goldOunce,
            gold18k = gold18k ?: other.gold18k,
            bitcoin = bitcoin ?: other.bitcoin,
            tether = tether ?: other.tether,
            bourse = bourse ?: other.bourse,
            crudeOil = crudeOil ?: other.crudeOil
        )

        fun missingWidgetKeys(): List<String> = buildList {
            if (dollar == null) add("price_dollar_rl")
            if (euro == null) add("price_eur")
            if (sekehEmami == null) {
                add("retail_sekee")
                add("sekee")
            }
            if (robSekeh == null) {
                add("retail_rob")
                add("rob")
            }
            if (goldOunce == null) add("ons")
            if (gold18k == null) add("geram18")
            if (bitcoin == null) add("crypto-bitcoin")
            if (tether == null) add("crypto-tether-irr")
            if (bourse == null) add("bourse")
            if (crudeOil == null) add("oil_brent")
        }
    }

    private enum class TgjuValueMode {
        RIAL_TO_TOMAN,
        USD_DECIMAL,
        USD_WHOLE,
        POINTS
    }

    private data class TgjuRawRow(
        val price: Double,
        val changeAmount: Double,
        val changePercent: Double,
        val isPositive: Boolean
    )

    companion object {
        const val TGJU_COIN_URL = "https://www.tgju.org/coin"
        const val TGJU_SOURCE_LABEL = "TGJU (tgju.org/coin)"

        private val HTML_TAG_REGEX = Regex("<[^>]*>")
        private val PERCENT_REGEX = Regex("""\(?([+-]?\d+(?:\.\d+)?)\s*%\)?""")
        private val NUMBER_REGEX = Regex("""[+-]?\d[\d,]*(?:\.\d+)?""")

        /**
         * Parses all 10 market items from TGJU's live ticker JSON feed (`call.tgju.org/ajax.json`).
         */
        fun parseAllFromTgjuAjaxJson(jsonText: String): Pair<TgjuExtractedRates, String> {
            val root = JSONObject(jsonText)
            val current = root.optJSONObject("current") ?: return Pair(TgjuExtractedRates(), "")

            fun firstObj(vararg keys: String): JSONObject? {
                for (k in keys) {
                    val obj = current.optJSONObject(k)
                    if (obj != null && obj.optString("p", "").isNotBlank()) return obj
                }
                return null
            }

            val dollarObj = firstObj("price_dollar_rl")
            val euroObj = firstObj("price_eur")
            val sekeeObj = firstObj("retail_sekee", "sekee")
            val robObj = firstObj("retail_rob", "rob")
            val onsObj = firstObj("ons")
            val gold18Obj = firstObj("geram18")
            val btcObj = firstObj("crypto-bitcoin", "crypto-bitcoin-usd", "bitcoin")
            val tetherObj = firstObj("crypto-tether-irr")
            val bourseObj = firstObj("bourse", "bourse_total", "tse")
            val oilObj = firstObj("oil_brent", "oil")

            val timestamp = listOfNotNull(
                dollarObj, sekeeObj, gold18Obj, bourseObj, oilObj, onsObj, euroObj
            ).firstNotNullOfOrNull { it.optString("ts", "").takeIf { s -> s.isNotBlank() } } ?: ""

            val rates = TgjuExtractedRates(
                dollar = dollarObj?.let { buildItemFromJson(it, "price_dollar_rl", "دلار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) },
                euro = euroObj?.let { buildItemFromJson(it, "price_eur", "یورو", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) },
                sekehEmami = sekeeObj?.let { buildItemFromJson(it, "retail_sekee", "سکه امامی (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 1_000_000.0) },
                robSekeh = robObj?.let { buildItemFromJson(it, "retail_rob", "ربع سکه (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 500_000.0) },
                goldOunce = onsObj?.let { buildItemFromJson(it, "ons", "انس جهانی طلا", TgjuValueMode.USD_DECIMAL, minPrice = 500.0) },
                gold18k = gold18Obj?.let { buildItemFromJson(it, "geram18", "طلای 18 عیار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 100_000.0) },
                bitcoin = btcObj?.let { buildItemFromJson(it, "crypto-bitcoin", "بیت‌کوین", TgjuValueMode.USD_WHOLE, minPrice = 1_000.0) },
                tether = tetherObj?.let { buildItemFromJson(it, "crypto-tether-irr", "تتر (USDT)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) },
                bourse = bourseObj?.let { buildItemFromJson(it, "bourse", "شاخص کل بورس", TgjuValueMode.POINTS, minPrice = 10_000.0) },
                crudeOil = oilObj?.let { buildItemFromJson(it, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0) }
            )
            return Pair(rates, timestamp)
        }

        /**
         * Parses all available market items from TGJU's live widget endpoint (`api.tgju.org/v1/widget/tmp`).
         */
        fun parseAllFromTgjuWidgetJson(widgetJson: String): Pair<TgjuExtractedRates, String> {
            val indicators = JSONObject(widgetJson)
                .optJSONObject("response")
                ?.optJSONArray("indicators")
                ?: return Pair(TgjuExtractedRates(), "")

            var dollar: MarketRateItem? = null
            var euro: MarketRateItem? = null
            var sekee: MarketRateItem? = null
            var rob: MarketRateItem? = null
            var ons: MarketRateItem? = null
            var gold18: MarketRateItem? = null
            var btc: MarketRateItem? = null
            var tether: MarketRateItem? = null
            var bourse: MarketRateItem? = null
            var oil: MarketRateItem? = null
            var timestamp = ""

            for (i in 0 until indicators.length()) {
                val obj = indicators.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val ts = obj.optString("updated_at", obj.optString("ts", obj.optString("t", "")))
                if (timestamp.isBlank() && ts.isNotBlank()) timestamp = ts

                when (name) {
                    "price_dollar_rl" -> if (dollar == null) {
                        dollar = buildItemFromJson(obj, "price_dollar_rl", "دلار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0)
                    }
                    "price_eur" -> if (euro == null) {
                        euro = buildItemFromJson(obj, "price_eur", "یورو", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0)
                    }
                    "retail_sekee", "sekee" -> if (sekee == null || name == "retail_sekee") {
                        buildItemFromJson(obj, "retail_sekee", "سکه امامی (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 1_000_000.0)?.let { sekee = it }
                    }
                    "retail_rob", "rob" -> if (rob == null || name == "retail_rob") {
                        buildItemFromJson(obj, "retail_rob", "ربع سکه (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 500_000.0)?.let { rob = it }
                    }
                    "ons" -> if (ons == null) {
                        ons = buildItemFromJson(obj, "ons", "انس جهانی طلا", TgjuValueMode.USD_DECIMAL, minPrice = 500.0)
                    }
                    "geram18" -> if (gold18 == null) {
                        gold18 = buildItemFromJson(obj, "geram18", "طلای 18 عیار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 100_000.0)
                    }
                    "crypto-bitcoin", "crypto-bitcoin-usd", "bitcoin" -> if (btc == null) {
                        btc = buildItemFromJson(obj, "crypto-bitcoin", "بیت‌کوین", TgjuValueMode.USD_WHOLE, minPrice = 1_000.0)
                    }
                    "crypto-tether-irr" -> if (tether == null) {
                        tether = buildItemFromJson(obj, "crypto-tether-irr", "تتر (USDT)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0)
                    }
                    "bourse", "bourse_total", "tse" -> if (bourse == null) {
                        bourse = buildItemFromJson(obj, "bourse", "شاخص کل بورس", TgjuValueMode.POINTS, minPrice = 10_000.0)
                    }
                    "oil_brent", "oil" -> if (oil == null || name == "oil_brent") {
                        buildItemFromJson(obj, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0)?.let { oil = it }
                    }
                }
            }

            return Pair(
                TgjuExtractedRates(
                    dollar = dollar,
                    euro = euro,
                    sekehEmami = sekee,
                    robSekeh = rob,
                    goldOunce = ons,
                    gold18k = gold18,
                    bitcoin = btc,
                    tether = tether,
                    bourse = bourse,
                    crudeOil = oil
                ),
                timestamp
            )
        }

        /**
         * Extracts ALL available market rates directly from https://www.tgju.org/coin HTML.
         */
        fun parseAllFromTgjuCoinHtml(html: String): TgjuExtractedRates {
            val dollar = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-price_dollar_rl\"", "data-market-row=\"price_dollar_rl\"", "l-price_dollar_rl")
            )?.let { buildItemFromRaw(it, "price_dollar_rl", "دلار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) }

            val euro = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-price_eur\"", "data-market-row=\"price_eur\"", "l-price_eur")
            )?.let { buildItemFromRaw(it, "price_eur", "یورو", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) }

            val sekee = extractRowFromTgjuHtml(
                html,
                listOf(
                    "data-market-row=\"retail_sekee\"",
                    "data-market-nameslug=\"retail_sekee\"",
                    "id=\"l-retail_sekee\"",
                    "profile/retail_sekee",
                    "l-retail_sekee",
                    "id=\"l-sekee\"",
                    "data-market-row=\"sekee\"",
                    "l-sekee",
                    "سکه امامی"
                )
            )?.let { buildItemFromRaw(it, "retail_sekee", "سکه امامی (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 1_000_000.0) }

            val rob = extractRowFromTgjuHtml(
                html,
                listOf(
                    "data-market-row=\"retail_rob\"",
                    "data-market-nameslug=\"retail_rob\"",
                    "id=\"l-retail_rob\"",
                    "profile/retail_rob",
                    "l-retail_rob",
                    "id=\"l-rob\"",
                    "data-market-row=\"rob\"",
                    "l-rob",
                    "ربع سکه"
                )
            )?.let { buildItemFromRaw(it, "retail_rob", "ربع سکه (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 500_000.0) }

            val ons = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-ons\"", "data-market-row=\"ons\"", "l-ons", "انس طلا")
            )?.let { buildItemFromRaw(it, "ons", "انس جهانی طلا", TgjuValueMode.USD_DECIMAL, minPrice = 500.0) }

            val gold18 = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-geram18\"", "data-market-row=\"geram18\"", "l-geram18", "طلای 18 عیار", "طلای ۱۸ عیار")
            )?.let { buildItemFromRaw(it, "geram18", "طلای 18 عیار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 100_000.0) }

            val btc = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-crypto-bitcoin\"", "data-market-row=\"crypto-bitcoin\"", "l-crypto-bitcoin")
            )?.let { buildItemFromRaw(it, "crypto-bitcoin", "بیت‌کوین", TgjuValueMode.USD_WHOLE, minPrice = 1_000.0) }

            val tether = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-crypto-tether-irr\"", "data-market-row=\"crypto-tether-irr\"", "l-crypto-tether-irr")
            )?.let { buildItemFromRaw(it, "crypto-tether-irr", "تتر (USDT)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0) }

            val bourse = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-bourse\"", "data-market-row=\"bourse\"", "l-bourse", "شاخص بورس")
            )?.let { buildItemFromRaw(it, "bourse", "شاخص کل بورس", TgjuValueMode.POINTS, minPrice = 10_000.0) }

            val oil = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-oil_brent\"", "data-market-row=\"oil_brent\"", "l-oil_brent", "نفت برنت")
            )?.let {
                if (it.price in 10.0..500.0) {
                    buildItemFromRaw(it, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0)
                } else null
            }

            return TgjuExtractedRates(
                dollar = dollar,
                euro = euro,
                sekehEmami = sekee,
                robSekeh = rob,
                goldOunce = ons,
                gold18k = gold18,
                bitcoin = btc,
                tether = tether,
                bourse = bourse,
                crudeOil = oil
            )
        }

        /**
         * Backwards-compatible helper used by existing unit tests.
         */
        fun parseBourseAndOilFromTgjuCoinHtml(html: String): Pair<MarketRateItem?, MarketRateItem?> {
            val all = parseAllFromTgjuCoinHtml(html)
            return Pair(all.bourse, all.crudeOil)
        }

        private fun buildItemFromJson(
            obj: JSONObject,
            key: String,
            title: String,
            mode: TgjuValueMode,
            minPrice: Double = 0.0
        ): MarketRateItem? {
            val pDouble = parseCleanDouble(obj.optString("p", "")) ?: return null
            if (pDouble <= 0.0 || pDouble < minPrice) return null

            val rawD = obj.optString("d", "")
            val rawDp = obj.opt("dp")?.toString() ?: ""
            val dSigned = parseCleanDouble(rawD)
            val dpSigned = parseCleanDouble(rawDp) ?: 0.0
            val dt = obj.optString("dt", "").trim()

            val isNegative = dt.equals("low", ignoreCase = true) ||
                (dSigned != null && dSigned < 0.0) ||
                dpSigned < 0.0
            val isPositive = !isNegative

            val absDp = abs(dpSigned)
            var absD = abs(dSigned ?: 0.0)
            if (absD == 0.0 && absDp > 0.0) {
                absD = pDouble * (absDp / 100.0)
            }

            val rawRow = TgjuRawRow(
                price = pDouble,
                changeAmount = absD,
                changePercent = absDp,
                isPositive = isPositive
            )
            return buildItemFromRaw(
                raw = rawRow,
                key = key,
                title = title,
                mode = mode,
                minPrice = minPrice,
                timeFormatted = obj.optString("t", "")
            )
        }

        private fun buildItemFromRaw(
            raw: TgjuRawRow,
            key: String,
            title: String,
            mode: TgjuValueMode,
            minPrice: Double = 0.0,
            timeFormatted: String = ""
        ): MarketRateItem? {
            if (raw.price <= 0.0 || raw.price < minPrice) return null
            val absDp = abs(raw.changePercent)
            val absD = if (raw.changeAmount > 0.0) {
                raw.changeAmount
            } else if (absDp > 0.0) {
                raw.price * (absDp / 100.0)
            } else {
                0.0
            }

            return when (mode) {
                TgjuValueMode.RIAL_TO_TOMAN -> {
                    val priceToman = (raw.price / 10.0).roundToLong()
                    val diffToman = (absD / 10.0).roundToLong()
                    MarketRateItem(
                        key = key,
                        title = title,
                        priceTomans = priceToman,
                        changePercent = absDp,
                        changeTomans = diffToman,
                        isPositiveChange = raw.isPositive,
                        timeFormatted = timeFormatted,
                        unitLabel = "تومان",
                        displayPriceFormatted = null,
                        displayDiffFormatted = null,
                        sourceLabel = TGJU_SOURCE_LABEL
                    )
                }

                TgjuValueMode.USD_DECIMAL -> {
                    val priceStr = "$" + String.format(Locale.US, "%,.2f", raw.price)
                    val diffStr = if (absD >= 0.005) "$" + String.format(Locale.US, "%,.2f", absD) else null
                    MarketRateItem(
                        key = key,
                        title = title,
                        priceTomans = raw.price.roundToLong(),
                        changePercent = absDp,
                        changeTomans = absD.roundToLong(),
                        isPositiveChange = raw.isPositive,
                        timeFormatted = timeFormatted,
                        unitLabel = "دلار",
                        displayPriceFormatted = priceStr,
                        displayDiffFormatted = diffStr,
                        sourceLabel = TGJU_SOURCE_LABEL
                    )
                }

                TgjuValueMode.USD_WHOLE -> {
                    val priceLong = raw.price.roundToLong()
                    val diffLong = absD.roundToLong()
                    val priceStr = "$" + PersianDateHelper.formatCurrency(priceLong, includeUnit = false)
                    val diffStr = if (diffLong > 0L) "$" + PersianDateHelper.formatCurrency(diffLong, includeUnit = false) else null
                    MarketRateItem(
                        key = key,
                        title = title,
                        priceTomans = priceLong,
                        changePercent = absDp,
                        changeTomans = diffLong,
                        isPositiveChange = raw.isPositive,
                        timeFormatted = timeFormatted,
                        unitLabel = "دلار",
                        displayPriceFormatted = priceStr,
                        displayDiffFormatted = diffStr,
                        sourceLabel = TGJU_SOURCE_LABEL
                    )
                }

                TgjuValueMode.POINTS -> {
                    val pts = raw.price.roundToLong()
                    val diffPts = absD.roundToLong()
                    MarketRateItem(
                        key = key,
                        title = title,
                        priceTomans = pts,
                        changePercent = absDp,
                        changeTomans = diffPts,
                        isPositiveChange = raw.isPositive,
                        timeFormatted = timeFormatted,
                        unitLabel = "واحد",
                        displayPriceFormatted = PersianDateHelper.formatCurrency(pts, includeUnit = false),
                        displayDiffFormatted = if (diffPts > 0L) PersianDateHelper.formatCurrency(diffPts, includeUnit = false) else null,
                        sourceLabel = TGJU_SOURCE_LABEL
                    )
                }
            }
        }

        private fun parseCleanDouble(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            val stripped = HTML_TAG_REGEX.replace(raw, " ")
            val normalized = PersianDateHelper.toEnglishDigits(stripped)
                .replace("٬", "")
                .replace(",", "")
                .replace("$", "")
                .replace("٪", "%")
                .trim()
            return NUMBER_REGEX.find(normalized)?.value?.replace(",", "")?.toDoubleOrNull()
        }

        private fun extractRowFromTgjuHtml(
            html: String,
            markers: List<String>
        ): TgjuRawRow? {
            for (marker in markers) {
                var searchFrom = 0
                while (searchFrom < html.length) {
                    val idx = html.indexOf(marker, startIndex = searchFrom, ignoreCase = true)
                    if (idx < 0) break
                    searchFrom = idx + marker.length

                    val liStart = html.lastIndexOf("<li", idx, ignoreCase = true)
                    val trStart = html.lastIndexOf("<tr", idx, ignoreCase = true)
                    val tagStart = maxOf(liStart, trStart).takeIf { it >= 0 && idx - it < 260 } ?: idx
                    val end = minOf(html.length, tagStart + 1400)
                    val snippet = html.substring(tagStart, end)
                    val closeTag = if (snippet.startsWith("<tr", ignoreCase = true)) "</tr>" else "</li>"
                    val closeIdx = snippet.indexOf(closeTag, ignoreCase = true).takeIf { it > 0 }
                        ?: snippet.indexOf("</li>", ignoreCase = true).takeIf { it > 0 }
                        ?: snippet.indexOf("</tr>", ignoreCase = true).takeIf { it > 0 }
                        ?: snippet.length
                    val block = snippet.substring(0, closeIdx)

                    val isNegative = Regex("""class\s*=\s*["'][^"']*\blow\b[^"']*["']""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(block) ||
                        Regex("""data-dt\s*=\s*["']low["']""", RegexOption.IGNORE_CASE).containsMatchIn(block)

                    val dataPriceMatch = Regex("""data-price\s*=\s*["']([\d,\.]+)["']""").find(block)
                    val infoPriceMatches = Regex("""class\s*=\s*["'][^"']*\b(?:info-price|nf)\b[^"']*["'][^>]*>\s*([^<]+)<""").findAll(block).toList()
                    val plainTdMatches = if (dataPriceMatch == null && infoPriceMatches.isEmpty()) {
                        Regex("""<td[^>]*>\s*([0-9۰-۹,٬\.]+)\s*</td>""", RegexOption.IGNORE_CASE).findAll(block).toList()
                    } else emptyList()

                    val candidateStrings = buildList {
                        dataPriceMatch?.groupValues?.get(1)?.let { add(it) }
                        infoPriceMatches.firstOrNull()?.groupValues?.get(1)?.let { add(it) }
                        if (isEmpty()) {
                            infoPriceMatches.forEach { add(it.groupValues[1]) }
                            plainTdMatches.forEach { add(it.groupValues[1]) }
                        }
                    }

                    for (rawPriceStr in candidateStrings) {
                        val priceVal = parseCleanDouble(rawPriceStr)
                        if (priceVal != null && priceVal > 0.0) {
                            // Extract change block: either .info-change or .high/.low span or second nf cell
                            val infoChangeRaw = Regex(
                                """class\s*=\s*["'][^"']*\b(?:info-change|change)\b[^"']*["'][^>]*>([\s\S]*?)</(?:span|td|div)>""",
                                RegexOption.IGNORE_CASE
                            ).find(block)?.groupValues?.get(1)
                                ?: Regex(
                                    """<span[^>]*class\s*=\s*["'][^"']*\b(?:high|low)\b[^"']*["'][^>]*>([\s\S]*?)</span>""",
                                    RegexOption.IGNORE_CASE
                                ).find(block)?.groupValues?.get(1)
                                ?: infoPriceMatches.getOrNull(1)?.groupValues?.get(1)
                                ?: ""

                            val cleanChange = PersianDateHelper.toEnglishDigits(HTML_TAG_REGEX.replace(infoChangeRaw, " "))
                                .replace("٬", ",")
                                .replace("٪", "%")
                                .trim()

                            val pctMatch = PERCENT_REGEX.find(cleanChange)
                            val pctVal = abs(pctMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0)
                            val withoutPct = if (pctMatch != null) {
                                cleanChange.removeRange(pctMatch.range)
                            } else {
                                cleanChange
                            }
                            val amountMatch = NUMBER_REGEX.find(withoutPct)
                            val signedChange = amountMatch?.value?.replace(",", "")?.toDoubleOrNull() ?: 0.0
                            val negFromSign = signedChange < 0.0 ||
                                (pctMatch?.groupValues?.get(1)?.startsWith("-") == true)

                            return TgjuRawRow(
                                price = priceVal,
                                changeAmount = abs(signedChange),
                                changePercent = pctVal,
                                isPositive = !(isNegative || negFromSign)
                            )
                        }
                    }
                }
            }
            return null
        }

        /**
         * Formats the exact live refresh moment (HH:mm:ss  YYYY/MM/DD) using device/local clock.
         */
        fun formatExactRefreshTimestamp(epochMs: Long = System.currentTimeMillis()): String {
            val currentTimeStr = PersianDateHelper.getTimeFormatted(epochMs)
            val currentShamsiStr = PersianDateHelper.getShamsiDate(epochMs).format()
            return "$currentTimeStr  $currentShamsiStr"
        }

        /**
         * Converts any raw Gregorian date/time into Solar Hijri (Shamsi) date format.
         */
        fun formatToShamsiDateTime(raw: String?): String {
            val now = System.currentTimeMillis()
            val currentTimeStr = PersianDateHelper.getTimeFormatted(now)
            val currentShamsiStr = PersianDateHelper.getShamsiDate(now).format()

            if (raw.isNullOrBlank() || raw.equals("هم‌اکنون", ignoreCase = true) || raw.contains("لحظه‌ای")) {
                return "$currentTimeStr  $currentShamsiStr"
            }

            val clean = PersianDateHelper.toEnglishDigits(raw).trim()
            val parts = clean.split(" ")
            var timePart = ""
            var datePart = ""
            for (p in parts) {
                if (p.contains(":")) {
                    timePart = p
                } else if (p.contains("-") || p.contains("/")) {
                    datePart = p
                }
            }

            if (datePart.isNotBlank()) {
                val dSplit = datePart.split(if (datePart.contains("-")) "-" else "/")
                if (dSplit.size >= 3) {
                    val p0 = dSplit[0].toIntOrNull() ?: 0
                    val p1 = dSplit[1].toIntOrNull() ?: 0
                    val p2 = dSplit[2].toIntOrNull() ?: 0
                    val gy: Int
                    val gm: Int
                    val gd: Int
                    if (p0 > 1000) {
                        gy = p0
                        gm = p1
                        gd = p2
                    } else if (p2 > 1000) {
                        gd = p0
                        gm = p1
                        gy = p2
                    } else {
                        gy = 2026
                        gm = p1
                        gd = p0
                    }

                    if (gy in 1900..2100 && gm in 1..12 && gd in 1..31) {
                        val shamsi = PersianDateHelper.gregorianToJalali(gy, gm, gd)
                        val shamsiStr = shamsi.format()
                        val finalTime = if (timePart.isNotBlank()) timePart else currentTimeStr
                        return "$finalTime  $shamsiStr"
                    }
                }
            }

            val finalTime = if (timePart.isNotBlank()) timePart else currentTimeStr
            return "$finalTime  $currentShamsiStr"
        }
    }
}
