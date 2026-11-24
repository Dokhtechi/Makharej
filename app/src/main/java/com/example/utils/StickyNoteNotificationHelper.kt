package com.example.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.provider.AlarmClock
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.StickyNoteColor
import com.example.data.model.StickyNoteItem
import com.example.data.model.StickyNoteStorage
import java.util.Calendar
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Synthesizes and plays a short, pleasant crystal-bell "Ding!" chime (~420 ms)
 * whenever a Sticky Note pops up on the home screen.
 */
object StickyNoteSoundPlayer {

    private const val SAMPLE_RATE = 44100
    private const val DURATION_MS = 430

    /**
     * Generates 16-bit signed PCM samples for a warm bell "ding" (C6 + C7 + E7 harmonics with exponential decay).
     */
    fun generateDingPcmSamples(): ShortArray {
        val totalSamples = (SAMPLE_RATE * DURATION_MS) / 1000
        val samples = ShortArray(totalSamples)
        val attackSamples = (SAMPLE_RATE * 6) / 1000 // crisp 6ms mallet attack

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE.toDouble()
            val attack = if (i < attackSamples) i.toDouble() / attackSamples.toDouble() else 1.0
            val fundamental = sin(2.0 * PI * 1046.50 * t) * exp(-6.8 * t)        // C6 bell core
            val octave = 0.42 * sin(2.0 * PI * 2093.00 * t) * exp(-10.5 * t)     // C7 sparkle
            val fifthOvertone = 0.20 * sin(2.0 * PI * 2637.02 * t) * exp(-14.0 * t) // E7 chime shimmer
            val mixed = (fundamental + octave + fifthOvertone) * attack * 0.72
            val clamped = mixed.coerceIn(-1.0, 1.0)
            samples[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
        return samples
    }

    /**
     * Plays the short "Ding!" chime asynchronously on notification audio stream, respecting silent/DND mode.
     */
    fun playDing(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null && audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }
        thread(start = true, isDaemon = true, name = "sticky-note-ding") {
            val played = runCatching {
                val pcm = generateDingPcmSamples()
                val byteLength = pcm.size * 2
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val audioFormat = AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
                val track = AudioTrack.Builder()
                    .setAudioAttributes(audioAttrs)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(byteLength)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(DURATION_MS + 90L)
                runCatching { track.stop() }
                runCatching { track.release() }
                true
            }.getOrDefault(false)

            if (!played) {
                runCatching {
                    val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85)
                    tg.startTone(ToneGenerator.TONE_PROP_BEEP, 180)
                    Thread.sleep(240L)
                    tg.release()
                }
            }
        }
    }
}

/**
 * Renders a Sticky Note (including handwritten ink strokes) to a crisp Bitmap for Android
 * BigPictureStyle notifications & home-screen heads-up banners.
 */
object StickyNoteBitmapRenderer {

    fun renderPreviewBitmap(note: StickyNoteItem, widthPx: Int = 720, heightPx: Int = 360): Bitmap? {
        if (!note.hasHandwriting) return null
        return runCatching {
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val palette = note.noteColor

            // Paper fill
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.paperHex.toInt()
                style = Paint.Style.FILL
            }
            val rect = RectF(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawRoundRect(rect, 28f, 28f, bgPaint)

            // Top color tape strip
            val stripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.foldHex.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, widthPx.toFloat(), 20f, stripPaint)

            // Subtle ruled lines
            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.borderHex.toInt()
                alpha = 70
                strokeWidth = 2f
            }
            var y = 64f
            while (y < heightPx - 20f) {
                canvas.drawLine(24f, y, widthPx - 24f, y, linePaint)
                y += 48f
            }

            // Draw handwriting strokes
            val padX = 24f
            val padY = 28f
            val drawW = (widthPx - padX * 2f).coerceAtLeast(1f)
            val drawH = (heightPx - padY * 2f).coerceAtLeast(1f)

            note.strokes.forEach { stroke ->
                val pts = stroke.points
                if (pts.isEmpty()) return@forEach
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.colorHex.toInt()
                    strokeWidth = (stroke.strokeWidthDp * 2.1f).coerceIn(3f, 24f)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    style = Paint.Style.STROKE
                }
                if (pts.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(
                        padX + pts[0].x * drawW,
                        padY + pts[0].y * drawH,
                        paint.strokeWidth * 0.55f,
                        paint
                    )
                } else {
                    val path = Path()
                    val firstX = padX + pts[0].x * drawW
                    val firstY = padY + pts[0].y * drawH
                    path.moveTo(firstX, firstY)
                    for (i in 1 until pts.size) {
                        val prevX = padX + pts[i - 1].x * drawW
                        val prevY = padY + pts[i - 1].y * drawH
                        val currX = padX + pts[i].x * drawW
                        val currY = padY + pts[i].y * drawH
                        path.quadTo(prevX, prevY, (prevX + currX) / 2f, (prevY + currY) / 2f)
                    }
                    val last = pts.last()
                    path.lineTo(padX + last.x * drawW, padY + last.y * drawH)
                    canvas.drawPath(path, paint)
                }
            }
            bitmap
        }.getOrNull()
    }
}

/**
 * BroadcastReceiver handling scheduled Sticky Note date/time alarms, repeating home-screen
 * heads-up pop-up reminders (with "Ding!" chime), and quick notification actions.
 */
class StickyNoteAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val noteId = intent.getStringExtra(StickyNoteNotificationHelper.EXTRA_NOTE_ID) ?: return

        when (action) {
            StickyNoteNotificationHelper.ACTION_ALARM_TRIGGER -> {
                val note = StickyNoteStorage.loadNotes(context).firstOrNull { it.id == noteId }
                if (note != null && note.alarmEnabled) {
                    StickyNoteNotificationHelper.showScheduledAlarmNotification(context, note)
                    // One-shot alarm fired; keep note intact and mark alarm fired cleanly
                    StickyNoteStorage.updateSingleNote(context, noteId) {
                        it.copy(alarmEnabled = false, updatedAtMs = System.currentTimeMillis())
                    }
                }
            }

            StickyNoteNotificationHelper.ACTION_POPUP_TRIGGER -> {
                val note = StickyNoteStorage.loadNotes(context).firstOrNull { it.id == noteId }
                if (note != null && note.popupReminderEnabled) {
                    StickyNoteNotificationHelper.showHeadsUpPopupNotification(context, note)
                    // Automatically reschedule next periodic pop-up
                    StickyNoteNotificationHelper.scheduleNextPopup(context, note)
                }
            }

            StickyNoteNotificationHelper.ACTION_SNOOZE_ALARM -> {
                StickyNoteNotificationHelper.dismissNotification(
                    context,
                    StickyNoteNotificationHelper.alarmNotificationId(noteId)
                )
                val note = StickyNoteStorage.loadNotes(context).firstOrNull { it.id == noteId }
                if (note != null) {
                    StickyNoteStorage.updateSingleNote(context, noteId) {
                        it.copy(alarmEnabled = true, updatedAtMs = System.currentTimeMillis())
                    }
                    StickyNoteNotificationHelper.scheduleSnoozeAlarm(context, note, snoozeMinutes = 10)
                }
            }

            StickyNoteNotificationHelper.ACTION_DISMISS_ALARM -> {
                StickyNoteNotificationHelper.dismissNotification(
                    context,
                    StickyNoteNotificationHelper.alarmNotificationId(noteId)
                )
            }

            StickyNoteNotificationHelper.ACTION_STOP_POPUP -> {
                StickyNoteNotificationHelper.cancelPopupReminder(context, noteId)
                StickyNoteNotificationHelper.dismissNotification(
                    context,
                    StickyNoteNotificationHelper.popupNotificationId(noteId)
                )
                StickyNoteStorage.updateSingleNote(context, noteId) {
                    it.copy(popupReminderEnabled = false, updatedAtMs = System.currentTimeMillis())
                }
            }

            StickyNoteNotificationHelper.ACTION_UNPIN_NOTIFICATION -> {
                StickyNoteNotificationHelper.dismissPinnedNotification(context, noteId)
                StickyNoteStorage.updateSingleNote(context, noteId) {
                    it.copy(pinInNotification = false, updatedAtMs = System.currentTimeMillis())
                }
            }
        }
    }
}

/**
 * Restores all active Sticky Note alarms, periodic pop-ups, and pinned notifications after device reboot.
 */
class StickyNoteBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val notes = StickyNoteStorage.loadNotes(context)
            StickyNoteNotificationHelper.syncAllNotes(context, notes)
        }
    }
}

/**
 * Central manager for Sticky Note notification channels, date/time alarms,
 * pinned ongoing notifications, and timed home-screen heads-up pop-up reminders with "Ding!" sound.
 */
object StickyNoteNotificationHelper {

    const val EXTRA_NOTE_ID = "sticky_note_id"
    const val EXTRA_OPEN_STICKY_NOTES = "open_sticky_notes_tool"

    const val ACTION_ALARM_TRIGGER = "com.example.action.STICKY_NOTE_ALARM_TRIGGER"
    const val ACTION_POPUP_TRIGGER = "com.example.action.STICKY_NOTE_POPUP_TRIGGER"
    const val ACTION_SNOOZE_ALARM = "com.example.action.STICKY_NOTE_SNOOZE_ALARM"
    const val ACTION_DISMISS_ALARM = "com.example.action.STICKY_NOTE_DISMISS_ALARM"
    const val ACTION_STOP_POPUP = "com.example.action.STICKY_NOTE_STOP_POPUP"
    const val ACTION_UNPIN_NOTIFICATION = "com.example.action.STICKY_NOTE_UNPIN_NOTIFICATION"

    const val CHANNEL_ALARM_ID = "sticky_note_alarm_channel_v1"
    const val CHANNEL_POPUP_ID = "sticky_note_popup_ding_channel_v1"
    const val CHANNEL_PINNED_ID = "sticky_note_pinned_channel_v1"

    fun alarmNotificationId(noteId: String): Int = 41000 + (noteId.hashCode() and 0x1FFF)
    fun popupNotificationId(noteId: String): Int = 51000 + (noteId.hashCode() and 0x1FFF)
    fun pinnedNotificationId(noteId: String): Int = 61000 + (noteId.hashCode() and 0x1FFF)

    private fun alarmRequestCode(noteId: String): Int = 71000 + (noteId.hashCode() and 0x1FFF)
    private fun popupRequestCode(noteId: String): Int = 81000 + (noteId.hashCode() and 0x1FFF)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        // 1. Scheduled Date/Time Alarm Channel (Alarm ringtone + strong vibration + heads-up)
        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val alarmAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM_ID,
            "آلارم یادداشت‌های چسبان",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "زنگ هشدار و آلارم تاریخ‌دار برای یادداشت‌های چسبان"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 350, 180, 350, 180, 450)
            setSound(alarmSound, alarmAudioAttrs)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 2. Timed Home-Screen Heads-Up Pop-Up Channel (Silent system tone so custom crystal "Ding!" shines cleanly)
        val popupChannel = NotificationChannel(
            CHANNEL_POPUP_ID,
            "یادآوری پاپ‌آپ یادداشت‌ها (با صدای دینگ)",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "نمایش پاپ‌آپ یادداشت روی صفحه اصلی موبایل در بازه زمانی دلخواه همراه با صدای دینگ"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 140, 80, 140)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        // 3. Persistent Pinned Sticky Note Channel (Quiet ongoing status bar card)
        val pinnedChannel = NotificationChannel(
            CHANNEL_PINNED_ID,
            "یادداشت‌های پین‌شده در نوتیفیکیشن",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "نمایش ثابت یادداشت‌های مهم در نوار نوتیفیکیشن"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannel(alarmChannel)
        manager.createNotificationChannel(popupChannel)
        manager.createNotificationChannel(pinnedChannel)
    }

    /**
     * Converts a Shamsi date string ("YYYY/MM/DD") + hour + minute into epoch milliseconds.
     */
    fun computeAlarmEpochMs(shamsiDate: String, hour: Int, minute: Int): Long? {
        val cleaned = PersianDateHelper.toEnglishDigits(shamsiDate).trim()
        val parts = cleaned.split("/", "-", ".")
        if (parts.size != 3) return null
        val jy = parts[0].toIntOrNull() ?: return null
        val jm = parts[1].toIntOrNull() ?: return null
        val jd = parts[2].toIntOrNull() ?: return null
        if (jy < 1300 || jm !in 1..12 || jd !in 1..31) return null

        val maxDay = PersianDateHelper.getDaysInMonth(jy, jm)
        val safeDay = jd.coerceIn(1, maxDay)
        val (gy, gm, gd) = PersianDateHelper.jalaliToGregorian(jy, jm, safeDay)

        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, gy)
            set(Calendar.MONTH, gm - 1)
            set(Calendar.DAY_OF_MONTH, gd)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    /**
     * Calculates the next timed pop-up trigger timestamp from now + intervalMinutes.
     */
    fun computeNextPopupEpochMs(nowMs: Long, intervalMinutes: Int): Long {
        val safeMinutes = intervalMinutes.coerceIn(1, 10080)
        return nowMs + safeMinutes * 60_000L
    }

    private fun buildOpenAppIntent(context: Context, noteId: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_STICKY_NOTES, true)
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNoteBodySummary(note: StickyNoteItem): String {
        val text = note.textContent.trim()
        return when {
            text.isNotEmpty() && note.hasHandwriting -> "$text\n✏️ همراه با یادداشت دست‌نویس"
            text.isNotEmpty() -> text
            note.hasHandwriting -> "✏️ یادداشت دست‌نویس (برای مشاهده کامل لمس کنید)"
            else -> "یادداشت چسبان"
        }
    }

    /**
     * Posts the scheduled Date/Time Alarm notification (alarm ringtone + Snooze/Dismiss actions).
     */
    fun showScheduledAlarmNotification(context: Context, note: StickyNoteItem) {
        createNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = alarmNotificationId(note.id)
        val contentText = buildNoteBodySummary(note)
        val bitmap = StickyNoteBitmapRenderer.renderPreviewBitmap(note)

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            Intent(context, StickyNoteAlarmReceiver::class.java).apply {
                action = ACTION_SNOOZE_ALARM
                putExtra(EXTRA_NOTE_ID, note.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            Intent(context, StickyNoteAlarmReceiver::class.java).apply {
                action = ACTION_DISMISS_ALARM
                putExtra(EXTRA_NOTE_ID, note.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ آلارم یادداشت: ${note.displayTitle()}")
            .setContentText(contentText)
            .setColor(note.noteColor.accentHex.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context, note.id, notifId))
            .addAction(android.R.drawable.ic_popup_reminder, "تعویق ۱۰ دقیقه", snoozeIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "بستن آلارم", dismissIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(contentText)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }

        runCatching { manager.notify(notifId, builder.build()) }
    }

    /**
     * Posts a high-priority Heads-Up Pop-Up banner directly over the mobile home screen
     * and plays the short crystal "Ding!" attention chime.
     */
    fun showHeadsUpPopupNotification(context: Context, note: StickyNoteItem) {
        createNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = popupNotificationId(note.id)
        val contentText = buildNoteBodySummary(note)
        val bitmap = StickyNoteBitmapRenderer.renderPreviewBitmap(note)

        // Play the signature short "Ding!" chime for attention
        if (note.playDingSound) {
            StickyNoteSoundPlayer.playDing(context)
        }

        val stopIntent = PendingIntent.getBroadcast(
            context,
            notifId + 3,
            Intent(context, StickyNoteAlarmReceiver::class.java).apply {
                action = ACTION_STOP_POPUP
                putExtra(EXTRA_NOTE_ID, note.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalTag = StickyNoteStorage.formatPopupIntervalLabel(note.popupIntervalMinutes)
        val builder = NotificationCompat.Builder(context, CHANNEL_POPUP_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("📌 یادآوری: ${note.displayTitle()}")
            .setContentText(contentText)
            .setSubText("پاپ‌آپ $intervalTag")
            .setTicker("یادآوری: ${note.displayTitle()}")
            .setColor(note.noteColor.accentHex.toInt())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 140, 80, 140))
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(buildOpenAppIntent(context, note.id, notifId))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "توقف پاپ‌آپ", stopIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(contentText)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }

        // Cancel & re-post briefly ensures Android re-triggers the Heads-Up pop-up banner on top of Home Screen
        runCatching {
            manager.cancel(notifId)
            manager.notify(notifId, builder.build())
        }
    }

    /**
     * Keeps a Sticky Note continuously visible in the Android notification drawer until unpinned.
     */
    fun showPinnedNotification(context: Context, note: StickyNoteItem) {
        createNotificationChannels(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val notifId = pinnedNotificationId(note.id)
        val contentText = buildNoteBodySummary(note)
        val bitmap = StickyNoteBitmapRenderer.renderPreviewBitmap(note)

        val unpinIntent = PendingIntent.getBroadcast(
            context,
            notifId + 4,
            Intent(context, StickyNoteAlarmReceiver::class.java).apply {
                action = ACTION_UNPIN_NOTIFICATION
                putExtra(EXTRA_NOTE_ID, note.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_PINNED_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("🗒️ ${note.displayTitle()}")
            .setContentText(contentText)
            .setColor(note.noteColor.accentHex.toInt())
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildOpenAppIntent(context, note.id, notifId))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "برداشتن از نوتیفیکیشن", unpinIntent)

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(bitmap)
                    .setSummaryText(contentText)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
        }

        runCatching { manager.notify(notifId, builder.build()) }
    }

    fun dismissPinnedNotification(context: Context, noteId: String) {
        dismissNotification(context, pinnedNotificationId(noteId))
    }

    fun dismissNotification(context: Context, notificationId: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching { manager.cancel(notificationId) }
    }

    private fun scheduleAlarmAt(context: Context, triggerAtMs: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
            }
        } catch (_: SecurityException) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback
        }
    }

    fun scheduleDateAlarm(context: Context, note: StickyNoteItem): Boolean {
        createNotificationChannels(context)
        if (!note.alarmEnabled || note.alarmShamsiDate.isBlank()) {
            cancelDateAlarm(context, note.id)
            return false
        }
        val triggerMs = computeAlarmEpochMs(note.alarmShamsiDate, note.alarmHour, note.alarmMinute)
            ?: return false
        if (triggerMs <= System.currentTimeMillis()) {
            cancelDateAlarm(context, note.id)
            return false
        }
        val intent = Intent(context, StickyNoteAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_NOTE_ID, note.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(note.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmAt(context, triggerMs, pi)
        return true
    }

    fun scheduleSnoozeAlarm(context: Context, note: StickyNoteItem, snoozeMinutes: Int = 10) {
        createNotificationChannels(context)
        val triggerMs = System.currentTimeMillis() + snoozeMinutes * 60_000L
        val intent = Intent(context, StickyNoteAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_NOTE_ID, note.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(note.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmAt(context, triggerMs, pi)
    }

    fun cancelDateAlarm(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StickyNoteAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(noteId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    fun scheduleNextPopup(context: Context, note: StickyNoteItem) {
        createNotificationChannels(context)
        if (!note.popupReminderEnabled) {
            cancelPopupReminder(context, note.id)
            return
        }
        val triggerMs = computeNextPopupEpochMs(System.currentTimeMillis(), note.popupIntervalMinutes)
        val intent = Intent(context, StickyNoteAlarmReceiver::class.java).apply {
            action = ACTION_POPUP_TRIGGER
            putExtra(EXTRA_NOTE_ID, note.id)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            popupRequestCode(note.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarmAt(context, triggerMs, pi)
    }

    fun cancelPopupReminder(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StickyNoteAlarmReceiver::class.java).apply {
            action = ACTION_POPUP_TRIGGER
        }
        val pi = PendingIntent.getBroadcast(
            context,
            popupRequestCode(noteId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /**
     * Applies all alarm, pinned-notification, and timed pop-up settings for a single Sticky Note.
     */
    fun applyNoteSchedules(context: Context, note: StickyNoteItem) {
        createNotificationChannels(context)
        if (note.alarmEnabled) {
            scheduleDateAlarm(context, note)
        } else {
            cancelDateAlarm(context, note.id)
        }

        if (note.pinInNotification) {
            showPinnedNotification(context, note)
        } else {
            dismissPinnedNotification(context, note.id)
        }

        if (note.popupReminderEnabled) {
            scheduleNextPopup(context, note)
        } else {
            cancelPopupReminder(context, note.id)
        }
    }

    /**
     * Cancels all alarms, pop-ups, and notifications associated with a deleted Sticky Note.
     */
    fun clearNoteSchedules(context: Context, noteId: String) {
        cancelDateAlarm(context, noteId)
        cancelPopupReminder(context, noteId)
        dismissPinnedNotification(context, noteId)
        dismissNotification(context, alarmNotificationId(noteId))
        dismissNotification(context, popupNotificationId(noteId))
    }

    /**
     * Synchronizes all saved Sticky Notes (used on boot and when opening the Sticky Notes board).
     */
    fun syncAllNotes(context: Context, notes: List<StickyNoteItem>) {
        createNotificationChannels(context)
        notes.forEach { note ->
            if (note.alarmEnabled) scheduleDateAlarm(context, note)
            if (note.pinInNotification) showPinnedNotification(context, note)
            if (note.popupReminderEnabled) scheduleNextPopup(context, note)
        }
    }

    /**
     * Optional helper to also send the alarm time into the phone's native Clock app via standard Intent.
     */
    fun openInSystemClockAlarm(context: Context, note: StickyNoteItem): Boolean {
        return runCatching {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, note.alarmHour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, note.alarmMinute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, note.displayTitle())
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
