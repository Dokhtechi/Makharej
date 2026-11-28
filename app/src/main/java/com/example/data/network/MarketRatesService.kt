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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToLong

class MarketRatesService(private val context: Context) {

    private val prefs = context.getSharedPreferences("market_rates_cache", Context.MODE_PRIVATE)

    /**
     * Fetches live market rates using strictly isolated per-source boundaries:
     * - TGJU (Dollar, Euro, Sekeh Emami, Rob Sekeh, Gold Ounce, 18K Gold, Bitcoin, Brent Oil)
     * - Nobitex (Tether USDT and GRAM from https://nobitex.ir/price/gram)
     * A network error, timeout, or layout change in either source is completely isolated
     * and gracefully falls back to the last cached rates without impacting the rest of the app.
     */
    suspend fun fetchRates(): MarketRatesState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val nonce = UUID.randomUUID().toString().replace("-", "").take(12)
        val todayTehranDay = getTehranShamsiDay(now)

        // Purge any previous-day cached entries immediately so yesterday's prices are never shown
        purgePreviousDayCacheIfNeeded(todayTehranDay)

        var merged = TgjuExtractedRates()
        var tgjuSucceeded = false
        var nobitexSucceeded = false

        supervisorScope {
            // 1. ایزوله‌سازی کامل منبع TGJU با اولویت فید لحظه‌ای زنده (call.tgju.org) و HTML مستقیم tgju.org/coin
            val tgjuResult = try {
                withTimeoutOrNull(20_000L) {
                    fetchTgjuRatesIsolated(now = now, nonce = nonce, todayTehranDay = todayTehranDay)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (tgjuResult != null && tgjuResult.hasAny()) {
                merged = merged.mergeFresher(tgjuResult.copy(tether = null, gram = null), todayTehranDay)
                tgjuSucceeded = true
            }

            // 2. ایزوله‌سازی کامل منبع Nobitex (تتر به تومان و رمزارز گرام به دلار از nobitex.ir/price/gram)
            val prevTetherDay = prefs.getString("nobitex_v1_tether_tehran_day", "") ?: ""
            val prevNobitexTether = if (prevTetherDay == todayTehranDay) {
                prefs.getLong("nobitex_v1_tether_price", 0L)
            } else 0L
            val prevGramDay = prefs.getString("nobitex_v2_gram_usd_tehran_day", "") ?: ""
            val prevNobitexGramUsd = if (prevGramDay == todayTehranDay) {
                prefs.getString("nobitex_v2_gram_usd_price_str", "")
                    ?.replace("$", "")?.replace(",", "")?.trim()?.toDoubleOrNull() ?: 0.0
            } else 0.0

            val nobitexResult = try {
                withTimeoutOrNull(15_000L) {
                    NobitexMarketService.fetchTetherAndGram(
                        previousTetherTomans = prevNobitexTether,
                        previousGramUsd = prevNobitexGramUsd
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                null
            }
            if (nobitexResult != null && nobitexResult.hasAny()) {
                merged = merged.copy(
                    tether = nobitexResult.tether?.copy(
                        sourceTimestampMs = now,
                        sourceTehranDay = todayTehranDay
                    ) ?: merged.tether,
                    gram = nobitexResult.gram?.copy(
                        sourceTimestampMs = now,
                        sourceTehranDay = todayTehranDay
                    ) ?: merged.gram
                )
                nobitexSucceeded = true
            }
        }

        val fetchedAny = tgjuSucceeded || nobitexSucceeded || merged.hasAny()
        if (fetchedAny) {
            saveAllTgjuRates(merged, System.currentTimeMillis())
        }

        val err = if (!fetchedAny) {
            "خطا در دریافت نرخ‌های امروز از tgju.org و nobitex.ir"
        } else {
            null
        }
        buildCurrentState(errorMessage = err)
    }

    /**
     * Dedicated independent refresh for Tether (USDT) ONLY from Nobitex (`nobitex.ir/price/gram`).
     * Updates only Tether in cache without touching any other currency or triggering TGJU requests.
     */
    suspend fun fetchTetherOnly(): Pair<MarketRatesState, Boolean> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todayTehranDay = getTehranShamsiDay(now)
        purgePreviousDayCacheIfNeeded(todayTehranDay)

        val prevTetherDay = prefs.getString("nobitex_v1_tether_tehran_day", "") ?: ""
        val prevNobitexTether = if (prevTetherDay == todayTehranDay) {
            prefs.getLong("nobitex_v1_tether_price", 0L)
        } else 0L

        val freshTether = try {
            withTimeoutOrNull(12_000L) {
                NobitexMarketService.fetchTetherOnly(previousTetherTomans = prevNobitexTether)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }

        if (freshTether != null) {
            val stamped = freshTether.copy(sourceTimestampMs = now, sourceTehranDay = todayTehranDay)
            val shamsiUpdate = formatExactRefreshTimestamp(now)
            prefs.edit().apply {
                saveItemIfFresh(this, "nobitex_v1_tether", stamped, todayTehranDay)
                putString("tgju_v5_last_update", shamsiUpdate)
                putString("tgju_v5_cache_day", todayTehranDay)
                apply()
            }
            return@withContext buildCurrentState(errorMessage = null) to true
        }
        buildCurrentState(errorMessage = null) to false
    }

    /**
     * Live-first TGJU extraction designed specifically to prevent stale intraday caches (11:00-17:00 IRST)
     * and after-17:00 regressions to yesterday's prices:
     * 1) Primary: TGJU's real-time AJAX ticker mirrors (`call.tgju.org` + fresh rotated `call1..5`) — picks the newest server timestamp per item.
     * 2) Secondary / Live Intraday Verification: Direct HTML from `https://www.tgju.org/coin` (and `/currency`, `/crypto` if needed)
     *    whenever any item is missing OR its live ticker timestamp looks older than 2 minutes.
     * 3) Last Resort Only: `api.tgju.org/v1/widget/tmp` (only fills still-missing today items, never overwrites fresh live rates).
     */
    private fun fetchTgjuRatesIsolated(
        now: Long,
        nonce: String,
        todayTehranDay: String
    ): TgjuExtractedRates {
        var merged = TgjuExtractedRates()

        // اولویت اول: تیکر لحظه‌ای رسمی TGJU (منبع زنده سایت tgju.org) — مقایسه دو سرور برای گرفتن جدیدترین تیک
        val rotatedMirrors = listOf(
            "https://call.tgju.org/ajax.json",
            "https://call1.tgju.org/ajax.json",
            "https://call2.tgju.org/ajax.json",
            "https://call3.tgju.org/ajax.json",
            "https://call4.tgju.org/ajax.json",
            "https://call5.tgju.org/ajax.json"
        )
        val mirrorStartIdx = ((now / 1000L) % rotatedMirrors.size).toInt().coerceIn(0, rotatedMirrors.lastIndex)
        val orderedAjaxEndpoints = buildList {
            add(rotatedMirrors[0]) // همیشه call.tgju.org اصلی در صدر
            for (offset in 0 until rotatedMirrors.size) {
                val ep = rotatedMirrors[(mirrorStartIdx + offset) % rotatedMirrors.size]
                if (!contains(ep)) add(ep)
            }
        }

        var successfulMirrors = 0
        for (endpoint in orderedAjaxEndpoints) {
            if (successfulMirrors >= 2 && merged.isTgjuAssetsComplete()) break
            runCatching {
                val url = "$endpoint?_t=$now&_r=$nonce&nc=${UUID.randomUUID().toString().take(8)}"
                val jsonText = downloadText(url, TGJU_COIN_URL)
                if (!jsonText.isNullOrBlank()) {
                    val (parsed, _) = parseAllFromTgjuAjaxJson(jsonText, nowMs = now)
                    val filteredToday = parsed.filterOutPreviousDays(todayTehranDay).copy(tether = null, gram = null)
                    if (filteredToday.hasAny()) {
                        merged = merged.mergeFresher(filteredToday, todayTehranDay)
                        successfulMirrors++
                    }
                }
            }
        }

        // اولویت دوم: استخراج مستقیم از HTML زنده صفحه https://www.tgju.org/coin
        // اگر هر قلمی ناقص باشد یا قیمت دلار/سکه در فید AJAX قدیمی‌تر از 2 دقیقه باشد، صفحه اصلی را مستقیم چک می‌کنیم
        val needsLiveCoinHtml = !merged.isTgjuAssetsComplete() ||
            isStaleIntraday(merged.dollar, now) ||
            isStaleIntraday(merged.sekehEmami, now) ||
            isStaleIntraday(merged.robSekeh, now)

        if (needsLiveCoinHtml) {
            runCatching {
                val html = downloadText("$TGJU_COIN_URL?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html, nowMs = now, todayTehranDay = todayTehranDay)
                        .copy(tether = null, gram = null)
                    // HTML صفحه اصلی tgju.org/coin اگر تازه است، موارد ناقص یا قدیمی‌تر از 2 دقیقه را به‌روزرسانی می‌کند
                    merged = merged.mergeMissingOrStale(parsed, nowMs = now, todayTehranDay = todayTehranDay)
                }
            }
        }

        // اولویت سوم: صفحات تکمیلی TGJU برای دلار، یورو و بیت‌کوین در صورت ناقص بودن
        if (merged.euro == null || merged.dollar == null) {
            runCatching {
                val html = downloadText("https://www.tgju.org/currency?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html, nowMs = now, todayTehranDay = todayTehranDay)
                        .copy(tether = null, gram = null)
                    merged = merged.mergeMissingOrStale(parsed, nowMs = now, todayTehranDay = todayTehranDay)
                }
            }
        }
        if (merged.bitcoin == null) {
            runCatching {
                val html = downloadText("https://www.tgju.org/crypto?_t=$now&_r=$nonce", TGJU_COIN_URL)
                if (!html.isNullOrBlank()) {
                    val parsed = parseAllFromTgjuCoinHtml(html, nowMs = now, todayTehranDay = todayTehranDay)
                        .copy(tether = null, gram = null)
                    merged = merged.mergeMissingOrStale(parsed, nowMs = now, todayTehranDay = todayTehranDay)
                }
            }
        }

        // اولویت آخر (فقط در صورت باقی ماندن قلم ناقص): ویجت api.tgju.org/v1/widget/tmp با فیلتر اکید حذف قیمت دیروز
        if (!merged.isTgjuAssetsComplete()) {
            val allWidgetKeys = listOf(
                "price_dollar_rl", "price_eur", "retail_sekee",
                "retail_rob", "ons", "geram18",
                "crypto-bitcoin", "oil_brent"
            )
            runCatching {
                val widgetUrl = "https://api.tgju.org/v1/widget/tmp?keys=${allWidgetKeys.joinToString(",")}&_t=$now&_r=$nonce"
                val widgetJson = downloadText(widgetUrl, TGJU_COIN_URL)
                if (!widgetJson.isNullOrBlank()) {
                    val (parsed, _) = parseAllFromTgjuWidgetJson(widgetJson, nowMs = now)
                    val filteredToday = parsed.filterOutPreviousDays(todayTehranDay).copy(tether = null, gram = null)
                    merged = merged.mergeFresher(filteredToday, todayTehranDay)
                }
            }
        }

        return merged
    }

    private fun isStaleIntraday(item: MarketRateItem?, nowMs: Long): Boolean {
        if (item == null) return true
        if (item.sourceTimestampMs <= 0L) return false
        return (nowMs - item.sourceTimestampMs) > 120_000L
    }

    fun getCachedOrDefault(err: String? = null): MarketRatesState {
        purgePreviousDayCacheIfNeeded(getTehranShamsiDay())
        return buildCurrentState(errorMessage = err)
    }

    fun hasAnyCachedTgjuRate(): Boolean {
        val todayTehranDay = getTehranShamsiDay()
        purgePreviousDayCacheIfNeeded(todayTehranDay)
        return (prefs.getString("tgju_v5_dollar_tehran_day", "") == todayTehranDay && prefs.getLong("tgju_v5_dollar_price", 0L) > 0L) ||
            (prefs.getString("tgju_v6_retail_sekee_tehran_day", "") == todayTehranDay && prefs.getLong("tgju_v6_retail_sekee_price", 0L) > 0L) ||
            (prefs.getString("tgju_v5_gold18_tehran_day", "") == todayTehranDay && prefs.getLong("tgju_v5_gold18_price", 0L) > 0L) ||
            (prefs.getString("nobitex_v2_gram_usd_tehran_day", "") == todayTehranDay && !prefs.getString("nobitex_v2_gram_usd_price_str", "").isNullOrBlank()) ||
            (prefs.getString("nobitex_v1_tether_tehran_day", "") == todayTehranDay && prefs.getLong("nobitex_v1_tether_price", 0L) > 0L)
    }

    /**
     * Purges cached market prices from previous days (Tehran calendar day) so yesterday's
     * closing prices are never displayed when the user opens or refreshes the app.
     */
    private fun purgePreviousDayCacheIfNeeded(todayTehranDay: String) {
        val savedDay = prefs.getString("tgju_v5_cache_day", "") ?: ""
        if (savedDay == todayTehranDay) return
        val editor = prefs.edit()
        for (prefix in ALL_CACHE_PREFIXES) {
            val itemDay = prefs.getString("${prefix}_tehran_day", "") ?: ""
            if (itemDay != todayTehranDay) {
                editor.remove("${prefix}_price")
                editor.remove("${prefix}_diff")
                editor.remove("${prefix}_pct")
                editor.remove("${prefix}_pos")
                editor.remove("${prefix}_price_str")
                editor.remove("${prefix}_diff_str")
                editor.remove("${prefix}_time")
                editor.remove("${prefix}_source_ts")
                editor.remove("${prefix}_tehran_day")
            }
        }
        editor.remove("tgju_v5_last_update")
        editor.putString("tgju_v5_cache_day", todayTehranDay)
        editor.apply()
    }

    private fun saveItemIfFresh(
        editor: android.content.SharedPreferences.Editor,
        prefix: String,
        item: MarketRateItem?,
        todayTehranDay: String
    ) {
        item ?: return
        val existingDay = prefs.getString("${prefix}_tehran_day", "") ?: ""
        val existingTs = prefs.getLong("${prefix}_source_ts", 0L)
        if (!shouldAcceptIncomingRate(existingDay, existingTs, item, todayTehranDay)) {
            return
        }
        val effectiveDay = item.sourceTehranDay.takeIf { it.isNotBlank() } ?: todayTehranDay
        editor.putLong("${prefix}_price", item.priceTomans)
        editor.putLong("${prefix}_diff", item.changeTomans)
        editor.putFloat("${prefix}_pct", item.changePercent.toFloat())
        editor.putBoolean("${prefix}_pos", item.isPositiveChange)
        editor.putString("${prefix}_price_str", item.displayPriceFormatted ?: "")
        editor.putString("${prefix}_diff_str", item.displayDiffFormatted ?: "")
        editor.putString("${prefix}_time", item.timeFormatted)
        editor.putLong("${prefix}_source_ts", item.sourceTimestampMs)
        editor.putString("${prefix}_tehran_day", effectiveDay)
    }

    private fun loadItem(
        prefix: String,
        key: String,
        title: String,
        unitLabel: String,
        todayTehranDay: String,
        sourceLabel: String = TGJU_SOURCE_LABEL
    ): MarketRateItem {
        val savedDay = prefs.getString("${prefix}_tehran_day", "") ?: ""
        // Strictly reject previous-day cached prices so yesterday's rate is never shown
        if (savedDay != todayTehranDay) {
            return MarketRateItem(
                key = key,
                title = title,
                priceTomans = 0L,
                changePercent = 0.0,
                changeTomans = 0L,
                isPositiveChange = true,
                timeFormatted = "",
                unitLabel = unitLabel,
                displayPriceFormatted = null,
                displayDiffFormatted = null,
                sourceLabel = sourceLabel,
                sourceTimestampMs = 0L,
                sourceTehranDay = ""
            )
        }

        val price = prefs.getLong("${prefix}_price", 0L)
        val diff = prefs.getLong("${prefix}_diff", 0L)
        val pct = prefs.getFloat("${prefix}_pct", 0f).toDouble()
        val pos = prefs.getBoolean("${prefix}_pos", true)
        val priceStr = prefs.getString("${prefix}_price_str", "")?.takeIf { it.isNotBlank() }
        val diffStr = prefs.getString("${prefix}_diff_str", "")?.takeIf { it.isNotBlank() }
        val timeStr = prefs.getString("${prefix}_time", "") ?: ""
        val sourceTs = prefs.getLong("${prefix}_source_ts", 0L)

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
            sourceLabel = sourceLabel,
            sourceTimestampMs = sourceTs,
            sourceTehranDay = savedDay
        )
    }

    private fun saveAllTgjuRates(rates: TgjuExtractedRates, fetchedAtMs: Long) {
        val todayTehranDay = getTehranShamsiDay(fetchedAtMs)
        purgePreviousDayCacheIfNeeded(todayTehranDay)
        val shamsiUpdate = formatExactRefreshTimestamp(fetchedAtMs)
        prefs.edit().apply {
            saveItemIfFresh(this, "tgju_v5_dollar", rates.dollar, todayTehranDay)
            saveItemIfFresh(this, "tgju_v5_euro", rates.euro, todayTehranDay)
            saveItemIfFresh(this, "tgju_v6_retail_sekee", rates.sekehEmami, todayTehranDay)
            saveItemIfFresh(this, "tgju_v6_retail_rob", rates.robSekeh, todayTehranDay)
            saveItemIfFresh(this, "tgju_v5_ons", rates.goldOunce, todayTehranDay)
            saveItemIfFresh(this, "tgju_v5_gold18", rates.gold18k, todayTehranDay)
            saveItemIfFresh(this, "tgju_v5_btc", rates.bitcoin, todayTehranDay)
            saveItemIfFresh(this, "nobitex_v1_tether", rates.tether, todayTehranDay)
            saveItemIfFresh(this, "nobitex_v2_gram_usd", rates.gram, todayTehranDay)
            saveItemIfFresh(this, "tgju_v5_oil", rates.crudeOil, todayTehranDay)
            putString("tgju_v5_last_update", shamsiUpdate)
            putString("tgju_v5_cache_day", todayTehranDay)
            apply()
        }
    }

    private fun buildCurrentState(errorMessage: String? = null): MarketRatesState {
        val todayTehranDay = getTehranShamsiDay()
        val cacheDay = prefs.getString("tgju_v5_cache_day", "") ?: ""
        val lastUpdate = if (cacheDay == todayTehranDay) {
            prefs.getString("tgju_v5_last_update", "")?.takeIf { it.isNotBlank() } ?: ""
        } else ""

        return MarketRatesState(
            dollar = loadItem("tgju_v5_dollar", "price_dollar_rl", "دلار", "تومان", todayTehranDay),
            euro = loadItem("tgju_v5_euro", "price_eur", "یورو", "تومان", todayTehranDay),
            sekehEmami = loadItem("tgju_v6_retail_sekee", "retail_sekee", "سکه امامی (تک‌فروشی)", "تومان", todayTehranDay),
            robSekeh = loadItem("tgju_v6_retail_rob", "retail_rob", "ربع سکه (تک‌فروشی)", "تومان", todayTehranDay),
            goldOunce = loadItem("tgju_v5_ons", "ons", "انس جهانی طلا", "دلار", todayTehranDay),
            gold18k = loadItem("tgju_v5_gold18", "geram18", "طلای 18 عیار", "تومان", todayTehranDay),
            bitcoin = loadItem("tgju_v5_btc", "crypto-bitcoin", "بیت‌کوین", "دلار", todayTehranDay),
            tether = loadItem(
                prefix = "nobitex_v1_tether",
                key = "nobitex-usdt",
                title = "تتر (USDT)",
                unitLabel = "تومان",
                todayTehranDay = todayTehranDay,
                sourceLabel = NobitexMarketService.SOURCE_LABEL
            ),
            gram = loadItem(
                prefix = "nobitex_v2_gram_usd",
                key = "nobitex-gram",
                title = "گرام (GRAM)",
                unitLabel = "دلار",
                todayTehranDay = todayTehranDay,
                sourceLabel = NobitexMarketService.SOURCE_LABEL
            ),
            crudeOil = loadItem("tgju_v5_oil", "oil_brent", "نفت برنت", "دلار", todayTehranDay),
            lastUpdateTime = lastUpdate,
            isLoading = false,
            isTetherRefreshing = false,
            isSuccess = errorMessage == null,
            errorMessage = errorMessage
        )
    }

    private fun downloadText(urlString: String, referer: String): String? {
        val url = URL(urlString)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            useCaches = false
            defaultUseCaches = false
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Expires", "0")
            setRequestProperty("Connection", "close")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )
            setRequestProperty("Referer", referer)
            setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8,en;q=0.7")
            setRequestProperty("Accept", "application/json,text/html,text/plain,*/*")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            runCatching { connection.disconnect() }
        }
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
        val gram: MarketRateItem? = null,
        val crudeOil: MarketRateItem? = null
    ) {
        fun hasAny(): Boolean =
            dollar != null || euro != null || sekehEmami != null || robSekeh != null ||
                goldOunce != null || gold18k != null || bitcoin != null || tether != null ||
                gram != null || crudeOil != null

        fun isTgjuAssetsComplete(): Boolean =
            dollar != null && euro != null && sekehEmami != null && robSekeh != null &&
                goldOunce != null && gold18k != null && bitcoin != null && crudeOil != null

        fun isComplete(): Boolean =
            isTgjuAssetsComplete() && tether != null && gram != null

        fun mergeMissing(other: TgjuExtractedRates): TgjuExtractedRates = TgjuExtractedRates(
            dollar = dollar ?: other.dollar,
            euro = euro ?: other.euro,
            sekehEmami = sekehEmami ?: other.sekehEmami,
            robSekeh = robSekeh ?: other.robSekeh,
            goldOunce = goldOunce ?: other.goldOunce,
            gold18k = gold18k ?: other.gold18k,
            bitcoin = bitcoin ?: other.bitcoin,
            tether = tether ?: other.tether,
            gram = gram ?: other.gram,
            crudeOil = crudeOil ?: other.crudeOil
        )

        fun filterOutPreviousDays(todayTehranDay: String): TgjuExtractedRates {
            fun keep(item: MarketRateItem?): MarketRateItem? =
                if (item == null || isFromPreviousDay(item.sourceTehranDay, todayTehranDay)) null else item
            return TgjuExtractedRates(
                dollar = keep(dollar),
                euro = keep(euro),
                sekehEmami = keep(sekehEmami),
                robSekeh = keep(robSekeh),
                goldOunce = keep(goldOunce),
                gold18k = keep(gold18k),
                bitcoin = keep(bitcoin),
                tether = keep(tether),
                gram = keep(gram),
                crudeOil = keep(crudeOil)
            )
        }

        fun mergeFresher(other: TgjuExtractedRates, todayTehranDay: String): TgjuExtractedRates = TgjuExtractedRates(
            dollar = pickFresherRate(dollar, other.dollar, todayTehranDay),
            euro = pickFresherRate(euro, other.euro, todayTehranDay),
            sekehEmami = pickFresherRate(sekehEmami, other.sekehEmami, todayTehranDay),
            robSekeh = pickFresherRate(robSekeh, other.robSekeh, todayTehranDay),
            goldOunce = pickFresherRate(goldOunce, other.goldOunce, todayTehranDay),
            gold18k = pickFresherRate(gold18k, other.gold18k, todayTehranDay),
            bitcoin = pickFresherRate(bitcoin, other.bitcoin, todayTehranDay),
            tether = pickFresherRate(tether, other.tether, todayTehranDay),
            gram = pickFresherRate(gram, other.gram, todayTehranDay),
            crudeOil = pickFresherRate(crudeOil, other.crudeOil, todayTehranDay)
        )

        fun mergeMissingOrStale(
            other: TgjuExtractedRates,
            nowMs: Long,
            todayTehranDay: String
        ): TgjuExtractedRates {
            fun resolve(cur: MarketRateItem?, cand: MarketRateItem?): MarketRateItem? {
                if (cand == null || isFromPreviousDay(cand.sourceTehranDay, todayTehranDay)) return cur
                if (cur == null) return cand
                val curAgeMs = if (cur.sourceTimestampMs > 0L) nowMs - cur.sourceTimestampMs else Long.MAX_VALUE
                return if (curAgeMs > 120_000L) cand else cur
            }
            return TgjuExtractedRates(
                dollar = resolve(dollar, other.dollar),
                euro = resolve(euro, other.euro),
                sekehEmami = resolve(sekehEmami, other.sekehEmami),
                robSekeh = resolve(robSekeh, other.robSekeh),
                goldOunce = resolve(goldOunce, other.goldOunce),
                gold18k = resolve(gold18k, other.gold18k),
                bitcoin = resolve(bitcoin, other.bitcoin),
                tether = resolve(tether, other.tether),
                gram = resolve(gram, other.gram),
                crudeOil = resolve(crudeOil, other.crudeOil)
            )
        }

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
            if (gram == null) add("crypto-gram")
            if (crudeOil == null) add("oil_brent")
        }
    }

    private enum class TgjuValueMode {
        RIAL_TO_TOMAN,
        USD_DECIMAL,
        USD_CRYPTO_PRECISE,
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
        const val TGJU_GRAM_SOURCE_LABEL = "TGJU (tgju.org/profile/crypto-gram)"

        private val TEHRAN_TZ: TimeZone = TimeZone.getTimeZone("Asia/Tehran")
        private val ALL_CACHE_PREFIXES = listOf(
            "tgju_v5_dollar", "tgju_v5_euro", "tgju_v6_retail_sekee", "tgju_v6_retail_rob",
            "tgju_v5_ons", "tgju_v5_gold18", "tgju_v5_btc", "nobitex_v1_tether",
            "nobitex_v2_gram_usd", "tgju_v5_oil"
        )

        private val HTML_TAG_REGEX = Regex("<[^>]*>")
        private val PERCENT_REGEX = Regex("""\(?([+-]?\d+(?:\.\d+)?)\s*%\)?""")
        private val NUMBER_REGEX = Regex("""[+-]?\d[\d,]*(?:\.\d+)?""")

        /**
         * Returns today's Solar Hijri (Shamsi) date ("YYYY/MM/DD") in Iran Standard Time (Asia/Tehran).
         */
        fun getTehranShamsiDay(epochMs: Long = System.currentTimeMillis()): String {
            val cal = Calendar.getInstance(TEHRAN_TZ).apply { timeInMillis = epochMs }
            return PersianDateHelper.gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            ).format()
        }

        /**
         * Returns true when a rate's Tehran Shamsi date string is strictly older than today.
         */
        fun isFromPreviousDay(sourceTehranDay: String, todayTehranDay: String): Boolean {
            val clean = PersianDateHelper.toEnglishDigits(sourceTehranDay).trim()
            if (clean.isBlank() || todayTehranDay.isBlank()) return false
            return clean < todayTehranDay
        }

        /**
         * Guards cache updates against yesterday's prices (e.g. after 17:00 IRST) and lagging CDN mirrors.
         */
        fun shouldAcceptIncomingRate(
            existingDay: String,
            existingTsMs: Long,
            incoming: MarketRateItem,
            todayTehranDay: String
        ): Boolean {
            if (isFromPreviousDay(incoming.sourceTehranDay, todayTehranDay)) {
                return false
            }
            if (existingDay == todayTehranDay && existingTsMs > 0L &&
                incoming.sourceTimestampMs > 0L && incoming.sourceTimestampMs < existingTsMs
            ) {
                return false
            }
            return true
        }

        /**
         * Selects the fresher of two MarketRateItem candidates for today, rejecting any previous-day rate.
         */
        fun pickFresherRate(
            current: MarketRateItem?,
            candidate: MarketRateItem?,
            todayTehranDay: String
        ): MarketRateItem? {
            val validCur = current?.takeUnless { isFromPreviousDay(it.sourceTehranDay, todayTehranDay) }
            val validCand = candidate?.takeUnless { isFromPreviousDay(it.sourceTehranDay, todayTehranDay) }
            if (validCur == null) return validCand
            if (validCand == null) return validCur
            return when {
                validCand.sourceTimestampMs > validCur.sourceTimestampMs -> validCand
                validCur.sourceTimestampMs > validCand.sourceTimestampMs -> validCur
                else -> validCur
            }
        }

        /**
         * Parses TGJU's per-item timestamp (`ts` / `updated_at` Gregorian datetime and/or `t` Shamsi date or HH:mm:ss)
         * into `(epochMs, tehranShamsiDay)`.
         */
        fun parseTgjuItemTimestamp(
            tsRaw: String?,
            tRaw: String?,
            nowMs: Long = System.currentTimeMillis()
        ): Pair<Long, String> {
            val todayTehran = getTehranShamsiDay(nowMs)

            // 1. Check `ts` / `updated_at` (e.g. "2026-11-25 14:25:10" or "2026-11-25T14:25:10")
            val cleanTs = PersianDateHelper.toEnglishDigits(tsRaw.orEmpty()).trim().replace('T', ' ')
            val dateTimeMatch = Regex("""(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?""")
                .find(cleanTs)
            if (dateTimeMatch != null) {
                val y = dateTimeMatch.groupValues[1].toIntOrNull() ?: 0
                val m = dateTimeMatch.groupValues[2].toIntOrNull() ?: 0
                val d = dateTimeMatch.groupValues[3].toIntOrNull() ?: 0
                val hh = dateTimeMatch.groupValues[4].toIntOrNull() ?: 12
                val mm = dateTimeMatch.groupValues[5].toIntOrNull() ?: 0
                val ss = dateTimeMatch.groupValues[6].toIntOrNull() ?: 0

                if (y in 1900..2100 && m in 1..12 && d in 1..31) {
                    val cal = Calendar.getInstance(TEHRAN_TZ).apply {
                        set(Calendar.YEAR, y)
                        set(Calendar.MONTH, m - 1)
                        set(Calendar.DAY_OF_MONTH, d)
                        set(Calendar.HOUR_OF_DAY, hh.coerceIn(0, 23))
                        set(Calendar.MINUTE, mm.coerceIn(0, 59))
                        set(Calendar.SECOND, ss.coerceIn(0, 59))
                        set(Calendar.MILLISECOND, 0)
                    }
                    val shamsi = PersianDateHelper.gregorianToJalali(y, m, d).format()
                    return Pair(cal.timeInMillis, shamsi)
                } else if (y in 1300..1500 && m in 1..12 && d in 1..31) {
                    val (gy, gm, gd) = PersianDateHelper.jalaliToGregorian(y, m, d)
                    val cal = Calendar.getInstance(TEHRAN_TZ).apply {
                        set(Calendar.YEAR, gy)
                        set(Calendar.MONTH, gm - 1)
                        set(Calendar.DAY_OF_MONTH, gd)
                        set(Calendar.HOUR_OF_DAY, hh.coerceIn(0, 23))
                        set(Calendar.MINUTE, mm.coerceIn(0, 59))
                        set(Calendar.SECOND, ss.coerceIn(0, 59))
                        set(Calendar.MILLISECOND, 0)
                    }
                    val shamsi = "%04d/%02d/%02d".format(Locale.US, y, m, d)
                    return Pair(cal.timeInMillis, shamsi)
                }
            }

            // 2. Check `t` field: TGJU puts Shamsi date (e.g. "1405/09/03") when NOT updated today,
            //    and puts clock time ("14:25:10") when updated today!
            val cleanT = PersianDateHelper.toEnglishDigits(tRaw.orEmpty()).trim()
            val shamsiDateMatch = Regex("""(1[34]\d{2})/(\d{1,2})/(\d{1,2})""").find(cleanT)
            if (shamsiDateMatch != null) {
                val jy = shamsiDateMatch.groupValues[1].toIntOrNull() ?: 0
                val jm = shamsiDateMatch.groupValues[2].toIntOrNull() ?: 0
                val jd = shamsiDateMatch.groupValues[3].toIntOrNull() ?: 0
                if (jy in 1300..1500 && jm in 1..12 && jd in 1..31) {
                    val (gy, gm, gd) = PersianDateHelper.jalaliToGregorian(jy, jm, jd)
                    val cal = Calendar.getInstance(TEHRAN_TZ).apply {
                        set(Calendar.YEAR, gy)
                        set(Calendar.MONTH, gm - 1)
                        set(Calendar.DAY_OF_MONTH, gd)
                        set(Calendar.HOUR_OF_DAY, 17)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    val shamsi = "%04d/%02d/%02d".format(Locale.US, jy, jm, jd)
                    return Pair(cal.timeInMillis, shamsi)
                }
            }

            val timeOnlyMatch = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(cleanT)
            if (timeOnlyMatch != null) {
                val hh = timeOnlyMatch.groupValues[1].toIntOrNull() ?: 0
                val mm = timeOnlyMatch.groupValues[2].toIntOrNull() ?: 0
                val ss = timeOnlyMatch.groupValues[3].toIntOrNull() ?: 0
                val cal = Calendar.getInstance(TEHRAN_TZ).apply {
                    timeInMillis = nowMs
                    set(Calendar.HOUR_OF_DAY, hh.coerceIn(0, 23))
                    set(Calendar.MINUTE, mm.coerceIn(0, 59))
                    set(Calendar.SECOND, ss.coerceIn(0, 59))
                    set(Calendar.MILLISECOND, 0)
                }
                return Pair(cal.timeInMillis, todayTehran)
            }

            return Pair(0L, "")
        }

        /**
         * Parses all 10 market items from TGJU's live ticker JSON feed (`call.tgju.org/ajax.json`).
         */
        fun parseAllFromTgjuAjaxJson(
            jsonText: String,
            nowMs: Long = System.currentTimeMillis()
        ): Pair<TgjuExtractedRates, String> {
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
            val gramObj = firstObj("crypto-gram", "crypto-gram-usd", "gram", "crypto-toncoin")
            val oilObj = firstObj("oil_brent", "oil")

            val timestamp = listOfNotNull(
                dollarObj, sekeeObj, gold18Obj, gramObj, oilObj, onsObj, euroObj
            ).firstNotNullOfOrNull { it.optString("ts", "").takeIf { s -> s.isNotBlank() } } ?: ""

            val rates = TgjuExtractedRates(
                dollar = dollarObj?.let { buildItemFromJson(it, "price_dollar_rl", "دلار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs) },
                euro = euroObj?.let { buildItemFromJson(it, "price_eur", "یورو", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs) },
                sekehEmami = sekeeObj?.let { buildItemFromJson(it, "retail_sekee", "سکه امامی (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 1_000_000.0, nowMs = nowMs) },
                robSekeh = robObj?.let { buildItemFromJson(it, "retail_rob", "ربع سکه (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 500_000.0, nowMs = nowMs) },
                goldOunce = onsObj?.let { buildItemFromJson(it, "ons", "انس جهانی طلا", TgjuValueMode.USD_DECIMAL, minPrice = 500.0, nowMs = nowMs) },
                gold18k = gold18Obj?.let { buildItemFromJson(it, "geram18", "طلای 18 عیار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 100_000.0, nowMs = nowMs) },
                bitcoin = btcObj?.let { buildItemFromJson(it, "crypto-bitcoin", "بیت‌کوین", TgjuValueMode.USD_WHOLE, minPrice = 1_000.0, nowMs = nowMs) },
                tether = tetherObj?.let { buildItemFromJson(it, "crypto-tether-irr", "تتر (USDT)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs) },
                gram = gramObj?.let { buildItemFromJson(it, "crypto-gram", "گرام (GRAM)", TgjuValueMode.USD_CRYPTO_PRECISE, minPrice = 0.000001, nowMs = nowMs) },
                crudeOil = oilObj?.let { buildItemFromJson(it, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0, nowMs = nowMs) }
            )
            return Pair(rates, timestamp)
        }

        /**
         * Parses all available market items from TGJU's live widget endpoint (`api.tgju.org/v1/widget/tmp`).
         */
        fun parseAllFromTgjuWidgetJson(
            widgetJson: String,
            nowMs: Long = System.currentTimeMillis()
        ): Pair<TgjuExtractedRates, String> {
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
            var gram: MarketRateItem? = null
            var oil: MarketRateItem? = null
            var timestamp = ""

            for (i in 0 until indicators.length()) {
                val obj = indicators.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val ts = obj.optString("updated_at", obj.optString("ts", obj.optString("t", "")))
                if (timestamp.isBlank() && ts.isNotBlank()) timestamp = ts

                when (name) {
                    "price_dollar_rl" -> if (dollar == null) {
                        dollar = buildItemFromJson(obj, "price_dollar_rl", "دلار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs)
                    }
                    "price_eur" -> if (euro == null) {
                        euro = buildItemFromJson(obj, "price_eur", "یورو", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs)
                    }
                    "retail_sekee", "sekee" -> if (sekee == null || name == "retail_sekee") {
                        buildItemFromJson(obj, "retail_sekee", "سکه امامی (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 1_000_000.0, nowMs = nowMs)?.let { sekee = it }
                    }
                    "retail_rob", "rob" -> if (rob == null || name == "retail_rob") {
                        buildItemFromJson(obj, "retail_rob", "ربع سکه (تک‌فروشی)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 500_000.0, nowMs = nowMs)?.let { rob = it }
                    }
                    "ons" -> if (ons == null) {
                        ons = buildItemFromJson(obj, "ons", "انس جهانی طلا", TgjuValueMode.USD_DECIMAL, minPrice = 500.0, nowMs = nowMs)
                    }
                    "geram18" -> if (gold18 == null) {
                        gold18 = buildItemFromJson(obj, "geram18", "طلای 18 عیار", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 100_000.0, nowMs = nowMs)
                    }
                    "crypto-bitcoin", "crypto-bitcoin-usd", "bitcoin" -> if (btc == null) {
                        btc = buildItemFromJson(obj, "crypto-bitcoin", "بیت‌کوین", TgjuValueMode.USD_WHOLE, minPrice = 1_000.0, nowMs = nowMs)
                    }
                    "crypto-tether-irr" -> if (tether == null) {
                        tether = buildItemFromJson(obj, "crypto-tether-irr", "تتر (USDT)", TgjuValueMode.RIAL_TO_TOMAN, minPrice = 10_000.0, nowMs = nowMs)
                    }
                    "crypto-gram", "crypto-gram-usd", "gram", "crypto-toncoin" -> if (gram == null || name == "crypto-gram") {
                        buildItemFromJson(obj, "crypto-gram", "گرام (GRAM)", TgjuValueMode.USD_CRYPTO_PRECISE, minPrice = 0.000001, nowMs = nowMs)?.let { gram = it }
                    }
                    "oil_brent", "oil" -> if (oil == null || name == "oil_brent") {
                        buildItemFromJson(obj, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0, nowMs = nowMs)?.let { oil = it }
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
                    gram = gram,
                    crudeOil = oil
                ),
                timestamp
            )
        }

        /**
         * Extracts ALL available market rates directly from https://www.tgju.org/coin HTML.
         */
        fun parseAllFromTgjuCoinHtml(
            html: String,
            nowMs: Long = System.currentTimeMillis(),
            todayTehranDay: String = getTehranShamsiDay(nowMs)
        ): TgjuExtractedRates {
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

            val gram = extractRowFromTgjuHtml(
                html,
                listOf(
                    "data-market-row=\"crypto-gram\"",
                    "data-market-nameslug=\"crypto-gram\"",
                    "id=\"l-crypto-gram\"",
                    "profile/crypto-gram",
                    "l-crypto-gram",
                    "data-market-row=\"crypto-toncoin\"",
                    "id=\"l-crypto-toncoin\"",
                    "profile/crypto-toncoin"
                )
            )?.let { buildItemFromRaw(it, "crypto-gram", "گرام (GRAM)", TgjuValueMode.USD_CRYPTO_PRECISE, minPrice = 0.000001) }

            val oil = extractRowFromTgjuHtml(
                html,
                listOf("id=\"l-oil_brent\"", "data-market-row=\"oil_brent\"", "l-oil_brent", "نفت برنت")
            )?.let {
                if (it.price in 10.0..500.0) {
                    buildItemFromRaw(it, "oil_brent", "نفت برنت", TgjuValueMode.USD_DECIMAL, minPrice = 10.0)
                } else null
            }

            fun stampHtml(item: MarketRateItem?): MarketRateItem? =
                item?.copy(
                    sourceTimestampMs = if (item.sourceTimestampMs > 0L) item.sourceTimestampMs else nowMs,
                    sourceTehranDay = item.sourceTehranDay.ifBlank { todayTehranDay }
                )

            return TgjuExtractedRates(
                dollar = stampHtml(dollar),
                euro = stampHtml(euro),
                sekehEmami = stampHtml(sekee),
                robSekeh = stampHtml(rob),
                goldOunce = stampHtml(ons),
                gold18k = stampHtml(gold18),
                bitcoin = stampHtml(btc),
                tether = stampHtml(tether),
                gram = stampHtml(gram),
                crudeOil = stampHtml(oil)
            )
        }

        /**
         * Extracts live GRAM price directly from TGJU's profile page HTML (`tgju.org/profile/crypto-gram`).
         */
        fun parseGramFromProfileHtml(html: String): MarketRateItem? {
            extractRowFromTgjuHtml(
                html,
                listOf(
                    "data-market-row=\"crypto-gram\"",
                    "id=\"l-crypto-gram\"",
                    "profile/crypto-gram",
                    "data-market-row=\"crypto-toncoin\"",
                    "id=\"l-crypto-toncoin\""
                )
            )?.let {
                return buildItemFromRaw(it, "crypto-gram", "گرام (GRAM)", TgjuValueMode.USD_CRYPTO_PRECISE, minPrice = 0.000001)
            }

            val priceMatch = Regex(
                """data-col\s*=\s*["']info\.last_trade\.PDrCotVal["'][^>]*>\s*([^<]+)<""",
                RegexOption.IGNORE_CASE
            ).find(html) ?: Regex(
                """class\s*=\s*["'][^"']*\binfo-price\b[^"']*["'][^>]*>\s*([^<]+)<""",
                RegexOption.IGNORE_CASE
            ).find(html)

            val priceVal = parseCleanDouble(priceMatch?.groupValues?.get(1)) ?: return null
            if (priceVal <= 0.0) return null

            val diffMatch = Regex(
                """data-col\s*=\s*["']info\.last_trade\.PDrCotVal_diff["'][^>]*>\s*([^<]+)<""",
                RegexOption.IGNORE_CASE
            ).find(html)
            val pctMatch = Regex(
                """data-col\s*=\s*["']info\.last_trade\.PDrCotVal_percent["'][^>]*>\s*([^<]+)<""",
                RegexOption.IGNORE_CASE
            ).find(html)
            val diffVal = parseCleanDouble(diffMatch?.groupValues?.get(1)) ?: 0.0
            val pctVal = parseCleanDouble(pctMatch?.groupValues?.get(1)) ?: 0.0
            val isNeg = Regex("""data-col\s*=\s*["']info\.last_trade\.PDrCotVal["'][^>]*class\s*=\s*["'][^"']*\blow\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(html) || diffVal < 0.0 || pctVal < 0.0

            return buildItemFromRaw(
                raw = TgjuRawRow(
                    price = priceVal,
                    changeAmount = abs(diffVal),
                    changePercent = abs(pctVal),
                    isPositive = !isNeg
                ),
                key = "crypto-gram",
                title = "گرام (GRAM)",
                mode = TgjuValueMode.USD_CRYPTO_PRECISE,
                minPrice = 0.000001
            )
        }

        /**
         * Helper used by unit tests to verify GRAM and Brent Oil extraction from TGJU HTML.
         */
        fun parseGramAndOilFromTgjuCoinHtml(html: String): Pair<MarketRateItem?, MarketRateItem?> {
            val all = parseAllFromTgjuCoinHtml(html)
            return Pair(all.gram, all.crudeOil)
        }

        private fun buildItemFromJson(
            obj: JSONObject,
            key: String,
            title: String,
            mode: TgjuValueMode,
            minPrice: Double = 0.0,
            nowMs: Long = System.currentTimeMillis()
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

            val tsRaw = obj.optString("ts", obj.optString("updated_at", ""))
            val tRaw = obj.optString("t", "")
            val (parsedTsMs, parsedDay) = parseTgjuItemTimestamp(tsRaw, tRaw, nowMs)

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
                timeFormatted = tRaw,
                sourceTimestampMs = parsedTsMs,
                sourceTehranDay = parsedDay
            )
        }

        private fun buildItemFromRaw(
            raw: TgjuRawRow,
            key: String,
            title: String,
            mode: TgjuValueMode,
            minPrice: Double = 0.0,
            timeFormatted: String = "",
            sourceTimestampMs: Long = 0L,
            sourceTehranDay: String = ""
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
                        sourceLabel = TGJU_SOURCE_LABEL,
                        sourceTimestampMs = sourceTimestampMs,
                        sourceTehranDay = sourceTehranDay
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
                        sourceLabel = TGJU_SOURCE_LABEL,
                        sourceTimestampMs = sourceTimestampMs,
                        sourceTehranDay = sourceTehranDay
                    )
                }

                TgjuValueMode.USD_CRYPTO_PRECISE -> {
                    val priceStr = formatAdaptiveUsdAmount(raw.price)
                    val diffStr = if (absD > 0.0) formatAdaptiveUsdAmount(absD) else null
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
                        sourceLabel = TGJU_GRAM_SOURCE_LABEL,
                        sourceTimestampMs = sourceTimestampMs,
                        sourceTehranDay = sourceTehranDay
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
                        sourceLabel = TGJU_SOURCE_LABEL,
                        sourceTimestampMs = sourceTimestampMs,
                        sourceTehranDay = sourceTehranDay
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
                        sourceLabel = TGJU_SOURCE_LABEL,
                        sourceTimestampMs = sourceTimestampMs,
                        sourceTehranDay = sourceTehranDay
                    )
                }
            }
        }

        fun formatAdaptiveUsdAmount(value: Double): String {
            val absVal = abs(value)
            return when {
                absVal >= 1.0 -> "$" + String.format(Locale.US, "%,.2f", absVal)
                absVal >= 0.01 -> "$" + String.format(Locale.US, "%.4f", absVal)
                absVal >= 0.0001 -> "$" + String.format(Locale.US, "%.6f", absVal).trimEnd('0').trimEnd('.')
                absVal > 0.0 -> "$" + String.format(Locale.US, "%.8f", absVal).trimEnd('0').trimEnd('.')
                else -> "$0.00"
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
