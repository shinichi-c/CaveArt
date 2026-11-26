package com.android.CaveArt.lockscreenkit

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.abs

enum class ToolbarIconKind {
    STYLE, OPTIONS, FONT, COLOR, THICKNESS, ROUNDNESS, WIDTH, OUTLINE, OVERLAP,
    SURFACE, LAYOUT, ALIGN, DATE, SLANT, VIBRANCE, OPACITY, HEATMAP, DEPTH, ZOOM,
    CUBE, CLOCK, SPARK, GRID, SCALE, TOGGLE
}

object LockscreenToolbarIcons {

    private val fallbackKinds = listOf(
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

    fun shortLabel(label: String): String = when (label) {
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

    fun preferredKind(
        id: String,
        label: String,
        clockIcon: ClockIcon?,
        isFont: Boolean = false,
        isColor: Boolean = false,
        isQuickChips: Boolean = false
    ): ToolbarIconKind {
        val lowId = id.lowercase()
        return when {
            label == "Thickness" || lowId.endsWith("stroke_width") -> ToolbarIconKind.THICKNESS
            label == "Roundness" || lowId.endsWith("roundness") -> ToolbarIconKind.ROUNDNESS
            label == "Glass Outline" || lowId.endsWith("_stroke") -> ToolbarIconKind.OUTLINE
            label == "Condensed Width" || lowId.endsWith("tallness") -> ToolbarIconKind.WIDTH
            label == "Row Overlap" || lowId.endsWith("overlap") || lowId.endsWith("row_gap") -> ToolbarIconKind.OVERLAP
            label == "Minute Surface" || lowId.endsWith("minute_style") -> ToolbarIconKind.SURFACE
            label == "Composition" || lowId.endsWith("layout_mode") -> ToolbarIconKind.LAYOUT
            label == "Alignment" || lowId.endsWith("align") -> ToolbarIconKind.ALIGN
            label == "Date Tag" || lowId.endsWith("date_mode") -> ToolbarIconKind.DATE
            label == "Typography" || isFont -> ToolbarIconKind.FONT
            label == "Color Palette" || isColor -> ToolbarIconKind.COLOR
            label == "Layout & Fit" || label == "Options" || isQuickChips -> ToolbarIconKind.OPTIONS
            label == "Gravity Slant" || lowId == "clock_gravity_intensity" -> ToolbarIconKind.SLANT
            label == "Background Vibrance" || lowId == "bg_vibrance" -> ToolbarIconKind.VIBRANCE
            label == "Background Opacity" || lowId == "bg_opacity" || label == "Subject Opacity" || lowId == "subject_opacity" -> ToolbarIconKind.OPACITY
            label == "Heatmap Opacity" || lowId == "depth_opacity" -> ToolbarIconKind.HEATMAP
            label == "Occlusion Threshold" || label == "Depth Occlusion" -> ToolbarIconKind.DEPTH
            label == "Wallpaper Zoom" || lowId == "all_zoom" -> ToolbarIconKind.ZOOM
            clockIcon != null -> when (clockIcon) {
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

    fun buildUniqueKinds(
        candidates: List<ToolbarIconKind>,
        reserveStyle: Boolean
    ): List<ToolbarIconKind> {
        val used = if (reserveStyle) mutableSetOf(ToolbarIconKind.STYLE) else mutableSetOf()
        return candidates.map { candidate ->
            if (used.add(candidate)) candidate
            else fallbackKinds.firstOrNull { used.add(it) } ?: candidate
        }
    }

    fun draw(
        canvas: Canvas,
        kind: ToolbarIconKind,
        tint: Int,
        boxSizePx: Float,
        strokeWidthPx: Float
    ) {
        val u = boxSizePx / 24f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            setStyle(Paint.Style.STROKE)
            strokeWidth = strokeWidthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tint
            setStyle(Paint.Style.FILL)
        }

        when (kind) {
            ToolbarIconKind.STYLE -> {
                canvas.drawRoundRect(4.5f * u, 5.2f * u, 15.7f * u, 19.4f * u, 3.2f * u, 3.2f * u, p)
                val back = Path().apply {
                    moveTo(15.7f * u, 7.2f * u)
                    lineTo(17.2f * u, 7.2f * u)
                    cubicTo(18.8f * u, 7.2f * u, 19.8f * u, 8.2f * u, 19.8f * u, 9.8f * u)
                    lineTo(19.8f * u, 16.4f * u)
                    cubicTo(19.8f * u, 18.0f * u, 18.8f * u, 19.0f * u, 17.2f * u, 19.0f * u)
                    lineTo(15.7f * u, 19.0f * u)
                }
                canvas.drawPath(back, p)
            }
            ToolbarIconKind.OPTIONS -> {
                canvas.drawLine(3.5f * u, 8.2f * u, 6.4f * u, 8.2f * u, p)
                canvas.drawCircle(8.8f * u, 8.2f * u, 2.4f * u, p)
                canvas.drawLine(11.2f * u, 8.2f * u, 20.5f * u, 8.2f * u, p)
                canvas.drawLine(3.5f * u, 15.8f * u, 12.8f * u, 15.8f * u, p)
                canvas.drawCircle(15.2f * u, 15.8f * u, 2.4f * u, p)
                canvas.drawLine(17.6f * u, 15.8f * u, 20.5f * u, 15.8f * u, p)
            }
            ToolbarIconKind.FONT -> {
                val aPath = Path().apply {
                    moveTo(3.8f * u, 18.2f * u)
                    lineTo(8.1f * u, 5.8f * u)
                    lineTo(12.4f * u, 18.2f * u)
                }
                canvas.drawPath(aPath, p)
                canvas.drawLine(5.2f * u, 14.3f * u, 11.0f * u, 14.3f * u, p)
                canvas.drawCircle(17.2f * u, 14.9f * u, 3.3f * u, p)
            }
            ToolbarIconKind.COLOR -> {
                canvas.drawCircle(12f * u, 12f * u, 8.6f * u, p)
                canvas.drawCircle(12f * u, 8.2f * u, 1.35f * u, fillP)
                canvas.drawCircle(8.6f * u, 14.2f * u, 1.35f * u, fillP)
                canvas.drawCircle(15.4f * u, 14.2f * u, 1.35f * u, fillP)
            }
            ToolbarIconKind.THICKNESS -> {
                p.strokeWidth = strokeWidthPx * 0.75f
                canvas.drawLine(4.5f * u, 6.5f * u, 19.5f * u, 6.5f * u, p)
                p.strokeWidth = strokeWidthPx * 1.15f
                canvas.drawLine(4.5f * u, 12f * u, 19.5f * u, 12f * u, p)
                p.strokeWidth = strokeWidthPx * 1.6f
                canvas.drawLine(4.5f * u, 17.5f * u, 19.5f * u, 17.5f * u, p)
            }
            ToolbarIconKind.ROUNDNESS -> {
                val c = Path().apply {
                    moveTo(5.5f * u, 18.5f * u)
                    lineTo(5.5f * u, 12f * u)
                    cubicTo(5.5f * u, 7.5f * u, 7.5f * u, 5.5f * u, 12f * u, 5.5f * u)
                    lineTo(18.5f * u, 5.5f * u)
                }
                canvas.drawPath(c, p)
                canvas.drawCircle(13.5f * u, 13.5f * u, 1.5f * u, fillP)
            }
            ToolbarIconKind.WIDTH -> {
                canvas.drawLine(4.5f * u, 5.5f * u, 4.5f * u, 18.5f * u, p)
                canvas.drawLine(19.5f * u, 5.5f * u, 19.5f * u, 18.5f * u, p)
                canvas.drawLine(8f * u, 12f * u, 16f * u, 12f * u, p)
                canvas.drawPath(Path().apply { moveTo(10.2f * u, 9.6f * u); lineTo(7.8f * u, 12f * u); lineTo(10.2f * u, 14.4f * u) }, p)
                canvas.drawPath(Path().apply { moveTo(13.8f * u, 9.6f * u); lineTo(16.2f * u, 12f * u); lineTo(13.8f * u, 14.4f * u) }, p)
            }
            ToolbarIconKind.OUTLINE -> {
                canvas.drawRoundRect(4f * u, 4f * u, 20f * u, 20f * u, 4f * u, 4f * u, p)
                canvas.drawRoundRect(8f * u, 8f * u, 16f * u, 16f * u, 2f * u, 2f * u, p)
            }
            ToolbarIconKind.OVERLAP -> {
                canvas.drawRoundRect(4.5f * u, 4.5f * u, 14.5f * u, 14f * u, 2.5f * u, 2.5f * u, p)
                canvas.drawRoundRect(9.5f * u, 10f * u, 19.5f * u, 19.5f * u, 2.5f * u, 2.5f * u, p)
            }
            ToolbarIconKind.SURFACE -> {
                canvas.drawCircle(12f * u, 12f * u, 8.4f * u, p)
                p.strokeWidth = strokeWidthPx * 0.85f
                canvas.drawLine(12f * u, 5.2f * u, 18.2f * u, 11.4f * u, p)
                canvas.drawLine(9.8f * u, 9.2f * u, 17.4f * u, 16.8f * u, p)
                canvas.drawLine(8.5f * u, 14.2f * u, 13.8f * u, 19.5f * u, p)
            }
            ToolbarIconKind.LAYOUT -> {
                canvas.drawRoundRect(4.2f * u, 4.2f * u, 19.8f * u, 19.8f * u, 3.5f * u, 3.5f * u, p)
                canvas.drawLine(11.5f * u, 4.5f * u, 11.5f * u, 19.5f * u, p)
                canvas.drawLine(11.5f * u, 12f * u, 19.5f * u, 12f * u, p)
            }
            ToolbarIconKind.ALIGN -> {
                canvas.drawLine(4.5f * u, 7f * u, 14.5f * u, 7f * u, p)
                canvas.drawLine(7f * u, 12f * u, 17f * u, 12f * u, p)
                canvas.drawLine(9.5f * u, 17f * u, 19.5f * u, 17f * u, p)
            }
            ToolbarIconKind.DATE -> {
                canvas.drawRoundRect(4.5f * u, 5.8f * u, 19.5f * u, 19.8f * u, 3.2f * u, 3.2f * u, p)
                canvas.drawLine(8.5f * u, 3.8f * u, 8.5f * u, 6.8f * u, p)
                canvas.drawLine(15.5f * u, 3.8f * u, 15.5f * u, 6.8f * u, p)
                canvas.drawLine(4.8f * u, 10.6f * u, 19.2f * u, 10.6f * u, p)
                canvas.drawCircle(12f * u, 15.2f * u, 1.4f * u, fillP)
            }
            ToolbarIconKind.SLANT -> {
                val plane = Path().apply {
                    moveTo(6.8f * u, 5.5f * u)
                    lineTo(17.2f * u, 5.5f * u)
                    lineTo(20f * u, 18.5f * u)
                    lineTo(4f * u, 18.5f * u)
                    close()
                }
                canvas.drawPath(plane, p)
                canvas.drawLine(12f * u, 8.2f * u, 12f * u, 15.8f * u, p)
            }
            ToolbarIconKind.VIBRANCE -> {
                canvas.drawCircle(12f * u, 12f * u, 4.6f * u, p)
                canvas.drawLine(12f * u, 3.5f * u, 12f * u, 5.2f * u, p)
                canvas.drawLine(12f * u, 18.8f * u, 12f * u, 20.5f * u, p)
                canvas.drawLine(3.5f * u, 12f * u, 5.2f * u, 12f * u, p)
                canvas.drawLine(18.8f * u, 12f * u, 20.5f * u, 12f * u, p)
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
                canvas.drawPath(drop, p)
                canvas.drawLine(7.2f * u, 15.2f * u, 16.8f * u, 15.2f * u, p)
            }
            ToolbarIconKind.HEATMAP -> {
                for (yOff in listOf(8.5f, 13f, 17.5f)) {
                    val wave = Path().apply {
                        moveTo(4f * u, yOff * u)
                        cubicTo(8f * u, (yOff - 3.5f) * u, 16f * u, (yOff + 3.5f) * u, 20f * u, yOff * u)
                    }
                    canvas.drawPath(wave, p)
                }
            }
            ToolbarIconKind.DEPTH -> {
                val top = Path().apply {
                    moveTo(12f * u, 4.5f * u)
                    lineTo(19.5f * u, 9f * u)
                    lineTo(12f * u, 13.5f * u)
                    lineTo(4.5f * u, 9f * u)
                    close()
                }
                val bot = Path().apply {
                    moveTo(4.5f * u, 14.2f * u)
                    lineTo(12f * u, 18.7f * u)
                    lineTo(19.5f * u, 14.2f * u)
                }
                canvas.drawPath(top, p)
                canvas.drawPath(bot, p)
            }
            ToolbarIconKind.ZOOM -> {
                canvas.drawRoundRect(4.5f * u, 4.5f * u, 19.5f * u, 19.5f * u, 3.2f * u, 3.2f * u, p)
                canvas.drawLine(8.5f * u, 15.5f * u, 15.5f * u, 8.5f * u, p)
                canvas.drawPath(Path().apply { moveTo(11.5f * u, 8.2f * u); lineTo(15.8f * u, 8.2f * u); lineTo(15.8f * u, 12.5f * u) }, p)
            }
            ToolbarIconKind.CUBE -> {
                val outer = Path().apply {
                    moveTo(12f * u, 3.8f * u); lineTo(19.2f * u, 7.8f * u)
                    lineTo(19.2f * u, 16.2f * u); lineTo(12f * u, 20.2f * u)
                    lineTo(4.8f * u, 16.2f * u); lineTo(4.8f * u, 7.8f * u); close()
                }
                canvas.drawPath(outer, p)
                canvas.drawLine(4.8f * u, 7.8f * u, 12f * u, 11.8f * u, p)
                canvas.drawLine(19.2f * u, 7.8f * u, 12f * u, 11.8f * u, p)
                canvas.drawLine(12f * u, 11.8f * u, 12f * u, 20.2f * u, p)
            }
            ToolbarIconKind.CLOCK -> {
                canvas.drawCircle(12f * u, 12f * u, 8.4f * u, p)
                canvas.drawLine(12f * u, 12f * u, 12f * u, 7.6f * u, p)
                canvas.drawLine(12f * u, 12f * u, 15.4f * u, 13.8f * u, p)
            }
            ToolbarIconKind.SPARK -> {
                val star = Path().apply {
                    moveTo(12f * u, 3.8f * u)
                    quadTo(12.6f * u, 11.4f * u, 20.2f * u, 12f * u)
                    quadTo(12.6f * u, 12.6f * u, 12f * u, 20.2f * u)
                    quadTo(11.4f * u, 12.6f * u, 3.8f * u, 12f * u)
                    quadTo(11.4f * u, 11.4f * u, 12f * u, 3.8f * u)
                    close()
                }
                canvas.drawPath(star, p)
            }
            ToolbarIconKind.GRID -> {
                for (r in 0..2) {
                    for (c in 0..2) {
                        canvas.drawCircle((6.5f + c * 5.5f) * u, (6.5f + r * 5.5f) * u, 1.45f * u, fillP)
                    }
                }
            }
            ToolbarIconKind.SCALE -> {
                p.pathEffect = DashPathEffect(floatArrayOf(3f * u, 3f * u), 0f)
                canvas.drawRoundRect(4.5f * u, 4.5f * u, 19.5f * u, 19.5f * u, 3.2f * u, 3.2f * u, p)
            }
            ToolbarIconKind.TOGGLE -> {
                canvas.drawRoundRect(3.5f * u, 7.5f * u, 20.5f * u, 16.5f * u, 4.5f * u, 4.5f * u, p)
                canvas.drawCircle(15.8f * u, 12f * u, 2.4f * u, fillP)
            }
        }
    }

    fun drawRulerTrack(
        canvas: Canvas,
        width: Float,
        trackTop: Float,
        trackHeight: Float,
        density: Float,
        progress: Float,
        accent: Int,
        normalTickColor: Int,
        majorTickColor: Int
    ) {
        val tickSpacing = 5f * density
        val totalTicks = (width / tickSpacing).toInt()
        val centerY = trackTop + (trackHeight / 2f)

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
        for (i in 0..totalTicks) {
            val x = i * tickSpacing
            val isMajor = (i % 5 == 0)
            val tickH = (if (isMajor) 11f else 6f) * density
            linePaint.color = if (isMajor) majorTickColor else normalTickColor
            linePaint.strokeWidth = (if (isMajor) 1.5f else 1f) * density
            canvas.drawLine(x, centerY - tickH / 2f, x, centerY + tickH / 2f, linePaint)
        }

        val indicatorX = progress.coerceIn(0f, 1f) * width
        val thumbW = 4f * density
        val thumbH = 15f * density
        val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawRoundRect(
            indicatorX - thumbW / 2f,
            centerY - thumbH / 2f,
            indicatorX + thumbW / 2f,
            centerY + thumbH / 2f,
            2f * density, 2f * density,
            thumbPaint
        )

        val underlineHalf = 12f * density
        linePaint.color = (205 shl 24) or (accent and 0x00FFFFFF)
        linePaint.strokeWidth = 2.5f * density
        val bottomY = trackTop + trackHeight - (1f * density)
        canvas.drawLine(
            (indicatorX - underlineHalf).coerceAtLeast(0f),
            bottomY,
            (indicatorX + underlineHalf).coerceAtMost(width),
            bottomY,
            linePaint
        )
    }

    fun drawFastScrollIndicator(
        canvas: Canvas,
        width: Float,
        height: Float,
        density: Float,
        scrollStyle: Int,
        count: Int,
        activePage: Int,
        animatedOffsetPx: Float,
        isDragging: Boolean,
        accent: Int,
        baseColor: Int
    ) {
        if (count <= 1 || width <= 0f || height <= 0f) return
        val cy = height / 2f
        val edgePad = 10f * density
        val usableW = (width - edgePad * 2f).coerceAtLeast(1f)
        val thumbX = (width / 2f) + animatedOffsetPx

        val trackAlpha = if (isDragging) 82 else 38
        val trackColor = (trackAlpha shl 24) or (baseColor and 0x00FFFFFF)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 2.5f * density
            color = trackColor
        }

        when (scrollStyle) {
            0 -> {
                val thumbW = (if (isDragging) 40f else 22f) * density
                val thumbH = (if (isDragging) 8f else 5f) * density
                val cx = thumbX.coerceIn(thumbW / 2f, width - thumbW / 2f)
                fillPaint.color = accent
                canvas.drawRoundRect(cx - thumbW / 2f, cy - thumbH / 2f, cx + thumbW / 2f, cy + thumbH / 2f, thumbH / 2f, thumbH / 2f, fillPaint)
            }
            1 -> {
                val trackH = 10f * density
                fillPaint.color = trackColor
                canvas.drawRoundRect(0f, cy - trackH / 2f, width, cy + trackH / 2f, trackH / 2f, trackH / 2f, fillPaint)

                val thumbW = (if (isDragging) 42f else 24f) * density
                val thumbH = (if (isDragging) 10f else 7f) * density
                val cx = thumbX.coerceIn(thumbW / 2f, width - thumbW / 2f)
                fillPaint.color = accent
                canvas.drawRoundRect(cx - thumbW / 2f, cy - thumbH / 2f, cx + thumbW / 2f, cy + thumbH / 2f, thumbH / 2f, thumbH / 2f, fillPaint)
            }
            2 -> {
                canvas.drawLine(edgePad, cy, width - edgePad, cy, strokePaint)
                val stretch = if (isDragging) abs(animatedOffsetPx) * 0.14f else 0f
                val thumbW = (18f * density) + stretch
                val thumbH = 8f * density
                val cx = thumbX.coerceIn(thumbW / 2f, width - thumbW / 2f)
                fillPaint.color = accent
                canvas.drawRoundRect(cx - thumbW / 2f, cy - thumbH / 2f, cx + thumbW / 2f, cy + thumbH / 2f, thumbH / 2f, thumbH / 2f, fillPaint)
            }
            3 -> {
                canvas.drawLine(edgePad, cy, width - edgePad, cy, strokePaint)
                val r = 7f * density
                val cx = thumbX.coerceIn(r, width - r)
                fillPaint.color = accent
                canvas.drawCircle(cx, cy, r, fillPaint)

                if (isDragging) {
                    val label = "${activePage + 1} / $count"
                    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 11f * density
                        typeface = Typeface.create("sans-serif", Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    val badgeW = textPaint.measureText(label) + 16f * density
                    val badgeH = 20f * density
                    val badgeCy = cy - 22f * density
                    val bx = cx.coerceIn(badgeW / 2f, width - badgeW / 2f)
                    fillPaint.color = accent
                    canvas.drawRoundRect(bx - badgeW / 2f, badgeCy - badgeH / 2f, bx + badgeW / 2f, badgeCy + badgeH / 2f, 10f * density, 10f * density, fillPaint)
                    val ty = badgeCy - (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(label, bx, ty, textPaint)
                }
            }
            else -> {
                val pullRange = 26f * density
                val nearThreshold = 9f * density
                for (i in 0 until count) {
                    val dotX = edgePad + (i.toFloat() / (count - 1)) * usableW
                    val dist = abs(dotX - thumbX)
                    val pullY = if (dist < pullRange) (pullRange - dist) * 0.42f else 0f
                    val isNear = dist < nearThreshold
                    fillPaint.color = if (isNear) accent else trackColor
                    val r = (if (isNear) 5f else 2.5f) * density
                    canvas.drawCircle(dotX, cy - pullY, r, fillPaint)
                }
            }
        }
    }
}
