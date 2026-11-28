package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.NewsArticle
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

private const val TTS_TAG = "PersianNewsSpeech"

/**
 * Four unmistakably distinct authentic Iranian-Tehrani Persian newsreader personas:
 * - Two clearly contrasting male voices: deep baritone formal (`ARASH_MALE`) vs bright energetic young (`KAMRAN_MALE`).
 * - Two clearly contrasting female voices: bright warm friendly (`SARA_FEMALE`) vs deep formal anchor (`NAZANIN_FEMALE`).
 *
 * Online streaming only (no offline audio storage or robotic offline TTS fallback).
 * Primary engine: Firebase AI Logic Gemini TTS (`gemini-3.1-flash-tts-preview`) with authentic
 * standard Tehran-Iranian pronunciation.
 * Backup engine: Native Iranian `fa-IR-FaridNeural` / `fa-IR-DilaraNeural` with strongly
 * differentiated pitch & pace profiles so every persona sounds genuinely distinct.
 */
enum class NewsVoicePersona(
    val key: String,
    val shortLabelFa: String,
    val fullLabelFa: String,
    val isMale: Boolean,
    val geminiVoiceName: String,
    val geminiStyleCueFa: String,
    val nativeIrEdgeName: String,
    val nativeIrBingVoice: String,
    val nativeIrGender: String,
    val edgePitch: String,
    val bingPitch: String,
    val prosodyRate: String
) {
    ARASH_MALE(
        key = "arash_tehrani_male",
        shortLabelFa = "آرش (مرد بم و رسمی)",
        fullLabelFa = "آرش • گوینده مرد بم و رسمی تهرانی",
        isMale = true,
        geminiVoiceName = "Enceladus",
        geminiStyleCueFa = "با صدای بسیار بم، عمیق، موقر، آرام و سنگین یک گوینده ارشد خبر مرد در تهران",
        nativeIrEdgeName = "Microsoft Server Speech Text to Speech Voice (fa-IR, FaridNeural)",
        nativeIrBingVoice = "fa-IR-FaridNeural",
        nativeIrGender = "Male",
        edgePitch = "-16Hz",
        bingPitch = "-11%",
        prosodyRate = "-7%"
    ),
    KAMRAN_MALE(
        key = "kamran_tehrani_male",
        shortLabelFa = "کامران (مرد جوان و شاداب)",
        fullLabelFa = "کامران • گوینده مرد جوان و پرانرژی تهرانی",
        isMale = true,
        geminiVoiceName = "Puck",
        geminiStyleCueFa = "با صدای جوان، روشن، پرانرژی، کمی تندتر و بسیار صمیمی یک گزارشگر جوان مرد در تهران",
        nativeIrEdgeName = "Microsoft Server Speech Text to Speech Voice (fa-IR, FaridNeural)",
        nativeIrBingVoice = "fa-IR-FaridNeural",
        nativeIrGender = "Male",
        edgePitch = "+26Hz",
        bingPitch = "+16%",
        prosodyRate = "+8%"
    ),
    SARA_FEMALE(
        key = "sara_tehrani_female",
        shortLabelFa = "سارا (زن جوان و صمیمی)",
        fullLabelFa = "سارا • گوینده زن جوان و صمیمی تهرانی",
        isMale = false,
        geminiVoiceName = "Aoede",
        geminiStyleCueFa = "با صدای جوان، لطیف، روشن، گرم و پرنشاط یک گوینده زن صمیمی در تهران",
        nativeIrEdgeName = "Microsoft Server Speech Text to Speech Voice (fa-IR, DilaraNeural)",
        nativeIrBingVoice = "fa-IR-DilaraNeural",
        nativeIrGender = "Female",
        edgePitch = "+24Hz",
        bingPitch = "+14%",
        prosodyRate = "+7%"
    ),
    NAZANIN_FEMALE(
        key = "nazanin_tehrani_female",
        shortLabelFa = "نازنین (زن سنگین و رسمی)",
        fullLabelFa = "نازنین • مجری رسمی و متین زن تهرانی",
        isMale = false,
        geminiVoiceName = "Gacrux",
        geminiStyleCueFa = "با صدای پخته، آرام، کمی بم‌تر، شمرده و کاملاً رسمی یک مجری ارشد اخبار زن در تهران",
        nativeIrEdgeName = "Microsoft Server Speech Text to Speech Voice (fa-IR, DilaraNeural)",
        nativeIrBingVoice = "fa-IR-DilaraNeural",
        nativeIrGender = "Female",
        edgePitch = "-22Hz",
        bingPitch = "-13%",
        prosodyRate = "-7%"
    );

    fun nextPersona(): NewsVoicePersona {
        val list = entries
        return list[(ordinal + 1) % list.size]
    }
}

data class NewsSpeechState(
    val activeArticleId: String? = null,
    val activeArticleTitle: String = "",
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 0,
    val speechSpeed: Float = 1.0f,
    val voicePersona: NewsVoicePersona = NewsVoicePersona.ARASH_MALE,
    val statusMessage: String? = null
) {
    val useMaleVoice: Boolean get() = voicePersona.isMale
}

/**
 * Online-Only Authentic Tehran-Iranian Persian News Audio Reader powered by
 * Firebase AI Logic (`gemini-3.1-flash-tts-preview`) + Native Iranian `fa-IR` Neural Backup.
 */
class PersianNewsSpeechEngine(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Lazy per-voice Gemini TTS models via Firebase AI Logic (never initialized eagerly at construction)
    private val geminiTtsModels = ConcurrentHashMap<String, GenerativeModel>()

    @OptIn(PublicPreviewAPI::class)
    private fun getOrCreateGeminiTtsModel(persona: NewsVoicePersona): GenerativeModel {
        return geminiTtsModels.getOrPut(persona.geminiVoiceName) {
            Firebase.ai.generativeModel(
                modelName = GEMINI_TTS_MODEL_NAME,
                generationConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(voice = Voice(persona.geminiVoiceName))
                }
            )
        }
    }

    private data class SynthesizedChunk(
        val bytes: ByteArray,
        val extension: String // "wav" or "mp3"
    )

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val memoryCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val existing = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(existing) {
                for (cookie in cookies) {
                    existing.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                    existing.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val now = System.currentTimeMillis()
            val result = mutableListOf<Cookie>()
            cookieStore.forEach { (_, list) ->
                synchronized(list) {
                    list.removeAll { it.expiresAt < now }
                    list.filterTo(result) { it.matches(url) }
                }
            }
            return result
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .cookieJar(memoryCookieJar)
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow(NewsSpeechState())
    val state: StateFlow<NewsSpeechState> = _state.asStateFlow()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaPlayer: MediaPlayer? = null
    private var offlineTts: TextToSpeech? = null
    private var playbackJob: Job? = null
    private var activeArticleRef: NewsArticle? = null

    @Volatile
    private var clockSkewSeconds: Double = 0.0

    @Volatile
    private var cachedBingSession: BingTtsSession? = null

    private data class BingTtsSession(
        val ig: String,
        val iid: String,
        val key: String,
        val token: String,
        val fetchedAtMs: Long
    )

    private val tempAudioCacheDir: File
        get() = File(context.cacheDir, "news_audio_stream").apply {
            if (!exists()) mkdirs()
        }

    init {
        // Remove any legacy offline audio files from previous versions to free storage
        scope.launch(Dispatchers.IO) {
            runCatching {
                val legacyDir = File(context.filesDir, "offline_news_audio")
                if (legacyDir.exists()) legacyDir.deleteRecursively()
            }
        }
    }

    fun toggleArticle(article: NewsArticle) {
        val current = _state.value
        if (current.activeArticleId == article.id && (current.isPlaying || current.isLoading)) {
            stop()
        } else {
            speakArticle(article)
        }
    }

    /**
     * Toggles Persian voice readout for arbitrary plain/markdown text (e.g., AI assistant replies).
     * Uses fast Iranian neural speech (Edge -> Bing -> Google Translate Persian MP3 -> Gemini -> offline TTS)
     * so playback works reliably on all Android devices even without an installed Persian system TTS pack.
     */
    fun togglePlainText(utteranceId: String, title: String, rawText: String) {
        val current = _state.value
        if (current.activeArticleId == utteranceId && (current.isPlaying || current.isLoading)) {
            stop()
        } else {
            speakPlainText(utteranceId, title, rawText)
        }
    }

    fun speakPlainText(utteranceId: String, title: String, rawText: String) {
        stopInternal(clearStatus = true)
        activeArticleRef = null

        val cleaned = normalizePlainPersianForSpeech(rawText)
        val chunks = splitIntoNaturalPersianChunks(cleaned, maxChunkLength = 340)
        if (chunks.isEmpty()) {
            _state.value = _state.value.copy(statusMessage = "متن قابل خوانش یافت نشد")
            return
        }

        val speed = _state.value.speechSpeed
        // Use a lively, warm Iranian male persona instead of the heavy deep news-anchor baritone
        val persona = NewsVoicePersona.KAMRAN_MALE

        _state.value = NewsSpeechState(
            activeArticleId = utteranceId,
            activeArticleTitle = title,
            isLoading = true,
            isPlaying = false,
            currentChunkIndex = 0,
            totalChunks = chunks.size,
            speechSpeed = speed,
            voicePersona = persona,
            statusMessage = null
        )

        requestAudioFocus()

        playbackJob = scope.launch {
            try {
                val firstChunk = withContext(Dispatchers.IO) {
                    synthesizeFastPersianChunk(chunks[0], persona)
                }

                if (!isActive) return@launch

                if (firstChunk != null) {
                    playStreamedChunks(chunks, firstChunk, persona, fastFirst = true)
                } else {
                    // Online engines unreachable — try device offline Persian TTS before reporting error
                    val spokeOffline = tryOfflineDevicePersianTts(utteranceId, cleaned)
                    if (!isActive) return@launch
                    if (!spokeOffline) {
                        stopInternal(clearStatus = false)
                        _state.value = _state.value.copy(
                            statusMessage = "خطا در پخش صوتی؛ اتصال اینترنت را بررسی کنید یا صدای فارسی گوشی را نصب کنید."
                        )
                    } else {
                        stopInternal(clearStatus = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TTS_TAG, "Plain-text speech playback failed", e)
                if (isActive) {
                    stopInternal(clearStatus = false)
                    _state.value = _state.value.copy(
                        statusMessage = "خطا در پخش صوتی؛ لطفاً دوباره تلاش کنید."
                    )
                }
            }
        }
    }

    fun selectVoicePersona(persona: NewsVoicePersona) {
        if (_state.value.voicePersona == persona) return
        val activeArticle = activeArticleRef
        val wasActive = _state.value.activeArticleId != null && (_state.value.isPlaying || _state.value.isLoading)
        _state.value = _state.value.copy(voicePersona = persona)
        if (wasActive && activeArticle != null) {
            speakArticle(activeArticle)
        }
    }

    fun toggleVoicePersona() {
        selectVoicePersona(_state.value.voicePersona.nextPersona())
    }

    fun speakArticle(article: NewsArticle) {
        stopInternal(clearStatus = true)
        activeArticleRef = article

        val spokenText = preparePersianNewsScript(article)
        val chunks = splitIntoNaturalPersianChunks(spokenText, maxChunkLength = 520)
        if (chunks.isEmpty()) {
            _state.value = _state.value.copy(statusMessage = "متن قابل خوانش برای این خبر یافت نشد")
            return
        }

        val speed = _state.value.speechSpeed
        val persona = _state.value.voicePersona

        _state.value = NewsSpeechState(
            activeArticleId = article.id,
            activeArticleTitle = article.title,
            isLoading = true,
            isPlaying = false,
            currentChunkIndex = 0,
            totalChunks = chunks.size,
            speechSpeed = speed,
            voicePersona = persona,
            statusMessage = null
        )

        requestAudioFocus()

        playbackJob = scope.launch {
            try {
                val firstChunk = withContext(Dispatchers.IO) {
                    synthesizeTehraniPersianChunk(chunks[0], persona)
                }

                if (!isActive) return@launch

                if (firstChunk != null) {
                    playStreamedChunks(chunks, firstChunk, persona)
                } else {
                    stopInternal(clearStatus = false)
                    _state.value = _state.value.copy(
                        statusMessage = "خطا در دریافت صدای آنلاین (${persona.shortLabelFa})؛ اتصال اینترنت را بررسی کنید."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TTS_TAG, "Speech playback failed", e)
                if (isActive) {
                    stopInternal(clearStatus = false)
                    _state.value = _state.value.copy(
                        statusMessage = "خطا در پخش صوتی خبر؛ لطفاً دوباره تلاش کنید."
                    )
                }
            }
        }
    }

    private suspend fun playStreamedChunks(
        chunks: List<String>,
        firstChunk: SynthesizedChunk,
        persona: NewsVoicePersona,
        fastFirst: Boolean = false
    ) {
        var currentChunk: SynthesizedChunk? = firstChunk

        for (index in chunks.indices) {
            if (!scope.isActive) break

            if (currentChunk == null) {
                val retried = withContext(Dispatchers.IO) {
                    if (fastFirst) {
                        synthesizeFastPersianChunk(chunks[index], persona)
                    } else {
                        synthesizeTehraniPersianChunk(chunks[index], persona)
                    }
                }
                if (retried == null) {
                    stopInternal(clearStatus = false)
                    _state.value = _state.value.copy(
                        statusMessage = "اتصال برای ادامه پخش صوتی قطع شد."
                    )
                    return
                }
                currentChunk = retried
            }

            val readyChunk = currentChunk ?: break
            val tempFile = withContext(Dispatchers.IO) {
                File(
                    tempAudioCacheDir,
                    "stream_fa_${System.currentTimeMillis()}_$index.${readyChunk.extension}"
                ).apply {
                    writeBytes(readyChunk.bytes)
                }
            }

            val nextDeferred = if (index + 1 < chunks.size) {
                scope.async(Dispatchers.IO) {
                    if (fastFirst) {
                        synthesizeFastPersianChunk(chunks[index + 1], persona)
                    } else {
                        synthesizeTehraniPersianChunk(chunks[index + 1], persona)
                    }
                }
            } else {
                null
            }

            _state.value = _state.value.copy(
                isLoading = false,
                isPlaying = true,
                currentChunkIndex = index + 1,
                totalChunks = chunks.size
            )

            val playedOk = playAudioFileSuspending(tempFile)
            runCatching { tempFile.delete() }

            if (!playedOk) {
                nextDeferred?.cancel()
                break
            }

            currentChunk = nextDeferred?.await()
        }

        if (_state.value.activeArticleId != null) {
            stopInternal(clearStatus = true)
        }
    }

    /**
     * Fast-first Iranian Persian synthesis for interactive AI replies:
     * 1) Edge Native Iranian Neural (`fa-IR-FaridNeural` / `fa-IR-DilaraNeural`) — fast, works without VPN
     * 2) Bing Native Iranian Neural (`fa-IR`)
     * 3) Google Translate Persian TTS MP3 (lightweight HTTPS fallback)
     * 4) Gemini TTS with shorter timeout
     */
    private suspend fun synthesizeFastPersianChunk(
        chunkText: String,
        persona: NewsVoicePersona
    ): SynthesizedChunk? {
        val edgeNative = tryEdgeNativeIranianWebSocket(chunkText, persona, naturalFriendlyMale = true)
        if (edgeNative != null && edgeNative.size > 400) {
            return SynthesizedChunk(bytes = edgeNative, extension = "mp3")
        }

        val bingNative = tryBingNativeIranianTts(chunkText, persona, naturalFriendlyMale = true)
        if (bingNative != null && bingNative.size > 400) {
            return SynthesizedChunk(bytes = bingNative, extension = "mp3")
        }

        val googleTranslateMp3 = tryGoogleTranslatePersianTts(chunkText)
        if (googleTranslateMp3 != null && googleTranslateMp3.size > 400) {
            return SynthesizedChunk(bytes = googleTranslateMp3, extension = "mp3")
        }

        val geminiAudio = withTimeoutOrNull(12_000L) {
            tryGeminiTehraniTts(chunkText, persona)
        }
        if (geminiAudio != null) return geminiAudio

        return null
    }

    private fun tryGoogleTranslatePersianTts(chunkText: String): ByteArray? {
        val subParts = splitIntoNaturalPersianChunks(chunkText, maxChunkLength = 175)
        if (subParts.isEmpty()) return null
        val combined = ByteArrayOutputStream()
        for (part in subParts) {
            val encoded = URLEncoder.encode(part, "UTF-8")
            val url = "https://translate.googleapis.com/translate_tts?ie=UTF-8&tl=fa&client=tw-ob&q=$encoded"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", "https://translate.google.com/")
                .build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    val contentType = resp.header("Content-Type").orEmpty()
                    val bytes = resp.body?.bytes()
                    if (resp.isSuccessful && bytes != null && isValidAudioPayload(bytes, contentType)) {
                        combined.write(bytes)
                    } else {
                        return null
                    }
                }
            } catch (_: Exception) {
                return null
            }
        }
        val out = combined.toByteArray()
        return if (out.size > 400) out else null
    }

    private suspend fun tryOfflineDevicePersianTts(utteranceId: String, text: String): Boolean =
        suspendCancellableCoroutine { cont ->
            var engine: TextToSpeech? = null
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    runCatching { engine?.shutdown() }
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                val tts = engine ?: run {
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                offlineTts = tts
                val faIr = tts.isLanguageAvailable(Locale("fa", "IR"))
                val faOnly = tts.isLanguageAvailable(Locale("fa"))
                val selectedLocale = when {
                    faIr >= TextToSpeech.LANG_AVAILABLE -> Locale("fa", "IR")
                    faOnly >= TextToSpeech.LANG_AVAILABLE -> Locale("fa")
                    else -> null
                }
                if (selectedLocale == null) {
                    runCatching { tts.shutdown() }
                    if (offlineTts === tts) offlineTts = null
                    if (cont.isActive) cont.resume(false)
                    return@TextToSpeech
                }
                tts.language = selectedLocale
                tts.setSpeechRate(_state.value.speechSpeed.coerceIn(0.8f, 1.4f))
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {
                        _state.value = _state.value.copy(isLoading = false, isPlaying = true)
                    }

                    override fun onDone(id: String?) {
                        runCatching { tts.shutdown() }
                        if (offlineTts === tts) offlineTts = null
                        if (cont.isActive) cont.resume(true)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(id: String?) {
                        runCatching { tts.shutdown() }
                        if (offlineTts === tts) offlineTts = null
                        if (cont.isActive) cont.resume(false)
                    }
                })
                _state.value = _state.value.copy(isLoading = false, isPlaying = true)
                val res = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (res != TextToSpeech.SUCCESS) {
                    runCatching { tts.shutdown() }
                    if (offlineTts === tts) offlineTts = null
                    if (cont.isActive) cont.resume(false)
                }
            }
            cont.invokeOnCancellation {
                runCatching {
                    engine?.stop()
                    engine?.shutdown()
                }
                if (offlineTts === engine) offlineTts = null
            }
        }

    /**
     * Online Tehran-Iranian Persian synthesis:
     * 1) Firebase AI Logic Gemini TTS (`gemini-3.1-flash-tts-preview`) with 4 distinct studio voices.
     * 2) Native Iranian Neural (`fa-IR-FaridNeural` / `fa-IR-DilaraNeural`) with strongly distinct
     *    pitch & rate profiles per persona if Gemini TTS fails or times out.
     */
    private suspend fun synthesizeTehraniPersianChunk(
        chunkText: String,
        persona: NewsVoicePersona
    ): SynthesizedChunk? {
        val geminiAudio = tryGeminiTehraniTts(chunkText, persona)
        if (geminiAudio != null) return geminiAudio

        val edgeNative = tryEdgeNativeIranianWebSocket(chunkText, persona)
        if (edgeNative != null && edgeNative.size > 400) {
            return SynthesizedChunk(bytes = edgeNative, extension = "mp3")
        }

        val bingNative = tryBingNativeIranianTts(chunkText, persona)
        if (bingNative != null && bingNative.size > 400) {
            return SynthesizedChunk(bytes = bingNative, extension = "mp3")
        }

        return null
    }

    // -------------------------------------------------------------------------
    // TIER 1: Firebase AI Logic Gemini TTS (Authentic Tehran-Iranian Persian)
    // -------------------------------------------------------------------------
    private suspend fun tryGeminiTehraniTts(
        chunkText: String,
        persona: NewsVoicePersona
    ): SynthesizedChunk? {
        return try {
            val model = getOrCreateGeminiTtsModel(persona)
            val prompt = buildGeminiTehraniTtsPrompt(chunkText, persona)
            val response = withTimeout(45_000L) {
                model.generateContent(prompt)
            }
            val audioPart = response.candidates
                .asSequence()
                .flatMap { it.content.parts.asSequence() }
                .filterIsInstance<InlineDataPart>()
                .firstOrNull { part ->
                    part.inlineData.size > 400 &&
                        (part.mimeType.startsWith("audio/", ignoreCase = true) ||
                            part.mimeType.equals("application/octet-stream", ignoreCase = true))
                } ?: return null

            val rawBytes = audioPart.inlineData
            val mime = audioPart.mimeType.lowercase(Locale.US)
            when {
                mime.contains("l16") || mime.contains("pcm") -> {
                    val sampleRate = parseSampleRateFromMime(mime)
                    SynthesizedChunk(bytes = wrapPcm16AsWav(rawBytes, sampleRate), extension = "wav")
                }
                mime.contains("wav") || isWavHeader(rawBytes) -> {
                    SynthesizedChunk(bytes = rawBytes, extension = "wav")
                }
                else -> {
                    SynthesizedChunk(bytes = rawBytes, extension = "mp3")
                }
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.e(TTS_TAG, "Gemini TTS timed out after 45s", e)
                null
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.e(TTS_TAG, "Gemini TTS failed (${persona.geminiVoiceName}): ${e.localizedMessage}", e)
            null
        }
    }

    // -------------------------------------------------------------------------
    // TIER 2A: Native Iranian Neural WebSocket (`fa-IR-FaridNeural` / `fa-IR-DilaraNeural`)
    // -------------------------------------------------------------------------
    private suspend fun tryEdgeNativeIranianWebSocket(
        chunkText: String,
        persona: NewsVoicePersona,
        naturalFriendlyMale: Boolean = false
    ): ByteArray? {
        for (attempt in 0..1) {
            val result = executeEdgeWebSocketOnce(chunkText, persona, naturalFriendlyMale)
            if (result.audioBytes != null && result.audioBytes.size > 400) {
                return result.audioBytes
            }
            if (result.serverDateHeader != null && attempt == 0) {
                adjustClockSkewFromHttpDate(result.serverDateHeader)
            } else {
                break
            }
        }
        return null
    }

    private data class EdgeWsAttemptResult(
        val audioBytes: ByteArray? = null,
        val serverDateHeader: String? = null
    )

    private suspend fun executeEdgeWebSocketOnce(
        chunkText: String,
        persona: NewsVoicePersona,
        naturalFriendlyMale: Boolean = false
    ): EdgeWsAttemptResult = withTimeoutOrNull(16_000L) {
        suspendCancellableCoroutine { cont ->
            val connectionId = UUID.randomUUID().toString().replace("-", "")
            val requestId = UUID.randomUUID().toString().replace("-", "")
            val unixSeconds = (System.currentTimeMillis() / 1000.0) + clockSkewSeconds
            val secMsGec = buildEdgeSecMsGec(unixSeconds)
            val muid = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.US)

            val wsUrl = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1" +
                "?TrustedClientToken=$EDGE_TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$EDGE_SEC_MS_GEC_VERSION"

            val request = Request.Builder()
                .url(wsUrl)
                .header("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
                .header("User-Agent", EDGE_USER_AGENT)
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8")
                .header("Pragma", "no-cache")
                .header("Cache-Control", "no-cache")
                .header("Cookie", "muid=$muid;")
                .build()

            val audioStream = ByteArrayOutputStream()
            val pathAudioMarker = "Path:audio\r\n".toByteArray(Charsets.US_ASCII)

            val ws = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val timestamp = buildEdgeTimestamp()
                    val configMsg = "X-Timestamp:$timestamp\r\n" +
                        "Content-Type:application/json; charset=utf-8\r\n" +
                        "Path:speech.config\r\n\r\n" +
                        """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}"""
                    webSocket.send(configMsg)

                    val ssml = if (naturalFriendlyMale) {
                        buildNaturalFriendlyMaleEdgeSsml(chunkText)
                    } else {
                        buildEdgeSsmlForPersona(chunkText, persona)
                    }
                    val ssmlMsg = "X-RequestId:$requestId\r\n" +
                        "Content-Type:application/ssml+xml\r\n" +
                        "X-Timestamp:${timestamp}Z\r\n" +
                        "Path:ssml\r\n\r\n$ssml"
                    webSocket.send(ssmlMsg)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val raw = bytes.toByteArray()
                    if (raw.size < 2) return
                    val headerLen = ((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)
                    val audioStart = 2 + headerLen
                    if (audioStart < raw.size) {
                        audioStream.write(raw, audioStart, raw.size - audioStart)
                    } else {
                        val markerIdx = indexOfSubArray(raw, pathAudioMarker)
                        if (markerIdx >= 0) {
                            val start = markerIdx + pathAudioMarker.size
                            if (start < raw.size) audioStream.write(raw, start, raw.size - start)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        webSocket.close(1000, "done")
                        if (cont.isActive) {
                            cont.resume(EdgeWsAttemptResult(audioBytes = audioStream.toByteArray()))
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val serverDate = response?.header("Date")
                    if (cont.isActive) {
                        val partial = audioStream.toByteArray()
                        cont.resume(
                            EdgeWsAttemptResult(
                                audioBytes = if (partial.size > 400) partial else null,
                                serverDateHeader = serverDate
                            )
                        )
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (cont.isActive) {
                        val data = audioStream.toByteArray()
                        cont.resume(EdgeWsAttemptResult(audioBytes = if (data.size > 400) data else null))
                    }
                }
            })

            cont.invokeOnCancellation { ws.cancel() }
        }
    } ?: EdgeWsAttemptResult()

    private fun adjustClockSkewFromHttpDate(dateHeader: String) {
        try {
            val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("GMT")
            val serverMs = sdf.parse(dateHeader)?.time ?: return
            clockSkewSeconds = (serverMs - System.currentTimeMillis()) / 1000.0
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------------------
    // TIER 2B: Native Iranian Neural Backup via Bing Translator (`fa-IR`)
    // -------------------------------------------------------------------------
    private fun tryBingNativeIranianTts(
        chunkText: String,
        persona: NewsVoicePersona,
        naturalFriendlyMale: Boolean = false
    ): ByteArray? {
        for (attempt in 0..1) {
            val session = getOrFetchBingSession(forceRefresh = attempt > 0) ?: return null
            val ssml = if (naturalFriendlyMale) {
                buildNaturalFriendlyMaleBingSsml(chunkText)
            } else {
                buildBingSsmlForPersona(chunkText, persona)
            }
            val url = "https://www.bing.com/tfettts?isVertical=1&&IG=${session.ig}&IID=${session.iid}"
            val formBody = FormBody.Builder()
                .add("ssml", ssml)
                .add("token", session.token)
                .add("key", session.key)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Referer", "https://www.bing.com/translator")
                .header("Origin", "https://www.bing.com")
                .header("Accept", "audio/mpeg, audio/*;q=0.9, */*;q=0.8")
                .build()

            try {
                httpClient.newCall(request).execute().use { resp ->
                    val contentType = resp.header("Content-Type").orEmpty()
                    val bodyBytes = resp.body?.bytes()
                    if (resp.isSuccessful && bodyBytes != null && isValidAudioPayload(bodyBytes, contentType)) {
                        return bodyBytes
                    }
                    if (resp.code == 401 || resp.code == 403) {
                        cachedBingSession = null
                    }
                }
            } catch (_: Exception) {
                cachedBingSession = null
            }
        }
        return null
    }

    private fun getOrFetchBingSession(forceRefresh: Boolean): BingTtsSession? {
        val now = System.currentTimeMillis()
        val existing = cachedBingSession
        if (!forceRefresh && existing != null && (now - existing.fetchedAtMs) < 8 * 60_000L) {
            return existing
        }
        val req = Request.Builder()
            .url("https://www.bing.com/translator")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "fa-IR,fa;q=0.9,en-US;q=0.8")
            .build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val html = resp.body?.string().orEmpty()
                parseBingTranslatorSession(html, now)?.also { cachedBingSession = it }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun playAudioFileSuspending(file: File): Boolean =
        suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            mediaPlayer = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(file.absolutePath)
                mp.setOnPreparedListener { player ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        runCatching {
                            val params = player.playbackParams
                            params.speed = _state.value.speechSpeed.coerceIn(0.8f, 1.4f)
                            player.playbackParams = params
                        }
                    }
                    player.start()
                }
                mp.setOnCompletionListener { player ->
                    runCatching { player.release() }
                    if (mediaPlayer === player) mediaPlayer = null
                    if (cont.isActive) cont.resume(true)
                }
                mp.setOnErrorListener { player, _, _ ->
                    runCatching { player.release() }
                    if (mediaPlayer === player) mediaPlayer = null
                    if (cont.isActive) cont.resume(false)
                    true
                }
                mp.prepareAsync()
            } catch (_: Exception) {
                runCatching { mp.release() }
                if (mediaPlayer === mp) mediaPlayer = null
                if (cont.isActive) cont.resume(false)
            }

            cont.invokeOnCancellation {
                runCatching {
                    if (mp.isPlaying) mp.stop()
                    mp.reset()
                    mp.release()
                }
                if (mediaPlayer === mp) mediaPlayer = null
            }
        }

    fun cycleSpeed() {
        val next = when (_state.value.speechSpeed) {
            in 0.0f..0.95f -> 1.0f
            in 0.96f..1.10f -> 1.15f
            in 1.11f..1.25f -> 1.30f
            else -> 0.90f
        }
        _state.value = _state.value.copy(speechSpeed = next)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val params = mp.playbackParams
                        params.speed = next
                        mp.playbackParams = params
                    }
                }
            }
        }
    }

    fun clearStatusMessage() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    fun stop() {
        stopInternal(clearStatus = true)
    }

    fun release() {
        stopInternal(clearStatus = true)
        runCatching { tempAudioCacheDir.listFiles()?.forEach { it.delete() } }
        scope.cancel()
    }

    private fun stopInternal(clearStatus: Boolean) {
        playbackJob?.cancel()
        playbackJob = null
        releaseMediaPlayer()
        runCatching {
            offlineTts?.stop()
            offlineTts?.shutdown()
        }
        offlineTts = null
        abandonAudioFocus()
        val prev = _state.value
        _state.value = NewsSpeechState(
            speechSpeed = prev.speechSpeed,
            voicePersona = prev.voicePersona,
            statusMessage = if (clearStatus) null else prev.statusMessage
        )
    }

    private fun releaseMediaPlayer() {
        runCatching {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.reset()
                mp.release()
            }
        }
        mediaPlayer = null
    }

    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    private fun parseBingTranslatorSession(html: String, nowMs: Long): BingTtsSession? {
        if (html.isBlank()) return null
        val ig = BING_IG_REGEX.find(html)?.groupValues?.get(1) ?: return null
        val iid = BING_IID_REGEX.find(html)?.groupValues?.get(1) ?: "translator.5028"
        val abuseMatch = BING_ABUSE_REGEX.find(html) ?: return null
        val key = abuseMatch.groupValues[1]
        val token = abuseMatch.groupValues[2]
        if (key.isBlank() || token.isBlank()) return null
        return BingTtsSession(ig = ig, iid = iid, key = key, token = token, fetchedAtMs = nowMs)
    }

    companion object {
        const val GEMINI_TTS_MODEL_NAME = "gemini-3.1-flash-tts-preview"
        private const val EDGE_TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
        private const val EDGE_SEC_MS_GEC_VERSION = "1-143.0.3650.75"
        private const val WIN_EPOCH_SECONDS = 11644473600.0
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36"
        private const val EDGE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0"

        private val BING_IG_REGEX = Regex("""["']?IG["']?\s*:\s*["']([A-Za-z0-9]+)["']""")
        private val BING_IID_REGEX = Regex("""data-iid=["']([^"']+)["']""")
        private val BING_ABUSE_REGEX = Regex("""params_AbusePreventionHelper\s*=\s*\[\s*(\d+)\s*,\s*["']([^"']+)["']""")
        private val RATE_REGEX = Regex("""rate=(\d+)""")

        private val URL_REGEX = Regex("""https?://\S+""")
        private val MULTI_SPACE_REGEX = Regex("""\s+""")

        fun buildGeminiTehraniTtsPrompt(text: String, persona: NewsVoicePersona): String {
            return buildString {
                append("متن خبر زیر را دقیقاً و فقط همان‌گونه که نوشته شده، ")
                append(persona.geminiStyleCueFa)
                append(" و با لهجه کاملاً اصیل ایرانی-تهرانی (فارسی معیار ایران، شبیه گویندگان خبر صداوسیمای تهران، بدون هیچ‌گونه لهجه دری، افغانی یا تاجیکی و با لحن زنده، طبیعی و غیرماشینی) بخوان. هیچ جمله اضافه‌ای از خودت قبل یا بعد از خبر نگو:\n\n")
                append(text.trim())
            }
        }

        fun parseSampleRateFromMime(mimeType: String): Int {
            return RATE_REGEX.find(mimeType)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.coerceIn(8000, 48000) ?: 24000
        }

        fun isWavHeader(bytes: ByteArray): Boolean {
            return bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
        }

        fun wrapPcm16AsWav(pcmBytes: ByteArray, sampleRate: Int = 24000): ByteArray {
            val channels = 1
            val bitsPerSample = 16
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = (channels * bitsPerSample / 8).toShort()
            val dataSize = pcmBytes.size
            val chunkSize = 36 + dataSize

            val buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
            buffer.putInt(chunkSize)
            buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
            buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
            buffer.putInt(16)
            buffer.putShort(1)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign)
            buffer.putShort(bitsPerSample.toShort())
            buffer.put("data".toByteArray(Charsets.US_ASCII))
            buffer.putInt(dataSize)
            buffer.put(pcmBytes)
            return buffer.array()
        }

        fun buildEdgeSecMsGec(unixSecondsUtc: Double): String {
            var ticks = unixSecondsUtc + WIN_EPOCH_SECONDS
            ticks -= (ticks % 300.0)
            val winTicks = (ticks * 10_000_000.0).toLong()
            val payload = "$winTicks$EDGE_TRUSTED_CLIENT_TOKEN"
            val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.US_ASCII))
            return digest.joinToString("") { "%02X".format(Locale.US, it) }
        }

        fun buildBingSsmlForPersona(
            text: String,
            persona: NewsVoicePersona
        ): String {
            val escaped = escapeXml(text)
                .replace(". ", ". <break time='210ms'/> ")
                .replace("، ", "، <break time='105ms'/> ")
            return "<speak version='1.0' xml:lang='fa-IR'>" +
                "<voice xml:lang='fa-IR' xml:gender='${persona.nativeIrGender}' name='${persona.nativeIrBingVoice}'>" +
                "<prosody rate='${persona.prosodyRate}' pitch='${persona.bingPitch}'>$escaped</prosody>" +
                "</voice></speak>"
        }

        fun buildEdgeSsmlForPersona(
            text: String,
            persona: NewsVoicePersona
        ): String {
            val escaped = escapeXml(text)
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='fa-IR'>" +
                "<voice name='${persona.nativeIrEdgeName}'>" +
                "<prosody pitch='${persona.edgePitch}' rate='${persona.prosodyRate}' volume='+0%'>$escaped</prosody>" +
                "</voice></speak>"
        }

        /**
         * Warm, engaging, natural young Iranian male voice profile for AI replies
         * (clear & lively, avoiding an overly deep/booming baritone pitch).
         */
        fun buildNaturalFriendlyMaleEdgeSsml(text: String): String {
            val escaped = escapeXml(text)
            return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='fa-IR'>" +
                "<voice name='Microsoft Server Speech Text to Speech Voice (fa-IR, FaridNeural)'>" +
                "<prosody pitch='+10Hz' rate='+5%' volume='+0%'>$escaped</prosody>" +
                "</voice></speak>"
        }

        fun buildNaturalFriendlyMaleBingSsml(text: String): String {
            val escaped = escapeXml(text)
                .replace(". ", ". <break time='190ms'/> ")
                .replace("، ", "، <break time='95ms'/> ")
            return "<speak version='1.0' xml:lang='fa-IR'>" +
                "<voice xml:lang='fa-IR' xml:gender='Male' name='fa-IR-FaridNeural'>" +
                "<prosody rate='+5%' pitch='+7%'>$escaped</prosody>" +
                "</voice></speak>"
        }

        fun buildBingSsml(text: String, useMale: Boolean = true): String {
            val persona = if (useMale) NewsVoicePersona.ARASH_MALE else NewsVoicePersona.SARA_FEMALE
            return buildBingSsmlForPersona(text, persona)
        }

        fun buildEdgeSsml(text: String, useMale: Boolean = true): String {
            val persona = if (useMale) NewsVoicePersona.ARASH_MALE else NewsVoicePersona.SARA_FEMALE
            return buildEdgeSsmlForPersona(text, persona)
        }

        private fun buildEdgeTimestamp(): String {
            val sdf = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date())
        }

        private fun escapeXml(text: String): String {
            return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;")
        }

        private fun isValidAudioPayload(bytes: ByteArray, contentType: String): Boolean {
            if (bytes.size < 400) return false
            val firstByte = bytes[0].toInt() and 0xFF
            if (firstByte == '<'.code || firstByte == '{'.code) return false
            if (contentType.contains("audio", ignoreCase = true) ||
                contentType.contains("octet-stream", ignoreCase = true)
            ) {
                return true
            }
            val isId3 = bytes.size > 3 && bytes[0] == 'I'.code.toByte() &&
                bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
            val isMpegSync = firstByte == 0xFF && ((bytes[1].toInt() and 0xE0) == 0xE0)
            return isId3 || isMpegSync
        }

        private fun indexOfSubArray(source: ByteArray, target: ByteArray): Int {
            if (target.isEmpty() || source.size < target.size) return -1
            outer@ for (i in 0..(source.size - target.size)) {
                for (j in target.indices) {
                    if (source[i + j] != target[j]) continue@outer
                }
                return i
            }
            return -1
        }

        fun preparePersianNewsScript(article: NewsArticle): String {
            val cleanTitle = normalizePersianForSpeech(article.title)
            val rawBody = article.fullContent.ifBlank { article.summary }
            val strippedBody = rawBody
                .replace(Regex("""منبع\s*:\s*.*$""", RegexOption.MULTILINE), "")
                .replace(article.title, "")
                .trim()
            val cleanBody = normalizePersianForSpeech(strippedBody)

            return buildString {
                append("گزارش خبری از ")
                append(article.source.displayName)
                append(". ")
                append(cleanTitle)
                if (cleanBody.isNotBlank()) {
                    append(". ")
                    append(cleanBody)
                }
            }.trim()
        }

        fun normalizePlainPersianForSpeech(raw: String): String {
            val stripped = raw
                .replace(Regex("""```[\s\S]*?```"""), " ")
                .replace(Regex("""[*_`#~>]+"""), " ")
                .replace("☐", "مورد: ")
                .replace("☑", "انجام‌شده: ")
                .replace("✅", " ")
                .replace("❌", " ")
            return normalizePersianForSpeech(stripped)
        }

        fun normalizePersianForSpeech(raw: String): String {
            var text = URL_REGEX.replace(raw, " ")
            text = text
                .replace('ي', 'ی')
                .replace('ك', 'ک')
                .replace('ة', 'ه')
                .replace("•", "، ")
                .replace("|", "، ")
                .replace("—", "، ")
                .replace("–", "، ")
                .replace("…", ".")
                .replace("...", ".")
                .replace("%", " درصد ")
                .replace("٪", " درصد ")
                .replace("$", " دلار ")
                .replace("«", " ")
                .replace("»", " ")
                .replace("\"", " ")
                .replace("\n", ". ")

            text = PersianDateHelper.toPersianDigits(text)
            text = MULTI_SPACE_REGEX.replace(text, " ").trim()
            return text
        }

        fun splitIntoNaturalPersianChunks(text: String, maxChunkLength: Int = 520): List<String> {
            val cleaned = text.trim()
            if (cleaned.isEmpty()) return emptyList()

            val result = mutableListOf<String>()
            val rawSentences = cleaned
                .split(Regex("""(?<=[.!؟?؛\n])\s+"""))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val buffer = StringBuilder()
            fun flushBuffer() {
                val s = buffer.toString().trim()
                if (s.isNotEmpty()) result.add(s)
                buffer.clear()
            }

            for (sentence in rawSentences) {
                if (sentence.length <= maxChunkLength) {
                    if (buffer.isEmpty()) {
                        buffer.append(sentence)
                    } else if (buffer.length + 1 + sentence.length <= maxChunkLength) {
                        buffer.append(' ').append(sentence)
                    } else {
                        flushBuffer()
                        buffer.append(sentence)
                    }
                } else {
                    flushBuffer()
                    val clauses = sentence.split(Regex("""(?<=[،,:])\s+"""))
                    for (clause in clauses) {
                        val c = clause.trim()
                        if (c.isEmpty()) continue
                        if (c.length <= maxChunkLength) {
                            if (buffer.isEmpty()) {
                                buffer.append(c)
                            } else if (buffer.length + 1 + c.length <= maxChunkLength) {
                                buffer.append(' ').append(c)
                            } else {
                                flushBuffer()
                                buffer.append(c)
                            }
                        } else {
                            flushBuffer()
                            val words = c.split(" ")
                            for (word in words) {
                                if (buffer.isEmpty()) {
                                    buffer.append(word)
                                } else if (buffer.length + 1 + word.length <= maxChunkLength) {
                                    buffer.append(' ').append(word)
                                } else {
                                    flushBuffer()
                                    buffer.append(word)
                                }
                            }
                            flushBuffer()
                        }
                    }
                    flushBuffer()
                }
            }
            flushBuffer()
            return result
        }
    }
}
