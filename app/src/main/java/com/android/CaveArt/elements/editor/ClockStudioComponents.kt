package com.android.CaveArt

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.clockkit.*
import kotlin.math.abs

fun clockIconToImageVector(icon: ClockIcon): ImageVector = when (icon) {
    ClockIcon.STYLE -> Icons.Outlined.Style
    ClockIcon.SIZE -> Icons.Outlined.AspectRatio
    ClockIcon.THICKNESS -> Icons.Outlined.LineWeight
    ClockIcon.ROUNDNESS -> Icons.Outlined.RoundedCorner
    ClockIcon.SPACING -> Icons.Outlined.SwapHoriz
    ClockIcon.OPACITY -> Icons.Outlined.Opacity
    ClockIcon.GRAVITY -> Icons.Outlined.Explore
    ClockIcon.FONT -> Icons.Outlined.TextFields
    ClockIcon.COLOR -> Icons.Outlined.Palette
    ClockIcon.LAYOUT -> Icons.Outlined.DashboardCustomize
    ClockIcon.OPTIONS -> Icons.Outlined.Tune
    ClockIcon.DEPTH -> Icons.Outlined.Layers
    ClockIcon.DATE -> Icons.Outlined.CalendarToday
    ClockIcon.DIAL -> Icons.Outlined.Schedule
    ClockIcon.EFFECTS -> Icons.Outlined.BlurOn
    ClockIcon.TUNE -> Icons.Outlined.Straighten
}

private enum class ToolbarIconKind {
    STYLE,
    OPTIONS,
    FONT,
    COLOR,
    THICKNESS,
    ROUNDNESS,
    WIDTH,
    OUTLINE,
    OVERLAP,
    SURFACE,
    LAYOUT,
    ALIGN,
    DATE,
    SLANT,
    VIBRANCE,
    OPACITY,
    HEATMAP,
    DEPTH,
    ZOOM,
    CUBE,
    CLOCK,
    SPARK,
    GRID,
    SCALE,
    TOGGLE
}

private fun shortActionLabel(label: String): String = when (label) {
    "Typography" -> "Font"
    "Color Palette" -> "Color"
    "Layout & Fit" -> "Options"
    "Gravity Slant" -> "Slant"
    "Row Overlap" -> "Overlap"
    "Condensed Width" -> "Width"
    "Glass Outline" -> "Outline"
    "Minute Surface" -> "Surface"
    "Composition" -> "Layout"
    "Date Tag" -> "Date"
    "Background Vibrance" -> "Vibrance"
    "Background Opacity" -> "Opacity"
    "Subject Opacity" -> "Opacity"
    "Heatmap Opacity" -> "Heatmap"
    "Occlusion Threshold" -> "Depth"
    "Depth Occlusion" -> "Depth"
    "Wallpaper Zoom" -> "Zoom"
    else -> label
}

private fun preferredToolbarIconKind(action: LayerAction): ToolbarIconKind {
    val label = action.label
    val id = action.id.lowercase()
    return when {
        label == "Thickness" || id.endsWith("_stroke_width") -> ToolbarIconKind.THICKNESS
        label == "Roundness" || id.endsWith("_roundness") -> ToolbarIconKind.ROUNDNESS
        label == "Glass Outline" || id.endsWith("_stroke") -> ToolbarIconKind.OUTLINE
        label == "Condensed Width" || id.endsWith("_tallness") -> ToolbarIconKind.WIDTH
        label == "Row Overlap" || id.endsWith("_overlap") || id.endsWith("_row_gap") -> ToolbarIconKind.OVERLAP
        label == "Minute Surface" || id.endsWith("_minute_style") -> ToolbarIconKind.SURFACE
        label == "Composition" || id.endsWith("_layout_mode") -> ToolbarIconKind.LAYOUT
        label == "Alignment" || id.endsWith("_align") -> ToolbarIconKind.ALIGN
        label == "Date Tag" || id.endsWith("_date_mode") -> ToolbarIconKind.DATE
        label == "Typography" || action.toolType == ContextualToolType.FONT -> ToolbarIconKind.FONT
        label == "Color Palette" || action.toolType == ContextualToolType.COLOR -> ToolbarIconKind.COLOR
        label == "Layout & Fit" || label == "Options" || action.toolType == ContextualToolType.QUICK_CHIPS -> ToolbarIconKind.OPTIONS
        label == "Gravity Slant" || id == "clock_gravity_intensity" -> ToolbarIconKind.SLANT
        label == "Background Vibrance" || id == "bg_vibrance" -> ToolbarIconKind.VIBRANCE
        label == "Background Opacity" || id == "bg_opacity" || label == "Subject Opacity" || id == "subject_opacity" -> ToolbarIconKind.OPACITY
        label == "Heatmap Opacity" || id == "depth_opacity" -> ToolbarIconKind.HEATMAP
        label == "Occlusion Threshold" || label == "Depth Occlusion" -> ToolbarIconKind.DEPTH
        label == "Wallpaper Zoom" || id == "all_zoom" -> ToolbarIconKind.ZOOM
        action.clockControl != null -> when (action.clockControl.icon) {
            ClockIcon.STYLE -> ToolbarIconKind.STYLE
            ClockIcon.SIZE -> ToolbarIconKind.ZOOM
            ClockIcon.THICKNESS -> ToolbarIconKind.THICKNESS
            ClockIcon.ROUNDNESS -> ToolbarIconKind.ROUNDNESS
            ClockIcon.SPACING -> ToolbarIconKind.WIDTH
            ClockIcon.OPACITY -> ToolbarIconKind.OPACITY
            ClockIcon.GRAVITY -> ToolbarIconKind.SLANT
            ClockIcon.FONT -> ToolbarIconKind.FONT
            ClockIcon.COLOR -> ToolbarIconKind.COLOR
            ClockIcon.LAYOUT -> ToolbarIconKind.LAYOUT
            ClockIcon.OPTIONS -> ToolbarIconKind.OPTIONS
            ClockIcon.DEPTH -> ToolbarIconKind.DEPTH
            ClockIcon.DATE -> ToolbarIconKind.DATE
            ClockIcon.DIAL -> ToolbarIconKind.CLOCK
            ClockIcon.EFFECTS -> ToolbarIconKind.SURFACE
            ClockIcon.TUNE -> ToolbarIconKind.SCALE
        }
        else -> ToolbarIconKind.SPARK
    }
}

private val fallbackIconKinds = listOf(
    ToolbarIconKind.CUBE,
    ToolbarIconKind.SPARK,
    ToolbarIconKind.GRID,
    ToolbarIconKind.SCALE,
    ToolbarIconKind.TOGGLE,
    ToolbarIconKind.CLOCK,
    ToolbarIconKind.SURFACE,
    ToolbarIconKind.DEPTH,
    ToolbarIconKind.SLANT,
    ToolbarIconKind.ZOOM,
    ToolbarIconKind.VIBRANCE,
    ToolbarIconKind.OUTLINE
)

private fun buildUniqueToolbarIcons(
    actions: List<LayerAction>,
    reserveStyle: Boolean
): List<ToolbarIconKind> {
    val used = if (reserveStyle) mutableSetOf(ToolbarIconKind.STYLE) else mutableSetOf()
    return actions.map { action ->
        val candidate = preferredToolbarIconKind(action)
        if (used.add(candidate)) {
            candidate
        } else {
            fallbackIconKinds.firstOrNull { used.add(it) } ?: candidate
        }
    }
}

@Composable
private fun UnifiedLineIcon(
    kind: ToolbarIconKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val u = size.minDimension / 24f
        val sw = 1.85.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (kind) {
            ToolbarIconKind.STYLE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.5f * u, 5.2f * u),
                    size = Size(11.2f * u, 14.2f * u),
                    cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
                    style = stroke
                )
                val backCard = Path().apply {
                    moveTo(15.7f * u, 7.2f * u)
                    lineTo(17.2f * u, 7.2f * u)
                    cubicTo(18.8f * u, 7.2f * u, 19.8f * u, 8.2f * u, 19.8f * u, 9.8f * u)
                    lineTo(19.8f * u, 16.4f * u)
                    cubicTo(19.8f * u, 18.0f * u, 18.8f * u, 19.0f * u, 17.2f * u, 19.0f * u)
                    lineTo(15.7f * u, 19.0f * u)
                }
                drawPath(backCard, tint, style = stroke)
            }

            ToolbarIconKind.OPTIONS -> {
                
                drawLine(tint, Offset(3.5f * u, 8.2f * u), Offset(6.4f * u, 8.2f * u), sw, StrokeCap.Round)
                drawCircle(tint, radius = 2.4f * u, center = Offset(8.8f * u, 8.2f * u), style = stroke)
                drawLine(tint, Offset(11.2f * u, 8.2f * u), Offset(20.5f * u, 8.2f * u), sw, StrokeCap.Round)

                drawLine(tint, Offset(3.5f * u, 15.8f * u), Offset(12.8f * u, 15.8f * u), sw, StrokeCap.Round)
                drawCircle(tint, radius = 2.4f * u, center = Offset(15.2f * u, 15.8f * u), style = stroke)
                drawLine(tint, Offset(17.6f * u, 15.8f * u), Offset(20.5f * u, 15.8f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.FONT -> {
                
                val aPath = Path().apply {
                    moveTo(3.8f * u, 18.2f * u)
                    lineTo(8.1f * u, 5.8f * u)
                    lineTo(12.4f * u, 18.2f * u)
                }
                drawPath(aPath, tint, style = stroke)
                drawLine(tint, Offset(5.2f * u, 14.3f * u), Offset(11.0f * u, 14.3f * u), sw, StrokeCap.Round)
                drawCircle(tint, radius = 3.3f * u, center = Offset(17.2f * u, 14.9f * u), style = stroke)
            }

            ToolbarIconKind.COLOR -> {
                
                drawCircle(tint, radius = 8.6f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawCircle(tint, radius = 1.35f * u, center = Offset(12f * u, 8.2f * u))
                drawCircle(tint, radius = 1.35f * u, center = Offset(8.6f * u, 14.2f * u))
                drawCircle(tint, radius = 1.35f * u, center = Offset(15.4f * u, 14.2f * u))
            }

            ToolbarIconKind.THICKNESS -> {
                drawLine(tint, Offset(4.5f * u, 6.5f * u), Offset(19.5f * u, 6.5f * u), sw * 0.75f, StrokeCap.Round)
                drawLine(tint, Offset(4.5f * u, 12f * u), Offset(19.5f * u, 12f * u), sw * 1.15f, StrokeCap.Round)
                drawLine(tint, Offset(4.5f * u, 17.5f * u), Offset(19.5f * u, 17.5f * u), sw * 1.6f, StrokeCap.Round)
            }

            ToolbarIconKind.ROUNDNESS -> {
                val corner = Path().apply {
                    moveTo(5.5f * u, 18.5f * u)
                    lineTo(5.5f * u, 12f * u)
                    cubicTo(5.5f * u, 7.5f * u, 7.5f * u, 5.5f * u, 12f * u, 5.5f * u)
                    lineTo(18.5f * u, 5.5f * u)
                }
                drawPath(corner, tint, style = stroke)
                drawCircle(tint, radius = 1.5f * u, center = Offset(13.5f * u, 13.5f * u))
            }

            ToolbarIconKind.WIDTH -> {
                drawLine(tint, Offset(4.5f * u, 5.5f * u), Offset(4.5f * u, 18.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(19.5f * u, 5.5f * u), Offset(19.5f * u, 18.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(8f * u, 12f * u), Offset(16f * u, 12f * u), sw, StrokeCap.Round)
                val lArrow = Path().apply {
                    moveTo(10.2f * u, 9.6f * u); lineTo(7.8f * u, 12f * u); lineTo(10.2f * u, 14.4f * u)
                }
                val rArrow = Path().apply {
                    moveTo(13.8f * u, 9.6f * u); lineTo(16.2f * u, 12f * u); lineTo(13.8f * u, 14.4f * u)
                }
                drawPath(lArrow, tint, style = stroke)
                drawPath(rArrow, tint, style = stroke)
            }

            ToolbarIconKind.OUTLINE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4f * u, 4f * u),
                    size = Size(16f * u, 16f * u),
                    cornerRadius = CornerRadius(4f * u, 4f * u),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(8f * u, 8f * u),
                    size = Size(8f * u, 8f * u),
                    cornerRadius = CornerRadius(2f * u, 2f * u),
                    style = stroke
                )
            }

            ToolbarIconKind.OVERLAP -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.5f * u, 4.5f * u),
                    size = Size(10f * u, 9.5f * u),
                    cornerRadius = CornerRadius(2.5f * u, 2.5f * u),
                    style = stroke
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(9.5f * u, 10f * u),
                    size = Size(10f * u, 9.5f * u),
                    cornerRadius = CornerRadius(2.5f * u, 2.5f * u),
                    style = stroke
                )
            }

            ToolbarIconKind.SURFACE -> {
                drawCircle(tint, radius = 8.4f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawLine(tint, Offset(12f * u, 5.2f * u), Offset(18.2f * u, 11.4f * u), sw * 0.85f, StrokeCap.Round)
                drawLine(tint, Offset(9.8f * u, 9.2f * u), Offset(17.4f * u, 16.8f * u), sw * 0.85f, StrokeCap.Round)
                drawLine(tint, Offset(8.5f * u, 14.2f * u), Offset(13.8f * u, 19.5f * u), sw * 0.85f, StrokeCap.Round)
            }

            ToolbarIconKind.LAYOUT -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.2f * u, 4.2f * u),
                    size = Size(15.6f * u, 15.6f * u),
                    cornerRadius = CornerRadius(3.5f * u, 3.5f * u),
                    style = stroke
                )
                drawLine(tint, Offset(11.5f * u, 4.5f * u), Offset(11.5f * u, 19.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(11.5f * u, 12f * u), Offset(19.5f * u, 12f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.ALIGN -> {
                drawLine(tint, Offset(4.5f * u, 7f * u), Offset(14.5f * u, 7f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(7f * u, 12f * u), Offset(17f * u, 12f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(9.5f * u, 17f * u), Offset(19.5f * u, 17f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.DATE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.5f * u, 5.8f * u),
                    size = Size(15f * u, 14f * u),
                    cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
                    style = stroke
                )
                drawLine(tint, Offset(8.5f * u, 3.8f * u), Offset(8.5f * u, 6.8f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(15.5f * u, 3.8f * u), Offset(15.5f * u, 6.8f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(4.8f * u, 10.6f * u), Offset(19.2f * u, 10.6f * u), sw, StrokeCap.Round)
                drawCircle(tint, radius = 1.4f * u, center = Offset(12f * u, 15.2f * u))
            }

            ToolbarIconKind.SLANT -> {
                val plane = Path().apply {
                    moveTo(6.8f * u, 5.5f * u)
                    lineTo(17.2f * u, 5.5f * u)
                    lineTo(20f * u, 18.5f * u)
                    lineTo(4f * u, 18.5f * u)
                    close()
                }
                drawPath(plane, tint, style = stroke)
                drawLine(tint, Offset(12f * u, 8.2f * u), Offset(12f * u, 15.8f * u), sw * 0.9f, StrokeCap.Round)
            }

            ToolbarIconKind.VIBRANCE -> {
                drawCircle(tint, radius = 4.6f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawLine(tint, Offset(12f * u, 3.5f * u), Offset(12f * u, 5.2f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 18.8f * u), Offset(12f * u, 20.5f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(3.5f * u, 12f * u), Offset(5.2f * u, 12f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(18.8f * u, 12f * u), Offset(20.5f * u, 12f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.OPACITY -> {
                val drop = Path().apply {
                    moveTo(12f * u, 3.8f * u)
                    cubicTo(16.5f * u, 9.2f * u, 18.5f * u, 12.2f * u, 18.5f * u, 14.5f * u)
                    cubicTo(18.5f * u, 18.2f * u, 15.6f * u, 20.4f * u, 12f * u, 20.4f * u)
                    cubicTo(8.4f * u, 20.4f * u, 5.5f * u, 18.2f * u, 5.5f * u, 14.5f * u)
                    cubicTo(5.5f * u, 12.2f * u, 7.5f * u, 9.2f * u, 12f * u, 3.8f * u)
                    close()
                }
                drawPath(drop, tint, style = stroke)
                drawLine(tint, Offset(7.2f * u, 15.2f * u), Offset(16.8f * u, 15.2f * u), sw * 0.9f, StrokeCap.Round)
            }

            ToolbarIconKind.HEATMAP -> {
                val wave1 = Path().apply {
                    moveTo(4f * u, 8.5f * u)
                    cubicTo(8f * u, 5f * u, 16f * u, 12f * u, 20f * u, 8.5f * u)
                }
                val wave2 = Path().apply {
                    moveTo(4f * u, 13f * u)
                    cubicTo(8f * u, 9.5f * u, 16f * u, 16.5f * u, 20f * u, 13f * u)
                }
                val wave3 = Path().apply {
                    moveTo(4f * u, 17.5f * u)
                    cubicTo(8f * u, 14f * u, 16f * u, 21f * u, 20f * u, 17.5f * u)
                }
                drawPath(wave1, tint, style = stroke)
                drawPath(wave2, tint, style = stroke)
                drawPath(wave3, tint, style = stroke)
            }

            ToolbarIconKind.DEPTH -> {
                val topLayer = Path().apply {
                    moveTo(12f * u, 4.5f * u)
                    lineTo(19.5f * u, 9f * u)
                    lineTo(12f * u, 13.5f * u)
                    lineTo(4.5f * u, 9f * u)
                    close()
                }
                val bottomLayer = Path().apply {
                    moveTo(4.5f * u, 14.2f * u)
                    lineTo(12f * u, 18.7f * u)
                    lineTo(19.5f * u, 14.2f * u)
                }
                drawPath(topLayer, tint, style = stroke)
                drawPath(bottomLayer, tint, style = stroke)
            }

            ToolbarIconKind.ZOOM -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.5f * u, 4.5f * u),
                    size = Size(15f * u, 15f * u),
                    cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
                    style = stroke
                )
                drawLine(tint, Offset(8.5f * u, 15.5f * u), Offset(15.5f * u, 8.5f * u), sw, StrokeCap.Round)
                val arrow = Path().apply {
                    moveTo(11.5f * u, 8.2f * u); lineTo(15.8f * u, 8.2f * u); lineTo(15.8f * u, 12.5f * u)
                }
                drawPath(arrow, tint, style = stroke)
            }

            ToolbarIconKind.CUBE -> {
                val outer = Path().apply {
                    moveTo(12f * u, 3.8f * u)
                    lineTo(19.2f * u, 7.8f * u)
                    lineTo(19.2f * u, 16.2f * u)
                    lineTo(12f * u, 20.2f * u)
                    lineTo(4.8f * u, 16.2f * u)
                    lineTo(4.8f * u, 7.8f * u)
                    close()
                }
                drawPath(outer, tint, style = stroke)
                drawLine(tint, Offset(4.8f * u, 7.8f * u), Offset(12f * u, 11.8f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(19.2f * u, 7.8f * u), Offset(12f * u, 11.8f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 11.8f * u), Offset(12f * u, 20.2f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.CLOCK -> {
                drawCircle(tint, radius = 8.4f * u, center = Offset(12f * u, 12f * u), style = stroke)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(12f * u, 7.6f * u), sw, StrokeCap.Round)
                drawLine(tint, Offset(12f * u, 12f * u), Offset(15.4f * u, 13.8f * u), sw, StrokeCap.Round)
            }

            ToolbarIconKind.SPARK -> {
                val star = Path().apply {
                    moveTo(12f * u, 3.8f * u)
                    quadraticTo(12.6f * u, 11.4f * u, 20.2f * u, 12f * u)
                    quadraticTo(12.6f * u, 12.6f * u, 12f * u, 20.2f * u)
                    quadraticTo(11.4f * u, 12.6f * u, 3.8f * u, 12f * u)
                    quadraticTo(11.4f * u, 11.4f * u, 12f * u, 3.8f * u)
                    close()
                }
                drawPath(star, tint, style = stroke)
            }

            ToolbarIconKind.GRID -> {
                for (r in 0..2) {
                    for (c in 0..2) {
                        drawCircle(
                            color = tint,
                            radius = 1.45f * u,
                            center = Offset((6.5f + c * 5.5f) * u, (6.5f + r * 5.5f) * u)
                        )
                    }
                }
            }

            ToolbarIconKind.SCALE -> {
                val dashed = Stroke(
                    width = sw,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * u, 3f * u), 0f)
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(4.5f * u, 4.5f * u),
                    size = Size(15f * u, 15f * u),
                    cornerRadius = CornerRadius(3.2f * u, 3.2f * u),
                    style = dashed
                )
            }

            ToolbarIconKind.TOGGLE -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(3.5f * u, 7.5f * u),
                    size = Size(17f * u, 9f * u),
                    cornerRadius = CornerRadius(4.5f * u, 4.5f * u),
                    style = stroke
                )
                drawCircle(tint, radius = 2.4f * u, center = Offset(15.8f * u, 12f * u))
            }
        }
    }
}

@Composable
fun HorizontalBottomToolbarStrip(
    actions: List<LayerAction>,
    selectedIndex: Int,
    isStyleSelected: Boolean,
    showStyleItem: Boolean = true,
    onStyleSelected: () -> Unit,
    onActionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uniqueKinds = remember(actions, showStyleItem) {
        buildUniqueToolbarIcons(actions = actions, reserveStyle = showStyleItem)
    }
    val totalCount = actions.size + if (showStyleItem) 1 else 0

    if (totalCount <= 5) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStyleItem) {
                BottomToolbarItem(
                    iconKind = ToolbarIconKind.STYLE,
                    label = "Style",
                    isSelected = isStyleSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onStyleSelected()
                    }
                )
            }
            actions.forEachIndexed { index, action ->
                val isSelected = !isStyleSelected && index == selectedIndex
                BottomToolbarItem(
                    iconKind = uniqueKinds[index],
                    label = shortActionLabel(action.label),
                    isSelected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onActionSelected(index)
                    }
                )
            }
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStyleItem) {
                item(key = "tb_style") {
                    BottomToolbarItem(
                        iconKind = ToolbarIconKind.STYLE,
                        label = "Style",
                        isSelected = isStyleSelected,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onStyleSelected()
                        }
                    )
                }
            }
            itemsIndexed(actions, key = { _, a -> a.id }) { index, action ->
                val isSelected = !isStyleSelected && index == selectedIndex
                BottomToolbarItem(
                    iconKind = uniqueKinds[index],
                    label = shortActionLabel(action.label),
                    isSelected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onActionSelected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomToolbarItem(
    iconKind: ToolbarIconKind,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
        label = "bottomStripScale"
    )
    val contentTint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        label = "bottomStripTint"
    )
    val pillBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        else Color.Transparent,
        label = "bottomStripPillBg"
    )

    Column(
        modifier = Modifier
            .widthIn(min = 62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(pillBgColor),
            contentAlignment = Alignment.Center
        ) {
            UnifiedLineIcon(
                kind = iconKind,
                tint = contentTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentTint
        )
    }
}

@Composable
fun VerticalLayerActionSwitcher(
    actions: List<LayerAction>,
    selectedIndex: Int,
    onActionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .wrapContentSize()
            .pointerInput(actions.size, selectedIndex) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val thresholdPx = 32f
                        if (abs(dragAccumulator) > thresholdPx) {
                            val step = if (dragAccumulator > 0) 1 else -1
                            val newIdx = (selectedIndex + step).coerceIn(0, actions.size - 1)
                            if (newIdx != selectedIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onActionSelected(newIdx)
                            }
                            dragAccumulator = 0f
                        }
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        actions.forEachIndexed { index, action ->
            val isSelected = index == selectedIndex
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.14f else 0.94f,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
                label = "actionIconScale"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                label = "actionIconTint"
            )
            val dotProgress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(stiffness = 520f, dampingRatio = 0.7f),
                label = "actionDotScale"
            )

            Column(
                modifier = Modifier
                    .size(width = 36.dp, height = 38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onActionSelected(index)
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.5.dp)
                        .graphicsLayer {
                            scaleX = dotProgress
                            scaleY = dotProgress
                            alpha = dotProgress
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun CompactChoiceStrip(
    options: List<ClockControl.ChoiceOption>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    val view = LocalView.current
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(options, key = { it.key }) { opt ->
            FilterChip(
                selected = opt.key == selectedKey,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSelected(opt.key)
                },
                leadingIcon = opt.icon?.let { ic ->
                    {
                        Icon(
                            imageVector = clockIconToImageVector(ic),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                label = { Text(opt.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
    }
}

@Composable
fun CompactQuickChipsStrip(
    items: List<QuickChipItem>,
    styleId: String,
    config: ClockKitConfig,
    onConfigChange: (ClockKitConfig) -> Unit,
    onCustomAction: (String) -> Unit
) {
    val view = LocalView.current
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items.size) { idx ->
            when (val chip = items[idx]) {
                is QuickChipItem.ToggleChip -> {
                    val isChecked = when (chip.globalBinding) {
                        "depth" -> config.isDepthEnabled
                        "gravity" -> config.isGravityEnabled
                        else -> config.getBool(styleId, chip.paramId, chip.defaultValue)
                    }
                    FilterChip(
                        selected = isChecked,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val next = !isChecked
                            val updated = when (chip.globalBinding) {
                                "depth" -> config.copy(isDepthEnabled = next)
                                "gravity" -> config.copy(isGravityEnabled = next)
                                else -> config.withBool(styleId, chip.paramId, next)
                            }
                            onConfigChange(updated)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (chip.globalBinding) {
                                    "depth" -> Icons.Outlined.Layers
                                    "gravity" -> Icons.Outlined.Explore
                                    else -> clockIconToImageVector(chip.icon)
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
                is QuickChipItem.CycleChip -> {
                    val currentKey = config.getString(styleId, chip.paramId, chip.defaultKey)
                    val curIdx = chip.options.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
                    val currentOpt = chip.options[curIdx]
                    FilterChip(
                        selected = curIdx > 0,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val nextOpt = chip.options[(curIdx + 1) % chip.options.size]
                            onConfigChange(config.withString(styleId, chip.paramId, nextOpt.key))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentOpt.key == "vertical") Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(currentOpt.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
                is QuickChipItem.ActionChip -> {
                    FilterChip(
                        selected = false,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onCustomAction(chip.actionId)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSingleToggleBar(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCheckedChange(it)
            }
        )
    }
}

@Composable
fun CompactFontSelectorStrip(
    availableFonts: List<String>,
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    hours: String,
    mins: String
) {
    val context = LocalContext.current
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(availableFonts) { fontFile ->
            val isSelected = selectedFont == fontFile
            val androidTypeface = remember(fontFile) {
                if (fontFile == "default" || fontFile.isEmpty()) {
                    Typeface.create("sans-serif-bold", Typeface.NORMAL)
                } else {
                    try {
                        Typeface.createFromAsset(context.assets, "fonts/$fontFile")
                    } catch (_: Exception) {
                        Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    }
                }
            }
            val composeFont = remember(androidTypeface) { FontFamily(androidTypeface) }

            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFontSelected(fontFile)
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hours:$mins",
                        fontFamily = composeFont,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CompactColorSelectorStrip(
    extractedColors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val view = LocalView.current
    var showCustomPicker by remember { mutableStateOf(false) }

    val eyeDropperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val colorInt = result.data?.getIntExtra("android.intent.extra.COLOR", selectedColor)
                ?: result.data?.getIntExtra(Intent.EXTRA_COLOR, selectedColor)
                ?: selectedColor
            onColorSelected(colorInt)
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val intent = Intent("android.intent.action.OPEN_EYE_DROPPER")
                    try {
                        eyeDropperLauncher.launch(intent)
                    } catch (_: Exception) {
                        showCustomPicker = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Colorize,
                    contentDescription = "Pick Custom Color",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(extractedColors) { colorInt ->
                val isSelected = colorInt == selectedColor
                val composeColor = safeColor(colorInt)
                val isBright = composeColor.luminance() > 0.5f

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onColorSelected(colorInt)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize(if (isSelected) 0.8f else 0.95f)
                            .clip(CircleShape)
                            .background(composeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isBright) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomPicker) {
        ExpressiveCustomColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomPicker = false },
            onColorConfirmed = { chosen ->
                onColorSelected(chosen)
                showCustomPicker = false
            }
        )
    }
}

@Composable
fun CompactClockOptionsStrip(
    isVertical: Boolean,
    onToggleLayout: () -> Unit,
    isDualTone: Boolean,
    onToggleDualTone: () -> Unit,
    isDepthEnabled: Boolean,
    onToggleDepth: () -> Unit,
    isAdaptive: Boolean,
    onToggleAdaptive: () -> Unit,
    isGravity: Boolean,
    onToggleGravity: () -> Unit,
    onAutoFit: () -> Unit
) {
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = isVertical,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleLayout()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isVertical) Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (isVertical) "Vertical" else "Horizontal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isGravity,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onToggleGravity()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Gravity 3D", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }

        item {
            FilterChip(
                selected = isDualTone,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleDualTone()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Contrast,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Dual Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isDepthEnabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onToggleDepth()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Depth", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isAdaptive,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleAdaptive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Transform,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Adaptive", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = false,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onAutoFit()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Auto Fit", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
    }
}

@Composable
fun SlimRulerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val view = LocalView.current
    var lastHapticTickIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = accentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 12.5.sp
                ),
                color = accentColor
            )
        }

        Spacer(Modifier.height(3.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        val newProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)

                        val tickSpacingPx = 5f * view.resources.displayMetrics.density
                        val totalTicks = (widthPx / tickSpacingPx).toInt().coerceAtLeast(1)
                        val currentTick = (newProgress * totalTicks).toInt()

                        if (currentTick != lastHapticTickIndex) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            lastHapticTickIndex = currentTick
                        }
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onValueChange(newValue)
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val indicatorXPx = progress * widthPx

            val normalTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            val majorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tickSpacing = 5.dp.toPx()
                val totalTicks = (size.width / tickSpacing).toInt()
                val centerY = size.height / 2f

                for (i in 0..totalTicks) {
                    val x = i * tickSpacing
                    val isMajor = (i % 5 == 0)
                    val tickHeight = if (isMajor) 11.dp.toPx() else 6.dp.toPx()
                    val tickColor = if (isMajor) majorTickColor else normalTickColor

                    drawLine(
                        color = tickColor,
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 15.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(indicatorXPx - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                val underlineWidth = 24.dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.8f),
                    start = Offset((indicatorXPx - underlineWidth / 2f).coerceAtLeast(0f), size.height - 1.dp.toPx()),
                    end = Offset((indicatorXPx + underlineWidth / 2f).coerceAtMost(size.width), size.height - 1.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
