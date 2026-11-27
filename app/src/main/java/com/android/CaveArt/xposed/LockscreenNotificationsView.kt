package com.android.CaveArt.xposed

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.CaveArt.lockscreenkit.*
import kotlin.math.abs

class LockscreenNotificationsView(context: Context) : View(context) {

    var clockConfig: LockscreenConfig = LockscreenConfig()
    var groups: List<LockNotifAppGroup> = emptyList()
    var isDozingState: Boolean = false
    var dozeAmount: Float = 0f

    private var expandedGroupId: String? = null
    private var collapsingGroupId: String? = null
    private var expandProgress: Float = 0f
    private val expandHolder = FloatValueHolder(0f)
    private var expandSpring: SpringAnimation? = null

    private var peekGroupId: String? = null
    private var peekProgress: Float = 0f
    private val peekHolder = FloatValueHolder(0f)
    private var peekSpring: SpringAnimation? = null
    private var peekSwipeOffsetPx: Float = 0f
    private var swipingPeek: Boolean = false

    private var swipingGroupId: String? = null
    private var swipeOffsetPx: Float = 0f
    private val swipeHolder = FloatValueHolder(0f)
    private var swipeSpring: SpringAnimation? = null
    private var crossedDismissThreshold: Boolean = false

    private var dismissingGroupId: String? = null
    private var dismissCollapseScale: Float = 1f
    private val collapseHolder = FloatValueHolder(1f)
    private var collapseSpring: SpringAnimation? = null

    private var pressedTarget: NotificationHitTarget? = null
    private var pressScale: Float = 1f
    private val pressHolder = FloatValueHolder(1f)
    private var pressSpring: SpringAnimation? = null

    private var knownGroupIds: Set<String> = emptySet()
    private var newGroupIds: Set<String> = emptySet()
    private var entryProgress: Float = 1f
    private val entryHolder = FloatValueHolder(1f)
    private var entrySpring: SpringAnimation? = null

    private var lastHitLayout = NotificationHitLayout()
    private var lastExpansionDeltaPx = 0f
    private val screenPos = IntArray(2)

    fun containsScreenPoint(sx: Float, sy: Float): Boolean {
        if (visibility != View.VISIBLE || alpha < 0.1f || groups.isEmpty()) return false
        if (lastHitLayout.targets.any { it.bounds.contains(sx, sy) }) return true
        if (!lastHitLayout.totalBounds.isEmpty) {
            val pad = 6f * resources.displayMetrics.density
            return sx in (lastHitLayout.totalBounds.left - pad)..(lastHitLayout.totalBounds.right + pad) &&
                sy in (lastHitLayout.totalBounds.top - pad)..(lastHitLayout.totalBounds.bottom + pad)
        }
        return false
    }
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null

    private var downScreenX = 0f
    private var downScreenY = 0f
    private var downTarget: NotificationHitTarget? = null
    private var isTrackingHorizontal = false
    private var isTrackingTouch = false

    private var cachedWallpaperThumb: Bitmap? = null
    private var cachedWallpaperColors: WallpaperColors? = null
    private var colorsChangedListener: Any? = null

    private val progressWatcherHandler = Handler(Looper.getMainLooper())
    private var progressWatcherActive = false
    private val progressWatcherRunnable = object : Runnable {
        override fun run() {
            if (!isAttachedToWindow || visibility != View.VISIBLE || isDozingState || dozeAmount >= 0.5f ||
                !CaveArtXposedModule.isKeyguardActive(context) ||
                groups.none { g -> g.items.any { it.hasProgress } }
            ) {
                progressWatcherActive = false
                return
            }
            CaveArtNotifSystemBridge.scheduleRefresh(context, immediate = false)
            progressWatcherHandler.postDelayed(this, 450L)
        }
    }

    private fun syncProgressWatcher() {
        val shouldWatch = isAttachedToWindow &&
            visibility == View.VISIBLE &&
            !isDozingState &&
            dozeAmount < 0.5f &&
            CaveArtXposedModule.isKeyguardActive(context) &&
            groups.any { g -> g.items.any { it.hasProgress } }
        if (shouldWatch && !progressWatcherActive) {
            progressWatcherActive = true
            progressWatcherHandler.postDelayed(progressWatcherRunnable, 450L)
        } else if (!shouldWatch && progressWatcherActive) {
            progressWatcherActive = false
            progressWatcherHandler.removeCallbacks(progressWatcherRunnable)
        }
    }

    private val notifListener: (List<LockNotifAppGroup>) -> Unit = { updated ->
        val incomingIds = updated.map { it.groupId }.toSet()
        val freshlyAdded = if (knownGroupIds.isNotEmpty()) incomingIds - knownGroupIds else emptySet()
        knownGroupIds = incomingIds
        groups = updated

        if (freshlyAdded.isNotEmpty() && visibility == View.VISIBLE) {
            newGroupIds = freshlyAdded
            triggerEntrySpring()
        } else {
            newGroupIds = emptySet()
            entryProgress = 1f
        }

        if (expandedGroupId != null && updated.none { it.groupId == expandedGroupId }) {
            expandedGroupId = null
            collapsingGroupId = null
            expandProgress = 0f
        }
        if (peekGroupId != null && updated.none { it.groupId == peekGroupId }) {
            peekGroupId = null
            peekProgress = 0f
            peekSwipeOffsetPx = 0f
        }
        if (dismissingGroupId != null && updated.none { it.groupId == dismissingGroupId }) {
            dismissingGroupId = null
            dismissCollapseScale = 1f
        }
        updateVisibilityState()
        invalidate()
    }

    init {
        isClickable = false
        isFocusable = false
    }

    fun updateConfig(cfg: LockscreenConfig) {
        clockConfig = cfg
        if (NotificationStyle.fromId(cfg.notificationStyle) != NotificationStyle.ICONS_ONLY) {
            peekGroupId = null
            peekProgress = 0f
        }
        updateVisibilityState()
        invalidate()
    }

    fun updateVisibilityState() {
        val style = NotificationStyle.fromId(clockConfig.notificationStyle)
        val shouldShow = !CaveArtXposedModule.isEditModeActive &&
            !CaveArtXposedModule.isEditorDismissing &&
            !CaveArtXposedModule.isBouncerShowing &&
            !CaveArtXposedModule.isQsExpanded &&
            CaveArtXposedModule.isKeyguardActive(context) &&
            style != NotificationStyle.STOCK &&
            style != NotificationStyle.HIDDEN &&
            groups.isNotEmpty()
        if (shouldShow && alpha < 1f) alpha = 1f
        visibility = if (shouldShow) View.VISIBLE else View.GONE
        if (!shouldShow && lastExpansionDeltaPx != 0f) {
            lastExpansionDeltaPx = 0f
            CaveArtXposedModule.updateMediaCarouselPushForExpansion(0f)
        }
        syncProgressWatcher()
    }

    private fun decodeSampledFd(fd: java.io.FileDescriptor, maxDim: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFileDescriptor(fd, null, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
        var sample = 1
        while ((opts.outWidth / sample) > maxDim && (opts.outHeight / sample) > maxDim) sample *= 2
        opts.inSampleSize = sample
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFileDescriptor(fd, null, opts)
    }

    fun reloadWallpaperAppearanceAsync() {
        Thread {
            var thumb: Bitmap? = null
            var wmColors: WallpaperColors? = null
            try {
                val wm = WallpaperManager.getInstance(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    wmColors = wm.getWallpaperColors(WallpaperManager.FLAG_LOCK)
                        ?: wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                }
                val isCaveArtLive = wm.wallpaperInfo?.packageName == "com.android.CaveArt"
                val isOtherLive = wm.wallpaperInfo != null && !isCaveArtLive
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val lockFd = try { wm.getWallpaperFile(WallpaperManager.FLAG_LOCK) } catch (_: Throwable) { null }
                    if (lockFd != null) {
                        lockFd.use { thumb = decodeSampledFd(it.fileDescriptor, 384) }
                    } else if (!isOtherLive && !isCaveArtLive) {
                        val sysFd = try { wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM) } catch (_: Throwable) { null }
                        sysFd?.use { thumb = decodeSampledFd(it.fileDescriptor, 384) }
                    }
                }
                if (thumb == null && !isOtherLive) {
                    try {
                        context.contentResolver.openFileDescriptor(
                            Uri.parse("content://com.android.CaveArt.settings/lock_wallpaper"), "r"
                        )?.use { pfd -> thumb = decodeSampledFd(pfd.fileDescriptor, 384) }
                    } catch (_: Exception) {}
                }
            } catch (_: Throwable) {}

            post {
                if (!isAttachedToWindow) {
                    thumb?.takeIf { !it.isRecycled }?.recycle()
                    return@post
                }
                val old = cachedWallpaperThumb
                cachedWallpaperThumb = thumb
                cachedWallpaperColors = wmColors
                if (old != null && old !== thumb && !old.isRecycled) old.recycle()
                invalidate()
            }
        }.start()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        CaveArtNotifSystemBridge.addListener(notifListener)
        groups = CaveArtNotifSystemBridge.activeGroups
        CaveArtNotifSystemBridge.scheduleRefresh(context)
        reloadWallpaperAppearanceAsync()
        updateVisibilityState()
        syncProgressWatcher()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && colorsChangedListener == null) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val listener = WallpaperManager.OnColorsChangedListener { _, which ->
                    if ((which and (WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM)) != 0) {
                        reloadWallpaperAppearanceAsync()
                    }
                }
                colorsChangedListener = listener
                wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
            } catch (_: Throwable) {}
        }
    }

    override fun onDetachedFromWindow() {
        progressWatcherActive = false
        progressWatcherHandler.removeCallbacks(progressWatcherRunnable)
        CaveArtNotifSystemBridge.removeListener(notifListener)
        swipeSpring?.cancel()
        expandSpring?.cancel()
        peekSpring?.cancel()
        collapseSpring?.cancel()
        pressSpring?.cancel()
        entrySpring?.cancel()
        velocityTracker?.recycle()
        velocityTracker = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            (colorsChangedListener as? WallpaperManager.OnColorsChangedListener)?.let { listener ->
                try {
                    WallpaperManager.getInstance(context).removeOnColorsChangedListener(listener)
                } catch (_: Throwable) {}
            }
        }
        colorsChangedListener = null
        if (lastExpansionDeltaPx != 0f) {
            lastExpansionDeltaPx = 0f
            CaveArtXposedModule.updateMediaCarouselPushForExpansion(0f)
        }
        val oldThumb = cachedWallpaperThumb
        cachedWallpaperThumb = null
        oldThumb?.takeIf { !it.isRecycled }?.recycle()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            CaveArtXposedModule.screenW.toInt().coerceAtLeast(1),
            CaveArtXposedModule.screenH.toInt().coerceAtLeast(1)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val style = NotificationStyle.fromId(clockConfig.notificationStyle)
        if (groups.isEmpty() || style == NotificationStyle.STOCK || style == NotificationStyle.HIDDEN) {
            lastHitLayout = NotificationHitLayout()
            return
        }
        if (CaveArtXposedModule.isEditModeActive ||
            CaveArtXposedModule.isEditorDismissing ||
            CaveArtXposedModule.isBouncerShowing ||
            CaveArtXposedModule.isQsExpanded ||
            !CaveArtXposedModule.isKeyguardActive(context)
        ) {
            lastHitLayout = NotificationHitLayout()
            return
        }

        val density = resources.displayMetrics.density
        val sw = CaveArtXposedModule.screenW
        val sh = CaveArtXposedModule.screenH
        val zoom = CaveArtXposedModule.activeClock?.telemetryZoom ?: LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM

        val anchorBottom = CaveArtXposedModule.computeCurrentHeaderBottomPx(context)
        val maxBottom = LockscreenSceneRenderer.computeNotificationsMaxBottomPx(
            screenH = sh,
            density = density,
            udfpsY = CaveArtXposedModule.cachedUdfpsY,
            udfpsRadius = CaveArtXposedModule.cachedUdfpsRadius
        )
        val activeExpanded = expandedGroupId ?: collapsingGroupId
        val notifH = NotificationKitRenderer.measureTotalHeightPx(
            groups = groups,
            config = clockConfig,
            density = density,
            expandedGroupId = activeExpanded,
            expandProgress = expandProgress,
            dismissingGroupId = dismissingGroupId,
            dismissCollapseScale = dismissCollapseScale,
            screenW = sw
        )
        val collapsedNotifH = if (activeExpanded != null) {
            NotificationKitRenderer.measureTotalHeightPx(
                groups = groups,
                config = clockConfig,
                density = density,
                dismissingGroupId = dismissingGroupId,
                dismissCollapseScale = dismissCollapseScale,
                screenW = sw
            )
        } else notifH
        val expansionDeltaPx = kotlin.math.max(0f, notifH - collapsedNotifH)
        if (abs(expansionDeltaPx - lastExpansionDeltaPx) > 1f) {
            lastExpansionDeltaPx = expansionDeltaPx
            CaveArtXposedModule.updateMediaCarouselPushForExpansion(expansionDeltaPx)
        }

        val (clockTopPx, dateBottomPx) = CaveArtXposedModule.computeCurrentHeaderBoundsPx(context)
        val dateTopPx = CaveArtXposedModule.computeCurrentDateTopPx(context)
        val topY = LockscreenSceneRenderer.computeNotificationsTopPx(
            anchorBottomPx = anchorBottom,
            notifHeightPx = notifH,
            maxBottomPx = maxBottom,
            density = density,
            isCollapsingStyle = style.collapsesClockWhenNotifsPresent,
            isIconsOnly = style == NotificationStyle.ICONS_ONLY,
            clockTopPx = clockTopPx,
            dateBottomPx = dateBottomPx,
            dateTopPx = dateTopPx
        )

        getLocationOnScreen(screenPos)
        canvas.save()
        canvas.translate(-screenPos[0].toFloat(), -screenPos[1].toFloat())

        lastHitLayout = NotificationKitRenderer.draw(
            canvas = canvas,
            groups = groups,
            config = clockConfig,
            screenW = sw,
            screenH = sh,
            topY = topY,
            maxBottomY = maxBottom,
            density = density,
            nowMillis = System.currentTimeMillis(),
            wallpaperBitmap = cachedWallpaperThumb,
            wallpaperZoom = zoom,
            fallbackColors = cachedWallpaperColors,
            expandedGroupId = expandedGroupId,
            collapsingGroupId = collapsingGroupId,
            expandProgress = expandProgress,
            swipingGroupId = swipingGroupId,
            swipeOffsetPx = swipeOffsetPx,
            isDozing = isDozingState,
            dismissingGroupId = dismissingGroupId,
            dismissCollapseScale = dismissCollapseScale,
            peekGroupId = peekGroupId,
            peekProgress = peekProgress,
            peekSwipeOffsetPx = peekSwipeOffsetPx,
            dozeAmount = dozeAmount,
            pressedTarget = pressedTarget,
            pressScale = pressScale,
            newGroupIds = newGroupIds,
            entryProgress = entryProgress,
            isNightMode = NotificationKitRenderer.isSystemNightMode(context),
            clockAvoidRect = CaveArtXposedModule.activeClock?.lastRenderedBounds
        )

        val progressVisible = !isDozingState && dozeAmount < 0.98f &&
            (style == NotificationStyle.GROUPED_CARDS || (style == NotificationStyle.ICONS_ONLY && peekGroupId != null))
        if (progressVisible && groups.any { g -> g.items.any { it.hasProgress && it.progressIndeterminate } }) {
            postInvalidateOnAnimation()
        }

        canvas.restore()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val style = NotificationStyle.fromId(clockConfig.notificationStyle)
        if (visibility != View.VISIBLE || alpha < 0.1f || groups.isEmpty() ||
            style == NotificationStyle.STOCK || style == NotificationStyle.HIDDEN ||
            isDozingState || dozeAmount > 0.5f || CaveArtXposedModule.isBouncerShowing ||
            CaveArtXposedModule.isQsExpanded || !CaveArtXposedModule.isKeyguardActive(context)
        ) {
            return false
        }

        getLocationOnScreen(screenPos)
        val sx = ev.x + screenPos[0].toFloat()
        val sy = ev.y + screenPos[1].toFloat()

        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain()
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = findHitTarget(sx, sy)
                if (hit == null) {
                    if (peekGroupId != null) animatePeek(null)
                    return false
                }
                swipeSpring?.cancel()
                downScreenX = sx
                downScreenY = sy
                downTarget = hit
                pressedTarget = hit
                isTrackingTouch = true
                isTrackingHorizontal = false
                swipingPeek = false
                crossedDismissThreshold = false
                animatePress(0.972f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isTrackingTouch) return false
                val dx = sx - downScreenX
                val dy = sy - downScreenY

                if (!isTrackingHorizontal) {
                    if (abs(dy) > touchSlop && abs(dy) > abs(dx) * 1.15f) {
                        clearPress()
                        isTrackingTouch = false
                        downTarget = null
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    val tgt = downTarget
                    val group = tgt?.groupId?.let { gid -> groups.firstOrNull { it.groupId == gid } }
                    val canSwipeGroup = style == NotificationStyle.GROUPED_CARDS && group != null
                    val canSwipePeek = tgt?.kind == NotificationHitKind.PEEK_CARD && group != null
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) && (canSwipeGroup || canSwipePeek)) {
                        isTrackingHorizontal = true
                        clearPress()
                        if (canSwipePeek) {
                            swipingPeek = true
                        } else if (group != null) {
                            swipingGroupId = group.groupId
                        }
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isTrackingHorizontal) {
                    val gid = if (swipingPeek) peekGroupId else swipingGroupId
                    val group = groups.firstOrNull { it.groupId == gid }
                    if (group?.isClearable == true) {
                        if (swipingPeek) peekSwipeOffsetPx = dx else swipeOffsetPx = dx
                        val crossedNow = abs(dx) >= width * 0.28f
                        if (crossedNow != crossedDismissThreshold) {
                            crossedDismissThreshold = crossedNow
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } else {
                        val maxRubber = 54f * resources.displayMetrics.density
                        val damped = (dx * 0.22f).coerceIn(-maxRubber, maxRubber)
                        if (swipingPeek) peekSwipeOffsetPx = damped else swipeOffsetPx = damped
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!isTrackingTouch) return false
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.computeCurrentVelocity(1000)
                val vx = velocityTracker?.xVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val target = downTarget
                isTrackingTouch = false
                downTarget = null
                clearPress()

                if (isTrackingHorizontal) {
                    isTrackingHorizontal = false
                    val threshold = width * 0.28f
                    if (swipingPeek) {
                        val group = groups.firstOrNull { it.groupId == peekGroupId }
                        val flingDismiss = abs(vx) > 1400f && ((vx > 0f && peekSwipeOffsetPx > 0f) || (vx < 0f && peekSwipeOffsetPx < 0f))
                        if (group != null && group.isClearable && (abs(peekSwipeOffsetPx) > threshold || flingDismiss)) {
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val endX = if (peekSwipeOffsetPx >= 0f) width.toFloat() else -width.toFloat()
                            springSwipe(peekSwipeOffsetPx, endX, vx, isPeek = true) {
                                swipingPeek = false
                                peekSwipeOffsetPx = 0f
                                peekGroupId = null
                                peekProgress = 0f
                                CaveArtNotifSystemBridge.dismissGroup(context, group)
                            }
                        } else {
                            springSwipe(peekSwipeOffsetPx, 0f, vx, isPeek = true) {
                                swipingPeek = false
                                peekSwipeOffsetPx = 0f
                            }
                        }
                    } else {
                        val dismissingId = swipingGroupId
                        val group = groups.firstOrNull { it.groupId == dismissingId }
                        val flingDismiss = abs(vx) > 1400f && ((vx > 0f && swipeOffsetPx > 0f) || (vx < 0f && swipeOffsetPx < 0f))
                        if (group != null && group.isClearable && (abs(swipeOffsetPx) > threshold || flingDismiss)) {
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val endX = if (swipeOffsetPx >= 0f) width.toFloat() else -width.toFloat()
                            springSwipe(swipeOffsetPx, endX, vx, isPeek = false) {
                                swipingGroupId = null
                                swipeOffsetPx = 0f
                                animateDismissCollapse(group)
                            }
                        } else {
                            springSwipe(swipeOffsetPx, 0f, vx, isPeek = false) {
                                swipingGroupId = null
                                swipeOffsetPx = 0f
                            }
                        }
                    }
                } else if (target != null) {
                    handleTap(target)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker?.recycle()
                velocityTracker = null
                isTrackingTouch = false
                downTarget = null
                clearPress()
                if (isTrackingHorizontal) {
                    isTrackingHorizontal = false
                    if (swipingPeek) {
                        springSwipe(peekSwipeOffsetPx, 0f, 0f, isPeek = true) {
                            swipingPeek = false
                            peekSwipeOffsetPx = 0f
                        }
                    } else {
                        springSwipe(swipeOffsetPx, 0f, 0f, isPeek = false) {
                            swipingGroupId = null
                            swipeOffsetPx = 0f
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    private fun findHitTarget(sx: Float, sy: Float): NotificationHitTarget? {
        return lastHitLayout.targets.firstOrNull { it.bounds.contains(sx, sy) }
    }

    private fun clearPress() {
        pressedTarget = null
        animatePress(1f)
    }

    private fun animatePress(target: Float) {
        pressSpring?.cancel()
        pressHolder.value = pressScale
        pressSpring = SpringAnimation(pressHolder).apply {
            spring = SpringForce(target).apply {
                stiffness = 650f
                dampingRatio = 0.72f
            }
            addUpdateListener { _, value, _ ->
                pressScale = value
                invalidate()
            }
            start()
        }
    }

    private fun triggerEntrySpring() {
        entrySpring?.cancel()
        entryProgress = 0f
        entryHolder.value = 0f
        entrySpring = SpringAnimation(entryHolder).apply {
            spring = SpringForce(1f).apply {
                stiffness = 380f
                dampingRatio = 0.84f
            }
            addUpdateListener { _, value, _ ->
                entryProgress = value.coerceIn(0f, 1.05f)
                invalidate()
            }
            addEndListener { _, _, _, _ ->
                newGroupIds = emptySet()
                entryProgress = 1f
                invalidate()
            }
            start()
        }
    }

    private fun handleTap(target: NotificationHitTarget) {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        when (target.kind) {
            NotificationHitKind.ICON_LAUNCH -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                CaveArtNotifSystemBridge.launchPendingIntent(context, group.primaryIntent)
            }
            NotificationHitKind.ICON_PEEK -> {
                val gid = target.groupId ?: return
                animatePeek(if (peekGroupId == gid) null else gid)
            }
            NotificationHitKind.PEEK_CARD -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                animatePeek(null)
                CaveArtNotifSystemBridge.launchPendingIntent(context, group.primaryIntent)
            }
            NotificationHitKind.ACTION_PILL -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                val intent = group.items.getOrNull(target.messageIndex)?.actions?.getOrNull(target.actionIndex)?.intent
                    ?: group.primaryIntent
                CaveArtNotifSystemBridge.launchPendingIntent(context, intent)
            }
            NotificationHitKind.CLEAR_ALL -> {
                CaveArtNotifSystemBridge.dismissAllClearable(context)
            }
            NotificationHitKind.ICONS_CONTAINER -> {
                CaveArtNotifSystemBridge.expandNotificationsShade()
            }
            NotificationHitKind.MESSAGE_ROW -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                val intent = group.items.getOrNull(target.messageIndex)?.contentIntent ?: group.primaryIntent
                CaveArtNotifSystemBridge.launchPendingIntent(context, intent)
            }
            NotificationHitKind.GROUP_CARD -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                if (NotificationStyle.fromId(clockConfig.notificationStyle) == NotificationStyle.GROUPED_CARDS && group.canExpand) {
                    toggleGroupExpansion(group.groupId)
                } else {
                    CaveArtNotifSystemBridge.launchPendingIntent(context, group.primaryIntent)
                }
            }
        }
    }

    private fun toggleGroupExpansion(groupId: String) {
        expandSpring?.cancel()
        if (expandedGroupId == groupId) {
            collapsingGroupId = groupId
            expandedGroupId = null
            expandHolder.value = expandProgress * 100f
            expandSpring = SpringAnimation(expandHolder).apply {
                spring = SpringForce(0f).apply {
                    stiffness = 460f
                    dampingRatio = 0.82f
                }
                addUpdateListener { _, value, _ ->
                    expandProgress = (value / 100f).coerceIn(0f, 1f)
                    invalidate()
                }
                addEndListener { _, _, _, _ ->
                    collapsingGroupId = null
                    expandProgress = 0f
                    invalidate()
                }
                start()
            }
        } else {
            collapsingGroupId = null
            expandedGroupId = groupId
            val startP = if (expandProgress > 0.01f) 0f else expandProgress
            expandProgress = startP
            expandHolder.value = startP * 100f
            expandSpring = SpringAnimation(expandHolder).apply {
                spring = SpringForce(100f).apply {
                    stiffness = 380f
                    dampingRatio = 0.76f
                }
                addUpdateListener { _, value, _ ->
                    expandProgress = (value / 100f).coerceIn(0f, 1.04f)
                    invalidate()
                }
                start()
            }
        }
    }

    private fun animatePeek(targetGroupId: String?) {
        peekSpring?.cancel()
        if (targetGroupId == null) {
            if (peekGroupId == null) return
            peekHolder.value = peekProgress * 100f
            peekSpring = SpringAnimation(peekHolder).apply {
                spring = SpringForce(0f).apply {
                    stiffness = 520f
                    dampingRatio = 0.85f
                }
                addUpdateListener { _, value, _ ->
                    peekProgress = (value / 100f).coerceIn(0f, 1f)
                    invalidate()
                }
                addEndListener { _, _, _, _ ->
                    peekGroupId = null
                    peekProgress = 0f
                    peekSwipeOffsetPx = 0f
                    invalidate()
                }
                start()
            }
        } else {
            peekGroupId = targetGroupId
            peekSwipeOffsetPx = 0f
            peekHolder.value = 0f
            peekSpring = SpringAnimation(peekHolder).apply {
                spring = SpringForce(100f).apply {
                    stiffness = 420f
                    dampingRatio = 0.74f
                }
                addUpdateListener { _, value, _ ->
                    peekProgress = (value / 100f).coerceIn(0f, 1.04f)
                    invalidate()
                }
                start()
            }
        }
    }

    private fun animateDismissCollapse(group: LockNotifAppGroup) {
        collapseSpring?.cancel()
        dismissingGroupId = group.groupId
        dismissCollapseScale = 1f
        collapseHolder.value = 100f
        collapseSpring = SpringAnimation(collapseHolder).apply {
            spring = SpringForce(0f).apply {
                stiffness = 520f
                dampingRatio = 0.90f
            }
            addUpdateListener { _, value, _ ->
                dismissCollapseScale = (value / 100f).coerceIn(0f, 1f)
                invalidate()
            }
            addEndListener { _, _, _, _ ->
                dismissingGroupId = null
                dismissCollapseScale = 1f
                CaveArtNotifSystemBridge.dismissGroup(context, group)
            }
            start()
        }
    }

    private fun springSwipe(from: Float, to: Float, velocityPx: Float, isPeek: Boolean, onEnd: () -> Unit) {
        swipeSpring?.cancel()
        swipeHolder.value = from
        swipeSpring = SpringAnimation(swipeHolder).apply {
            setStartVelocity(velocityPx)
            spring = SpringForce(to).apply {
                stiffness = if (to == 0f) 460f else 620f
                dampingRatio = if (to == 0f) 0.76f else 0.92f
            }
            addUpdateListener { _, value, _ ->
                if (isPeek) peekSwipeOffsetPx = value else swipeOffsetPx = value
                invalidate()
            }
            addEndListener { _, _, _, _ ->
                onEnd()
                invalidate()
            }
            start()
        }
    }
}
