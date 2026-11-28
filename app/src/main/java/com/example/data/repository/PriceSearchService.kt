package com.example.data.repository

import com.example.data.model.PriceProductItem
import com.example.data.model.PriceSearchResponse
import com.example.data.model.PriceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Lightweight direct price-search service for Basalam public search API
 * (`https://search.basalam.com/ai-engine/api/v2.0/product/search?q=...`)
 * plus direct browser search URL builders for Digikala and Torob.
 *
 * Never generates mock or fabricated products.
 */
class PriceSearchService {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun searchBasalam(query: String): PriceSearchResponse = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return@withContext PriceSearchResponse(
                query = "",
                items = emptyList(),
                errorMessage = "لطفاً نام کالا را برای جستجو وارد کنید."
            )
        }

        try {
            withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                fetchFromBasalamDirect(trimmedQuery)
            } ?: PriceSearchResponse(
                query = trimmedQuery,
                items = emptyList(),
                errorMessage = "پاسخ‌دهی باسلام بیش از حد طول کشید (Timeout). لطفاً مجدداً تلاش کنید."
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            PriceSearchResponse(
                query = trimmedQuery,
                items = emptyList(),
                errorMessage = formatNetworkExceptionMessage(e)
            )
        }
    }

    private fun fetchFromBasalamDirect(query: String): PriceSearchResponse {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val requestUrl = "$BASALAM_SEARCH_API_URL?q=$encodedQuery&from=0&size=24"

        val request = Request.Builder()
            .url(requestUrl)
            .header("Accept", "application/json")
            .header("Accept-Language", "fa-IR,fa;q=0.9")
            .header("Origin", "https://basalam.com")
            .header("Referer", "https://basalam.com/")
            .header("User-Agent", MOBILE_USER_AGENT)
            .build()

        return httpClient.newCall(request).execute().use { response ->
            val code = response.code
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return PriceSearchResponse(
                    query = query,
                    items = emptyList(),
                    errorMessage = formatHttpErrorMessage(code)
                )
            }
            if (body.isBlank()) {
                return PriceSearchResponse(
                    query = query,
                    items = emptyList(),
                    errorMessage = "پاسخ خالی از سرور باسلام دریافت شد."
                )
            }

            val parsed = parseBasalamResponse(body, query)
                .distinctBy { "${it.productUrl.ifBlank { it.title }}_${it.priceToman}" }

            if (parsed.isNotEmpty()) {
                PriceSearchResponse(
                    query = query,
                    items = parsed,
                    errorMessage = null
                )
            } else {
                PriceSearchResponse(
                    query = query,
                    items = emptyList(),
                    errorMessage = "نتیجه‌ای برای «$query» در باسلام یافت نشد."
                )
            }
        }
    }

    private fun formatHttpErrorMessage(statusCode: Int): String {
        return when (statusCode) {
            403, 429 -> "دسترسی به باسلام موقتاً محدود شد (کد $statusCode)."
            404 -> "مسیر جستجوی باسلام یافت نشد (کد 404)."
            in 500..599 -> "خطای موقت در سرور باسلام رخ داد (کد $statusCode)."
            else -> "خطا در دریافت اطلاعات از باسلام (کد $statusCode)."
        }
    }

    private fun formatNetworkExceptionMessage(error: Exception): String {
        return when (error) {
            is UnknownHostException, is ConnectException, is SSLException ->
                "خطا در اتصال به سرور باسلام. اتصال اینترنت خود را بررسی کنید."
            is SocketTimeoutException ->
                "پاسخ‌دهی باسلام بیش از حد طول کشید (Timeout)."
            is IOException ->
                "خطای شبکه هنگام دریافت اطلاعات از باسلام."
            else ->
                "خطا در پردازش پاسخ دریافتی از باسلام."
        }
    }

    companion object {
        const val BASALAM_SEARCH_API_URL = "https://search.basalam.com/ai-engine/api/v2.0/product/search"
        const val DIGIKALA_HOME_URL = "https://www.digikala.com"
        const val TOROB_HOME_URL = "https://torob.com"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        private const val REQUEST_TIMEOUT_MS = 16_000L

        fun buildDigikalaSearchUrl(query: String): String {
            val trimmed = query.trim()
            return if (trimmed.isBlank()) {
                DIGIKALA_HOME_URL
            } else {
                "$DIGIKALA_HOME_URL/search/?q=${URLEncoder.encode(trimmed, "UTF-8")}"
            }
        }

        fun buildTorobSearchUrl(query: String): String {
            val trimmed = query.trim()
            return if (trimmed.isBlank()) {
                TOROB_HOME_URL
            } else {
                "$TOROB_HOME_URL/search/?query=${URLEncoder.encode(trimmed, "UTF-8")}"
            }
        }

        /**
         * Parses Basalam public search API JSON (`ai-engine/api/v2.0/product/search`).
         * Converts Rial prices (Basalam default integer format) accurately to Toman.
         */
        fun parseBasalamResponse(jsonText: String, query: String = ""): List<PriceProductItem> {
            val trimmed = jsonText.trim()
            if (trimmed.isBlank()) return emptyList()

            val productsArray = extractProductsArray(trimmed) ?: return emptyList()
            val results = mutableListOf<PriceProductItem>()

            for (i in 0 until productsArray.length()) {
                val obj = productsArray.optJSONObject(i) ?: continue
                val title = firstNonBlank(
                    obj.optString("name"),
                    obj.optString("title")
                ) ?: continue

                val priceToman = extractBasalamPriceToman(obj)
                if (priceToman <= 0L) continue

                val imageUrl = extractImageUrl(obj)
                val vendorObj = obj.optJSONObject("vendor")
                val vendorName = firstNonBlank(
                    vendorObj?.optString("name"),
                    vendorObj?.optString("title"),
                    obj.optString("shopName"),
                    obj.optString("vendor_name")
                )
                val vendorIdentifier = firstNonBlank(
                    vendorObj?.optString("identifier"),
                    vendorObj?.optString("slug")
                )
                val vendorCity = firstNonBlank(
                    vendorObj?.optJSONObject("city")?.optString("title"),
                    vendorObj?.optJSONObject("city")?.optString("name"),
                    vendorObj?.optString("city")
                )

                val productId = firstNonBlank(
                    obj.optString("id"),
                    obj.optString("product_id")
                )
                val directUrl = firstNonBlank(
                    obj.optString("url"),
                    obj.optString("link")
                )
                val productUrl = when {
                    !directUrl.isNullOrBlank() && (directUrl.startsWith("http://") || directUrl.startsWith("https://")) ->
                        directUrl
                    !directUrl.isNullOrBlank() && directUrl.startsWith("/") ->
                        "https://basalam.com$directUrl"
                    !vendorIdentifier.isNullOrBlank() && !productId.isNullOrBlank() ->
                        "https://basalam.com/$vendorIdentifier/product/$productId"
                    !productId.isNullOrBlank() ->
                        "https://basalam.com/p/$productId"
                    query.isNotBlank() ->
                        "https://basalam.com/s?q=${URLEncoder.encode(query, "UTF-8")}"
                    else ->
                        PriceSource.BASALAM.websiteBaseUrl
                }

                val stockCount = if (obj.has("stock") && !obj.isNull("stock")) obj.optInt("stock", -1) else -1
                val statusTitle = obj.optJSONObject("status")?.optString("title")?.takeIf { it.isNotBlank() && it != "null" }
                val availability = when {
                    !statusTitle.isNullOrBlank() -> statusTitle
                    stockCount > 0 -> "موجود"
                    stockCount == 0 -> "ناموجود"
                    else -> null
                }

                val rawSpecs = mutableListOf<String>()
                rawSpecs.addAll(extractSpecsList(obj))

                val categoryTitle = firstNonBlank(
                    obj.optString("categoryTitle"),
                    obj.optJSONObject("category")?.optString("title"),
                    obj.optJSONObject("category")?.optString("name")
                )
                if (categoryTitle != null) {
                    rawSpecs.add("دسته‌بندی: $categoryTitle")
                }

                if (!vendorCity.isNullOrBlank()) {
                    rawSpecs.add("شهر غرفه: $vendorCity")
                }

                val ratingObj = obj.optJSONObject("rating")
                if (ratingObj != null) {
                    val avg = ratingObj.optDouble("average", ratingObj.optDouble("score", 0.0))
                    val count = ratingObj.optInt("count", ratingObj.optInt("signals", 0))
                    if (avg > 0.0) {
                        val avgStr = String.format(Locale.US, "%.1f", avg)
                        if (count > 0) {
                            rawSpecs.add("امتیاز خریداران: $avgStr از ۵ ($count نظر)")
                        } else {
                            rawSpecs.add("امتیاز خریداران: $avgStr از ۵")
                        }
                    }
                }

                val weightGrams = obj.optInt("weight", 0)
                if (weightGrams > 0) {
                    val weightText = if (weightGrams >= 1000) {
                        String.format(Locale.US, "%.1f کیلوگرم", weightGrams / 1000.0)
                    } else {
                        "$weightGrams گرم"
                    }
                    rawSpecs.add("وزن: $weightText")
                }

                val structuredSpecs = buildStructuredSpecs(
                    title = title,
                    rawSpecs = rawSpecs,
                    shopName = vendorName,
                    availability = availability
                )

                results.add(
                    PriceProductItem(
                        id = "basalam_${productId ?: i}_${title.hashCode()}",
                        title = title,
                        priceToman = priceToman,
                        imageUrl = imageUrl,
                        productUrl = productUrl,
                        source = PriceSource.BASALAM,
                        shopName = vendorName,
                        availability = availability,
                        specs = structuredSpecs
                    )
                )
            }
            return results
        }

        private fun extractBasalamPriceToman(obj: JSONObject): Long {
            val priceField = obj.opt("price")
            if (priceField is JSONObject) {
                val rawAmount = parsePriceValue(
                    priceField,
                    listOf("discounted_amount", "amount", "value", "final_price")
                )
                if (rawAmount <= 0L) return 0L
                val currency = priceField.optString("currency", "IRR").uppercase(Locale.ROOT)
                return if (currency.contains("TOMAN") || currency.contains("تومان")) {
                    rawAmount
                } else {
                    (rawAmount / 10L).coerceAtLeast(1L)
                }
            }

            // Basalam ai-engine/api/v2.0/product/search returns numeric `price` in Rials
            val rawPrice = parsePriceValue(obj, listOf("price", "primaryPrice", "primary_price"))
            if (rawPrice <= 0L) return 0L
            val currencyHint = obj.optString("currency", obj.optString("unit", "")).lowercase(Locale.ROOT)
            return if (currencyHint.contains("toman") || currencyHint.contains("تومان")) {
                rawPrice
            } else {
                (rawPrice / 10L).coerceAtLeast(1L)
            }
        }

        /**
         * Combines explicit response-supplied specs with structured factual attributes extracted
         * directly from the product title and fields. Never invents or fabricates data.
         */
        fun buildStructuredSpecs(
            title: String,
            rawSpecs: List<String>,
            shopName: String?,
            availability: String?
        ): List<String> {
            val specs = LinkedHashSet<String>()

            for (line in rawSpecs) {
                val cleaned = line.replace(Regex("\\s+"), " ").trim()
                if (cleaned.length >= 2) {
                    specs.add(cleaned)
                }
            }

            val normalizedTitle = normalizeDigits(title)
            extractSpecsFromTitle(normalizedTitle).forEach { candidate ->
                val prefix = candidate.substringBefore(":")
                if (specs.none { it.startsWith(prefix) }) {
                    specs.add(candidate)
                }
            }

            if (!availability.isNullOrBlank() && specs.none { it.contains("موجود") || it.startsWith("وضعیت:") }) {
                specs.add("وضعیت: $availability")
            }
            if (!shopName.isNullOrBlank() && specs.none { it.startsWith("فروشنده:") || it.startsWith("فروشگاه:") }) {
                specs.add("فروشنده: $shopName")
            }

            return specs.take(5).toList()
        }

        private fun extractSpecsFromTitle(title: String): List<String> {
            val extracted = mutableListOf<String>()

            val brands = listOf(
                "سامسونگ" to listOf("سامسونگ", "samsung", "galaxy", "گلکسی"),
                "اپل" to listOf("اپل", "آیفون", "iphone", "ipad", "macbook", "apple"),
                "شیائومی" to listOf("شیائومی", "xiaomi", "redmi", "ردمی", "poco", "پوکو"),
                "ایسوس" to listOf("ایسوس", "asus", "vivobook", "zenbook", "tuf", "rog"),
                "لنوو" to listOf("لنوو", "lenovo", "ideapad", "thinkpad", "loq"),
                "اچ‌پی" to listOf("اچ پی", "اچ‌پی", "hp ", "victus", "pavilion"),
                "دل" to listOf("دل", "dell", "inspiron", "latitude", "vostro"),
                "سونی" to listOf("سونی", "sony", "playstation"),
                "ال‌جی" to listOf("ال جی", "ال‌جی", "lg "),
                "آنر" to listOf("آنر", "honor"),
                "هوآوی" to listOf("هوآوی", "هواوی", "huawei"),
                "موتورولا" to listOf("موتورولا", "motorola", "moto "),
                "نوکیا" to listOf("نوکیا", "nokia"),
                "ریلمی" to listOf("ریلمی", "realme"),
                "بوش" to listOf("بوش", "bosch"),
                "پارس خزر" to listOf("پارس خزر")
            )
            val matchedBrand = brands.firstOrNull { (_, keys) ->
                keys.any { title.contains(it, ignoreCase = true) }
            }?.first
            if (matchedBrand != null) {
                extracted.add("برند: $matchedBrand")
            }

            val comboRegex = Regex(
                """\b(4|6|8|12|16|24|32|64)\s*(?:gb|گیگ|گیگابایت)?\s*/\s*(64|128|256|512|1024|1|2)\s*(?:gb|tb|گیگ|گیگابایت|ترابایت)?\b""",
                RegexOption.IGNORE_CASE
            )
            val comboMatch = comboRegex.find(title)
            if (comboMatch != null) {
                val ram = comboMatch.groupValues[1]
                val storage = comboMatch.groupValues[2]
                val storageUnit = if (storage == "1" || storage == "2") "ترابایت" else "گیگابایت"
                extracted.add("رم: $ram گیگابایت")
                extracted.add("حافظه: $storage $storageUnit")
            } else {
                val ramRegex = Regex(
                    """(?:رم|ram)\s*[:\-]?\s*(\d{1,2})\s*(?:gb|گیگ|گیگابایت)?""",
                    RegexOption.IGNORE_CASE
                )
                ramRegex.find(title)?.let { m ->
                    extracted.add("رم: ${m.groupValues[1]} گیگابایت")
                }
                val storageRegex = Regex(
                    """(?:حافظه|ظرفیت|ssd|hdd)?\s*\b(64|128|256|512)\s*(?:gb|گیگ|گیگابایت)\b|\b([124])\s*(?:tb|ترابایت)\b""",
                    RegexOption.IGNORE_CASE
                )
                storageRegex.find(title)?.let { m ->
                    val gb = m.groupValues[1]
                    val tb = m.groupValues[2]
                    if (gb.isNotBlank()) {
                        extracted.add("حافظه: $gb گیگابایت")
                    } else if (tb.isNotBlank()) {
                        extracted.add("حافظه: $tb ترابایت")
                    }
                }
            }

            val cpuRegex = Regex(
                """(Core\s*i[3579](?:-\w+)?|Ryzen\s*[3579](?:\s*\w+)?|Snapdragon\s*\w+|Dimensity\s*\w+|Helio\s*\w+|Exynos\s*\w+|Apple\s*M[1-4](?:\s*(?:Pro|Max))?|\bM[1-4]\s*(?:Pro|Max)?\b)""",
                RegexOption.IGNORE_CASE
            )
            cpuRegex.find(title)?.let { m ->
                extracted.add("پردازنده: ${m.groupValues[1].trim()}")
            }

            val screenRegex = Regex("""(\d{1,2}(?:\.\d)?)\s*(?:اینچ|inch|")""", RegexOption.IGNORE_CASE)
            screenRegex.find(title)?.let { m ->
                extracted.add("صفحه‌نمایش: ${m.groupValues[1]} اینچ")
            }

            val netFeatures = mutableListOf<String>()
            if (Regex("""\b5G\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)) netFeatures.add("5G")
            else if (Regex("""\b4G\b""", RegexOption.IGNORE_CASE).containsMatchIn(title)) netFeatures.add("4G")
            if (title.contains("دو سیم") || title.contains("دوسیم") || title.contains("dual sim", ignoreCase = true)) {
                netFeatures.add("دو سیم‌کارت")
            }
            if (netFeatures.isNotEmpty()) {
                extracted.add("شبکه/امکانات: ${netFeatures.joinToString(" • ")}")
            }

            return extracted
        }

        private fun extractSpecsList(obj: JSONObject): List<String> {
            val specs = mutableListOf<String>()
            val candidateKeys = listOf("specs", "specifications", "attributes", "features", "details")
            for (key in candidateKeys) {
                if (!obj.has(key) || obj.isNull(key)) continue
                val value = obj.opt(key)
                when (value) {
                    is JSONArray -> {
                        for (i in 0 until value.length()) {
                            when (val elem = value.opt(i)) {
                                is String -> if (elem.isNotBlank()) specs.add(elem.trim())
                                is JSONObject -> {
                                    val k = firstNonBlank(elem.optString("label"), elem.optString("key"), elem.optString("name"))
                                    val v = firstNonBlank(elem.optString("value"), elem.optString("text"))
                                    if (k != null && v != null) specs.add("$k: $v")
                                    else if (v != null) specs.add(v)
                                }
                            }
                        }
                    }
                    is JSONObject -> {
                        val iter = value.keys()
                        while (iter.hasNext()) {
                            val k = iter.next()
                            val v = value.optString(k).trim()
                            if (k.isNotBlank() && v.isNotBlank() && v != "null") {
                                specs.add("$k: $v")
                            }
                        }
                    }
                }
                if (specs.isNotEmpty()) break
            }
            return specs
        }

        private fun extractProductsArray(trimmedJson: String): JSONArray? {
            return try {
                if (trimmedJson.startsWith("[")) {
                    JSONArray(trimmedJson)
                } else {
                    val root = JSONObject(trimmedJson)
                    val directKeys = listOf("products", "results", "items", "data")
                    for (key in directKeys) {
                        val arr = root.optJSONArray(key)
                        if (arr != null) return arr
                    }
                    val nestedObj = root.optJSONObject("data") ?: root.optJSONObject("result")
                    if (nestedObj != null) {
                        for (key in directKeys) {
                            val arr = nestedObj.optJSONArray(key)
                            if (arr != null) return arr
                        }
                    }
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun parsePriceValue(obj: JSONObject, keys: List<String>): Long {
            for (key in keys) {
                if (!obj.has(key) || obj.isNull(key)) continue
                val value = obj.opt(key) ?: continue
                when (value) {
                    is Number -> {
                        val longVal = value.toLong()
                        if (longVal > 0L) return longVal
                    }
                    is String -> {
                        val digits = normalizeDigits(value).filter { it.isDigit() }
                        val parsed = digits.toLongOrNull()
                        if (parsed != null && parsed > 0L) return parsed
                    }
                }
            }
            return 0L
        }

        private fun extractImageUrl(obj: JSONObject): String? {
            val direct = firstNonBlank(
                obj.optString("image"),
                obj.optString("imageUrl"),
                obj.optString("image_url"),
                obj.optString("thumbnail")
            )
            if (direct != null && (direct.startsWith("http://") || direct.startsWith("https://"))) {
                return direct
            }

            val photoObj = obj.optJSONObject("photo") ?: obj.optJSONObject("image")
            if (photoObj != null) {
                val fromPhoto = firstNonBlank(
                    photoObj.optString("MEDIUM"),
                    photoObj.optString("SMALL"),
                    photoObj.optString("LARGE"),
                    photoObj.optString("url"),
                    photoObj.optString("default")
                )
                if (fromPhoto != null && (fromPhoto.startsWith("http://") || fromPhoto.startsWith("https://"))) {
                    return fromPhoto
                }
            }
            return null
        }

        private fun normalizeDigits(input: String): String {
            return input
                .replace('۰', '0').replace('۱', '1').replace('۲', '2')
                .replace('۳', '3').replace('۴', '4').replace('۵', '5')
                .replace('۶', '6').replace('۷', '7').replace('۸', '8')
                .replace('۹', '9')
                .replace('٠', '0').replace('١', '1').replace('٢', '2')
                .replace('٣', '3').replace('٤', '4').replace('٥', '5')
                .replace('٦', '6').replace('٧', '7').replace('٨', '8')
                .replace('٩', '9')
        }

        private fun firstNonBlank(vararg values: String?): String? {
            return values.firstOrNull { !it.isNullOrBlank() && it != "null" }?.trim()
        }
    }
}
