package com.android.CaveArt.clockkit.styles

import android.graphics.*
import com.android.CaveArt.clockkit.*
import kotlin.math.max

class EditorialSplitClockStyle : BaseClockStyle() {

    override val id: String = "editorial_split"
    override val name: String = "Editorial Stack"
    override val subtitle: String = "Bold typographic stack with built-in pill date"

    override fun datePolicy(config: ClockKitConfig): ClockDatePolicy =
        ClockDatePolicy.INTEGRATED_BY_STYLE

    private class Scratch {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setShadowLayer(18f, 0f, 6f, Color.argb(140, 0, 0, 0))
        }
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()
        val tmpPath = Path()
        val bounds = RectF()
        val pillRect = RectF()
    }

    private val scratch = ThreadLocal.withInitial { Scratch() }
    
    override fun describeControls(config: ClockKitConfig): List<ClockControl> = listOf(
        ClockControl.Slider(
            id = "overlap", label = "Row Overlap", icon = ClockIcon.SPACING,
            min = -0.35f, max = 0.25f, defaultValue = -0.14f,
            formatter = { "${(it * 100).toInt()}%" }
        ),
        ClockControl.Choice(
            id = "align", label = "Alignment", icon = ClockIcon.LAYOUT,
            options = listOf(
                ClockControl.ChoiceOption("stagger", "Diagonal"),
                ClockControl.ChoiceOption("left", "Left"),
                ClockControl.ChoiceOption("center", "Center")
            ),
            defaultKey = "stagger"
        ),
        ClockControl.FontPicker(),
        ClockControl.ColorPicker(),
        ClockControl.QuickChips(
            id = "options", label = "Options", icon = ClockIcon.OPTIONS,
            items = listOf(
                QuickChipItem.ToggleChip("outline_hours", "Outline Hours", true),
                QuickChipItem.ToggleChip("depth", "Depth", true, globalBinding = "depth"),
                QuickChipItem.ToggleChip("gravity", "Gravity 3D", false, globalBinding = "gravity")
            )
        )
    )

    override fun measure(ctx: ClockMeasureContext, config: ClockKitConfig): ClockMeasureResult {
        val styleScale = config.scale
        val shrink = 1f - (0.32f * ctx.notificationProgress)
        val digitPx = 124f * ctx.density * styleScale * shrink
        val overlap = config.getFloat(id, "overlap", -0.14f)
        val rowStep = digitPx * (0.86f + overlap)
        val datePillH = 32f * ctx.density * shrink

        val w = digitPx * 2.3f
        val h = digitPx * 0.85f + rowStep + datePillH + (24f * ctx.density)

        return ClockMeasureResult(
            contentWidthPx = w,
            contentHeightPx = h,
            topPadPx = 24f * ctx.density,
            bottomPadPx = 32f * ctx.density,
            notificationCollapsedHeightPx = 84f * ctx.density * 1.6f
        )
    }

    override fun render(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult {
        val s = scratch.get()!!
        val styleScale = config.scale
        val shrink = 1f - (0.32f * frame.notificationProgress)
        val digitPx = 124f * frame.density * styleScale * shrink
        val overlap = config.getFloat(id, "overlap", -0.14f)
        val align = config.getString(id, "align", "stagger")
        val outlineHours = config.getBool(id, "outline_hours", true)

        val parts = frame.timeString.split(":")
        val hText = parts.getOrElse(0) { "09" }
        val mText = parts.getOrElse(1) { "41" }

        s.paint.typeface = frame.typeface
        s.paint.textSize = digitPx

        val hW = s.paint.measureText(hText)
        val mW = s.paint.measureText(mText)
        val staggerShift = if (align == "stagger") digitPx * 0.34f else 0f
        val maxRowW = max(hW, mW) + staggerShift

        val leftX = when (align) {
            "left" -> frame.clockCenterX - (maxRowW / 2f)
            "stagger" -> frame.clockCenterX - (maxRowW / 2f)
            else -> frame.clockCenterX - (hW / 2f)
        }
        val minX = when (align) {
            "left" -> leftX
            "stagger" -> frame.clockCenterX + (maxRowW / 2f) - mW
            else -> frame.clockCenterX - (mW / 2f)
        }

        val fm = s.paint.fontMetrics
        val capHeight = -fm.ascent * 0.85f
        val hourBaseline = frame.clockTopY + capHeight
        val minBaseline = hourBaseline + (digitPx * (0.86f + overlap))

        s.path.rewind()
        s.tmpPath.rewind()
        s.paint.getTextPath(hText, 0, hText.length, leftX, hourBaseline, s.tmpPath)
        s.path.addPath(s.tmpPath)

        if (outlineHours) {
            s.paint.style = Paint.Style.STROKE
            s.paint.strokeWidth = 3.5f * frame.density
            s.paint.color = Color.WHITE
            canvas.drawText(hText, leftX, hourBaseline, s.paint)
        } else {
            s.paint.style = Paint.Style.FILL
            s.paint.color = Color.WHITE
            canvas.drawText(hText, leftX, hourBaseline, s.paint)
        }

        s.paint.style = Paint.Style.FILL
        s.paint.color = config.color
        s.tmpPath.rewind()
        s.paint.getTextPath(mText, 0, mText.length, minX, minBaseline, s.tmpPath)
        s.path.addPath(s.tmpPath)
        canvas.drawText(mText, minX, minBaseline, s.paint)

        val dateText = frame.dateString.uppercase()
        s.datePaint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        s.datePaint.textSize = 13f * frame.density * shrink
        val dateW = s.datePaint.measureText(dateText)
        val pillPadH = 14f * frame.density * shrink
        val pillH = 28f * frame.density * shrink
        val pillTop = minBaseline + (18f * frame.density * shrink)

        s.pillRect.set(
            frame.clockCenterX - (dateW / 2f) - pillPadH,
            pillTop,
            frame.clockCenterX + (dateW / 2f) + pillPadH,
            pillTop + pillH
        )
        s.pillPaint.color = Color.argb(
            200,
            Color.red(config.color),
            Color.green(config.color),
            Color.blue(config.color)
        )
        canvas.drawRoundRect(s.pillRect, pillH / 2f, pillH / 2f, s.pillPaint)

        val lum = (0.299 * Color.red(config.color) + 0.587 * Color.green(config.color) + 0.114 * Color.blue(config.color)) / 255.0
        s.datePaint.color = if (lum > 0.55) Color.BLACK else Color.WHITE
        s.datePaint.textAlign = Paint.Align.CENTER
        val textY = s.pillRect.centerY() - (s.datePaint.descent() + s.datePaint.ascent()) / 2f
        canvas.drawText(dateText, s.pillRect.centerX(), textY, s.datePaint)

        s.path.computeBounds(s.bounds, true)
        s.bounds.union(s.pillRect)

        return ClockRenderResult(
            boundsOnScreen = RectF(s.bounds),
            occlusionPath = Path(s.path)
        )
    }
}
