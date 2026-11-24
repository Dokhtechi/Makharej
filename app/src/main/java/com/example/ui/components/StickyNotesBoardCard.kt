package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.StickyNoteItem
import com.example.data.model.StickyNoteMode
import com.example.data.model.StickyNoteStorage
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.ScaledDialogContent
import com.example.ui.theme.appSecondaryTextColor
import com.example.ui.theme.appTextColor
import com.example.ui.theme.enDigits
import com.example.ui.theme.scaledDp
import com.example.utils.StickyNoteNotificationHelper

private enum class StickyNoteFilter(val labelFa: String) {
    ALL("همه"),
    TYPED("تایپی"),
    HANDWRITING("دست‌خط"),
    ALARM("آلارم‌دار"),
    POPUP_NOTIF("پاپ‌آپ و نوتیف")
}

/**
 * Tactile Sticky Notes board embedded in the Tools ("امکانات") tab.
 * Supports typed & handwritten notes, scheduled Shamsi date/time alarms,
 * persistent notification bar pinning, and repeating home-screen heads-up pop-ups with "Ding!" chime.
 */
@Composable
fun StickyNotesBoardCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var notes by remember { mutableStateOf(StickyNoteStorage.loadNotes(context)) }
    var activeFilter by remember { mutableStateOf(StickyNoteFilter.ALL) }
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<StickyNoteItem?>(null) }
    var editorInitialMode by remember { mutableStateOf(StickyNoteMode.TYPED) }
    var notePendingDelete by remember { mutableStateOf<StickyNoteItem?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(context, "مجوز اعلان و پاپ‌آپ یادداشت‌ها فعال شد", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        StickyNoteNotificationHelper.createNotificationChannels(context)
        StickyNoteNotificationHelper.syncAllNotes(context, notes)
    }

    fun persistNotes(updated: List<StickyNoteItem>) {
        val sorted = updated.sortedWith(
            compareByDescending<StickyNoteItem> { it.isPinnedTop }
                .thenByDescending { it.updatedAtMs }
        )
        notes = sorted
        StickyNoteStorage.saveNotes(context, sorted)
    }

    fun saveNote(saved: StickyNoteItem) {
        if (saved.alarmEnabled || saved.pinInNotification || saved.popupReminderEnabled) {
            requestNotificationPermissionIfNeeded()
        }
        val exists = notes.any { it.id == saved.id }
        val nextList = if (exists) {
            notes.map { if (it.id == saved.id) saved else it }
        } else {
            listOf(saved) + notes
        }
        persistNotes(nextList)
        StickyNoteNotificationHelper.applyNoteSchedules(context, saved)

        val statusParts = mutableListOf<String>()
        if (saved.alarmEnabled) statusParts.add("آلارم ${saved.alarmShamsiDate} ساعت ${saved.formattedAlarmTime()}")
        if (saved.pinInNotification) statusParts.add("پین در نوتیفیکیشن")
        if (saved.popupReminderEnabled) {
            val interval = StickyNoteStorage.formatPopupIntervalLabel(saved.popupIntervalMinutes)
            statusParts.add("پاپ‌آپ $interval" + if (saved.playDingSound) " با صدای دینگ" else "")
        }
        val msg = if (statusParts.isEmpty()) {
            "یادداشت چسبان ذخیره شد"
        } else {
            "ذخیره شد (${statusParts.joinToString(" • ")})"
        }
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    fun deleteNote(note: StickyNoteItem) {
        StickyNoteNotificationHelper.clearNoteSchedules(context, note.id)
        persistNotes(notes.filterNot { it.id == note.id })
        Toast.makeText(context, "یادداشت حذف شد", Toast.LENGTH_SHORT).show()
    }

    val filteredNotes = remember(notes, activeFilter) {
        val base = when (activeFilter) {
            StickyNoteFilter.ALL -> notes
            StickyNoteFilter.TYPED -> notes.filter { it.hasTypedText }
            StickyNoteFilter.HANDWRITING -> notes.filter { it.hasHandwriting }
            StickyNoteFilter.ALARM -> notes.filter { it.alarmEnabled }
            StickyNoteFilter.POPUP_NOTIF -> notes.filter { it.pinInNotification || it.popupReminderEnabled }
        }
        base.sortedWith(
            compareByDescending<StickyNoteItem> { it.isPinnedTop }
                .thenByDescending { it.updatedAtMs }
        )
    }

    val activeAlarmsCount = notes.count { it.alarmEnabled }
    val activePopupsCount = notes.count { it.popupReminderEnabled || it.pinInNotification }

    val borderAccent = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val headerBrush = if (isDark) {
        Brush.horizontalGradient(listOf(Color(0xFF451A03), Color(0xFF78350F)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A)))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, borderAccent.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sticky_notes_board_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Warm Sticky-Note Board Header Banner
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBrush)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.StickyNote2,
                                    contentDescription = "یادداشت‌های چسبان",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "تابلوی یادداشت‌های چسبان",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFFFEF3C7) else Color(0xFF78350F)
                            )
                            Text(
                                text = enDigits("${notes.size} یادداشت • $activeAlarmsCount آلارم • $activePopupsCount نوتیف/پاپ‌آپ"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFFDE68A) else Color(0xFF92400E)
                            )
                        }
                    }
                }

                // Dual Quick-Create Action Row (Typed + Handwriting)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            editingNote = null
                            editorInitialMode = StickyNoteMode.TYPED
                            showEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD97706),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("sticky_add_typed_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "یادداشت تایپی",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            editingNote = null
                            editorInitialMode = StickyNoteMode.HANDWRITING
                            showEditor = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF7C3AED),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .testTag("sticky_add_handwriting_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "یادداشت دست‌خط",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Filter Pills Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StickyNoteFilter.entries.forEach { filter ->
                    val selected = activeFilter == filter
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) borderAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(
                            1.dp,
                            if (selected) borderAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { activeFilter = filter }
                    ) {
                        Text(
                            text = filter.labelFa,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                            color = if (selected) Color.White else appTextColor(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Sticky Notes 2-Column Tactile Board (No nested LazyGrid inside ToolsScreen LazyColumn)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredNotes.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, borderAccent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.StickyNote2,
                                contentDescription = null,
                                tint = borderAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = if (notes.isEmpty()) "هنوز یادداشت چسبانی ثبت نکرده‌اید"
                                else "یادداشتی در این فیلتر یافت نشد",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = appTextColor(MaterialTheme.colorScheme.onSurface)
                            )
                            Text(
                                text = "یادداشت تایپی یا دست‌خط بنویسید، آلارم تاریخ‌دار تنظیم کنید یا یادآوری پاپ‌آپ با صدای «دینگ» فعال کنید.",
                                fontSize = 11.sp,
                                color = appSecondaryTextColor(MaterialTheme.colorScheme.onSurfaceVariant)
                            )
                        }
                    }
                } else {
                    filteredNotes.chunked(2).forEach { rowNotes ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowNotes.forEach { note ->
                                StickyNotePaperCard(
                                    note = note,
                                    onClick = {
                                        editingNote = note
                                        editorInitialMode = note.mode
                                        showEditor = true
                                    },
                                    onToggleNotificationPin = {
                                        requestNotificationPermissionIfNeeded()
                                        val toggled = note.copy(
                                            pinInNotification = !note.pinInNotification,
                                            updatedAtMs = System.currentTimeMillis()
                                        )
                                        persistNotes(notes.map { if (it.id == note.id) toggled else it })
                                        StickyNoteNotificationHelper.applyNoteSchedules(context, toggled)
                                        Toast.makeText(
                                            context,
                                            if (toggled.pinInNotification) "یادداشت در نوتیفیکیشن پین شد"
                                            else "یادداشت از نوتیفیکیشن برداشته شد",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onTriggerDingPopup = {
                                        requestNotificationPermissionIfNeeded()
                                        StickyNoteNotificationHelper.showHeadsUpPopupNotification(context, note)
                                        Toast.makeText(
                                            context,
                                            "پاپ‌آپ یادداشت با صدای «دینگ» نمایش داده شد",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onDeleteClick = { notePendingDelete = note },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowNotes.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        StickyNoteEditorDialog(
            initialNote = editingNote,
            initialMode = editorInitialMode,
            onDismiss = {
                showEditor = false
                editingNote = null
            },
            onSave = { saved ->
                saveNote(saved)
                showEditor = false
                editingNote = null
            }
        )
    }

    if (notePendingDelete != null) {
        val target = notePendingDelete!!
        AlertDialog(
            onDismissRequest = { notePendingDelete = null },
            title = {
                ScaledDialogContent {
                    Text(
                        text = "حذف یادداشت چسبان",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            },
            text = {
                ScaledDialogContent {
                    Text(
                        text = "آیا از حذف یادداشت «${target.displayTitle()}» مطمئن هستید؟ آلارم و یادآوری پاپ‌آپ آن نیز لغو خواهد شد.",
                        fontSize = 12.5.sp
                    )
                }
            },
            confirmButton = {
                ScaledDialogContent {
                    Button(
                        onClick = {
                            deleteNote(target)
                            notePendingDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Text("بله، حذف شود")
                    }
                }
            },
            dismissButton = {
                ScaledDialogContent {
                    OutlinedButton(onClick = { notePendingDelete = null }) {
                        Text("انصراف")
                    }
                }
            }
        )
    }
}

@Composable
private fun StickyNotePaperCard(
    note: StickyNoteItem,
    onClick: () -> Unit,
    onToggleNotificationPin: () -> Unit,
    onTriggerDingPopup: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = note.noteColor
    val paperColor = Color(palette.paperHex)
    val foldColor = Color(palette.foldHex)
    val accentColor = Color(palette.accentHex)
    val borderColor = Color(palette.borderHex)
    val inkDark = Color(0xFF1E293B)
    val inkMuted = Color(0xFF334155)

    Surface(
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp),
        color = paperColor,
        border = BorderStroke(1.3.dp, borderColor),
        shadowElevation = 4.dp,
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 4.dp))
            .clickable(onClick = onClick)
            .drawBehind {
                // Realistic folded sticky-note paper corner at bottom-left
                val foldSize = 18.dp.toPx()
                val foldPath = Path().apply {
                    moveTo(0f, size.height - foldSize)
                    lineTo(foldSize, size.height - foldSize)
                    lineTo(foldSize, size.height)
                    close()
                }
                drawPath(foldPath, color = foldColor)
            }
            .testTag("sticky_note_card_${note.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top Adhesive Tape Header Strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(foldColor.copy(alpha = 0.85f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Title + Top Pin Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = note.displayTitle(),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Black,
                        color = inkDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (note.isPinnedTop) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "سنجاق‌شده",
                            tint = accentColor,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp)
                        )
                    }
                }

                // Typed Text Preview
                if (note.hasTypedText) {
                    Text(
                        text = note.textContent,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = inkMuted,
                        maxLines = if (note.hasHandwriting) 2 else 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Handwritten Strokes Mini-Preview
                if (note.hasHandwriting) {
                    StickyNoteMiniCanvas(
                        strokes = note.strokes,
                        noteColor = palette,
                        modifier = Modifier.height(78.dp)
                    )
                }

                // Active Alarm / Notification / Pop-Up Status Badges
                if (note.alarmEnabled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(0.8.dp, Color(0xFFFCA5A5))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessAlarm,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = enDigits("${note.alarmShamsiDate} • ${note.formattedAlarmTime()}"),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OutfitFontFamily,
                                color = Color(0xFFB91C1C),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (note.popupReminderEnabled) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3E8FF),
                        border = BorderStroke(0.8.dp, Color(0xFFD8B4FE))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF7C3AED),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = enDigits(
                                    StickyNoteStorage.formatPopupIntervalLabel(note.popupIntervalMinutes) +
                                        if (note.playDingSound) " • دینگ" else ""
                                ),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6D28D9),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bottom Action Bar (Pin to Notif / Instant Ding Pop-Up / Delete)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Toggle Pin in Notification
                        Surface(
                            shape = CircleShape,
                            color = if (note.pinInNotification) Color(0xFF0284C7) else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(scaledDp(28f))
                                .clip(CircleShape)
                                .clickable(onClick = onToggleNotificationPin)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (note.pinInNotification) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                                    contentDescription = "پین در نوتیفیکیشن",
                                    tint = if (note.pinInNotification) Color.White else Color(0xFF0369A1),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        // Instant Heads-Up Pop-Up + Ding Test
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(scaledDp(28f))
                                .clip(CircleShape)
                                .clickable(onClick = onTriggerDingPopup)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "ارسال پاپ‌آپ با صدای دینگ",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(scaledDp(28f))
                            .clip(CircleShape)
                            .clickable(onClick = onDeleteClick)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "حذف یادداشت",
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
