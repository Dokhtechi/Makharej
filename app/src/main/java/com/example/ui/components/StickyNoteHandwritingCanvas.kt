package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HandwritingStroke
import com.example.data.model.StickyNoteColor
import com.example.data.model.StrokePoint

private val INK_COLOR_OPTIONS = listOf(
    0xFF111827L to "مشکی",
    0xFF1D4ED8L to "آبی",
    0xFFDC2626L to "قرمز",
    0xFF047857L to "سبز",
    0xFF7C3AEDL to "بنفش"
)

private val STROKE_WIDTH_OPTIONS = listOf(
    2.6f to "نازک",
    4.8f to "متوسط",
    8.2f to "ضخیم"
)

/**
 * Interactive freehand handwriting canvas styled as a ruled sticky-note sheet.
 * Captures both continuous drag strokes and single-tap dots (essential for Persian lettering).
 */
@Composable
fun StickyNoteHandwritingCanvas(
    strokes: List<HandwritingStroke>,
    noteColor: StickyNoteColor,
    onStrokesChanged: (List<HandwritingStroke>) -> Unit,
    modifier: Modifier = Modifier,
    canvasHeight: Dp = 230.dp
) {
    var selectedInkHex by remember { mutableLongStateOf(INK_COLOR_OPTIONS[0].first) }
    var selectedWidthDp by remember { mutableFloatStateOf(STROKE_WIDTH_OPTIONS[1].first) }
    var activePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val latestStrokes by rememberUpdatedState(strokes)
    val latestInkHex by rememberUpdatedState(selectedInkHex)
    val latestWidthDp by rememberUpdatedState(selectedWidthDp)
    val latestOnChanged by rememberUpdatedState(onStrokesChanged)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ink & Brush Toolbar
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, Color(noteColor.borderHex).copy(alpha = 0.65f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Ink color circles
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    INK_COLOR_OPTIONS.forEach { (hex, label) ->
                        val isSelected = selectedInkHex == hex
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 28.dp else 24.dp)
                                .clip(CircleShape)
                                .background(Color(hex))
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color(noteColor.accentHex) else Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectedInkHex = hex }
                                .testTag("ink_color_$label")
                        )
                    }
                }

                // Stroke thickness pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    STROKE_WIDTH_OPTIONS.forEach { (w, _) ->
                        val isSelected = selectedWidthDp == w
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(noteColor.accentHex).copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color(noteColor.accentHex) else Color(0xFFCBD5E1),
                                    CircleShape
                                )
                                .clickable { selectedWidthDp = w }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size((w * 1.45f).coerceIn(4f, 13f).dp)
                                    .clip(CircleShape)
                                    .background(Color(selectedInkHex))
                            )
                        }
                    }
                }

                // Undo & Clear controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (strokes.isNotEmpty()) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFD97706).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = strokes.isNotEmpty()) {
                                if (strokes.isNotEmpty()) {
                                    onStrokesChanged(strokes.dropLast(1))
                                }
                            }
                            .testTag("handwriting_undo_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "واگرد خط آخر",
                                tint = if (strokes.isNotEmpty()) Color(0xFFB45309) else Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = if (strokes.isNotEmpty()) Color(0xFFFEE2E2) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(enabled = strokes.isNotEmpty()) {
                                activePoints = emptyList()
                                onStrokesChanged(emptyList())
                            }
                            .testTag("handwriting_clear_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "پاک کردن کل دست‌خط",
                                tint = if (strokes.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF94A3B8),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }

        // Drawing Paper Surface
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(noteColor.paperHex),
            border = BorderStroke(1.6.dp, Color(noteColor.borderHex)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .clip(RoundedCornerShape(16.dp))
                .testTag("sticky_note_handwriting_canvas")
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                val dot = StrokePoint(
                                    x = (tapOffset.x / w).coerceIn(0f, 1f),
                                    y = (tapOffset.y / h).coerceIn(0f, 1f)
                                )
                                val newStroke = HandwritingStroke(
                                    colorHex = latestInkHex,
                                    strokeWidthDp = latestWidthDp,
                                    points = listOf(dot)
                                )
                                latestOnChanged(latestStrokes + newStroke)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                    activePoints = listOf(
                                        StrokePoint(
                                            x = (startOffset.x / w).coerceIn(0f, 1f),
                                            y = (startOffset.y / h).coerceIn(0f, 1f)
                                        )
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                    val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                    val pos = change.position
                                    val pt = StrokePoint(
                                        x = (pos.x / w).coerceIn(0f, 1f),
                                        y = (pos.y / h).coerceIn(0f, 1f)
                                    )
                                    val prev = activePoints.lastOrNull()
                                    if (prev == null ||
                                        kotlin.math.abs(prev.x - pt.x) > 0.002f ||
                                        kotlin.math.abs(prev.y - pt.y) > 0.002f
                                    ) {
                                        activePoints = activePoints + pt
                                    }
                                },
                                onDragEnd = {
                                    if (activePoints.isNotEmpty()) {
                                        val completed = HandwritingStroke(
                                            colorHex = latestInkHex,
                                            strokeWidthDp = latestWidthDp,
                                            points = activePoints
                                        )
                                        latestOnChanged(latestStrokes + completed)
                                    }
                                    activePoints = emptyList()
                                },
                                onDragCancel = {
                                    activePoints = emptyList()
                                }
                            )
                        }
                ) {
                    drawRuledPaperLines(Color(noteColor.borderHex))
                    strokes.forEach { stroke ->
                        drawSmoothStroke(stroke, strokeScale = 1f)
                    }
                    if (activePoints.isNotEmpty()) {
                        drawSmoothStroke(
                            HandwritingStroke(
                                colorHex = selectedInkHex,
                                strokeWidthDp = selectedWidthDp,
                                points = activePoints
                            ),
                            strokeScale = 1f
                        )
                    }
                }

                // Helpful watermark when canvas is empty
                if (strokes.isEmpty() && activePoints.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Draw,
                            contentDescription = null,
                            tint = Color(noteColor.accentHex).copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "اینجا با انگشت یادداشت دست‌نویس بکشید یا بنویسید...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(noteColor.accentHex).copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact read-only preview of handwritten strokes displayed inside each sticky-note card on the board.
 */
@Composable
fun StickyNoteMiniCanvas(
    strokes: List<HandwritingStroke>,
    noteColor: StickyNoteColor,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.35f))
    ) {
        drawRuledPaperLines(Color(noteColor.borderHex), stepDp = 18f)
        strokes.forEach { stroke ->
            drawSmoothStroke(stroke, strokeScale = 0.58f)
        }
    }
}

private fun DrawScope.drawRuledPaperLines(borderColor: Color, stepDp: Float = 28f) {
    val stepPx = stepDp.dp.toPx().coerceAtLeast(16f)
    var y = stepPx
    while (y < size.height - 6f) {
        drawLine(
            color = borderColor.copy(alpha = 0.28f),
            start = Offset(10.dp.toPx(), y),
            end = Offset(size.width - 10.dp.toPx(), y),
            strokeWidth = 1.dp.toPx()
        )
        y += stepPx
    }
}

private fun DrawScope.drawSmoothStroke(stroke: HandwritingStroke, strokeScale: Float) {
    val pts = stroke.points
    if (pts.isEmpty()) return
    val color = Color(stroke.colorHex)
    val widthPx = (stroke.strokeWidthDp * strokeScale).dp.toPx().coerceAtLeast(1.8f)

    if (pts.size == 1) {
        drawCircle(
            color = color,
            radius = widthPx * 0.55f,
            center = Offset(pts[0].x * size.width, pts[0].y * size.height)
        )
        return
    }

    val path = Path().apply {
        val x0 = pts[0].x * size.width
        val y0 = pts[0].y * size.height
        moveTo(x0, y0)
        for (i in 1 until pts.size) {
            val prevX = pts[i - 1].x * size.width
            val prevY = pts[i - 1].y * size.height
            val currX = pts[i].x * size.width
            val currY = pts[i].y * size.height
            quadraticTo(prevX, prevY, (prevX + currX) / 2f, (prevY + currY) / 2f)
        }
        val last = pts.last()
        lineTo(last.x * size.width, last.y * size.height)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = widthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}
