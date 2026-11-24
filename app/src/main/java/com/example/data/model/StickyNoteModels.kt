package com.example.data.model

import android.content.Context
import com.squareup.moshi.Moshi
import java.util.UUID

/**
 * Curated vibrant pastel sticky-note paper palette with coordinated fold & ink accent colors.
 */
enum class StickyNoteColor(
    val id: String,
    val labelFa: String,
    val paperHex: Long,
    val foldHex: Long,
    val accentHex: Long,
    val borderHex: Long
) {
    SUNSHINE(
        id = "sunshine",
        labelFa = "زرد آفتابی",
        paperHex = 0xFFFEF08A,
        foldHex = 0xFFFACC15,
        accentHex = 0xFFB45309,
        borderHex = 0xFFEAB308
    ),
    CORAL_ROSE(
        id = "coral_rose",
        labelFa = "صورتی مرجانی",
        paperHex = 0xFFFECDD3,
        foldHex = 0xFFFDA4AF,
        accentHex = 0xFFBE123C,
        borderHex = 0xFFFB7185
    ),
    MINT_FRESH(
        id = "mint_fresh",
        labelFa = "سبز نعنایی",
        paperHex = 0xFFA7F3D0,
        foldHex = 0xFF6EE7B7,
        accentHex = 0xFF047857,
        borderHex = 0xFF34D399
    ),
    SKY_BREEZE(
        id = "sky_breeze",
        labelFa = "آبی آسمانی",
        paperHex = 0xFFBAE6FD,
        foldHex = 0xFF7DD3FC,
        accentHex = 0xFF0369A1,
        borderHex = 0xFF38BDF8
    ),
    LAVENDER_SOFT(
        id = "lavender_soft",
        labelFa = "یاسی روشن",
        paperHex = 0xFFE9D5FF,
        foldHex = 0xFFD8B4FE,
        accentHex = 0xFF6D28D9,
        borderHex = 0xFFC084FC
    ),
    WARM_APRICOT(
        id = "warm_apricot",
        labelFa = "هلویی گرم",
        paperHex = 0xFFFED7AA,
        foldHex = 0xFFFDBA74,
        accentHex = 0xFFC2410C,
        borderHex = 0xFFFB923C
    );

    companion object {
        fun fromId(id: String?): StickyNoteColor {
            return entries.firstOrNull { it.id == id } ?: SUNSHINE
        }
    }
}

/**
 * Input mode supported on every sticky note: typed text, freehand handwriting, or both combined.
 */
enum class StickyNoteMode(val labelFa: String) {
    TYPED("تایپی"),
    HANDWRITING("دست‌خط"),
    MIXED("ترکیبی");

    companion object {
        fun fromName(name: String?): StickyNoteMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TYPED
        }
    }
}

/**
 * Single point in a handwriting stroke using normalized coordinates (0f..1f)
 * so sketches render identically across editor canvas, board thumbnails, and notification bitmaps.
 */
data class StrokePoint(
    val x: Float,
    val y: Float
)

/**
 * One continuous pen stroke with ink color and stroke thickness.
 */
data class HandwritingStroke(
    val colorHex: Long = 0xFF1E293B,
    val strokeWidthDp: Float = 4.5f,
    val points: List<StrokePoint> = emptyList()
)

/**
 * Complete Sticky Note model supporting typed content, handwritten ink strokes,
 * scheduled Shamsi date/time alarm, persistent notification pin, and timed heads-up pop-up with "ding" chime.
 */
data class StickyNoteItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val textContent: String = "",
    val strokes: List<HandwritingStroke> = emptyList(),
    val mode: StickyNoteMode = StickyNoteMode.TYPED,
    val colorId: String = StickyNoteColor.SUNSHINE.id,
    val isPinnedTop: Boolean = false,
    val alarmEnabled: Boolean = false,
    val alarmShamsiDate: String = "",
    val alarmHour: Int = 9,
    val alarmMinute: Int = 0,
    val pinInNotification: Boolean = false,
    val popupReminderEnabled: Boolean = false,
    val popupIntervalMinutes: Int = 30,
    val playDingSound: Boolean = true,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val noteColor: StickyNoteColor
        get() = StickyNoteColor.fromId(colorId)

    val hasHandwriting: Boolean
        get() = strokes.any { it.points.isNotEmpty() }

    val hasTypedText: Boolean
        get() = textContent.isNotBlank()

    fun displayTitle(): String {
        val t = title.trim()
        if (t.isNotEmpty()) return t
        val firstLine = textContent.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isNotEmpty()) return firstLine.take(36)
        return if (hasHandwriting) "یادداشت دست‌نویس" else "یادداشت چسبان"
    }

    fun formattedAlarmTime(): String {
        val hh = alarmHour.coerceIn(0, 23).toString().padStart(2, '0')
        val mm = alarmMinute.coerceIn(0, 59).toString().padStart(2, '0')
        return "$hh:$mm"
    }
}

/**
 * Pure-JVM & Android safe JSON persistence for Sticky Notes (backed up automatically by BackupRestoreHelper).
 */
object StickyNoteStorage {
    const val PREFS_NAME = "sticky_notes_prefs"
    private const val KEY_NOTES_JSON = "sticky_notes_json_v1"

    private val moshiAdapter = Moshi.Builder().build().adapter(Any::class.java).lenient()

    fun loadNotes(context: Context): List<StickyNoteItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NOTES_JSON, null) ?: return emptyList()
        return deserializeNotes(raw)
    }

    fun saveNotes(context: Context, notes: List<StickyNoteItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NOTES_JSON, serializeNotes(notes)).apply()
    }

    fun updateSingleNote(context: Context, noteId: String, transform: (StickyNoteItem) -> StickyNoteItem): StickyNoteItem? {
        val current = loadNotes(context)
        var updatedItem: StickyNoteItem? = null
        val next = current.map { item ->
            if (item.id == noteId) {
                transform(item).also { updatedItem = it }
            } else {
                item
            }
        }
        if (updatedItem != null) {
            saveNotes(context, next)
        }
        return updatedItem
    }

    fun formatPopupIntervalLabel(minutes: Int): String {
        val safe = minutes.coerceAtLeast(1)
        return when {
            safe < 60 -> "هر $safe دقیقه"
            safe == 60 -> "هر 1 ساعت"
            safe % 1440 == 0 -> {
                val days = safe / 1440
                if (days == 1) "روزانه (24 ساعت)" else "هر $days روز"
            }
            safe % 60 == 0 -> "هر ${safe / 60} ساعت"
            else -> "هر ${safe / 60} ساعت و ${safe % 60} دقیقه"
        }
    }

    fun serializeNotes(notes: List<StickyNoteItem>): String {
        val serialized = notes.map { note ->
            linkedMapOf<String, Any?>(
                "id" to note.id,
                "title" to note.title,
                "textContent" to note.textContent,
                "mode" to note.mode.name,
                "colorId" to note.colorId,
                "isPinnedTop" to note.isPinnedTop,
                "alarmEnabled" to note.alarmEnabled,
                "alarmShamsiDate" to note.alarmShamsiDate,
                "alarmHour" to note.alarmHour,
                "alarmMinute" to note.alarmMinute,
                "pinInNotification" to note.pinInNotification,
                "popupReminderEnabled" to note.popupReminderEnabled,
                "popupIntervalMinutes" to note.popupIntervalMinutes,
                "playDingSound" to note.playDingSound,
                "createdAtMs" to note.createdAtMs,
                "updatedAtMs" to note.updatedAtMs,
                "strokes" to note.strokes.map { stroke ->
                    linkedMapOf<String, Any?>(
                        "c" to stroke.colorHex,
                        "w" to stroke.strokeWidthDp.toDouble(),
                        "p" to stroke.points.map { pt ->
                            listOf(
                                (pt.x * 10000f).toInt() / 10000.0,
                                (pt.y * 10000f).toInt() / 10000.0
                            )
                        }
                    )
                }
            )
        }
        return moshiAdapter.toJson(serialized)
    }

    @Suppress("UNCHECKED_CAST")
    fun deserializeNotes(json: String): List<StickyNoteItem> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val rawList = moshiAdapter.fromJson(json) as? List<*> ?: return emptyList()
            rawList.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val strokesRaw = map["strokes"] as? List<*> ?: emptyList<Any>()
                val strokes = strokesRaw.mapNotNull { st ->
                    val sm = st as? Map<*, *> ?: return@mapNotNull null
                    val colorHex = (sm["c"] as? Number)?.toLong() ?: 0xFF1E293B
                    val widthDp = (sm["w"] as? Number)?.toFloat() ?: 4.5f
                    val ptsRaw = sm["p"] as? List<*> ?: emptyList<Any>()
                    val pts = ptsRaw.mapNotNull { p ->
                        val pair = p as? List<*> ?: return@mapNotNull null
                        if (pair.size < 2) return@mapNotNull null
                        val x = (pair[0] as? Number)?.toFloat() ?: return@mapNotNull null
                        val y = (pair[1] as? Number)?.toFloat() ?: return@mapNotNull null
                        StrokePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
                    }
                    if (pts.isEmpty()) null else HandwritingStroke(colorHex, widthDp, pts)
                }
                StickyNoteItem(
                    id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    title = (map["title"] as? String) ?: "",
                    textContent = (map["textContent"] as? String) ?: "",
                    strokes = strokes,
                    mode = StickyNoteMode.fromName(map["mode"] as? String),
                    colorId = (map["colorId"] as? String) ?: StickyNoteColor.SUNSHINE.id,
                    isPinnedTop = (map["isPinnedTop"] as? Boolean) ?: false,
                    alarmEnabled = (map["alarmEnabled"] as? Boolean) ?: false,
                    alarmShamsiDate = (map["alarmShamsiDate"] as? String) ?: "",
                    alarmHour = ((map["alarmHour"] as? Number)?.toInt() ?: 9).coerceIn(0, 23),
                    alarmMinute = ((map["alarmMinute"] as? Number)?.toInt() ?: 0).coerceIn(0, 59),
                    pinInNotification = (map["pinInNotification"] as? Boolean) ?: false,
                    popupReminderEnabled = (map["popupReminderEnabled"] as? Boolean) ?: false,
                    popupIntervalMinutes = ((map["popupIntervalMinutes"] as? Number)?.toInt() ?: 30).coerceIn(1, 10080),
                    playDingSound = (map["playDingSound"] as? Boolean) ?: true,
                    createdAtMs = (map["createdAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAtMs = (map["updatedAtMs"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }
        }.getOrDefault(emptyList())
    }
}
