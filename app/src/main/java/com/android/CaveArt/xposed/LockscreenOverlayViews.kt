package com.android.CaveArt.xposed

import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.CaveArt.clockkit.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal enum class EditorLayerScope { ALL, BACKGROUND, CLOCK, SUBJECT }

internal enum class ActionKind { SLIDER, CHOICE, QUICK_CHIPS, TOGGLE, FONT, COLOR }

internal data class OverlayAction(
    val id: String,
    val label: String,
    val iconKind: ToolbarIconKind,
    val kind: ActionKind,
    val getVal: () -> Float = { 0f },
    val setVal: (Float) -> Unit = {},
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val formatter: (Float) -> String = { String.format("%.1f", it) },
    val control: ClockControl? = null
)

internal data class OverlayTheme(
    val isNight: Boolean,
    val accentColor: Int,
    val surfaceOverlayColor: Int,
    val onSurfaceColor: Int,
    val onSurfaceMuted: Int,
    val activePillBg: Int,
    val sheetBgColor: Int,
    val chipBgColor: Int
)

internal class UnifiedLineIconView(
    ctx: Context,
    private val kind: ToolbarIconKind,
    private val tint: Int,
    private val targetSizePx: Int
) : View(ctx) {
    private val strokePx = 1.85f * resources.displayMetrics.density

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(targetSizePx, targetSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val sz = min(width, height).toFloat()
        if (sz <= 0f) return
        ClockToolbarIcons.draw(canvas, kind, tint, sz, strokePx)
    }
}

internal class FastScrollIndicatorView(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private var scrollStyle = 0
    private var count = 1
    private var activePage = 0
    private var pageOffset = 0f
    private var isDragging = false
    private var rawDragOffset = 0f
    private var initialPage = 0
    private var lastX = 0f
    private var animatedOffsetPx = 0f
    private var accent = Color.parseColor("#0F5D73")
    private var base = Color.parseColor("#191C1E")

    private val springHolder = FloatValueHolder(0f)
    private val springAnim = SpringAnimation(springHolder).apply {
        spring = SpringForce(0f).apply {
            dampingRatio = 0.75f
            stiffness = 320f
        }
        addUpdateListener { _, value, _ ->
            animatedOffsetPx = value
            invalidate()
        }
    }

    var onPageSelected: ((Int) -> Unit)? = null

    fun setScrollStyle(style: Int) {
        scrollStyle = style
        invalidate()
    }

    fun setPageCount(c: Int) {
        count = c.coerceAtLeast(1)
        invalidate()
    }

    fun setPage(page: Int, frac: Float) {
        activePage = page.coerceIn(0, count - 1)
        pageOffset = frac
        if (!isDragging) {
            val target = frac * 38f * density
            springAnim.cancel()
            animatedOffsetPx = target
            springHolder.value = target
        }
        invalidate()
    }

    fun setColors(accentCol: Int, baseCol: Int) {
        accent = accentCol
        base = baseCol
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        ClockToolbarIcons.drawFastScrollIndicator(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            density = density,
            scrollStyle = scrollStyle,
            count = count,
            activePage = activePage,
            animatedOffsetPx = animatedOffsetPx,
            isDragging = isDragging,
            accent = accent,
            baseColor = base
        )
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (count <= 1 || width <= 0) return false
        val maxDragPx = ((width / 2f) - (16f * density)).coerceAtLeast(1f)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                initialPage = activePage
                rawDragOffset = 0f
                lastX = ev.x
                springAnim.cancel()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - lastX
                lastX = ev.x
                rawDragOffset = (rawDragOffset + dx).coerceIn(-maxDragPx, maxDragPx)
                springAnim.animateToFinalPosition(rawDragOffset)

                val dragFraction = rawDragOffset / maxDragPx
                val calculated = if (dragFraction > 0f) {
                    initialPage + dragFraction * (count - 1 - initialPage)
                } else {
                    initialPage + dragFraction * initialPage
                }
                val target = calculated.roundToInt().coerceIn(0, count - 1)
                if (target != activePage) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    activePage = target
                    onPageSelected?.invoke(target)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                rawDragOffset = 0f
                springAnim.animateToFinalPosition(pageOffset * 38f * density)
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        springAnim.cancel()
        super.onDetachedFromWindow()
    }
}

internal class SlimRulerSliderView(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private fun dpf(v: Float): Float = v * density

    private var labelText = ""
    private var minVal = 0f
    private var maxVal = 1f
    private var curVal = 0f
    private var valueFormatter: (Float) -> String = { String.format("%.1f", it) }
    private var accent = Color.parseColor("#0F5D73")
    private var onSurface = Color.parseColor("#191C1E")
    private var normalTickColor = Color.argb(65, 25, 28, 30)
    private var majorTickColor = Color.argb(155, 25, 28, 30)
    private var iconKind = ToolbarIconKind.OPTIONS
    private var lastTickIdx = -1

    var onValueChanged: ((Float) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun configure(
        iconKind: ToolbarIconKind,
        label: String,
        value: Float,
        min: Float,
        max: Float,
        formatter: (Float) -> String,
        accent: Int,
        textColor: Int,
        tickMuted: Int,
        tickMajor: Int
    ) {
        this.iconKind = iconKind
        this.labelText = label
        this.curVal = value
        this.minVal = min
        this.maxVal = max
        this.valueFormatter = formatter
        this.accent = accent
        this.onSurface = textColor
        this.normalTickColor = tickMuted
        this.majorTickColor = tickMajor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val w = width.toFloat()

        canvas.save()
        canvas.translate(dpf(4f), dpf(2f))
        ClockToolbarIcons.draw(canvas, iconKind, accent, dpf(15f), dpf(1.4f))
        canvas.restore()

        textPaint.textSize = dpf(12.5f)
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        textPaint.color = onSurface
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(labelText, dpf(26f), dpf(14f), textPaint)

        textPaint.color = accent
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        canvas.drawText(valueFormatter(curVal), w - dpf(4f), dpf(14f), textPaint)

        val progress = ((curVal - minVal) / (maxVal - minVal).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
        val trackTop = dpf(24f)
        ClockToolbarIcons.drawRulerTrack(
            canvas = canvas,
            width = w,
            trackTop = trackTop,
            trackHeight = (height - trackTop).coerceAtLeast(dpf(24f)),
            density = density,
            progress = progress,
            accent = accent,
            normalTickColor = normalTickColor,
            majorTickColor = majorTickColor
        )
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (width <= 0) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val w = width.toFloat()
                val progress = (ev.x / w).coerceIn(0f, 1f)
                curVal = minVal + progress * (maxVal - minVal)
                onValueChanged?.invoke(curVal)

                val totalTicks = (w / dpf(5f)).toInt().coerceAtLeast(1)
                val tickIdx = (progress * totalTicks).toInt()
                if (tickIdx != lastTickIdx) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                    lastTickIdx = tickIdx
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}

internal class LockscreenPreviewCanvasView(ctx: Context) : View(ctx) {
    private val density = resources.displayMetrics.density
    private fun dpf(v: Float): Float = v * density

    var sceneProvider: (() -> LockscreenSceneParams)? = null
    var getConfig: () -> ClockKitConfig = { ClockKitConfig() }
    var getStatusBarPadPx: () -> Int = { 0 }
    var getNavBarPadPx: () -> Int = { 0 }
    var isClockFocused: () -> Boolean = { true }
    var onConfigChanged: (ClockKitConfig) -> Unit = {}
    var onTapClockFocus: () -> Unit = {}

    private val clockBounds = RectF()
    private var isCardDragging = false
    private var dragMode = "CLOCK"
    private var lastX = 0f
    private var lastY = 0f
    private var rawClockX = 0f
    private var rawClockY = 0f
    private var rawDateX = 0f
    private var rawDateY = 0f
    private var isSnappedCenter = false

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 255, 255, 255)
        setStyle(Paint.Style.STROKE)
        strokeWidth = dpf(1.8f)
        pathEffect = DashPathEffect(floatArrayOf(dpf(6f), dpf(5f)), 0f)
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 120, 200, 255)
        setStyle(Paint.Style.STROKE)
        strokeWidth = dpf(1.5f)
    }

    private fun clamp(cfg: ClockKitConfig, resolveOverlap: Boolean): ClockKitConfig =
        LockscreenSceneRenderer.clampConfigToScreen(
            context = context,
            cfg = cfg,
            screenW = CaveArtXposedModule.screenW,
            screenH = CaveArtXposedModule.screenH,
            density = density,
            statusBarPadPx = getStatusBarPadPx(),
            navBarPadPx = getNavBarPadPx(),
            resolveDetachedOverlap = resolveOverlap,
            drawnClockBoundsPx = if (resolveOverlap) clockBounds else null
        )

    private val pinchDetector = ScaleGestureDetector(
        ctx,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cfg = getConfig()
                val clockStyle = ClockKitEngine.getStyle(cfg.styleId)
                val zoomed = clockStyle.onPinchZoom(cfg, detector.scaleFactor)
                onConfigChanged(clamp(zoomed, true))
                return true
            }
        }
    )

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        val params = sceneProvider?.invoke() ?: return
        LockscreenSceneRenderer.drawScene(context, canvas, width.toFloat(), params, clockBounds)

        if (isCardDragging && !clockBounds.isEmpty) {
            val scale = width / CaveArtXposedModule.screenW.coerceAtLeast(1f)
            val padX = dpf(12f) * scale
            val padY = dpf(8f) * scale
            if (isSnappedCenter) canvas.drawLine(width / 2f, 0f, width / 2f, height.toFloat(), guidePaint)
            canvas.drawRoundRect(
                clockBounds.left * scale - padX, clockBounds.top * scale - padY,
                clockBounds.right * scale + padX, clockBounds.bottom * scale + padY,
                dpf(16f), dpf(16f), dashPaint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        pinchDetector.onTouchEvent(event)
        if (pinchDetector.isInProgress) {
            isCardDragging = false
            return true
        }

        val previewScale = (width / CaveArtXposedModule.screenW.coerceAtLeast(1f)).coerceAtLeast(0.1f)
        val cfg = getConfig()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                rawClockX = cfg.clockX
                rawClockY = cfg.clockY
                rawDateX = cfg.dateX
                rawDateY = cfg.dateY
                isCardDragging = false

                val clockStyle = ClockKitEngine.getStyle(cfg.styleId)
                val extDate = clockStyle.datePolicy(cfg) == ClockDatePolicy.EXTERNAL_STANDARD && cfg.atGlanceEnabled
                val dateSp = LockscreenSceneRenderer.computeDateSizeSp(cfg)
                val snap = AtAGlanceDataCollector.collect(context, cfg)
                val glanceM = AtAGlanceRenderer.measure(cfg, snap, density, dateSp, CaveArtXposedModule.screenW)

                val scaledDateTop = (cfg.dateY * density) * previewScale
                val scaledDateBottom = ((cfg.dateY * density) + glanceM.heightPx) * previewScale
                val scaledDateCenterX = ((CaveArtXposedModule.screenW / 2f) + cfg.dateX * density) * previewScale
                val scaledDateHalfW = (glanceM.widthPx / 2f) * previewScale
                val hitPad = dpf(LockscreenLayoutSpec.DATE_HIT_PAD_DP) * previewScale

                val hitDate = extDate && !cfg.dateAttached &&
                    event.y in (scaledDateTop - hitPad)..(scaledDateBottom + hitPad) &&
                    event.x in (scaledDateCenterX - scaledDateHalfW - hitPad)..(scaledDateCenterX + scaledDateHalfW + hitPad)
                dragMode = if (hitDate) "DATE" else "CLOCK"
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) return true
                val dx = event.x - lastX
                val dy = event.y - lastY
                if (!isCardDragging && (abs(dx) > 3f || abs(dy) > 3f)) isCardDragging = true

                if (isCardDragging) {
                    val dxDp = (dx / previewScale) / density
                    val dyDp = (dy / previewScale) / density

                    if (dragMode == "DATE") {
                        rawDateX += dxDp
                        rawDateY += dyDp
                        val clamped = clamp(cfg.copy(dateX = rawDateX, dateY = rawDateY), resolveOverlap = false)
                        rawDateX = clamped.dateX
                        rawDateY = clamped.dateY
                        onConfigChanged(clamped)
                    } else {
                        rawClockX += dxDp
                        rawClockY += dyDp
                        val clamped = clamp(cfg.copy(clockX = rawClockX, clockY = rawClockY), resolveOverlap = false)
                        rawClockX = clamped.clockX
                        rawClockY = clamped.clockY
                        val snapThresholdDp = (CaveArtXposedModule.screenW * LockscreenLayoutSpec.MAGNETIC_SNAP_WIDTH_RATIO) / density
                        val snapped = abs(clamped.clockX) < snapThresholdDp
                        if (snapped && !isSnappedCenter) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isSnappedCenter = snapped
                        onConfigChanged(clamped)
                    }
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isCardDragging) {
                    val snapThresholdDp = (CaveArtXposedModule.screenW * LockscreenLayoutSpec.MAGNETIC_SNAP_WIDTH_RATIO) / density
                    val snapClockX = if (abs(cfg.clockX) < snapThresholdDp) 0f else cfg.clockX
                    val snapDateX = if (!cfg.dateAttached && abs(cfg.dateX) < snapThresholdDp) 0f else cfg.dateX
                    onConfigChanged(clamp(cfg.copy(clockX = snapClockX, dateX = snapDateX), resolveOverlap = true))
                } else if (!isClockFocused()) {
                    onTapClockFocus()
                }
                isCardDragging = false
                isSnappedCenter = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

internal class StyleCarouselPagerView(ctx: Context) : ViewGroup(ctx) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).roundToInt()
    private fun dpf(v: Float): Float = v * density

    private val styles = ClockKitEngine.styles
    private val cardViews = mutableListOf<View>()
    private val scroller = OverScroller(ctx, DecelerateInterpolator())
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop

    var sceneProviderForStyle: ((String) -> LockscreenSceneParams)? = null
    var onStyleSettled: ((ClockStyle) -> Unit)? = null
    var onStyleConfirmed: ((ClockStyle) -> Unit)? = null
    var onScrollProgress: ((Int, Float) -> Unit)? = null

    private var currentPage = 0
    private var cardWidth = 1
    private var cardHeight = 1
    private var pageStep = 1
    private var downX = 0f
    private var lastDragX = 0f
    private var isSwiping = false

    init {
        clipChildren = false
        clipToPadding = false
        styles.forEach { style ->
            val card = object : View(ctx) {
                init {
                    elevation = 0f
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(v: View, outline: Outline) {
                            outline.setRoundRect(0, 0, v.width, v.height, dpf(26f))
                        }
                    }
                    clipToOutline = true
                }

                override fun onDraw(canvas: Canvas) {
                    if (width <= 0 || height <= 0) return
                    val params = sceneProviderForStyle?.invoke(style.id) ?: return
                    LockscreenSceneRenderer.drawScene(context, canvas, width.toFloat(), params)
                }
            }
            cardViews.add(card)
            addView(card)
        }
    }

    fun setCurrentStyle(styleId: String) {
        val idx = styles.indexOfFirst { it.id == styleId }.coerceAtLeast(0)
        currentPage = idx
        scroller.abortAnimation()
        if (pageStep > 0) {
            scrollTo(idx * pageStep, 0)
            updateTransforms()
        }
        invalidateActiveCard()
    }

    fun smoothScrollToPage(page: Int) {
        val clamped = page.coerceIn(0, styles.size - 1)
        currentPage = clamped
        scroller.abortAnimation()
        scroller.startScroll(scrollX, 0, clamped * pageStep - scrollX, 0, 280)
        postInvalidateOnAnimation()
    }

    fun notifyAssetsLoaded() {
        cardViews.forEach { it.invalidate() }
    }

    fun invalidateActiveCard() {
        cardViews.getOrNull(currentPage)?.invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val aspect = (CaveArtXposedModule.screenW / CaveArtXposedModule.screenH.coerceAtLeast(1f)).coerceIn(0.35f, 0.75f)
        cardHeight = (h * 0.94f).roundToInt().coerceAtLeast(1)
        cardWidth = (cardHeight * aspect).roundToInt().coerceAtLeast(1)
        pageStep = cardWidth + dp(18f)
        val cw = MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY)
        val ch = MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY)
        cardViews.forEach { it.measure(cw, ch) }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val top = ((b - t) - cardHeight) / 2
        val startLeft = ((r - l) - cardWidth) / 2
        cardViews.forEachIndexed { i, child ->
            val left = startLeft + i * pageStep
            child.layout(left, top, left + cardWidth, top + cardHeight)
        }
        scrollTo((currentPage * pageStep).coerceIn(0, max(0, (styles.size - 1) * pageStep)), 0)
        updateTransforms()
    }

    private fun updateTransforms() {
        if (pageStep <= 0) return
        val exactPage = scrollX.toFloat() / pageStep.toFloat()
        cardViews.forEachIndexed { i, child ->
            val offset = abs(exactPage - i).coerceIn(0f, 1f)
            val s = 0.88f + (1f - offset) * 0.12f
            child.scaleX = s
            child.scaleY = s
            child.alpha = 0.45f + (1f - offset) * 0.55f
        }
        val nearest = exactPage.roundToInt().coerceIn(0, styles.size - 1)
        onScrollProgress?.invoke(nearest, exactPage - nearest)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, 0)
            updateTransforms()
            postInvalidateOnAnimation()
        } else if (!isSwiping && pageStep > 0) {
            val settled = (scrollX.toFloat() / pageStep).roundToInt().coerceIn(0, styles.size - 1)
            if (settled != currentPage) {
                currentPage = settled
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onStyleSettled?.invoke(styles[settled])
                invalidateActiveCard()
            }
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
                downX = ev.x
                lastDragX = ev.x
                isSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = lastDragX - ev.x
                if (!isSwiping && abs(ev.x - downX) > touchSlop) isSwiping = true
                if (isSwiping) {
                    scrollTo((scrollX + dx.roundToInt()).coerceIn(0, max(0, (styles.size - 1) * pageStep)), 0)
                    updateTransforms()
                }
                lastDragX = ev.x
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSwiping) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    var targetPage = (scrollX.toFloat() / pageStep).roundToInt()
                    if (abs(vx) > 650f) targetPage = if (vx < 0) (currentPage + 1) else (currentPage - 1)
                    targetPage = targetPage.coerceIn(0, styles.size - 1)
                    if (targetPage != currentPage) {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onStyleSettled?.invoke(styles[targetPage])
                    }
                    smoothScrollToPage(targetPage)
                } else {
                    val tapOffset = ev.x - width / 2f
                    if (abs(tapOffset) < cardWidth * 0.52f) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onStyleConfirmed?.invoke(styles[currentPage])
                    } else {
                        val next = if (tapOffset > 0) currentPage + 1 else currentPage - 1
                        if (next in styles.indices) {
                            onStyleSettled?.invoke(styles[next])
                            smoothScrollToPage(next)
                        }
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
                isSwiping = false
                return true
            }
        }
        return super.onTouchEvent(ev)
    }
}
