package com.example.data.repository

import android.content.Context
import coil.Coil
import coil.annotation.ExperimentalCoilApi
import com.example.data.model.NewsArticle
import com.example.data.model.NewsCategory
import com.example.data.model.NewsSource
import com.example.utils.PersianDateHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class NewsRepository(private val context: Context) {

    private val appContext = context.applicationContext
    private val cacheFile: File
        get() = File(appContext.filesDir, LATEST_NEWS_FILE_NAME)

    private val newsPrefs = appContext.getSharedPreferences(NEWS_PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(9, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _newsList = MutableStateFlow<List<NewsArticle>>(emptyList())
    val newsList: StateFlow<List<NewsArticle>> = _newsList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastUpdated = MutableStateFlow("")
    val lastUpdated: StateFlow<String> = _lastUpdated.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadOnlyLatestSavedBatch()
    }

    /**
     * Loads ONLY the articles stored from the very last successful refresh.
     * Never falls back to hardcoded/curated static articles.
     */
    private fun loadOnlyLatestSavedBatch() {
        try {
            val file = cacheFile
            if (!file.exists() || file.length() == 0L) {
                _newsList.value = emptyList()
                _lastUpdated.value = ""
                return
            }
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val savedUpdateTime = root.optString("lastUpdated", "")
            val arr = root.optJSONArray("articles") ?: JSONArray()
            val restored = mutableListOf<NewsArticle>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val sourceName = obj.optString("source", "")
                val source = NewsSource.entries.firstOrNull { it.name == sourceName } ?: continue
                val catName = obj.optString("category", "")
                val category = NewsCategory.entries.firstOrNull { it.name == catName } ?: NewsCategory.POLITICS
                val title = obj.optString("title", "").trim()
                if (title.isBlank()) continue

                val timestamp = obj.optLong("timestamp", 0L)
                val exactDateTime = obj.optString("fetchTime", "").ifBlank {
                    if (timestamp > 0L) formatExactShamsiDateTime(timestamp) else ""
                }

                restored.add(
                    NewsArticle(
                        id = obj.optString("id", "${source.name.lowercase()}_$i"),
                        title = title,
                        summary = obj.optString("summary", ""),
                        fullContent = obj.optString("fullContent", ""),
                        source = source,
                        category = category,
                        url = obj.optString("url", source.websiteUrl),
                        fetchTime = exactDateTime,
                        timestamp = timestamp,
                        imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                        videoUrl = obj.optString("videoUrl", "").takeIf { it.isNotBlank() }
                    )
                )
            }
            _newsList.value = restored.sortedByDescending { it.timestamp }.take(TARGET_TOTAL_NEWS_COUNT)
            _lastUpdated.value = savedUpdateTime
        } catch (_: Exception) {
            purgeAllPreviousNewsStorage()
            _newsList.value = emptyList()
            _lastUpdated.value = ""
        }
    }

    /**
     * Fetches up to 60 fresh live news articles concurrently from configured Iranian RSS feeds,
     * including dedicated Iranian Accidents/Incidents (حوادث) feeds.
     * Upon receiving fresh news, completely purges all previous-update news, thumbnails,
     * and audio caches from device storage and replaces them exclusively with the latest batch.
     */
    suspend fun refreshNews() {
        if (_isLoading.value) return
        _isLoading.value = true
        _errorMessage.value = null

        withContext(Dispatchers.IO) {
            try {
                val refreshEpochMs = System.currentTimeMillis()
                val feedConfigs = listOf(
                    // 1. حوادث (ACCIDENTS)
                    FeedConfig("https://www.rokna.net/feeds/", NewsSource.ROKNA, NewsCategory.ACCIDENTS),
                    FeedConfig("https://www.rokna.net/fa/rss/allnews", NewsSource.ROKNA, NewsCategory.ACCIDENTS),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/13", NewsSource.KHABARONLINE, NewsCategory.ACCIDENTS),
                    FeedConfig("https://www.tabnak.ir/fa/rss/6", NewsSource.TABNAK, NewsCategory.ACCIDENTS),

                    // 2. اقتصادی (ECONOMY)
                    FeedConfig("https://www.eghtesadonline.com/fa/rss/allnews", NewsSource.EGHTESADONLINE, NewsCategory.ECONOMY),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/2", NewsSource.KHABARONLINE, NewsCategory.ECONOMY),
                    FeedConfig("https://www.tabnak.ir/fa/rss/3", NewsSource.TABNAK, NewsCategory.ECONOMY),
                    FeedConfig("https://aftabnews.ir/fa/rss/4", NewsSource.AFTAB, NewsCategory.ECONOMY),

                    // 3. سیاسی (POLITICS)
                    FeedConfig("https://www.khabaronline.ir/rss/tp/1", NewsSource.KHABARONLINE, NewsCategory.POLITICS),
                    FeedConfig("https://www.tabnak.ir/fa/rss/2", NewsSource.TABNAK, NewsCategory.POLITICS),
                    FeedConfig("https://aftabnews.ir/fa/rss/5", NewsSource.AFTAB, NewsCategory.POLITICS),
                    FeedConfig("https://sahamnews.org/feed/", NewsSource.SAHAM, NewsCategory.POLITICS),

                    // 4. تکنولوژی (TECHNOLOGY)
                    FeedConfig("https://www.zoomit.ir/feed/", NewsSource.ZOOMIT, NewsCategory.TECHNOLOGY),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/7", NewsSource.KHABARONLINE, NewsCategory.TECHNOLOGY),
                    FeedConfig("https://www.tabnak.ir/fa/rss/22", NewsSource.TABNAK, NewsCategory.TECHNOLOGY),

                    // 5. علمی (SCIENCE)
                    FeedConfig("https://www.zoomit.ir/science/feed/", NewsSource.ZOOMIT, NewsCategory.SCIENCE),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/6", NewsSource.KHABARONLINE, NewsCategory.SCIENCE),
                    FeedConfig("https://aftabnews.ir/fa/rss/13", NewsSource.AFTAB, NewsCategory.SCIENCE),
                    FeedConfig("https://www.tabnak.ir/fa/rss/7", NewsSource.TABNAK, NewsCategory.SCIENCE),
                    FeedConfig("https://fararu.com/fa/rss/17", NewsSource.FARARU, NewsCategory.SCIENCE),

                    // 6. فرهنگی (CULTURAL)
                    FeedConfig("https://aftabnews.ir/fa/rss/3", NewsSource.AFTAB, NewsCategory.CULTURAL),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/4", NewsSource.KHABARONLINE, NewsCategory.CULTURAL),
                    FeedConfig("https://www.tabnak.ir/fa/rss/5", NewsSource.TABNAK, NewsCategory.CULTURAL),
                    FeedConfig("https://fararu.com/fa/rss/14", NewsSource.FARARU, NewsCategory.CULTURAL),

                    // 7. اجتماعی (SOCIAL)
                    FeedConfig("https://www.khabaronline.ir/rss/tp/5", NewsSource.KHABARONLINE, NewsCategory.SOCIAL),
                    FeedConfig("https://www.tabnak.ir/fa/rss/4", NewsSource.TABNAK, NewsCategory.SOCIAL),
                    FeedConfig("https://aftabnews.ir/fa/rss/2", NewsSource.AFTAB, NewsCategory.SOCIAL),
                    FeedConfig("https://fararu.com/fa/rss/13", NewsSource.FARARU, NewsCategory.SOCIAL),

                    // 8. سلامت (HEALTH)
                    FeedConfig("https://aftabnews.ir/fa/rss/6", NewsSource.AFTAB, NewsCategory.HEALTH),
                    FeedConfig("https://www.zoomit.ir/health/feed/", NewsSource.ZOOMIT, NewsCategory.HEALTH),
                    FeedConfig("https://www.khabaronline.ir/rss/tp/11", NewsSource.KHABARONLINE, NewsCategory.HEALTH),
                    FeedConfig("https://www.tabnak.ir/fa/rss/18", NewsSource.TABNAK, NewsCategory.HEALTH),
                    FeedConfig("https://fararu.com/fa/rss/18", NewsSource.FARARU, NewsCategory.HEALTH),

                    // 9. عمومی (All-news feeds for extra breadth across categories)
                    FeedConfig("https://fararu.com/fa/rss/allnews", NewsSource.FARARU, null),
                    FeedConfig("https://www.khabaronline.ir/rss", NewsSource.KHABARONLINE, null),
                    FeedConfig("https://aftabnews.ir/fa/rss/allnews", NewsSource.AFTAB, null),
                    FeedConfig("https://www.tabnak.ir/fa/rss/allnews", NewsSource.TABNAK, null)
                )

                val fetchedBatches = withTimeoutOrNull(32_000L) {
                    supervisorScope {
                        feedConfigs.map { cfg ->
                            async {
                                try {
                                    withTimeoutOrNull(12_000L) {
                                        fetchRssFeed(cfg, refreshEpochMs).take(25)
                                    }.orEmpty()
                                } catch (ce: CancellationException) {
                                    throw ce
                                } catch (_: Throwable) {
                                    emptyList()
                                }
                            }
                        }.awaitAll()
                    }
                }.orEmpty()

                val seenKeys = HashSet<String>()
                val allDeduped = fetchedBatches
                    .flatten()
                    .filter { article ->
                        val dedupKey = article.title.trim().lowercase(Locale.ROOT)
                        dedupKey.isNotBlank() && seenKeys.add(dedupKey)
                    }
                    .sortedByDescending { it.timestamp }

                // Balanced per-category selection: guarantee at least 10-12 articles per category (>= 80 total)
                val activeCategories = NewsCategory.entries.filter { it != NewsCategory.ALL }
                val selectedArticles = mutableListOf<NewsArticle>()
                val selectedIds = HashSet<String>()

                for (category in activeCategories) {
                    val catItems = allDeduped
                        .filter { it.category == category && it.id !in selectedIds }
                        .take(PREFERRED_PER_CATEGORY_COUNT)
                    for (item in catItems) {
                        selectedArticles.add(item)
                        selectedIds.add(item.id)
                    }
                }

                if (selectedArticles.size < TARGET_TOTAL_NEWS_COUNT) {
                    val remaining = allDeduped
                        .filter { it.id !in selectedIds }
                        .take(TARGET_TOTAL_NEWS_COUNT - selectedArticles.size)
                    selectedArticles.addAll(remaining)
                }

                val freshArticles = selectedArticles
                    .sortedByDescending { it.timestamp }
                    .take(TARGET_TOTAL_NEWS_COUNT)

                if (freshArticles.isNotEmpty()) {
                    val exactUpdateLabel = formatExactUpdateHeader(refreshEpochMs)

                    // Completely delete all pre-latest news, old cache files, and cached images/audio
                    purgeAllPreviousNewsStorage()

                    // Save ONLY this latest refresh batch to disk
                    saveLatestBatchOnly(freshArticles, exactUpdateLabel, refreshEpochMs)

                    _newsList.value = freshArticles
                    _lastUpdated.value = exactUpdateLabel
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = if (_newsList.value.isNotEmpty()) {
                        "اتصال به سرورهای خبری برقرار نشد؛ اخبار آخرین به‌روزرسانی نمایش داده می‌شود."
                    } else {
                        "خطا در دریافت اخبار جدید؛ لطفاً اتصال اینترنت را بررسی کرده و دوباره تلاش کنید."
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                _errorMessage.value = if (_newsList.value.isNotEmpty()) {
                    "خطا در به‌روزرسانی اخبار؛ آخرین اخبار ذخیره‌شده نمایش داده می‌شود."
                } else {
                    "خطا در به‌روزرسانی اخبار؛ لطفاً مجدداً تلاش کنید."
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Fetches the full multi-paragraph article body from the direct source URL so
     * "شرح خبر" displays complete, rich paragraphs (12-20+ lines) instead of a short RSS teaser.
     */
    suspend fun fetchFullArticleDetails(article: NewsArticle): NewsArticle = withContext(Dispatchers.IO) {
        val currentCleanBody = article.fullContent
            .replace(article.title, "")
            .replace(Regex("""منبع\s*:\s*.*$""", RegexOption.MULTILINE), "")
            .trim()
        // If we already have a rich multi-line body (>= 700 chars), return immediately
        if (currentCleanBody.length >= 700 || !article.url.startsWith("http")) {
            return@withContext article
        }

        val extracted = try {
            withTimeoutOrNull(11_000L) {
                val req = Request.Builder()
                    .url(article.url)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
                    .header("Accept-Language", "fa-IR,fa;q=0.9")
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val html = resp.body?.string().orEmpty()
                    extractArticleParagraphsFromHtml(html)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            null
        }

        if (extracted.isNullOrBlank() || extracted.length <= currentCleanBody.length + 60) {
            return@withContext article
        }

        val enrichedFull = "${article.title}\n\n$extracted\n\nمنبع: ${article.source.displayName}"
        val enrichedSummary = extracted.take(1600)
        val updatedArticle = article.copy(
            summary = enrichedSummary,
            fullContent = enrichedFull
        )

        val updatedList = _newsList.value.map { if (it.id == article.id) updatedArticle else it }
        _newsList.value = updatedList
        val lastUpd = _lastUpdated.value
        if (lastUpd.isNotBlank()) {
            saveLatestBatchOnly(updatedList, lastUpd, System.currentTimeMillis())
        }
        updatedArticle
    }

    /**
     * Erases all prior-update news files, preferences, cached thumbnails, and temp/offline news audio
     * from phone storage so nothing from previous updates lingers on disk.
     */
    @OptIn(ExperimentalCoilApi::class)
    private fun purgeAllPreviousNewsStorage() {
        runCatching {
            newsPrefs.edit().clear().commit()
        }
        runCatching {
            appContext.filesDir.listFiles()?.forEach { file ->
                if (file.name == LATEST_NEWS_FILE_NAME ||
                    file.name.startsWith("news_") ||
                    file.name.startsWith("latest_news_")
                ) {
                    file.delete()
                }
            }
        }
        runCatching {
            val audioDir = File(appContext.cacheDir, "news_audio_cache")
            if (audioDir.exists()) {
                audioDir.listFiles()?.forEach { it.delete() }
            }
        }
        runCatching {
            val offlineAudioDir = File(appContext.filesDir, "offline_news_audio")
            if (offlineAudioDir.exists()) {
                offlineAudioDir.listFiles()?.forEach { it.delete() }
            }
        }
        runCatching {
            val imageLoader = Coil.imageLoader(appContext)
            imageLoader.memoryCache?.clear()
            imageLoader.diskCache?.clear()
        }
    }

    private fun saveLatestBatchOnly(
        articles: List<NewsArticle>,
        exactUpdateLabel: String,
        refreshEpochMs: Long
    ) {
        try {
            val arr = JSONArray()
            for (article in articles) {
                val obj = JSONObject().apply {
                    put("id", article.id)
                    put("title", article.title)
                    put("summary", article.summary)
                    put("fullContent", article.fullContent)
                    put("source", article.source.name)
                    put("category", article.category.name)
                    put("url", article.url)
                    put("fetchTime", article.fetchTime)
                    put("timestamp", article.timestamp)
                    put("imageUrl", article.imageUrl ?: "")
                    put("videoUrl", article.videoUrl ?: "")
                }
                arr.put(obj)
            }
            val root = JSONObject().apply {
                put("lastUpdated", exactUpdateLabel)
                put("refreshEpochMs", refreshEpochMs)
                put("articles", arr)
            }
            cacheFile.writeText(root.toString(), Charsets.UTF_8)
            newsPrefs.edit()
                .putString("last_updated_exact", exactUpdateLabel)
                .putLong("last_updated_epoch", refreshEpochMs)
                .commit()
        } catch (_: Exception) {
        }
    }

    private data class FeedConfig(
        val url: String,
        val source: NewsSource,
        val defaultCategory: NewsCategory?
    )

    private fun fetchRssFeed(config: FeedConfig, fallbackEpochMs: Long): List<NewsArticle> {
        val request = Request.Builder()
            .url(config.url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            )
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .header("Cache-Control", "no-cache")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val xml = response.body?.string().orEmpty()
            if (xml.isBlank()) return emptyList()
            return parseRssXml(xml, config.source, config.defaultCategory, fallbackEpochMs)
        }
    }

    companion object {
        const val TARGET_TOTAL_NEWS_COUNT = 120
        const val PREFERRED_PER_CATEGORY_COUNT = 14
        private const val LATEST_NEWS_FILE_NAME = "latest_news_batch_v2.json"
        private const val NEWS_PREFS_NAME = "news_latest_prefs"

        private val IRAN_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Tehran")

        private val ITEM_REGEX = Regex("<(?:item|entry)\\b[^>]*>(.*?)</(?:item|entry)>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val TITLE_REGEX = Regex("<title[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val LINK_REGEX = Regex("<link[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</link>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val LINK_HREF_REGEX = Regex("""<link[^>]+href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val GUID_REGEX = Regex("<guid[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</guid>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val DESC_REGEX = Regex("<(?:description|summary)[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</(?:description|summary)>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val CONTENT_REGEX = Regex("<(?:content:encoded|content)[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</(?:content:encoded|content)>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val PUBDATE_REGEX = Regex("<(?:pubDate|dc:date|published|updated)[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</(?:pubDate|dc:date|published|updated)>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val CATEGORY_REGEX = Regex("<category[^>]*>(?:<!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?</category>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val IMG_REGEX = Regex("""(?:<enclosure[^>]*url=["']([^"']+)["']|<media:content[^>]*url=["']([^"']+)["']|<media:thumbnail[^>]*url=["']([^"']+)["']|<img[^>]*src=["']([^"']+)["'])""", RegexOption.IGNORE_CASE)
        private val SCRIPT_STYLE_REGEX = Regex("<(?:script|style|noscript|nav|footer|header|aside)[^>]*>.*?</(?:script|style|noscript|nav|footer|header|aside)>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val PARAGRAPH_REGEX = Regex("<p\\b[^>]*>(.*?)</p>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        fun extractArticleParagraphsFromHtml(html: String): String {
            if (html.isBlank()) return ""
            val stripped = SCRIPT_STYLE_REGEX.replace(html, " ")
            val paragraphs = PARAGRAPH_REGEX.findAll(stripped)
                .map { cleanHtmlText(it.groupValues[1]) }
                .filter { p ->
                    p.length >= 45 &&
                        !p.contains("کپی رایت", ignoreCase = true) &&
                        !p.contains("تمامی حقوق", ignoreCase = true) &&
                        !p.contains("کلیک کنید", ignoreCase = true) &&
                        !p.contains("بیشتر بخوانید", ignoreCase = true) &&
                        !p.contains("عضویت در کانال", ignoreCase = true) &&
                        !p.contains("تلگرام", ignoreCase = true)
                }
                .distinct()
                .take(12)
                .toList()
            return paragraphs.joinToString("\n\n").take(3200)
        }

        fun parseRssXml(
            xml: String,
            source: NewsSource,
            defaultCategory: NewsCategory? = null,
            fallbackEpochMs: Long = System.currentTimeMillis()
        ): List<NewsArticle> {
            val list = mutableListOf<NewsArticle>()
            for ((idx, match) in ITEM_REGEX.findAll(xml).withIndex()) {
                val content = match.groupValues[1]
                val rawTitle = TITLE_REGEX.find(content)?.groupValues?.get(1)?.trim() ?: continue
                val cleanTitle = cleanHtmlText(rawTitle)
                if (cleanTitle.isBlank()) continue

                val parsedLink = LINK_REGEX.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }
                val parsedGuid = GUID_REGEX.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }
                val parsedHref = LINK_HREF_REGEX.find(content)?.groupValues?.get(1)?.trim()?.takeIf { it.startsWith("http") }
                val rawLink = parsedLink ?: parsedGuid ?: parsedHref ?: (
                    "https://www.google.com/search?q=" + URLEncoder.encode("$cleanTitle ${source.displayName}", "UTF-8")
                    )

                val rawDesc = DESC_REGEX.find(content)?.groupValues?.get(1)?.trim().orEmpty()
                val rawFull = CONTENT_REGEX.find(content)?.groupValues?.get(1)?.trim().orEmpty()
                val cleanDesc = cleanHtmlText(rawDesc)
                val cleanFull = cleanHtmlText(rawFull)

                // Preserve rich multi-line news content (up to 1600 chars in summary for 14-line card display)
                val bodyText = when {
                    cleanFull.length > cleanDesc.length + 40 -> {
                        if (cleanDesc.isNotBlank() && !cleanFull.startsWith(cleanDesc.take(40))) {
                            "$cleanDesc\n\n$cleanFull"
                        } else {
                            cleanFull
                        }
                    }
                    cleanDesc.isNotBlank() -> cleanDesc
                    cleanFull.isNotBlank() -> cleanFull
                    else -> "گزارش خبری از ${source.displayName}؛ برای مطالعه و شنیدن متن کامل خبر کلیک کنید."
                }
                val summaryText = bodyText.take(1600)

                val rawCategories = CATEGORY_REGEX.findAll(content)
                    .map { cleanHtmlText(it.groupValues[1]) }
                    .joinToString(" ")
                val category = classifyCategory(cleanTitle, summaryText, rawCategories, defaultCategory)

                val rawPubDate = PUBDATE_REGEX.find(content)?.groupValues?.get(1)?.trim().orEmpty()
                val pubEpochMs = parseRssDateToEpochMs(rawPubDate) ?: fallbackEpochMs
                val exactDateTimeStr = formatExactShamsiDateTime(pubEpochMs)

                val rawImg = IMG_REGEX.find(content)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                val extractedImg = rawImg?.let { img ->
                    when {
                        img.startsWith("//") -> "https:$img"
                        img.startsWith("/") -> "${source.websiteUrl.trimEnd('/')}$img"
                        img.startsWith("http://") || img.startsWith("https://") -> img
                        else -> "${source.websiteUrl.trimEnd('/')}/$img"
                    }
                }

                val stableId = "${source.name.lowercase(Locale.ROOT)}_${pubEpochMs}_$idx"
                list.add(
                    NewsArticle(
                        id = stableId,
                        title = cleanTitle,
                        summary = summaryText,
                        fullContent = "$cleanTitle\n\n$bodyText\n\nمنبع: ${source.displayName}",
                        source = source,
                        category = category,
                        url = rawLink,
                        fetchTime = exactDateTimeStr,
                        timestamp = pubEpochMs,
                        imageUrl = extractedImg
                    )
                )
            }
            return list
        }

        private fun classifyCategory(
            title: String,
            summary: String,
            rssCategories: String,
            defaultCategory: NewsCategory?
        ): NewsCategory {
            val combined = "$rssCategories $title $summary"

            // Honor dedicated category RSS feeds directly (with Science/Health enrichment on Zoomit main feed)
            if (defaultCategory != null) {
                if (defaultCategory == NewsCategory.TECHNOLOGY) {
                    if (combined.contains("نجوم") || combined.contains("فضا") || combined.contains("ناسا") ||
                        combined.contains("فیزیک") || combined.contains("شیمی") || combined.contains("زیست‌شناسی") ||
                        combined.contains("کهکشان") || combined.contains("باستان‌شناسی") || combined.contains("علمی") ||
                        combined.contains("دانشمندان")
                    ) {
                        return NewsCategory.SCIENCE
                    }
                    if (combined.contains("سلامت") || combined.contains("پزشک") || combined.contains("بیماری") ||
                        combined.contains("دارو") || combined.contains("تغذیه") || combined.contains("درمان")
                    ) {
                        return NewsCategory.HEALTH
                    }
                }
                return defaultCategory
            }

            return when {
                combined.contains("حادثه") || combined.contains("حوادث") || combined.contains("تصادف") ||
                    combined.contains("آتش‌سوزی") || combined.contains("آتش سوزی") || combined.contains("حریق") ||
                    combined.contains("قتل") || combined.contains("سرقت") || combined.contains("سقوط") ||
                    combined.contains("انفجار") || combined.contains("غرق") || combined.contains("زلزله") ||
                    combined.contains("سیل") || combined.contains("اورژانس") || combined.contains("آتش‌نشانی") ||
                    combined.contains("آتش نشانی") || combined.contains("پلیس آگاهی") || combined.contains("واژگونی") ||
                    combined.contains("مصدوم") || combined.contains("بازداشت") || combined.contains("جنایت") ||
                    combined.contains("زورگیری") || combined.contains("کلاهبرداری") || combined.contains("گازگرفتگی") ->
                    NewsCategory.ACCIDENTS

                combined.contains("اقتصاد") || combined.contains("ارز") || combined.contains("دلار") ||
                    combined.contains("طلا") || combined.contains("سکه") || combined.contains("بورس") ||
                    combined.contains("بانک") || combined.contains("قیمت") || combined.contains("مسکن") ||
                    combined.contains("تورم") || combined.contains("نفت") || combined.contains("خودرو") ||
                    combined.contains("یارانه") || combined.contains("معیشت") -> NewsCategory.ECONOMY

                combined.contains("سلامت") || combined.contains("پزشک") || combined.contains("درمان") ||
                    combined.contains("دارو") || combined.contains("بیمار") || combined.contains("واکسن") ||
                    combined.contains("تغذیه") || combined.contains("بهداشت") || combined.contains("بیمارستان") ||
                    combined.contains("سرطان") || combined.contains("ویروس") -> NewsCategory.HEALTH

                combined.contains("علمی") || combined.contains("دانشمند") || combined.contains("نجوم") ||
                    combined.contains("دانشگاه") || combined.contains("فضا") || combined.contains("ناسا") ||
                    combined.contains("فیزیک") || combined.contains("پژوهش") || combined.contains("تحقیقات") ||
                    combined.contains("کهکشان") || combined.contains("مریخ") || combined.contains("زیست") ||
                    combined.contains("کنکور") || combined.contains("دانشجو") -> NewsCategory.SCIENCE

                combined.contains("فناوری") || combined.contains("تکنولوژی") || combined.contains("هوش مصنوعی") ||
                    combined.contains("اینترنت") || combined.contains("موبایل") || combined.contains("گوشی") ||
                    combined.contains("تراشه") || combined.contains("نرم‌افزار") || combined.contains("سامسونگ") ||
                    combined.contains("اپل") || combined.contains("گوگل") || combined.contains("لپ‌تاپ") -> NewsCategory.TECHNOLOGY

                combined.contains("فرهنگ") || combined.contains("کتاب") || combined.contains("سینما") ||
                    combined.contains("هنر") || combined.contains("موزه") || combined.contains("میراث") ||
                    combined.contains("موسیقی") || combined.contains("فیلم") || combined.contains("تئاتر") ||
                    combined.contains("سریال") || combined.contains("بازیگر") || combined.contains("کارگردان") -> NewsCategory.CULTURAL

                combined.contains("اجتماعی") || combined.contains("جامعه") || combined.contains("آموزش") ||
                    combined.contains("مدرسه") || combined.contains("خانواده") || combined.contains("شهرداری") ||
                    combined.contains("شهری") || combined.contains("ترافیک") || combined.contains("هواشناسی") ||
                    combined.contains("بارش") || combined.contains("آلودگی هوا") || combined.contains("محیط زیست") -> NewsCategory.SOCIAL

                else -> NewsCategory.POLITICS
            }
        }

        /**
         * Parses RFC-822, RFC-1123, ISO-8601, and numeric RSS publication date strings
         * into exact UTC epoch milliseconds.
         */
        fun parseRssDateToEpochMs(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val cleaned = PersianDateHelper.toEnglishDigits(raw)
                .replace(Regex("\\s+"), " ")
                .trim()

            val patterns = listOf(
                "EEE, dd MMM yyyy HH:mm:ss Z",
                "EEE, d MMM yyyy HH:mm:ss Z",
                "EEE, dd MMM yyyy HH:mm:ss zzz",
                "EEE, d MMM yyyy HH:mm:ss zzz",
                "dd MMM yyyy HH:mm:ss Z",
                "d MMM yyyy HH:mm:ss Z",
                "dd MMM yyyy HH:mm:ss zzz",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
            )

            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        isLenient = true
                        timeZone = if (pattern.endsWith("'Z'")) {
                            TimeZone.getTimeZone("UTC")
                        } else {
                            IRAN_TIMEZONE
                        }
                    }
                    val parsed: Date? = sdf.parse(cleaned)
                    if (parsed != null && parsed.time > 1_577_836_800_000L) {
                        return parsed.time
                    }
                } catch (_: Exception) {
                }
            }
            return null
        }

        /**
         * Formats an epoch timestamp into exact Iran-time Shamsi Date + Clock Time:
         * e.g. "1405/09/02  13:24"
         */
        fun formatExactShamsiDateTime(epochMs: Long): String {
            val cal = Calendar.getInstance(IRAN_TIMEZONE).apply { timeInMillis = epochMs }
            val shamsi = PersianDateHelper.gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            val dateStr = String.format(Locale.US, "%04d/%02d/%02d", shamsi.year, shamsi.month, shamsi.day)
            val timeStr = String.format(Locale.US, "%02d:%02d", hour, minute)
            return "$dateStr  $timeStr"
        }

        /**
         * Formats the exact refresh timestamp for the News header card:
         * e.g. "1405/09/02 - 13:28"
         */
        fun formatExactUpdateHeader(epochMs: Long): String {
            val cal = Calendar.getInstance(IRAN_TIMEZONE).apply { timeInMillis = epochMs }
            val shamsi = PersianDateHelper.gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)
            return String.format(
                Locale.US,
                "%04d/%02d/%02d - %02d:%02d",
                shamsi.year,
                shamsi.month,
                shamsi.day,
                hour,
                minute
            )
        }

        private fun cleanHtmlText(html: String): String {
            return html
                .replace(Regex("<[^>]*>"), " ")
                .replace("&nbsp;", " ")
                .replace("&zwnj;", "\u200C")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace(Regex("&#\\d+;"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }
    }
}
