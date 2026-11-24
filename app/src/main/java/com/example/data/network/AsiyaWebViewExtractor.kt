package com.example.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.MarketRateItem
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Headless offscreen WebView extractor for https://asiyaexchange.com.
 * Hardened against Chromium renderer crashes (`aw_browser_terminator.cc`):
 * - Handles `onRenderProcessGone` and returns `true` so a renderer crash/OOM never kills the app
 * - Uses software rendering + compact offscreen viewport + blocks heavy images/fonts/media/trackers
 * - Serializes execution via Mutex and applies a cooldown after any renderer termination
 * - Uses reflow-free `textContent` DOM scanning to avoid hanging the renderer main thread
 */
object AsiyaWebViewExtractor {

    data class WebViewExtractionResult(
        val item: MarketRateItem,
        val discoveredApiUrl: String? = null
    )

    private const val RENDERER_CRASH_COOLDOWN_MS = 90_000L

    private val moshiAnyAdapter = Moshi.Builder().build().adapter(Any::class.java).lenient()
    private val extractionMutex = Mutex()
    private val lastRendererCrashAtMs = AtomicLong(0L)

    private val NETWORK_HOOK_JS = """
        (function() {
            if (window.__asiyaHooked) return;
            window.__asiyaHooked = true;
            window.__asiyaPayloads = [];
            window.__asiyaApiUrls = [];
            function record(url, text) {
                try {
                    if (url && typeof url === 'string') {
                        if (window.__asiyaApiUrls.indexOf(url) === -1 && window.__asiyaApiUrls.length < 24) {
                            window.__asiyaApiUrls.push(url);
                        }
                    }
                    if (!text || typeof text !== 'string') return;
                    var trimmed = text.trim();
                    if (trimmed.length < 8 || trimmed.length > 60000) return;
                    var lower = trimmed.toLowerCase();
                    if (lower.indexOf('usdt') !== -1 || lower.indexOf('tether') !== -1 || trimmed.indexOf('تتر') !== -1) {
                        if (window.__asiyaPayloads.length < 24) {
                            window.__asiyaPayloads.push(trimmed);
                        }
                    }
                } catch (e) {}
            }
            try {
                var origFetch = window.fetch;
                if (origFetch) {
                    window.fetch = function() {
                        var reqUrl = (arguments[0] && typeof arguments[0] === 'string')
                            ? arguments[0]
                            : (arguments[0] && arguments[0].url ? arguments[0].url : '');
                        return origFetch.apply(this, arguments).then(function(resp) {
                            try {
                                resp.clone().text().then(function(txt) { record(reqUrl, txt); }).catch(function(){});
                            } catch (e) {}
                            return resp;
                        });
                    };
                }
            } catch (e) {}
            try {
                var origOpen = XMLHttpRequest.prototype.open;
                var origSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__asiyaUrl = url || '';
                    return origOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function() {
                    var self = this;
                    try {
                        self.addEventListener('load', function() {
                            try {
                                if (typeof self.responseText === 'string') {
                                    record(self.__asiyaUrl || '', self.responseText);
                                }
                            } catch (e) {}
                        });
                    } catch (e) {}
                    return origSend.apply(this, arguments);
                };
            } catch (e) {}
            try {
                var OrigWS = window.WebSocket;
                if (OrigWS) {
                    window.WebSocket = function(url, protocols) {
                        var ws = protocols ? new OrigWS(url, protocols) : new OrigWS(url);
                        try {
                            ws.addEventListener('message', function(ev) {
                                if (ev && typeof ev.data === 'string') {
                                    record(url || '', ev.data);
                                }
                            });
                        } catch (e) {}
                        return ws;
                    };
                    window.WebSocket.prototype = OrigWS.prototype;
                }
            } catch (e) {}
        })();
    """.trimIndent()

    // Uses reflow-free `textContent` instead of layout-forcing `innerText` to avoid hanging/crashing Chromium renderer
    private val SNAPSHOT_JS = """
        (function() {
            try {
                var cards = [];
                var seenCards = {};
                var all = document.querySelectorAll('tr, li, article, section, div, a, span, p, h1, h2, h3, h4, td');
                var limit = Math.min(all.length, 650);
                for (var i = 0; i < limit && cards.length < 18; i++) {
                    var el = all[i];
                    var txt = (el.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!txt || txt.length > 420) continue;
                    var lower = txt.toLowerCase();
                    if (txt.indexOf('تتر') === -1 && lower.indexOf('usdt') === -1 && lower.indexOf('tether') === -1) continue;
                    var target = el;
                    for (var up = 0; up < 3; up++) {
                        if (target.parentElement) {
                            var pTxt = (target.parentElement.textContent || '').replace(/\s+/g, ' ').trim();
                            if (pTxt.length > txt.length && pTxt.length <= 420) {
                                target = target.parentElement;
                                txt = pTxt;
                            } else {
                                break;
                            }
                        }
                    }
                    if (!seenCards[txt]) {
                        seenCards[txt] = true;
                        cards.push(txt);
                    }
                }
                var bodyText = (document.body && document.body.textContent) ? document.body.textContent : '';
                if (bodyText.length > 40000) bodyText = bodyText.substring(0, 40000);
                return JSON.stringify({
                    payloads: window.__asiyaPayloads || [],
                    apiUrls: window.__asiyaApiUrls || [],
                    cards: cards,
                    bodyText: bodyText
                });
            } catch (e) {
                return JSON.stringify({ payloads: [], apiUrls: [], cards: [], bodyText: '' });
            }
        })();
    """.trimIndent()

    private val CLICK_CRYPTO_TAB_JS = """
        (function() {
            try {
                if (window.__asiyaClickedTabs === undefined) window.__asiyaClickedTabs = 0;
                var keywords = ['تتر', 'usdt', 'ارز دیجیتال', 'ارزهای دیجیتال', 'رمزارز', 'کریپتو', 'دیجیتال', 'crypto'];
                var candidates = document.querySelectorAll('button, [role="tab"], a, li');
                var limit = Math.min(candidates.length, 250);
                for (var i = 0; i < limit; i++) {
                    var el = candidates[i];
                    if (el.__asiyaClicked) continue;
                    var t = (el.textContent || '').replace(/\s+/g, ' ').trim();
                    if (!t || t.length > 32) continue;
                    var lower = t.toLowerCase();
                    var matched = false;
                    for (var k = 0; k < keywords.length; k++) {
                        if (lower.indexOf(keywords[k]) !== -1) { matched = true; break; }
                    }
                    if (!matched) continue;
                    var href = el.getAttribute ? (el.getAttribute('href') || '') : '';
                    if (href && href.indexOf('http') === 0 && href.indexOf('asiyaexchange.com') === -1) continue;
                    el.__asiyaClicked = true;
                    window.__asiyaClickedTabs++;
                    el.click();
                    return t;
                }
                return '';
            } catch (e) {
                return '';
            }
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extractTether(
        context: Context,
        previousCachedPriceTomans: Long = 0L,
        referenceDollarTomans: Long = 0L,
        timeoutMs: Long = 13_500L
    ): WebViewExtractionResult? {
        // Respect cooldown if the Chromium WebView renderer recently crashed on this device/emulator
        val lastCrash = lastRendererCrashAtMs.get()
        if (lastCrash > 0L && (System.currentTimeMillis() - lastCrash) < RENDERER_CRASH_COOLDOWN_MS) {
            return null
        }

        return extractionMutex.withLock {
            withTimeoutOrNull(timeoutMs) {
                withContext(Dispatchers.Main) {
                    val interceptedUrls = Collections.synchronizedList(mutableListOf<String>())
                    val rendererGone = AtomicBoolean(false)
                    val destroyed = AtomicBoolean(false)
                    var webView: WebView? = null

                    fun safelyDestroyWebView(target: WebView?) {
                        if (target == null || !destroyed.compareAndSet(false, true)) return
                        try {
                            target.stopLoading()
                            target.webChromeClient = null
                            target.webViewClient = WebViewClient()
                            target.clearHistory()
                            target.removeAllViews()
                            target.destroy()
                        } catch (_: Throwable) {
                        }
                    }

                    try {
                        val wv = WebView(context.applicationContext).apply {
                            visibility = View.INVISIBLE
                            // Software layer prevents GPU/EGL compositor crashes in unattached offscreen WebViews
                            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false)
                            }
                            runCatching {
                                CookieManager.getInstance().setAcceptCookie(true)
                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            }
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = false
                                loadsImagesAutomatically = false
                                blockNetworkImage = true
                                mediaPlaybackRequiresUserGesture = true
                                cacheMode = WebSettings.LOAD_NO_CACHE
                                useWideViewPort = false
                                loadWithOverviewMode = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!rendererGone.get() && !destroyed.get()) {
                                        runCatching { view?.evaluateJavascript(NETWORK_HOOK_JS, null) }
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (!rendererGone.get() && !destroyed.get()) {
                                        runCatching { view?.evaluateJavascript(NETWORK_HOOK_JS, null) }
                                    }
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString().orEmpty()
                                    if (reqUrl.isBlank()) return null

                                    // Block heavy images/fonts/media/analytics to keep renderer memory minimal
                                    if (shouldBlockHeavyAsset(reqUrl)) {
                                        return WebResourceResponse(
                                            "text/plain",
                                            "utf-8",
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }

                                    if (isLikelyRateApiUrl(reqUrl)) {
                                        if (interceptedUrls.size < 20 && !interceptedUrls.contains(reqUrl)) {
                                            interceptedUrls.add(reqUrl)
                                        }
                                    }
                                    return null
                                }

                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?
                                ): Boolean {
                                    rendererGone.set(true)
                                    lastRendererCrashAtMs.set(System.currentTimeMillis())
                                    safelyDestroyWebView(view ?: webView)
                                    webView = null
                                    // Return true so Android NEVER terminates the host app process
                                    return true
                                }
                            }
                            // Compact offscreen layout avoids huge tile buffer allocations
                            layout(0, 0, 360, 640)
                            loadUrl(AsiyaExchangeService.BASE_URL)
                        }
                        webView = wv

                        // Poll rendered DOM + intercepted XHR/Fetch/WebSocket payloads
                        for (attempt in 1..13) {
                            delay(850L)
                            if (rendererGone.get() || destroyed.get()) {
                                return@withContext null
                            }
                            runCatching { wv.evaluateJavascript(NETWORK_HOOK_JS, null) }

                            val rawJson = evaluateJsSuspending(wv, SNAPSHOT_JS, rendererGone, destroyed)
                            if (rendererGone.get() || destroyed.get()) {
                                return@withContext null
                            }

                            val parsed = parseSnapshotJson(
                                rawJson = rawJson,
                                previousCachedPriceTomans = previousCachedPriceTomans,
                                referenceDollarTomans = referenceDollarTomans,
                                interceptedUrls = interceptedUrls
                            )
                            if (parsed != null) {
                                return@withContext parsed
                            }

                            // If landing view didn't show Tether after ~2.5s or ~6s, try clicking a Crypto/Tether tab
                            if (attempt == 3 || attempt == 7) {
                                evaluateJsSuspending(wv, CLICK_CRYPTO_TAB_JS, rendererGone, destroyed)
                            }
                        }
                        null
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) {
                        null
                    } finally {
                        withContext(NonCancellable + Dispatchers.Main) {
                            safelyDestroyWebView(webView)
                            webView = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun evaluateJsSuspending(
        webView: WebView,
        script: String,
        rendererGone: AtomicBoolean,
        destroyed: AtomicBoolean
    ): String? {
        if (rendererGone.get() || destroyed.get()) return null
        return withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { cont ->
                if (rendererGone.get() || destroyed.get()) {
                    if (cont.isActive) cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                try {
                    webView.evaluateJavascript(script) { value ->
                        if (cont.isActive) {
                            cont.resume(value)
                        }
                    }
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    internal fun shouldBlockHeavyAsset(url: String): Boolean {
        val clean = url.lowercase().substringBefore('?').substringBefore('#')
        if (clean.endsWith(".png") || clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
            clean.endsWith(".webp") || clean.endsWith(".gif") || clean.endsWith(".svg") ||
            clean.endsWith(".ico") || clean.endsWith(".bmp") || clean.endsWith(".avif") ||
            clean.endsWith(".woff") || clean.endsWith(".woff2") || clean.endsWith(".ttf") ||
            clean.endsWith(".otf") || clean.endsWith(".eot") ||
            clean.endsWith(".mp4") || clean.endsWith(".webm") || clean.endsWith(".mp3") ||
            clean.endsWith(".wav") || clean.endsWith(".ogg") || clean.endsWith(".pdf")
        ) {
            return true
        }
        val lower = url.lowercase()
        return lower.contains("google-analytics.com") ||
            lower.contains("googletagmanager.com") ||
            lower.contains("doubleclick.net") ||
            lower.contains("hotjar.com") ||
            lower.contains("clarity.ms") ||
            lower.contains("yektanet.com") ||
            lower.contains("goftino.com") ||
            lower.contains("raychat.io") ||
            lower.contains("crisp.chat")
    }

    private fun parseSnapshotJson(
        rawJson: String?,
        previousCachedPriceTomans: Long,
        referenceDollarTomans: Long,
        interceptedUrls: List<String>
    ): WebViewExtractionResult? {
        if (rawJson.isNullOrBlank() || rawJson == "null") return null
        val decodedString = try {
            moshiAnyAdapter.fromJson(rawJson) as? String
        } catch (_: Exception) {
            null
        } ?: return null

        val rootMap = try {
            moshiAnyAdapter.fromJson(decodedString) as? Map<*, *>
        } catch (_: Exception) {
            null
        } ?: return null

        val jsApiUrls = (rootMap["apiUrls"] as? List<*>)
            ?.mapNotNull { it?.toString()?.takeIf { u -> isLikelyRateApiUrl(u) } }
            .orEmpty()
        val bestApiUrl = (jsApiUrls + interceptedUrls).firstOrNull { url ->
            val lower = url.lowercase()
            lower.startsWith("http") && (
                lower.contains("rate") || lower.contains("price") || lower.contains("crypto") ||
                    lower.contains("currenc") || lower.contains("usdt") || lower.contains("tether") ||
                    lower.contains("market") || lower.contains("ticker") || lower.contains("/api")
                )
        }

        // 1. Check intercepted XHR / Fetch / WebSocket JSON payloads first (highest fidelity)
        val payloads = (rootMap["payloads"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        for (payload in payloads) {
            AsiyaExchangeService.parseTetherFromResponse(
                rawText = payload,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )?.let { return WebViewExtractionResult(item = it, discoveredApiUrl = bestApiUrl) }
        }

        // 2. Check targeted DOM cards/rows around "تتر" / "USDT"
        val cards = (rootMap["cards"] as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
        for (cardText in cards) {
            AsiyaExchangeService.parseTetherFromCleanText(
                plainText = cardText,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )?.let { return WebViewExtractionResult(item = it, discoveredApiUrl = bestApiUrl) }
        }

        // 3. Check full rendered page body text
        val bodyText = rootMap["bodyText"]?.toString().orEmpty()
        if (bodyText.isNotBlank()) {
            AsiyaExchangeService.parseTetherFromCleanText(
                plainText = bodyText,
                previousCachedPriceTomans = previousCachedPriceTomans,
                referenceDollarTomans = referenceDollarTomans
            )?.let { return WebViewExtractionResult(item = it, discoveredApiUrl = bestApiUrl) }
        }

        return null
    }

    private fun isLikelyRateApiUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (shouldBlockHeavyAsset(lower) || lower.endsWith(".js") || lower.endsWith(".css")) {
            return false
        }
        return lower.contains("/api") ||
            lower.contains("api.") ||
            lower.contains(".json") ||
            lower.contains("rate") ||
            lower.contains("price") ||
            lower.contains("crypto") ||
            lower.contains("currenc") ||
            lower.contains("ticker") ||
            lower.contains("market")
    }
}
