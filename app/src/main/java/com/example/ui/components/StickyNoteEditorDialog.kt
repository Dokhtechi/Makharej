package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.StickyNoteColor
import com.example.data.model.StickyNoteItem
import com.example.data.model.StickyNoteMode
import com.example.data.model.StickyNoteStorage
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.enDigits
import com.example.utils.PersianDateHelper
import com.example.utils.StickyNoteNotificationHelper
import java.util.Calendar
import java.util.UUID

private val POPUP_INTERVAL_PRESETS = listOf(
    1 to "۱ دقیقه",
    5 to "۵ دقیقه",
    15 to "۱۵ دقیقه",
    30 to "۳۰ دقیقه",
    60 to "۱ ساعت",
    120 to "۲ ساعت",
    360 to "۶ ساعت",
    1440 to "روزانه"
)

/**
 * Full-featured Sticky Note Creator & Editor Dialog:
 * - Paper color picker (6 joyful tones)
 * - Mode switcher: Typed / Handwriting / Mixed
 * - Scheduled Shamsi date & time alarm (+ optional system Clock shortcut)
 * - Persistent Notification Bar pin
 * - Timed Home-Screen Heads-Up Pop-Up reminder with signature short "Ding!" chime
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickyNoteEditorDialog(
    initialNote: StickyNoteItem?,
    initialMode: StickyNoteMode = StickyNoteMode.TYPED,
    onDismiss: () -> Unit,
    onSave: (StickyNoteItem) -> Unit
) {
    val context = LocalContext.current
    val todayShamsi = remember { PersianDateHelper.getShamsiDate() }
    val nowCal = remember { Calendar.getInstance() }

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var textContent by remember { mutableStateOf(initialNote?.textContent ?: "") }
    var strokes by remember { mutableStateOf(initialNote?.strokes ?: emptyList()) }
    var mode by remember { mutableStateOf(initialNote?.mode ?: initialMode) }
    var colorId by remember { mutableStateOf(initialNote?.colorId ?: StickyNoteColor.SUNSHINE.id) }
    var isPinnedTop by remember { mutableStateOf(initialNote?.isPinnedTop ?: false) }

    // Alarm state
    var alarmEnabled by remember { mutableStateOf(initialNote?.alarmEnabled ?: false) }
    var alarmShamsiDate by remember {
        mutableStateOf(
            initialNote?.alarmShamsiDate?.takeIf { it.isNotBlank() } ?: todayShamsi.format()
        )
    }
    var alarmHour by remember {
        mutableIntStateOf(initialNote?.alarmHour ?: ((nowCal.get(Calendar.HOUR_OF_DAY) + 1) % 24))
    }
    var alarmMinute by remember {
        mutableIntStateOf(initialNote?.alarmMinute ?: nowCal.get(Calendar.MINUTE))
    }
    var showDatePicker by remember { mutableStateOf(false) }

    // Notification pin & timed pop-up state
    var pinInNotification by remember { mutableStateOf(initialNote?.pinInNotification ?: false) }
    var popupReminderEnabled by remember { mutableStateOf(initialNote?.popupReminderEnabled ?: false) }
    var popupIntervalMinutes by remember { mutableIntStateOf(initialNote?.popupIntervalMinutes ?: 30) }
    var customIntervalText by remember { mutableStateOf("") }
    var playDingSound by remember { mutableStateOf(initialNote?.playDingSound ?: true) }

    val selectedColor = StickyNoteColor.fromId(colorId)
    val inkDark = Color(0xFF1E293B)
    val inkMuted = Color(0xFF475569)
    val accent = Color(selectedColor.accentHex)

    fun buildDraftNote(): StickyNoteItem {
        val now = System.currentTimeMillis()
        return StickyNoteItem(
            id = initialNote?.id ?: UUID.randomUUID().toString(),
            title = title.trim(),
            textContent = textContent.trim(),
            strokes = strokes,
            mode = when {
                textContent.isNotBlank() && strokes.isNotEmpty() -> StickyNoteMode.MIXED
                strokes.isNotEmpty() && mode == StickyNoteMode.HANDWRITING -> StickyNoteMode.HANDWRITING
                else -> mode
            },
            colorId = colorId,
            isPinnedTop = isPinnedTop,
            alarmEnabled = alarmEnabled,
            alarmShamsiDate = alarmShamsiDate,
            alarmHour = alarmHour.coerceIn(0, 23),
            alarmMinute = alarmMinute.coerceIn(0, 59),
            pinInNotification = pinInNotification,
            popupReminderEnabled = popupReminderEnabled,
            popupIntervalMinutes = popupIntervalMinutes.coerceIn(1, 10080),
            playDingSound = playDingSound,
            createdAtMs = initialNote?.createdAtMs ?: now,
            updatedAtMs = now
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ScaledDialogContent {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(selectedColor.paperHex),
                border = BorderStroke(2.dp, Color(selectedColor.borderHex)),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 12.dp)
                    .testTag("sticky_note_editor_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top Sticky Tape Accent Strip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .background(Color(selectedColor.foldHex))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (initialNote == null) "یادداشت چسبان جدید" else "ویرایش یادداشت چسبان",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = inkDark
                                )
                            }

                            // Pin to top toggle
                            Surface(
                                shape = CircleShape,
                                color = if (isPinnedTop) accent else Color.White.copy(alpha = 0.65f),
                                border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable { isPinnedTop = !isPinnedTop }
                                    .testTag("sticky_note_pin_top_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "سنجاق در بالای لیست",
                                        tint = if (isPinnedTop) Color.White else accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "بستن",
                                    tint = inkDark
                                )
                            }
                        }

                        // Paper Color Swatches Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "رنگ برگه:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = inkMuted
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StickyNoteColor.entries.forEach { palette ->
                                    val isChosen = palette.id == colorId
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(palette.paperHex))
                                            .border(
                                                width = if (isChosen) 2.6.dp else 1.3.dp,
                                                color = if (isChosen) Color(palette.accentHex) else Color(palette.borderHex),
                                                shape = CircleShape
                                            )
                                            .clickable { colorId = palette.id }
                                            .testTag("sticky_color_${palette.id}")
                                    ) {
                                        if (isChosen) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = palette.labelFa,
                                                tint = Color(palette.accentHex),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Input Mode Selector (Typed / Handwriting / Both)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StickyNoteMode.entries.forEach { m ->
                                val isSelected = mode == m
                                val modeIcon = when (m) {
                                    StickyNoteMode.TYPED -> Icons.Default.Keyboard
                                    StickyNoteMode.HANDWRITING -> Icons.Default.Draw
                                    StickyNoteMode.MIXED -> Icons.Default.StickyNote2
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) accent else Color.White.copy(alpha = 0.65f),
                                    border = BorderStroke(
                                        if (isSelected) 1.6.dp else 1.dp,
                                        if (isSelected) accent else Color(selectedColor.borderHex)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { mode = m }
                                        .testTag("sticky_mode_${m.name.lowercase()}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else accent,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = m.labelFa,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.White else inkDark,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Title Field
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("عنوان یادداشت (اختیاری)", fontSize = 12.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White.copy(alpha = 0.78f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.58f),
                                focusedBorderColor = accent,
                                unfocusedBorderColor = Color(selectedColor.borderHex),
                                focusedTextColor = inkDark,
                                unfocusedTextColor = inkDark
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sticky_note_title_input")
                        )

                        // Typed Text Section
                        if (mode == StickyNoteMode.TYPED || mode == StickyNoteMode.MIXED) {
                            OutlinedTextField(
                                value = textContent,
                                onValueChange = { textContent = it },
                                label = { Text("متن یادداشت مهم خود را بنویسید...", fontSize = 12.sp) },
                                minLines = if (mode == StickyNoteMode.MIXED) 3 else 5,
                                maxLines = 8,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.80f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.60f),
                                    focusedBorderColor = accent,
                                    unfocusedBorderColor = Color(selectedColor.borderHex),
                                    focusedTextColor = inkDark,
                                    unfocusedTextColor = inkDark
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("sticky_note_text_input")
                            )
                        }

                        // Handwriting Canvas Section
                        if (mode == StickyNoteMode.HANDWRITING || mode == StickyNoteMode.MIXED) {
                            StickyNoteHandwritingCanvas(
                                strokes = strokes,
                                noteColor = selectedColor,
                                onStrokesChanged = { strokes = it },
                                canvasHeight = if (mode == StickyNoteMode.HANDWRITING) 250.dp else 195.dp
                            )
                        }

                        // === SECTION 1: Scheduled Date & Time Alarm ===
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.78f),
                            border = BorderStroke(
                                1.2.dp,
                                if (alarmEnabled) Color(0xFFDC2626).copy(alpha = 0.6f)
                                else Color(selectedColor.borderHex).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccessAlarm,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "اتصال به آلارم تاریخ و ساعت",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = inkDark
                                            )
                                            Text(
                                                text = "در تاریخ و ساعت تنظیم‌شده زنگ هشدار می‌زند",
                                                fontSize = 10.sp,
                                                color = inkMuted
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = alarmEnabled,
                                        onCheckedChange = { alarmEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFDC2626)
                                        ),
                                        modifier = Modifier.testTag("sticky_note_alarm_switch")
                                    )
                                }

                                AnimatedVisibility(visible = alarmEnabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Shamsi Date Picker Trigger
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFEF2F2),
                                            border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { showDatePicker = true }
                                                .testTag("sticky_note_alarm_date_button")
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.CalendarMonth,
                                                        contentDescription = null,
                                                        tint = Color(0xFFDC2626),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "تاریخ آلارم (شمسی):",
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = inkDark
                                                    )
                                                }
                                                Text(
                                                    text = enDigits(alarmShamsiDate),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = OutfitFontFamily,
                                                    color = Color(0xFFB91C1C)
                                                )
                                            }
                                        }

                                        // Hour & Minute Steppers
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            TimeStepperPill(
                                                label = "ساعت",
                                                value = alarmHour,
                                                onDecrement = { alarmHour = if (alarmHour <= 0) 23 else alarmHour - 1 },
                                                onIncrement = { alarmHour = (alarmHour + 1) % 24 },
                                                accentColor = Color(0xFFDC2626),
                                                modifier = Modifier.weight(1f)
                                            )
                                            TimeStepperPill(
                                                label = "دقیقه",
                                                value = alarmMinute,
                                                onDecrement = { alarmMinute = if (alarmMinute < 5) 55 else alarmMinute - 5 },
                                                onIncrement = { alarmMinute = (alarmMinute + 5) % 60 },
                                                accentColor = Color(0xFFDC2626),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }

                                        // Quick alarm shortcuts + Native Clock App option
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            QuickTimeChip(
                                                text = "۱ ساعت بعد",
                                                onClick = {
                                                    val c = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) }
                                                    alarmShamsiDate = PersianDateHelper.getShamsiDate(c.timeInMillis).format()
                                                    alarmHour = c.get(Calendar.HOUR_OF_DAY)
                                                    alarmMinute = c.get(Calendar.MINUTE)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            QuickTimeChip(
                                                text = "فردا ۰۹:۰۰",
                                                onClick = {
                                                    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                                                    alarmShamsiDate = PersianDateHelper.getShamsiDate(c.timeInMillis).format()
                                                    alarmHour = 9
                                                    alarmMinute = 0
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                            QuickTimeChip(
                                                text = "ساعت گوشی",
                                                onClick = {
                                                    val ok = StickyNoteNotificationHelper.openInSystemClockAlarm(
                                                        context,
                                                        buildDraftNote()
                                                    )
                                                    if (!ok) {
                                                        Toast.makeText(context, "اپلیکیشن ساعت گوشی در دسترس نیست", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // === SECTION 2: Notification Pin + Timed Home-Screen Pop-Up with "Ding!" ===
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.78f),
                            border = BorderStroke(
                                1.2.dp,
                                if (pinInNotification || popupReminderEnabled) Color(0xFF0284C7).copy(alpha = 0.65f)
                                else Color(selectedColor.borderHex).copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Switch A: Pin in Notification Drawer
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.NotificationsActive,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "قرار دادن در نوتیفیکیشن گوشی",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = inkDark
                                            )
                                            Text(
                                                text = "نمایش دائمی یادداشت در نوار اعلان‌ها",
                                                fontSize = 10.sp,
                                                color = inkMuted
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = pinInNotification,
                                        onCheckedChange = { pinInNotification = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF0284C7)
                                        ),
                                        modifier = Modifier.testTag("sticky_note_pin_notif_switch")
                                    )
                                }

                                // Switch B: Timed Pop-Up Reminder on Home Screen
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = Color(0xFF7C3AED),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(
                                                text = "پاپ‌آپ دوره‌ای روی صفحه اصلی",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Black,
                                                color = inkDark
                                            )
                                            Text(
                                                text = "نمایش خودکار بنر یادآوری در بازه زمانی دلخواه",
                                                fontSize = 10.sp,
                                                color = inkMuted
                                            )
                                        }
                                    }
                                    Switch(
                                        checked = popupReminderEnabled,
                                        onCheckedChange = { popupReminderEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF7C3AED)
                                        ),
                                        modifier = Modifier.testTag("sticky_note_popup_switch")
                                    )
                                }

                                AnimatedVisibility(visible = popupReminderEnabled) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "بازه تکرار پاپ‌آپ (${enDigits(StickyNoteStorage.formatPopupIntervalLabel(popupIntervalMinutes))}):",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF5B21B6)
                                        )

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            POPUP_INTERVAL_PRESETS.forEach { (mins, label) ->
                                                val chosen = popupIntervalMinutes == mins
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (chosen) Color(0xFF7C3AED) else Color(0xFFF5F3FF),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (chosen) Color(0xFF6D28D9) else Color(0xFFC4B5FD)
                                                    ),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            popupIntervalMinutes = mins
                                                            customIntervalText = ""
                                                        }
                                                ) {
                                                    Text(
                                                        text = label,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (chosen) Color.White else Color(0xFF5B21B6),
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Optional custom minutes input
                                        OutlinedTextField(
                                            value = customIntervalText,
                                            onValueChange = { raw ->
                                                val digits = PersianDateHelper.toEnglishDigits(raw).filter { it.isDigit() }.take(4)
                                                customIntervalText = digits
                                                val parsed = digits.toIntOrNull()
                                                if (parsed != null && parsed >= 1) {
                                                    popupIntervalMinutes = parsed.coerceIn(1, 10080)
                                                }
                                            },
                                            label = { Text("یا تایم دلخواه به دقیقه (مثلاً 45)", fontSize = 11.sp) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Ding Sound Toggle
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color(0xFF059669),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "پخش صوت کوتاه «دینگ» هنگام هر پاپ‌آپ",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = inkDark
                                                )
                                            }
                                            Switch(
                                                checked = playDingSound,
                                                onCheckedChange = { playDingSound = it },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFF059669)
                                                ),
                                                modifier = Modifier.testTag("sticky_note_ding_switch")
                                            )
                                        }

                                        // Instant Test Pop-Up + Ding Sound Button
                                        OutlinedButton(
                                            onClick = {
                                                StickyNoteNotificationHelper.showHeadsUpPopupNotification(
                                                    context,
                                                    buildDraftNote()
                                                )
                                                Toast.makeText(
                                                    context,
                                                    "پاپ‌آپ آزمایشی با صدای «دینگ» ارسال شد",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            },
                                            border = BorderStroke(1.2.dp, Color(0xFF7C3AED)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 44.dp)
                                                .testTag("sticky_note_test_popup_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = null,
                                                tint = Color(0xFF7C3AED),
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "تست فوری پاپ‌آپ و صدای «دینگ»",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFF6D28D9)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Save & Cancel Buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                border = BorderStroke(1.2.dp, accent.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(
                                    text = "انصراف",
                                    fontWeight = FontWeight.Bold,
                                    color = inkDark
                                )
                            }

                            Button(
                                onClick = {
                                    if (title.isBlank() && textContent.isBlank() && strokes.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "لطفاً عنوان، متن تایپی یا یادداشت دست‌نویس وارد کنید",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@Button
                                    }
                                    if (alarmEnabled) {
                                        val triggerMs = StickyNoteNotificationHelper.computeAlarmEpochMs(
                                            alarmShamsiDate,
                                            alarmHour,
                                            alarmMinute
                                        )
                                        if (triggerMs == null || triggerMs <= System.currentTimeMillis()) {
                                            Toast.makeText(
                                                context,
                                                "زمان آلارم باید در آینده باشد؛ تاریخ یا ساعت را جلوتر تنظیم کنید",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@Button
                                        }
                                    }
                                    onSave(buildDraftNote())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .heightIn(min = 48.dp)
                                    .testTag("sticky_note_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ذخیره یادداشت",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val parts = PersianDateHelper.toEnglishDigits(alarmShamsiDate).split("/")
        val initY = parts.getOrNull(0)?.toIntOrNull() ?: todayShamsi.year
        val initM = parts.getOrNull(1)?.toIntOrNull() ?: todayShamsi.month
        val initD = parts.getOrNull(2)?.toIntOrNull() ?: todayShamsi.day
        PersianDatePickerDialog(
            initialYear = initY,
            initialMonth = initM,
            initialDay = initD,
            onDateSelected = { _, _, _, formatted ->
                alarmShamsiDate = formatted
                showDatePicker = false
            },
            onDismissRequest = { showDatePicker = false }
        )
    }
}

@Composable
private fun TimeStepperPill(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF5F5),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onDecrement,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "کاهش $label",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = label,
                    fontSize = 9.5.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = enDigits(value.toString().padStart(2, '0')),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = OutfitFontFamily,
                    color = Color(0xFF1E293B)
                )
            }
            IconButton(
                onClick = onIncrement,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "افزایش $label",
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickTimeChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFEE2E2),
        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp)
        ) {
            Text(
                text = text,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB91C1C),
                maxLines = 1
            )
        }
    }
}
