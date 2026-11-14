package com.android.CaveArt.clockkit.styles

import android.graphics.*
import com.android.CaveArt.AdaptiveClockHelper
import com.android.CaveArt.PerspectiveFitHelper
import com.android.CaveArt.clockkit.*
import kotlin.math.min

class AdaptiveVectorClockStyle : BaseClockStyle() {

    override val id: String = "adaptive_vector"
    override val name: String = "Adaptive Fluid"
    override val subtitle: String = "Stretchable dual-tone vector numerals"

    override val handlesGravityManually: Boolean = true

    override fun needsCollisionMap(config: ClockKitConfig): Boolean =
        config.getBool(id, "stretch", false)

    private class Scratch {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            setShadowLayer(15f, 0f, 5f, Color.argb(160, 0, 0, 0))
        }
        val combinedPath = Path()
        val bounds = RectF()
    }

    private val scratch = ThreadLocal.withInitial { Scratch() }
    
    override fun describeControls(config: ClockKitConfig): List<ClockControl> = listOf(
        ClockControl.Slider(
            id = "stroke_width", label = "Thickness", icon = ClockIcon.THICKNESS,
            min = 0f, max = 25f, defaultValue = 8f
        ),
        ClockControl.Slider(
            id = "roundness", label = "Roundness", icon = ClockIcon.ROUNDNESS,
            min = 0f, max = 80f, defaultValue = 30f
        ),
        ClockControl.FontPicker(),
        ClockControl.ColorPicker(),
        ClockControl.QuickChips(
            id = "options", label = "Layout & Fit", icon = ClockIcon.OPTIONS,
            items = listOf(
                QuickChipItem.CycleChip(
                    paramId = "layout",
                    options = listOf(
                        ClockControl.ChoiceOption("horizontal", "Horizontal"),
                        ClockControl.ChoiceOption("vertical", "Vertical")
                    ),
                    defaultKey = "horizontal"
                ),
                QuickChipItem.ToggleChip("gravity", "Gravity 3D", false, globalBinding = "gravity"),
                QuickChipItem.ToggleChip("dual_tone", "Dual Tone", true),
                QuickChipItem.ToggleChip("depth", "Depth", true, globalBinding = "depth"),
                QuickChipItem.ToggleChip("stretch", "Adaptive", false),
                QuickChipItem.ActionChip("auto_fit", "Auto Fit", ClockIcon.TUNE)
            )
        )
    )

    override fun onPinchZoom(config: ClockKitConfig, zoomFactor: Float): ClockKitConfig {
        val h = (config.getFloat(id, "hour_size", 100f) * zoomFactor).coerceIn(35f, 200f)
        val m = (config.getFloat(id, "min_size", 75f) * zoomFactor).coerceIn(30f, 180f)
        return config.withFloat(id, "hour_size", h).withFloat(id, "min_size", m)
    }

    override fun onAction(
        actionId: String,
        config: ClockKitConfig,
        mask: Bitmap?,
        screenW: Float,
        screenH: Float,
        density: Float
    ): ClockKitConfig {
        if (actionId == "auto_fit" && mask != null) {
            val h = config.getFloat(id, "hour_size", 100f)
            val m = config.getFloat(id, "min_size", 75f)
            val fit = AdaptiveClockHelper.calculateAutoFit(
                mask, screenW, screenH, h, m, density,
                isMagicShapeEnabled = false, magicScale = 1f, isCentered = false
            ) ?: return config
            return config
                .withFloat(id, "hour_size", fit.hourSize)
                .withFloat(id, "min_size", fit.minSize)
                .copy(clockX = 0f, clockY = fit.yDp)
        }
        return config
    }

    override fun measure(ctx: ClockMeasureContext, config: ClockKitConfig): ClockMeasureResult {
        val baseH = config.getFloat(id, "hour_size", 100f)
        val baseM = config.getFloat(id, "min_size", 75f)
        val strokeW = config.getFloat(id, "stroke_width", 8f)
        val isVert = config.getString(id, "layout", "horizontal") == "vertical"
        val isStretch = config.getBool(id, "stretch", false)

        val collapsedH = min(baseH, 85f)
        val collapsedM = min(baseM, 72f)
        val curH = (baseH + (collapsedH - baseH) * ctx.notificationProgress) * ctx.density
        val curM = (baseM + (collapsedM - baseM) * ctx.notificationProgress) * ctx.density

        val layoutP = (if (isVert) 1f else 0f) * (1f - ctx.notificationProgress)
        val w = AdaptiveClockHelper.measureClockWidth(ctx.timeString, curH, curM, ctx.typeface, layoutP)

        val maxDigitPx = maxOf(curH, curM)
        val verticalMult = if (isVert && ctx.notificationProgress < 0.5f) 2.15f else 1.15f
        val stretchExtra = if (isStretch && ctx.notificationProgress < 0.5f) maxDigitPx * 1.6f else 0f
        val strokePad = (strokeW * ctx.density) + (12f * ctx.density)

        return ClockMeasureResult(
            contentWidthPx = w + strokePad * 2f,
            contentHeightPx = maxDigitPx * verticalMult,
            topPadPx = strokePad,
            bottomPadPx = strokePad + stretchExtra,
            notificationCollapsedHeightPx = min(baseH, 85f) * ctx.density * 1.2f
        )
    }

    override fun render(canvas: Canvas, frame: ClockFrameContext, config: ClockKitConfig): ClockRenderResult {
        val s = scratch.get()!!
        val baseH = config.getFloat(id, "hour_size", 100f)
        val baseM = config.getFloat(id, "min_size", 75f)
        val strokeW = config.getFloat(id, "stroke_width", 8f)
        val roundness = config.getFloat(id, "roundness", 30f)
        val dualTone = config.getBool(id, "dual_tone", true)
        val isStretch = config.getBool(id, "stretch", false)
        val isVert = config.getString(id, "layout", "horizontal") == "vertical"

        val collapsedH = min(baseH, 85f)
        val collapsedM = min(baseM, 72f)
        val hPx = (baseH + (collapsedH - baseH) * frame.notificationProgress) * frame.density
        val mPx = (baseM + (collapsedM - baseM) * frame.notificationProgress) * frame.density

        val layoutP = (if (isVert) 1f else 0f) * (1f - frame.notificationProgress)
        val totalW = AdaptiveClockHelper.measureClockWidth(frame.timeString, hPx, mPx, frame.typeface, layoutP)
        val startX = frame.clockCenterX - (totalW / 2f)
        val startY = frame.clockTopY

        val paths = AdaptiveClockHelper.buildPaths(
            timeString = frame.timeString,
            startX = startX,
            startY = startY,
            absoluteClockX = 0f,
            absoluteClockY = 0f,
            hourSizePx = hPx,
            minSizePx = mPx,
            typeface = frame.typeface,
            screenW = frame.screenW,
            screenH = frame.screenH,
            isStretchEnabled = isStretch,
            layoutProgress = layoutP,
            collisionMap = frame.collisionMap,
            density = frame.density,
            strokeWidth = strokeW,
            stretchProgress = 1f - frame.notificationProgress
        )

        if (config.isGravityEnabled && frame.rawDepthMap != null && !frame.rawDepthMap.isRecycled) {
            s.combinedPath.rewind()
            s.combinedPath.addPath(paths.hours)
            s.combinedPath.addPath(paths.colon)
            s.combinedPath.addPath(paths.mins)
            s.combinedPath.computeBounds(s.bounds, true)

            val perspective = PerspectiveFitHelper.calculateAutoPerspective(
                rawDepthMap = frame.rawDepthMap,
                clockRect = s.bounds,
                sampleRectOnScreen = s.bounds,
                screenW = frame.screenW,
                screenH = frame.screenH,
                intensity = config.gravityIntensity,
                mode = config.gravityMode
            )
            paths.hours.transform(perspective.transformMatrix)
            paths.colon.transform(perspective.transformMatrix)
            paths.mins.transform(perspective.transformMatrix)
        }

        s.combinedPath.rewind()
        s.combinedPath.addPath(paths.hours)
        s.combinedPath.addPath(paths.colon)
        s.combinedPath.addPath(paths.mins)
        s.combinedPath.computeBounds(s.bounds, true)

        s.paint.strokeWidth = strokeW * frame.density
        s.paint.pathEffect = CornerPathEffect(roundness * frame.density)

        s.paint.color = if (dualTone) Color.WHITE else config.color
        canvas.drawPath(paths.hours, s.paint)
        canvas.drawPath(paths.colon, s.paint)

        s.paint.color = config.color
        canvas.drawPath(paths.mins, s.paint)

        return ClockRenderResult(
            boundsOnScreen = RectF(s.bounds),
            occlusionPath = Path(s.combinedPath)
        )
    }
}
