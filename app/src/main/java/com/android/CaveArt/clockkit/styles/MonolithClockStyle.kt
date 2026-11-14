package com.android.CaveArt.clockkit.styles

import android.graphics.*
import com.android.CaveArt.clockkit.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MonolithClockStyle : BaseClockStyle() {

    override val id: String = "monolith"
    override val name: String = "Monolith"
    override val subtitle: String = "Glass-split depth numerals & horizontal collapse"

    override fun datePolicy(config: ClockKitConfig): ClockDatePolicy =
        ClockDatePolicy.INTEGRATED_BY_STYLE

    private class Scratch {
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.LEFT
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val innerRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val dateTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        val emptyPath = Path()
        val hourPath = Path()
        val colonPath = Path()
        val minPath = Path()
        val tmpPath = Path()
        val hourBounds = RectF()
        val minBounds = RectF()
        val badgeBounds = RectF()
        val textRect = Rect()
        val cal: Calendar = Calendar.getInstance()
    }

    private val scratch = ThreadLocal.withInitial { Scratch() }
    
    override fun describeControls(config: ClockKitConfig): List<ClockControl> = listOf(
        ClockControl.Slider(
            id = "tallness", label = "Condensed Width", icon = ClockIcon.SPACING,
            min = 0.45f, max = 0.95f, defaultValue = 0.62f,
            formatter = { "${(it * 100).toInt()}%" }
        ),
        ClockControl.Slider(
            id = "stroke", label = "Glass Outline", icon = ClockIcon.THICKNESS,
            min = 1.5f, max = 9.0f, defaultValue = 3.5f
        ),
        ClockControl.Slider(
            id = "row_gap", label = "Row Overlap", icon = ClockIcon.ROUNDNESS,
            min = -0.18f, max = 0.22f, defaultValue = -0.02f,
            formatter = { "${(it * 100).toInt()}%" }
        ),
        ClockControl.Choice(
            id = "minute_style", label = "Minute Surface", icon = ClockIcon.EFFECTS,
            options = listOf(
                ClockControl.ChoiceOption("glass", "Frosted Glass"),
                ClockControl.ChoiceOption("double", "Double Rim"),
                ClockControl.ChoiceOption("outline", "Pure Outline")
            ),
            defaultKey = "glass"
        ),
        ClockControl.Choice(
            id = "layout_mode", label = "Composition", icon = ClockIcon.LAYOUT,
            options = listOf(
                ClockControl.ChoiceOption("center", "Centered"),
                ClockControl.ChoiceOption("offset", "Offset"),
                ClockControl.ChoiceOption("split", "Wide Split")
            ),
            defaultKey = "center"
        ),
        ClockControl.Choice(
            id = "date_mode", label = "Date Tag", icon = ClockIcon.DATE,
            options = listOf(
                ClockControl.ChoiceOption("inline_pill", "Inline Pill"),
                ClockControl.ChoiceOption("edge_ticker", "Side Ticker"),
                ClockControl.ChoiceOption("hidden", "None")
            ),
            defaultKey = "inline_pill"
        ),
        ClockControl.FontPicker(),
        ClockControl.ColorPicker(),
        ClockControl.QuickChips(
            id = "options", label = "Options", icon = ClockIcon.OPTIONS,
            items = listOf(
                QuickChipItem.ToggleChip("split_depth", "Split Depth", true),
                QuickChipItem.ToggleChip("outline_glow", "Glass Glow", true),
                QuickChipItem.ToggleChip("depth", "Depth", true, globalBinding = "depth"),
                QuickChipItem.ToggleChip("gravity", "Gravity 3D", false, globalBinding = "gravity")
            )
        )
    )

    private fun resolveMonolithTypeface(frameFont: Typeface, config: ClockKitConfig): Typeface {
        return if (config.font == "default" || config.font.isEmpty()) {
            Typeface.create("sans-serif-condensed", Typeface.BOLD)
        } else {
            frameFont
        }
    }

    private fun buildCleanGlyphPath(
        paint: Paint,
        text: String,
        targetCenterX: Float,
        targetTopY: Float,
        emptyPath: Path,
        tmpPath: Path,
        bounds: RectF,
        outPath: Path
    ) {
        outPath.rewind()
        if (text.isEmpty()) {
            bounds.setEmpty()
            return
        }
        paint.getTextPath(text, 0, text.length, 0f, 0f, tmpPath)
        tmpPath.fillType = Path.FillType.WINDING

        if (!outPath.op(tmpPath, emptyPath, Path.Op.UNION)) {
            outPath.set(tmpPath)
        }
        outPath.computeBounds(bounds, true)
        if (!bounds.isEmpty) {
            val dx = targetCenterX - bounds.centerX()
            val dy = targetTopY - bounds.top
            outPath.offset(dx, dy)
            outPath.computeBounds(bounds, true)
        }
    }

    private data class LayoutPlan(
        val digitSizePx: Float,
        val textScaleX: Float,
        val hourCenterX: Float,
        val hourTopY: Float,
        val minCenterX: Float,
        val minTopY: Float,
        val colonCenterX: Float,
        val colonTopY: Float,
        val visualRowHeight: Float
    )

    private fun computeLayoutPlan(
        screenW: Float,
        density: Float,
        clockCenterX: Float,
        clockTopY: Float,
        notificationProgress: Float,
        tf: Typeface,
        hText: String,
        mText: String,
        config: ClockKitConfig,
        s: Scratch
    ): LayoutPlan {
        val p = notificationProgress.coerceIn(0f, 1f)
        val userScale = config.scale

        val posterDigitPx = (screenW * 0.64f) * userScale
        val posterScaleX = config.getFloat(id, "tallness", 0.62f)
        val rowGapRatio = config.getFloat(id, "row_gap", -0.02f)
        val collapsedDigitPx = 88f * density
        val collapsedScaleX = 0.88f

        val curDigitPx = posterDigitPx + (collapsedDigitPx - posterDigitPx) * p
        val curScaleX = posterScaleX + (collapsedScaleX - posterScaleX) * p

        s.glyphPaint.typeface = tf
        s.glyphPaint.textSize = curDigitPx
        s.glyphPaint.textScaleX = curScaleX
        s.glyphPaint.getTextBounds(hText, 0, hText.length, s.textRect)
        val visualInkH = s.textRect.height().toFloat().coerceAtLeast(curDigitPx * 0.72f)

        val layoutMode = config.getString(id, "layout_mode", "center")
        val posterHorizOffset = when (layoutMode) {
            "offset" -> screenW * 0.055f
            "split" -> screenW * 0.11f
            else -> 0f
        }

        val posterHourCX = clockCenterX - posterHorizOffset
        val posterMinCX = clockCenterX + posterHorizOffset
        val posterHourTopY = clockTopY
        val posterMinTopY = clockTopY + visualInkH * (1f + rowGapRatio)

        val wH = s.glyphPaint.measureText(hText)
        val wColon = s.glyphPaint.measureText(":")
        val wM = s.glyphPaint.measureText(mText)
        val totalHorizW = wH + wColon + wM
        val horizStartX = clockCenterX - (totalHorizW / 2f)

        val horizHourCX = horizStartX + (wH / 2f)
        val horizColonCX = horizStartX + wH + (wColon / 2f)
        val horizMinCX = horizStartX + wH + wColon + (wM / 2f)

        return LayoutPlan(
            digitSizePx = curDigitPx,
            textScaleX = curScaleX,
            hourCenterX = posterHourCX + (horizHourCX - posterHourCX) * p,
            hourTopY = posterHourTopY,
            minCenterX = posterMinCX + (horizMinCX - posterMinCX) * p,
            minTopY = posterMinTopY + (clockTopY - posterMinTopY) * p,
            colonCenterX = horizColonCX,
            colonTopY = clockTopY + (visualInkH * 0.14f),
            visualRowHeight = visualInkH
        )
    }

    override fun measure(ctx: ClockMeasureContext, config: ClockKitConfig): ClockMeasureResult {
        val s = scratch.get()!!
        val tf = resolveMonolithTypeface(ctx.typeface, config)
        val parts = ctx.timeString.split(":")
        val hText = parts.getOrElse(0) { "08" }
        val mText = parts.getOrElse(1) { "34" }

        val plan = computeLayoutPlan(
            screenW = ctx.screenW,
            density = ctx.density,
            clockCenterX = ctx.screenW / 2f,
            clockTopY = 0f,
            notificationProgress = ctx.notificationProgress,
            tf = tf,
            hText = hText,
            mText = mText,
            config = config,
            s = s
        )

        s.glyphPaint.typeface = tf
        s.glyphPaint.textSize = plan.digitSizePx
        s.glyphPaint.textScaleX = plan.textScaleX

        val wH = s.glyphPaint.measureText(hText)
        val wM = s.glyphPaint.measureText(mText)
        val p = ctx.notificationProgress.coerceIn(0f, 1f)
        val layoutMode = config.getString(id, "layout_mode", "center")
        val offsetSpread = when (layoutMode) {
            "offset" -> ctx.screenW * 0.11f
            "split" -> ctx.screenW * 0.22f
            else -> 0f
        } * (1f - p)

        val posterW = max(wH, wM) + offsetSpread
        val horizW = wH + s.glyphPaint.measureText(":") + wM
        val totalW = posterW + (horizW - posterW) * p
        val totalH = (plan.minTopY + plan.visualRowHeight)

        return ClockMeasureResult(
            contentWidthPx = totalW.coerceAtLeast(ctx.screenW * 0.35f),
            contentHeightPx = totalH,
            topPadPx = 22f * ctx.density,
            bottomPadPx = 32f * ctx.density,
            wantsFullScreenBounds = false,
            notificationCollapsedHeightPx = 72f * ctx.density
        )
    }

    override fun render(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult {
        val s = scratch.get()!!
        val tf = resolveMonolithTypeface(frame.typeface, config)
        val parts = frame.timeString.split(":")
        val hText = parts.getOrElse(0) { "08" }
        val mText = parts.getOrElse(1) { "34" }

        val plan = computeLayoutPlan(
            screenW = frame.screenW,
            density = frame.density,
            clockCenterX = frame.clockCenterX,
            clockTopY = frame.clockTopY,
            notificationProgress = frame.notificationProgress,
            tf = tf,
            hText = hText,
            mText = mText,
            config = config,
            s = s
        )

        s.glyphPaint.typeface = tf
        s.glyphPaint.textSize = plan.digitSizePx
        s.glyphPaint.textScaleX = plan.textScaleX

        buildCleanGlyphPath(
            paint = s.glyphPaint,
            text = hText,
            targetCenterX = plan.hourCenterX,
            targetTopY = plan.hourTopY,
            emptyPath = s.emptyPath,
            tmpPath = s.tmpPath,
            bounds = s.hourBounds,
            outPath = s.hourPath
        )

        s.fillPaint.color = config.color
        s.fillPaint.clearShadowLayer()
        canvas.drawPath(s.hourPath, s.fillPaint)

        val splitDepth = config.getBool(id, "split_depth", true) && frame.notificationProgress < 0.2f
        if (!splitDepth) {
            drawMinutesAndDate(canvas, frame, config, plan, mText, s)
            s.hourPath.addPath(s.minPath)
            s.hourBounds.union(s.minBounds)
            if (!s.badgeBounds.isEmpty) {
                s.hourBounds.union(s.badgeBounds)
            }
        }

        return ClockRenderResult(
            boundsOnScreen = RectF(s.hourBounds),
            occlusionPath = Path(s.hourPath)
        )
    }

    override fun renderOverlay(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult? {
        val splitDepth = config.getBool(id, "split_depth", true) && frame.notificationProgress < 0.2f
        if (!splitDepth) return null

        val s = scratch.get()!!
        val tf = resolveMonolithTypeface(frame.typeface, config)
        val parts = frame.timeString.split(":")
        val hText = parts.getOrElse(0) { "08" }
        val mText = parts.getOrElse(1) { "34" }

        val plan = computeLayoutPlan(
            screenW = frame.screenW,
            density = frame.density,
            clockCenterX = frame.clockCenterX,
            clockTopY = frame.clockTopY,
            notificationProgress = frame.notificationProgress,
            tf = tf,
            hText = hText,
            mText = mText,
            config = config,
            s = s
        )

        s.glyphPaint.typeface = tf
        s.glyphPaint.textSize = plan.digitSizePx
        s.glyphPaint.textScaleX = plan.textScaleX

        drawMinutesAndDate(canvas, frame, config, plan, mText, s)

        val overlayBounds = RectF(s.minBounds)
        if (!s.badgeBounds.isEmpty) {
            overlayBounds.union(s.badgeBounds)
        }
        return ClockRenderResult(boundsOnScreen = overlayBounds, occlusionPath = null)
    }

    private fun drawMinutesAndDate(
        canvas: Canvas,
        frame: ClockFrameContext,
        config: ClockKitConfig,
        plan: LayoutPlan,
        mText: String,
        s: Scratch
    ) {
        val p = frame.notificationProgress.coerceIn(0f, 1f)

        if (p > 0.18f) {
            val colonAlpha = (((p - 0.18f) / 0.82f) * 255f).toInt().coerceIn(0, 255)
            buildCleanGlyphPath(
                paint = s.glyphPaint,
                text = ":",
                targetCenterX = plan.colonCenterX,
                targetTopY = plan.colonTopY,
                emptyPath = s.emptyPath,
                tmpPath = s.tmpPath,
                bounds = s.minBounds,
                outPath = s.colonPath
            )
            s.fillPaint.color = Color.argb(
                colonAlpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color)
            )
            canvas.drawPath(s.colonPath, s.fillPaint)
        }

        buildCleanGlyphPath(
            paint = s.glyphPaint,
            text = mText,
            targetCenterX = plan.minCenterX,
            targetTopY = plan.minTopY,
            emptyPath = s.emptyPath,
            tmpPath = s.tmpPath,
            bounds = s.minBounds,
            outPath = s.minPath
        )

        val minuteStyle = config.getString(id, "minute_style", "glass")
        val glow = config.getBool(id, "outline_glow", true)
        val baseStrokePx = config.getFloat(id, "stroke", 3.5f) * frame.density

        val baseGlassAlpha = when (minuteStyle) {
            "glass" -> 34
            "double" -> 22
            else -> 0
        }
        val effectiveFillAlpha = (baseGlassAlpha + ((255 - baseGlassAlpha) * p)).toInt().coerceIn(0, 255)
        if (effectiveFillAlpha > 0) {
            s.fillPaint.color = Color.argb(
                effectiveFillAlpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color)
            )
            s.fillPaint.clearShadowLayer()
            canvas.drawPath(s.minPath, s.fillPaint)
        }

        val strokeFade = (1f - (p * 0.75f)).coerceIn(0f, 1f)
        val strokeAlpha = (255f * strokeFade).toInt().coerceIn(0, 255)
        if (strokeAlpha > 8) {
            s.strokePaint.strokeWidth = baseStrokePx * (1f - 0.55f * p)
            s.strokePaint.color = Color.argb(
                strokeAlpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color)
            )

            if (glow && p < 0.4f) {
                s.strokePaint.setShadowLayer(
                    14f * frame.density,
                    0f,
                    0f,
                    Color.argb(135, Color.red(config.color), Color.green(config.color), Color.blue(config.color))
                )
            } else {
                s.strokePaint.setShadowLayer(
                    6f * frame.density,
                    0f,
                    2f * frame.density,
                    Color.argb(110, 0, 0, 0)
                )
            }
            canvas.drawPath(s.minPath, s.strokePaint)

            if (minuteStyle == "double" && p < 0.35f) {
                s.innerRimPaint.strokeWidth = (baseStrokePx * 0.32f).coerceAtLeast(1.2f * frame.density)
                s.innerRimPaint.color = Color.argb(180, 255, 255, 255)
                canvas.drawPath(s.minPath, s.innerRimPaint)
            }
        }

        s.badgeBounds.setEmpty()
        val dateMode = config.getString(id, "date_mode", "inline_pill")
        if (dateMode != "hidden" && p < 0.35f) {
            drawMonolithDate(canvas, frame, config, dateMode, plan, s)
        }
    }

    private fun drawMonolithDate(
        canvas: Canvas,
        frame: ClockFrameContext,
        config: ClockKitConfig,
        dateMode: String,
        plan: LayoutPlan,
        s: Scratch
    ) {
        val alpha = ((1f - (frame.notificationProgress / 0.35f)) * 255).toInt().coerceIn(0, 255)
        if (alpha <= 5) return

        val lum = (0.299 * Color.red(config.color) + 0.587 * Color.green(config.color) + 0.114 * Color.blue(config.color)) / 255.0
        val contrastText = if (lum > 0.55) Color.BLACK else Color.WHITE

        if (dateMode == "edge_ticker") {
            s.cal.timeInMillis = frame.timeMillis
            val monthStr = (s.cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            val dayStr = s.cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val dayShort = SimpleDateFormat("EEE", Locale.ENGLISH)
                .format(Date(frame.timeMillis))
                .uppercase(Locale.ENGLISH)
                .take(3)

            val safeTopY = max(frame.clockTopY + 12f * frame.density, 64f * frame.density)
            val colCenterX = frame.screenW - (24f * frame.density)
            var cursorY = safeTopY

            s.dateTextPaint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            s.dateTextPaint.textSize = 12.5f * frame.density
            s.dateTextPaint.color = Color.argb(alpha, Color.red(config.color), Color.green(config.color), Color.blue(config.color))
            s.dateTextPaint.setShadowLayer(4f * frame.density, 0f, 1f * frame.density, Color.argb(110, 0, 0, 0))

            val lineStep = 15.5f * frame.density
            canvas.drawText(monthStr, colCenterX, cursorY, s.dateTextPaint)
            cursorY += lineStep * 0.85f
            canvas.drawText("•", colCenterX, cursorY, s.dateTextPaint)
            cursorY += lineStep * 0.95f
            canvas.drawText(dayStr, colCenterX, cursorY, s.dateTextPaint)
            cursorY += 10f * frame.density

            val pillW = 22f * frame.density
            val pillH = 38f * frame.density
            s.badgeBounds.set(
                colCenterX - pillW / 2f,
                cursorY,
                colCenterX + pillW / 2f,
                cursorY + pillH
            )
            s.badgeFillPaint.color = Color.argb(
                alpha,
                Color.red(config.color),
                Color.green(config.color),
                Color.blue(config.color)
            )
            canvas.drawRoundRect(s.badgeBounds, 8f * frame.density, 8f * frame.density, s.badgeFillPaint)

            s.dateTextPaint.textSize = 10.5f * frame.density
            s.dateTextPaint.clearShadowLayer()
            s.dateTextPaint.color = Color.argb(alpha, Color.red(contrastText), Color.green(contrastText), Color.blue(contrastText))
            canvas.save()
            canvas.rotate(90f, s.badgeBounds.centerX(), s.badgeBounds.centerY())
            val textY = s.badgeBounds.centerY() - (s.dateTextPaint.descent() + s.dateTextPaint.ascent()) / 2f
            canvas.drawText(dayShort, s.badgeBounds.centerX(), textY, s.dateTextPaint)
            canvas.restore()
            return
        }

        val dateLabel = frame.dateString.uppercase(Locale.getDefault())
        s.dateTextPaint.typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        s.dateTextPaint.textSize = 12f * frame.density
        s.dateTextPaint.clearShadowLayer()

        val labelW = s.dateTextPaint.measureText(dateLabel)
        val dotGap = 10f * frame.density
        val padH = 14f * frame.density
        val pillH = 26f * frame.density
        val pillTop = s.minBounds.bottom + (14f * frame.density)
        val pillW = labelW + padH * 2f + dotGap

        s.badgeBounds.set(
            plan.minCenterX - pillW / 2f,
            pillTop,
            plan.minCenterX + pillW / 2f,
            pillTop + pillH
        )

        s.badgeFillPaint.color = Color.argb((alpha * 0.58f).toInt(), 14, 14, 18)
        canvas.drawRoundRect(s.badgeBounds, 10f * frame.density, 10f * frame.density, s.badgeFillPaint)

        s.badgeBorderPaint.strokeWidth = 1.4f * frame.density
        s.badgeBorderPaint.color = Color.argb((alpha * 0.85f).toInt(), Color.red(config.color), Color.green(config.color), Color.blue(config.color))
        canvas.drawRoundRect(s.badgeBounds, 10f * frame.density, 10f * frame.density, s.badgeBorderPaint)

        val dotR = 3.2f * frame.density
        s.badgeFillPaint.color = Color.argb(alpha, Color.red(config.color), Color.green(config.color), Color.blue(config.color))
        canvas.drawCircle(s.badgeBounds.left + padH, s.badgeBounds.centerY(), dotR, s.badgeFillPaint)

        s.dateTextPaint.color = Color.argb(alpha, 255, 255, 255)
        val textCenterX = s.badgeBounds.left + padH + dotGap + (labelW / 2f)
        val textCenterY = s.badgeBounds.centerY() - (s.dateTextPaint.descent() + s.dateTextPaint.ascent()) / 2f
        canvas.drawText(dateLabel, textCenterX, textCenterY, s.dateTextPaint)
    }
}
