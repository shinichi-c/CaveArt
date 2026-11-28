package com.android.CaveArt.xposed

import android.app.Notification
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.android.CaveArt.lockscreenkit.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class LockscreenNotificationsView(context: Context) : FrameLayout(context) {

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
    private val scrollPos = IntArray(2)
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
    private fun hasLiveOngoingContent(): Boolean {
        return groups.any { g ->
            g.items.any { it.hasProgress || it.usesChronometer || !it.isClearable }
        }
    }

    private val progressWatcherRunnable = object : Runnable {
        override fun run() {
            val hasLive = hasLiveOngoingContent()
            if (!isAttachedToWindow || visibility != View.VISIBLE || isDozingState || dozeAmount >= 0.5f ||
                !CaveArtXposedModule.isKeyguardActive(context) || !hasLive
            ) {
                progressWatcherActive = false
                return
            }
            CaveArtNotifSystemBridge.scheduleRefresh(context, immediate = false)
            if (!useRemoteCardsMode()) invalidate()
            progressWatcherHandler.postDelayed(this, 500L)
        }
    }

    private val density = resources.displayMetrics.density
    private val sidePadPx = (NotificationThemeSpec.CARD_SIDE_PADDING_DP * density).roundToInt()
    private val cardGapPx = (NotificationThemeSpec.CARD_GAP_DP * density).roundToInt()
    private var maxScrollHeightPx = (600f * density).roundToInt()

    private val cardsScrollView = object : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedH = MeasureSpec.makeMeasureSpec(maxScrollHeightPx.coerceAtLeast(1), MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, cappedH)
        }
    }.apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        clipToPadding = false
        visibility = View.GONE
    }

    private val cardsColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private val footerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    private val overflowIconsPill = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((12f * density).roundToInt(), 0, (12f * density).roundToInt(), 0)
        visibility = View.GONE
    }

    private val clearAllFooterPill = TextView(context).apply {
        text = "Clear all"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setPadding((16f * density).roundToInt(), 0, (16f * density).roundToInt(), 0)
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            CaveArtNotifSystemBridge.dismissAllClearable(context)
        }
    }

    private val cardViewPool = LinkedHashMap<String, CaveArtRemoteCardView>()
    private val remoteInteractionHandler by lazy {
        CaveArtNotifSystemBridge.createRemoteViewsInteractionHandler(context) {
            activeInteractionSbn
        }
    }
    @Volatile
    private var activeInteractionSbn: StatusBarNotification? = null

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
        syncRemoteCardsContainer()
        invalidate()
    }

    init {
        setWillNotDraw(false)
        isClickable = false
        isFocusable = false
        clipChildren = false
        clipToPadding = false

        cardsScrollView.addView(cardsColumn, LayoutParams(-1, -2))
        addView(
            cardsScrollView,
            LayoutParams(-1, -2).apply {
                leftMargin = sidePadPx
                rightMargin = sidePadPx
            }
        )
    }

    private fun useRemoteCardsMode(): Boolean {
        val style = NotificationStyle.fromId(clockConfig.notificationStyle)
        return style == NotificationStyle.GROUPED_CARDS &&
            !isDozingState &&
            dozeAmount < 0.5f &&
            !CaveArtXposedModule.isEditModeActive &&
            !CaveArtXposedModule.isEditorDismissing &&
            !CaveArtXposedModule.isBouncerShowing &&
            !CaveArtXposedModule.isQsExpanded &&
            CaveArtXposedModule.isKeyguardActive(context) &&
            groups.isNotEmpty()
    }

    fun containsScreenPoint(sx: Float, sy: Float): Boolean {
        if (visibility != View.VISIBLE || alpha < 0.1f || groups.isEmpty()) return false
        if (useRemoteCardsMode() && cardsScrollView.visibility == View.VISIBLE && cardsScrollView.height > 0) {
            cardsScrollView.getLocationOnScreen(scrollPos)
            val pad = 6f * density
            return sx in (scrollPos[0] - pad)..(scrollPos[0] + cardsScrollView.width + pad) &&
                sy in (scrollPos[1] - pad)..(scrollPos[1] + cardsScrollView.height + pad)
        }
        if (lastHitLayout.targets.any { it.bounds.contains(sx, sy) }) return true
        if (!lastHitLayout.totalBounds.isEmpty) {
            val pad = 6f * density
            return sx in (lastHitLayout.totalBounds.left - pad)..(lastHitLayout.totalBounds.right + pad) &&
                sy in (lastHitLayout.totalBounds.top - pad)..(lastHitLayout.totalBounds.bottom + pad)
        }
        return false
    }

    fun updateConfig(cfg: LockscreenConfig) {
        clockConfig = cfg
        if (NotificationStyle.fromId(cfg.notificationStyle) != NotificationStyle.ICONS_ONLY) {
            peekGroupId = null
            peekProgress = 0f
        }
        updateVisibilityState()
        syncRemoteCardsContainer()
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
        if (!useRemoteCardsMode() && cardsScrollView.visibility != View.GONE) {
            cardsScrollView.visibility = View.GONE
        }
        syncProgressWatcher()
    }

    private fun syncProgressWatcher() {
        val hasLive = hasLiveOngoingContent()
        val shouldWatch = isAttachedToWindow &&
            visibility == View.VISIBLE &&
            !isDozingState &&
            dozeAmount < 0.5f &&
            CaveArtXposedModule.isKeyguardActive(context) &&
            hasLive
        if (shouldWatch && !progressWatcherActive) {
            progressWatcherActive = true
            progressWatcherHandler.postDelayed(progressWatcherRunnable, 500L)
        } else if (!shouldWatch && progressWatcherActive) {
            progressWatcherActive = false
            progressWatcherHandler.removeCallbacks(progressWatcherRunnable)
        }
    }

    private fun syncRemoteCardsContainer() {
        if (!useRemoteCardsMode()) {
            if (cardsScrollView.visibility != View.GONE) {
                cardsScrollView.visibility = View.GONE
            }
            return
        }

        if (cardsScrollView.visibility != View.VISIBLE) {
            cardsScrollView.visibility = View.VISIBLE
        }

        val activeIds = groups.map { it.groupId }.toSet()
        val staleKeys = cardViewPool.keys.filter { it !in activeIds }
        staleKeys.forEach { cardViewPool.remove(it) }

        val maxCards = clockConfig.notificationMaxCards.coerceIn(1, 5)
        val activeExpandedGroup = expandedGroupId?.let { id -> groups.firstOrNull { it.groupId == id } }
        val visibleGroups: List<LockNotifAppGroup>
        val overflowGroups: List<LockNotifAppGroup>
        if (activeExpandedGroup != null) {
            visibleGroups = listOf(activeExpandedGroup)
            overflowGroups = groups.filter { it.groupId != activeExpandedGroup.groupId }
        } else {
            visibleGroups = groups.take(maxCards)
            overflowGroups = groups.drop(maxCards)
        }

        cardsColumn.removeAllViews()
        visibleGroups.forEachIndexed { idx, group ->
            val card = cardViewPool.getOrPut(group.groupId) {
                CaveArtRemoteCardView(context)
            }
            (card.parent as? ViewGroup)?.removeView(card)
            val isExpanded = group.groupId == expandedGroupId
            card.bind(group, isExpanded)
            val lp = LinearLayout.LayoutParams(-1, -2).apply {
                if (idx < visibleGroups.lastIndex) bottomMargin = cardGapPx
            }
            cardsColumn.addView(card, lp)
        }

        val hasClearable = groups.any { it.isClearable }
        if (overflowGroups.isNotEmpty() || hasClearable) {
            val isNight = NotificationKitRenderer.isSystemNightMode(context)
            val (primInk, _) = NotificationKitRenderer.resolveSurfaceTextColors(
                config = clockConfig,
                screenW = CaveArtXposedModule.screenW,
                screenH = CaveArtXposedModule.screenH,
                wallpaperBitmap = cachedWallpaperThumb,
                wallpaperZoom = CaveArtXposedModule.activeClock?.telemetryZoom ?: LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM,
                fallbackColors = cachedWallpaperColors,
                isNightMode = isNight
            )
            val pillBg = {
                GradientDrawable().apply {
                    setColor(if (isNight) Color.argb(128, 22, 24, 30) else Color.argb(190, 244, 246, 250))
                    cornerRadius = 17f * density
                    setStroke((0.8f * density).roundToInt().coerceAtLeast(1), if (isNight) Color.argb(55, 255, 255, 255) else Color.argb(40, 20, 22, 26))
                }
            }

            footerRow.removeAllViews()

            if (overflowGroups.isNotEmpty()) {
                overflowIconsPill.removeAllViews()
                overflowIconsPill.background = pillBg()
                val iconSz = (18f * density).roundToInt()
                val shownOverflow = overflowGroups.take(4)
                shownOverflow.forEachIndexed { i, ov ->
                    val iv = ImageView(context).apply {
                        val bmp = ov.smallIcon ?: ov.appIcon
                        if (bmp != null && !bmp.isRecycled) {
                            setImageBitmap(bmp)
                            if (ov.smallIcon != null) setColorFilter(primInk, PorterDuff.Mode.SRC_IN)
                        }
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        setOnClickListener {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (ov.count > 1 || (ov.items.firstOrNull()?.isExpandableSingle == true)) {
                                expandedGroupId = ov.groupId
                                syncRemoteCardsContainer()
                            } else {
                                CaveArtNotifSystemBridge.launchNotificationByKey(
                                    context,
                                    ov.items.firstOrNull()?.key,
                                    ov.primaryIntent
                                )
                            }
                        }
                    }
                    overflowIconsPill.addView(
                        iv,
                        LinearLayout.LayoutParams(iconSz, iconSz).apply {
                            if (i < shownOverflow.lastIndex || overflowGroups.size > shownOverflow.size) {
                                marginEnd = (10f * density).roundToInt()
                            }
                        }
                    )
                }
                if (overflowGroups.size > shownOverflow.size) {
                    val moreTxt = TextView(context).apply {
                        text = "+${overflowGroups.size - shownOverflow.size}"
                        setTextColor(primInk)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                        setOnClickListener {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (expandedGroupId != null) {
                                expandedGroupId = null
                                syncRemoteCardsContainer()
                            } else {
                                CaveArtNotifSystemBridge.expandNotificationsShade()
                            }
                        }
                    }
                    overflowIconsPill.addView(moreTxt, LinearLayout.LayoutParams(-2, -2))
                }
                overflowIconsPill.visibility = View.VISIBLE
                footerRow.addView(
                    overflowIconsPill,
                    LinearLayout.LayoutParams(-2, (34f * density).roundToInt()).apply {
                        if (hasClearable) marginEnd = (8f * density).roundToInt()
                    }
                )
            } else {
                overflowIconsPill.visibility = View.GONE
            }

            if (hasClearable) {
                clearAllFooterPill.setTextColor(primInk)
                clearAllFooterPill.background = pillBg()
                (clearAllFooterPill.parent as? ViewGroup)?.removeView(clearAllFooterPill)
                footerRow.addView(
                    clearAllFooterPill,
                    LinearLayout.LayoutParams(-2, (34f * density).roundToInt())
                )
            }

            (footerRow.parent as? ViewGroup)?.removeView(footerRow)
            cardsColumn.addView(
                footerRow,
                LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = (10f * density).roundToInt()
                }
            )
        }

        updateCardsContainerBounds()
    }

    private fun updateCardsContainerBounds() {
        if (cardsScrollView.visibility != View.VISIBLE) return
        val sw = CaveArtXposedModule.screenW
        val sh = CaveArtXposedModule.screenH
        val anchorBottom = CaveArtXposedModule.computeCurrentHeaderBottomPx(context)
        val baseMaxBottom = LockscreenSceneRenderer.computeNotificationsMaxBottomPx(
            screenH = sh,
            density = density,
            udfpsY = CaveArtXposedModule.cachedUdfpsY,
            udfpsRadius = CaveArtXposedModule.cachedUdfpsRadius
        )
        val maxBottom = if (CaveArtXposedModule.cachedUdfpsRadius > 0f && CaveArtXposedModule.cachedUdfpsY > 0f) {
            baseMaxBottom - (16f * density)
        } else {
            minOf(baseMaxBottom - (16f * density), sh - (176f * density))
        }
        val topY = (anchorBottom + (LockscreenLayoutSpec.NOTIF_PUSH_MARGIN_DP * density))
            .coerceAtLeast(LockscreenLayoutSpec.NOTIF_CLOCK_TOP_DP * density)
            .coerceAtMost((maxBottom - 74f * density).coerceAtLeast(LockscreenLayoutSpec.NOTIF_CLOCK_TOP_DP * density))

        val maxH = (maxBottom - topY).roundToInt().coerceAtLeast((74f * density).roundToInt())
        if (abs(maxScrollHeightPx - maxH) > 2) {
            maxScrollHeightPx = maxH
            cardsScrollView.requestLayout()
        }

        getLocationOnScreen(screenPos)
        val localTopY = topY - screenPos[1].toFloat()
        if (abs(cardsScrollView.translationY - localTopY) > 0.5f) {
            cardsScrollView.translationY = localTopY
        }

        if (expandedGroupId != null && cardsScrollView.height > 0) {
            val collapsedEst = NotificationKitRenderer.measureTotalHeightPx(
                groups = groups,
                config = clockConfig,
                density = density,
                screenW = sw
            )
            val delta = max(0f, cardsScrollView.height.toFloat() - collapsedEst)
            if (abs(delta - lastExpansionDeltaPx) > 2f) {
                lastExpansionDeltaPx = delta
                CaveArtXposedModule.updateMediaCarouselPushForExpansion(delta)
            }
        } else if (lastExpansionDeltaPx != 0f) {
            lastExpansionDeltaPx = 0f
            CaveArtXposedModule.updateMediaCarouselPushForExpansion(0f)
        }
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
                cardViewPool.values.forEach { it.invalidate() }
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
        syncRemoteCardsContainer()

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
        cardViewPool.clear()
        cardsColumn.removeAllViews()
        if (lastExpansionDeltaPx != 0f) {
            lastExpansionDeltaPx = 0f
            CaveArtXposedModule.updateMediaCarouselPushForExpansion(0f)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            (colorsChangedListener as? WallpaperManager.OnColorsChangedListener)?.let { listener ->
                try {
                    WallpaperManager.getInstance(context).removeOnColorsChangedListener(listener)
                } catch (_: Throwable) {}
            }
        }
        colorsChangedListener = null
        val oldThumb = cachedWallpaperThumb
        cachedWallpaperThumb = null
        oldThumb?.takeIf { !it.isRecycled }?.recycle()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        cardViewPool.clear()
        syncRemoteCardsContainer()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val exactW = MeasureSpec.makeMeasureSpec(CaveArtXposedModule.screenW.toInt().coerceAtLeast(1), MeasureSpec.EXACTLY)
        val exactH = MeasureSpec.makeMeasureSpec(CaveArtXposedModule.screenH.toInt().coerceAtLeast(1), MeasureSpec.EXACTLY)
        super.onMeasure(exactW, exactH)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (useRemoteCardsMode()) {
            updateCardsContainerBounds()
        }
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

        if (useRemoteCardsMode()) {
            if (cardsScrollView.visibility != View.VISIBLE) {
                syncRemoteCardsContainer()
            } else {
                updateCardsContainerBounds()
            }
            lastHitLayout = NotificationHitLayout()
            return
        } else if (cardsScrollView.visibility != View.GONE) {
            cardsScrollView.visibility = View.GONE
        }

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
            (style == NotificationStyle.ICONS_ONLY && peekGroupId != null)
        if (progressVisible && groups.any { g -> g.items.any { it.hasProgress && it.progressIndeterminate } }) {
            postInvalidateOnAnimation()
        }

        canvas.restore()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (useRemoteCardsMode()) {
            return false
        }
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
                    val canSwipePeek = tgt?.kind == NotificationHitKind.PEEK_CARD && group != null
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) && canSwipePeek) {
                        isTrackingHorizontal = true
                        clearPress()
                        swipingPeek = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }

                if (isTrackingHorizontal) {
                    val group = groups.firstOrNull { it.groupId == peekGroupId }
                    if (group?.isClearable == true) {
                        peekSwipeOffsetPx = dx
                        val crossedNow = abs(dx) >= width * 0.28f
                        if (crossedNow != crossedDismissThreshold) {
                            crossedDismissThreshold = crossedNow
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    } else {
                        val maxRubber = 54f * density
                        peekSwipeOffsetPx = (dx * 0.22f).coerceIn(-maxRubber, maxRubber)
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
                    springSwipe(peekSwipeOffsetPx, 0f, 0f, isPeek = true) {
                        swipingPeek = false
                        peekSwipeOffsetPx = 0f
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
                CaveArtNotifSystemBridge.launchNotificationByKey(
                    context,
                    group.items.firstOrNull()?.key,
                    group.primaryIntent
                )
            }
            NotificationHitKind.ICON_PEEK -> {
                val gid = target.groupId ?: return
                animatePeek(if (peekGroupId == gid) null else gid)
            }
            NotificationHitKind.PEEK_CARD -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                animatePeek(null)
                CaveArtNotifSystemBridge.launchNotificationByKey(
                    context,
                    group.items.firstOrNull()?.key,
                    group.primaryIntent
                )
            }
            NotificationHitKind.ACTION_PILL -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                val msg = group.items.getOrNull(target.messageIndex)
                val action = msg?.actions?.getOrNull(target.actionIndex)
                if (action != null) {
                    CaveArtNotifSystemBridge.triggerNotificationAction(
                        context = context,
                        fallbackIntent = msg.contentIntent ?: group.primaryIntent,
                        action = action,
                        choiceIndex = target.choiceIndex,
                        replyTitle = msg.title.ifBlank { group.appName }
                    )
                } else {
                    CaveArtNotifSystemBridge.launchNotificationByKey(
                        context,
                        msg?.key ?: group.items.firstOrNull()?.key,
                        group.primaryIntent
                    )
                }
            }
            NotificationHitKind.CLEAR_ALL -> {
                CaveArtNotifSystemBridge.dismissAllClearable(context)
            }
            NotificationHitKind.ICONS_CONTAINER -> {
                CaveArtNotifSystemBridge.expandNotificationsShade()
            }
            NotificationHitKind.MESSAGE_ROW -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                val msg = group.items.getOrNull(target.messageIndex)
                CaveArtNotifSystemBridge.launchNotificationByKey(
                    context,
                    msg?.key ?: group.items.firstOrNull()?.key,
                    msg?.contentIntent ?: group.primaryIntent
                )
            }
            NotificationHitKind.GROUP_CARD -> {
                val group = groups.firstOrNull { it.groupId == target.groupId } ?: return
                CaveArtNotifSystemBridge.launchNotificationByKey(
                    context,
                    group.items.firstOrNull()?.key,
                    group.primaryIntent
                )
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

    private inner class SwipeableMessageSlotLayout(
        ctx: Context,
        private val groupId: String,
        private val msg: LockNotifMessage,
        private val fallbackIntent: android.app.PendingIntent?
    ) : FrameLayout(ctx) {
        private var startX = 0f
        private var startY = 0f
        private var swiping = false
        private var crossed = false
        private var tracker: VelocityTracker? = null
        private var springAnim: SpringAnimation? = null

        init {
            val isNight = NotificationKitRenderer.isSystemNightMode(ctx)
            background = GradientDrawable().apply {
                setColor(if (isNight) Color.argb(24, 255, 255, 255) else Color.argb(52, 255, 255, 255))
                cornerRadius = 18f * density
                setStroke(
                    (0.75f * density).roundToInt().coerceAtLeast(1),
                    if (isNight) Color.argb(30, 255, 255, 255) else Color.argb(22, 20, 22, 26)
                )
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, 18f * density)
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val slotMaxH = MeasureSpec.makeMeasureSpec((300f * density).roundToInt(), MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, slotMaxH)
        }

        private fun setParentsDisallowIntercept(disallow: Boolean) {
            var p: ViewParent? = parent
            while (p != null) {
                p.requestDisallowInterceptTouchEvent(disallow)
                p = p.parent
            }
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (tracker == null) tracker = VelocityTracker.obtain()
            tracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springAnim?.cancel()
                    startX = ev.rawX
                    startY = ev.rawY
                    swiping = false
                    crossed = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY
                    if (msg.isClearable && abs(dx) > touchSlop * 0.65f && abs(dx) > abs(dy) * 0.9f) {
                        swiping = true
                        startX = ev.rawX - translationX
                        setParentsDisallowIntercept(true)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.recycle()
                    tracker = null
                }
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (tracker == null) tracker = VelocityTracker.obtain()
            tracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springAnim?.cancel()
                    startX = ev.rawX
                    startY = ev.rawY
                    swiping = false
                    crossed = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY
                    if (!swiping && msg.isClearable && abs(dx) > touchSlop * 0.65f && abs(dx) > abs(dy) * 0.9f) {
                        swiping = true
                        startX = ev.rawX - translationX
                        setParentsDisallowIntercept(true)
                    }
                    if (swiping) {
                        val moveX = ev.rawX - startX
                        translationX = moveX
                        alpha = (1f - abs(moveX) / (width.coerceAtLeast(1) * 0.72f)).coerceIn(0.15f, 1f)
                        val nowCrossed = abs(moveX) >= width * 0.28f
                        if (nowCrossed != crossed) {
                            crossed = nowCrossed
                            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        return true
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    setParentsDisallowIntercept(false)
                    tracker?.computeCurrentVelocity(1000)
                    val vx = tracker?.xVelocity ?: 0f
                    tracker?.recycle()
                    tracker = null
                    if (swiping) {
                        swiping = false
                        val threshold = width * 0.28f
                        val fling = abs(vx) > 1400f && ((vx > 0f && translationX > 0f) || (vx < 0f && translationX < 0f))
                        if (msg.isClearable && (abs(translationX) > threshold || fling)) {
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val endX = if (translationX >= 0f) width.toFloat() else -width.toFloat()
                            animateSlotX(translationX, endX, vx) {
                                translationX = 0f
                                alpha = 1f
                                CaveArtNotifSystemBridge.dismissMessage(context, groupId, msg.key)
                            }
                        } else {
                            animateSlotX(translationX, 0f, vx) {
                                translationX = 0f
                                alpha = 1f
                            }
                        }
                        return true
                    } else {
                        val dx = abs(ev.rawX - startX)
                        val dy = abs(ev.rawY - startY)
                        if (dx < touchSlop && dy < touchSlop) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            CaveArtNotifSystemBridge.launchNotificationByKey(
                                context,
                                msg.key,
                                msg.contentIntent ?: fallbackIntent
                            )
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    setParentsDisallowIntercept(false)
                    tracker?.recycle()
                    tracker = null
                    if (swiping) {
                        swiping = false
                        animateSlotX(translationX, 0f, 0f) {
                            translationX = 0f
                            alpha = 1f
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }

        private fun animateSlotX(from: Float, to: Float, vx: Float, onEnd: () -> Unit) {
            springAnim?.cancel()
            val holder = FloatValueHolder(from)
            springAnim = SpringAnimation(holder).apply {
                setStartVelocity(vx)
                spring = SpringForce(to).apply {
                    stiffness = if (to == 0f) 460f else 620f
                    dampingRatio = if (to == 0f) 0.76f else 0.92f
                }
                addUpdateListener { _, value, _ ->
                    translationX = value
                    alpha = if (to == 0f) {
                        (1f - abs(value) / (width.coerceAtLeast(1) * 0.72f)).coerceIn(0.15f, 1f)
                    } else {
                        (1f - abs(value) / (width.coerceAtLeast(1) * 0.85f)).coerceIn(0f, 1f)
                    }
                }
                addEndListener { _, _, _, _ -> onEnd() }
                start()
            }
        }
    }

    private inner class CaveArtRemoteCardView(ctx: Context) : FrameLayout(ctx) {

        private val cornerPx = NotificationThemeSpec.CARD_CORNER_RADIUS_DP * density
        private val outerColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val groupHeaderBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (16f * density).roundToInt(),
                (11f * density).roundToInt(),
                (12f * density).roundToInt(),
                (8f * density).roundToInt()
            )
            visibility = View.GONE
        }
        private val groupHeaderDivider = View(ctx).apply {
            visibility = View.GONE
        }
        private val groupHeaderIcon = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        private val groupHeaderTitle = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        private val groupHeaderCount = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding((8f * density).roundToInt(), (2f * density).roundToInt(), (8f * density).roundToInt(), (2f * density).roundToInt())
        }
        private val groupCollapseBtn = object : View(ctx) {
            private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.85f * density
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            var inkColor: Int = Color.WHITE
            var bgColor: Int = Color.argb(36, 255, 255, 255)

            override fun onDraw(canvas: Canvas) {
                val cx = width / 2f
                val cy = height / 2f
                bgPaint.color = bgColor
                canvas.drawRoundRect(
                    3f * density, 4f * density,
                    width - 3f * density, height - 4f * density,
                    12f * density, 12f * density,
                    bgPaint
                )
                p.color = inkColor
                val hw = 4.2f * density
                val hh = 2.4f * density
                val path = Path().apply {
                    moveTo(cx - hw, cy + hh * 0.6f)
                    lineTo(cx, cy - hh)
                    lineTo(cx + hw, cy + hh * 0.6f)
                }
                canvas.drawPath(path, p)
            }
        }.apply {
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                toggleGroupExpand()
            }
        }

        private var groupScrollMaxPx = (290f * density).roundToInt()
        private var scrollStartX = 0f
        private var scrollStartY = 0f
        private val groupScrollView = object : ScrollView(ctx) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cap = MeasureSpec.makeMeasureSpec(groupScrollMaxPx.coerceAtLeast(1), MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, cap)
            }

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        scrollStartX = ev.rawX
                        scrollStartY = ev.rawY
                        if (canScrollVertically(1) || canScrollVertically(-1)) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        return super.onInterceptTouchEvent(ev)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = abs(ev.rawX - scrollStartX)
                        val dy = abs(ev.rawY - scrollStartY)
                        if (dx > touchSlop * 0.65f && dx > dy * 0.9f) {
                            return false
                        }
                        return super.onInterceptTouchEvent(ev)
                    }
                }
                return super.onInterceptTouchEvent(ev)
            }
        }.apply {
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_ALWAYS
            visibility = View.GONE
        }

        private val groupInnerColumn = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (10f * density).roundToInt(),
                (8f * density).roundToInt(),
                (10f * density).roundToInt(),
                (10f * density).roundToInt()
            )
        }

        private val contentStack = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
        }

        private val cardLoc = IntArray(2)
        private val slotRecords = ArrayList<InflatedSlot>()

        private var boundGroup: LockNotifAppGroup? = null
        private var boundExpanded: Boolean = false
        private var cardSwiping = false
        private var downX = 0f
        private var downY = 0f
        private var cardVelocityTracker: VelocityTracker? = null
        private var crossedThreshold = false
        private var cardSwipeSpring: SpringAnimation? = null

        private inner class InflatedSlot(
            var key: String,
            var layoutId: Int,
            var pkgName: String,
            var expanded: Boolean,
            var sbn: StatusBarNotification?,
            var actionSig: String,
            var view: View
        )

        init {
            setWillNotDraw(false)
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
                }
            }

            groupHeaderBar.addView(
                groupHeaderIcon,
                LinearLayout.LayoutParams((22f * density).roundToInt(), (22f * density).roundToInt()).apply {
                    marginEnd = (10f * density).roundToInt()
                }
            )
            groupHeaderBar.addView(
                groupHeaderTitle,
                LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginEnd = (8f * density).roundToInt()
                }
            )
            groupHeaderBar.addView(
                groupHeaderCount,
                LinearLayout.LayoutParams(-2, -2).apply {
                    marginEnd = (6f * density).roundToInt()
                }
            )
            groupHeaderBar.addView(
                groupCollapseBtn,
                LinearLayout.LayoutParams((36f * density).roundToInt(), (30f * density).roundToInt())
            )

            groupScrollView.addView(groupInnerColumn, LayoutParams(-1, -2))
            outerColumn.addView(groupHeaderBar, LinearLayout.LayoutParams(-1, -2))
            outerColumn.addView(
                groupHeaderDivider,
                LinearLayout.LayoutParams(-1, (0.75f * density).roundToInt().coerceAtLeast(1)).apply {
                    leftMargin = (14f * density).roundToInt()
                    rightMargin = (14f * density).roundToInt()
                }
            )
            outerColumn.addView(groupScrollView, LinearLayout.LayoutParams(-1, -2))
            outerColumn.addView(contentStack, LinearLayout.LayoutParams(-1, -2))
            addView(outerColumn, LayoutParams(-1, -2))
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val totalCap = (maxScrollHeightPx - (56f * density).roundToInt()).coerceAtLeast((260f * density).roundToInt())
            groupScrollMaxPx = (totalCap - (50f * density).roundToInt()).coerceAtLeast((200f * density).roundToInt())
            val capPx = if (boundExpanded) totalCap else (160f * density).roundToInt()
            val boundedH = MeasureSpec.makeMeasureSpec(capPx, MeasureSpec.AT_MOST)
            super.onMeasure(widthMeasureSpec, boundedH)
        }

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(cardLoc)
            NotificationKitRenderer.drawSingleCardSurface(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                cornerRadius = cornerPx,
                config = clockConfig,
                screenW = CaveArtXposedModule.screenW,
                screenH = CaveArtXposedModule.screenH,
                viewLocX = cardLoc[0].toFloat(),
                viewLocY = cardLoc[1].toFloat(),
                density = density,
                wallpaperBitmap = cachedWallpaperThumb,
                wallpaperZoom = CaveArtXposedModule.activeClock?.telemetryZoom ?: LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM,
                fallbackColors = cachedWallpaperColors,
                isNightMode = NotificationKitRenderer.isSystemNightMode(context),
                alphaScale = alpha,
                isPressed = isPressed
            )
            super.onDraw(canvas)
        }

        fun bind(group: LockNotifAppGroup, isExpanded: Boolean) {
            boundGroup = group
            boundExpanded = isExpanded

            val isMultiExpanded = isExpanded && group.count > 1 && !group.isRedacted
            if (isMultiExpanded) {
                contentStack.visibility = View.GONE
                contentStack.removeAllViews()
                groupHeaderBar.visibility = View.VISIBLE
                groupHeaderDivider.visibility = View.VISIBLE
                groupScrollView.visibility = View.VISIBLE
                bindMultiExpandedContainer(group)
                return
            }

            groupHeaderBar.visibility = View.GONE
            groupHeaderDivider.visibility = View.GONE
            groupScrollView.visibility = View.GONE
            groupInnerColumn.removeAllViews()
            contentStack.visibility = View.VISIBLE

            val msg = group.items.firstOrNull() ?: run {
                contentStack.removeAllViews()
                slotRecords.clear()
                return
            }

            val singleReusable = slotRecords.size == 1 &&
                slotRecords[0].key == msg.key &&
                slotRecords[0].expanded == isExpanded &&
                slotRecords[0].view.parent === contentStack

            if (singleReusable) {
                val slot = slotRecords[0]
                if (slot.sbn === msg.sbn) {
                    invalidate()
                    return
                }
                if (tryReapplySlot(slot, msg, isExpanded, hideExpandBtn = false)) {
                    contentStack.requestLayout()
                    requestLayout()
                    invalidate()
                    return
                }
            }

            contentStack.removeAllViews()
            slotRecords.clear()

            val slot = inflateMessageSlot(msg, isExpanded, hideExpandBtn = false)
            if (slot != null) {
                slotRecords.add(slot)
                contentStack.addView(slot.view, LinearLayout.LayoutParams(-1, -2))
            } else {
                val fallback = buildFallbackView(group, msg, isExpanded)
                slotRecords.add(InflatedSlot(msg.key, -1, group.packageName, isExpanded, msg.sbn, actionSignature(msg), fallback))
                contentStack.addView(fallback, LinearLayout.LayoutParams(-1, -2))
            }
            contentStack.requestLayout()
            requestLayout()
            invalidate()
        }

        private fun bindMultiExpandedContainer(group: LockNotifAppGroup) {
            val isNight = NotificationKitRenderer.isSystemNightMode(context)
            val (primInk, _) = NotificationKitRenderer.resolveSurfaceTextColors(
                config = clockConfig,
                screenW = CaveArtXposedModule.screenW,
                screenH = CaveArtXposedModule.screenH,
                wallpaperBitmap = cachedWallpaperThumb,
                wallpaperZoom = CaveArtXposedModule.activeClock?.telemetryZoom ?: LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM,
                fallbackColors = cachedWallpaperColors,
                isNightMode = isNight
            )
            val headerBmp = group.appIcon ?: group.smallIcon
            if (headerBmp != null && !headerBmp.isRecycled) {
                groupHeaderIcon.setImageBitmap(headerBmp)
                if (group.appIcon == null && group.smallIcon != null) {
                    groupHeaderIcon.setColorFilter(primInk, PorterDuff.Mode.SRC_IN)
                } else {
                    groupHeaderIcon.clearColorFilter()
                }
            }
            groupHeaderTitle.text = group.appName
            groupHeaderTitle.setTextColor(primInk)
            groupHeaderCount.text = "${group.count}"
            groupHeaderCount.setTextColor(primInk)
            groupHeaderCount.background = GradientDrawable().apply {
                setColor(if (isNight) Color.argb(38, 255, 255, 255) else Color.argb(26, 20, 22, 26))
                cornerRadius = 10f * density
            }
            groupHeaderDivider.setBackgroundColor(
                if (isNight) Color.argb(32, 255, 255, 255) else Color.argb(22, 20, 22, 26)
            )
            groupCollapseBtn.inkColor = primInk
            groupCollapseBtn.bgColor = if (isNight) Color.argb(36, 255, 255, 255) else Color.argb(24, 20, 22, 26)
            groupCollapseBtn.invalidate()

            val items = group.items
            val canReuseSlots = slotRecords.size == items.size &&
                slotRecords.indices.all { i ->
                    slotRecords[i].key == items[i].key &&
                        slotRecords[i].expanded &&
                        slotRecords[i].view.parent is SwipeableMessageSlotLayout
                }

            if (canReuseSlots) {
                var anyChanged = false
                var allOk = true
                for (i in items.indices) {
                    val slot = slotRecords[i]
                    val msg = items[i]
                    if (slot.sbn === msg.sbn) continue
                    anyChanged = true
                    if (!tryReapplySlot(slot, msg, isExpanded = true, hideExpandBtn = true)) {
                        allOk = false
                        break
                    }
                }
                if (allOk) {
                    if (anyChanged) {
                        groupInnerColumn.requestLayout()
                        groupScrollView.requestLayout()
                        requestLayout()
                    }
                    invalidate()
                    return
                }
            }

            groupInnerColumn.removeAllViews()
            slotRecords.clear()

            items.forEachIndexed { idx, msg ->
                val slot = inflateMessageSlot(msg, isExpanded = true, hideExpandBtn = true)
                val innerView = if (slot != null) {
                    slotRecords.add(slot)
                    slot.view
                } else {
                    val fallback = buildFallbackView(group, msg, isExpanded = true)
                    slotRecords.add(
                        InflatedSlot(msg.key, -1, group.packageName, true, msg.sbn, actionSignature(msg), fallback)
                    )
                    fallback
                }

                val wrapper = SwipeableMessageSlotLayout(context, group.groupId, msg, group.primaryIntent)
                wrapper.addView(innerView, FrameLayout.LayoutParams(-1, -2))
                groupInnerColumn.addView(
                    wrapper,
                    LinearLayout.LayoutParams(-1, -2).apply {
                        if (idx < items.lastIndex) bottomMargin = (8f * density).roundToInt()
                    }
                )
            }

            groupInnerColumn.requestLayout()
            groupScrollView.requestLayout()
            requestLayout()
            invalidate()
        }

        private fun resolveRemoteViews(msg: LockNotifMessage, isExpanded: Boolean): Pair<RemoteViews?, Context> {
            val sbn = msg.sbn ?: return null to context
            val n = sbn.notification ?: return null to context
            val pkgCtx = try {
                (StatusBarNotification::class.java
                    .getMethod("getPackageContext", Context::class.java)
                    .invoke(sbn, context) as? Context)
                    ?: context.createPackageContext(sbn.packageName, Context.CONTEXT_RESTRICTED)
            } catch (_: Throwable) {
                try {
                    context.createPackageContext(sbn.packageName, Context.CONTEXT_RESTRICTED)
                } catch (_: Throwable) {
                    context
                }
            }

            if (msg.isRedacted) {
                val pub = n.publicVersion
                if (pub != null) {
                    val pubRv = try {
                        val b = Notification.Builder.recoverBuilder(context, pub)
                        b.createContentView() ?: pub.contentView
                    } catch (_: Throwable) {
                        pub.contentView
                    }
                    return pubRv to pkgCtx
                }
                return null to pkgCtx
            }

            val rv = try {
                val b = Notification.Builder.recoverBuilder(context, n)
                if (isExpanded) {
                    b.createBigContentView() ?: n.bigContentView ?: b.createContentView() ?: n.contentView
                } else {
                    b.createContentView() ?: n.contentView
                }
            } catch (_: Throwable) {
                if (isExpanded) (n.bigContentView ?: n.contentView) else n.contentView
            }
            return rv to pkgCtx
        }

        private fun applyRemoteViews(rv: RemoteViews, pkgCtx: Context, sbn: StatusBarNotification): View? {
            activeInteractionSbn = sbn
            val handler = remoteInteractionHandler
            if (handler != null) {
                try {
                    val handlerCls = Class.forName("android.widget.RemoteViews\$InteractionHandler")
                    val m3 = RemoteViews::class.java.methods.firstOrNull {
                        it.name == "apply" && it.parameterTypes.size == 3 && it.parameterTypes[2] == handlerCls
                    }
                    if (m3 != null) {
                        return m3.invoke(rv, pkgCtx, contentStack, handler) as? View
                    }
                } catch (_: Throwable) {}
            }
            return try {
                rv.apply(pkgCtx, contentStack)
            } catch (_: Throwable) {
                null
            }
        }

        private fun actionSignature(msg: LockNotifMessage): String {
            return msg.actions.joinToString("|") { it.title }
        }

        private fun tryReapplySlot(
            slot: InflatedSlot,
            msg: LockNotifMessage,
            isExpanded: Boolean,
            hideExpandBtn: Boolean
        ): Boolean {
            if (slot.layoutId == -1 || (!msg.hasProgress && !msg.usesChronometer)) return false
            val sbn = msg.sbn ?: return false
            val newSig = actionSignature(msg)
            if (newSig != slot.actionSig) return false
            val (rv, pkgCtx) = resolveRemoteViews(msg, isExpanded)
            if (rv == null || rv.layoutId != slot.layoutId || sbn.packageName != slot.pkgName) return false

            activeInteractionSbn = sbn
            val handler = remoteInteractionHandler
            val ok = if (handler != null) {
                try {
                    val handlerCls = Class.forName("android.widget.RemoteViews\$InteractionHandler")
                    val m = RemoteViews::class.java.methods
                        .filter { it.name == "reapply" && it.parameterTypes.size >= 3 && it.parameterTypes[2] == handlerCls }
                        .minByOrNull { it.parameterTypes.size }
                    if (m != null) {
                        val args = Array<Any?>(m.parameterTypes.size) { idx ->
                            when (idx) {
                                0 -> pkgCtx
                                1 -> slot.view
                                2 -> handler
                                else -> null
                            }
                        }
                        m.invoke(rv, *args)
                        true
                    } else {
                        false
                    }
                } catch (_: Throwable) {
                    false
                }
            } else {
                try {
                    rv.reapply(pkgCtx, slot.view)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
            if (ok) {
                slot.sbn = sbn
                postProcessInflatedView(slot.view, sbn, isExpanded, hideExpandBtn)
            }
            return ok
        }

        private fun inflateMessageSlot(
            msg: LockNotifMessage,
            isExpanded: Boolean,
            hideExpandBtn: Boolean
        ): InflatedSlot? {
            val sbn = msg.sbn ?: return null
            val (rv, pkgCtx) = resolveRemoteViews(msg, isExpanded)
            if (rv == null) return null
            val inflated = applyRemoteViews(rv, pkgCtx, sbn) ?: return null
            postProcessInflatedView(inflated, sbn, isExpanded, hideExpandBtn)
            return InflatedSlot(msg.key, rv.layoutId, sbn.packageName, isExpanded, sbn, actionSignature(msg), inflated)
        }

        private fun sysId(name: String): Int = Resources.getSystem().getIdentifier(name, "id", "android")

        private fun trimLeftIndent(view: View?, targetStartPx: Int, thresholdPx: Int) {
            if (view == null) return
            val lp = view.layoutParams as? MarginLayoutParams
            if (lp != null && lp.marginStart > thresholdPx) {
                lp.marginStart = targetStartPx
                view.layoutParams = lp
            }
            if (view.paddingStart > thresholdPx) {
                view.setPaddingRelative(targetStartPx, view.paddingTop, view.paddingEnd, view.paddingBottom)
            }
        }

        private fun postProcessInflatedView(
            inflated: View,
            sbn: StatusBarNotification,
            isExpanded: Boolean,
            hideExpandBtn: Boolean
        ) {
            val isColorized = sbn.notification.extras?.getBoolean("android.colorized", false) == true
            if (!isColorized && inflated.background is ColorDrawable) {
                inflated.background = null
            }
            (inflated.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                if (lp.weight != 0f) lp.weight = 0f
                if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                }
            }

            try {
                val expandId = sysId("expand_button")
                if (expandId != 0) {
                    inflated.findViewById<View>(expandId)?.let { btn ->
                        if (hideExpandBtn) {
                            btn.visibility = View.GONE
                            btn.setOnClickListener(null)
                        } else {
                            btn.visibility = View.VISIBLE
                            try {
                                btn.javaClass.getMethod("setExpanded", Boolean::class.javaPrimitiveType)
                                    .invoke(btn, isExpanded)
                            } catch (_: Throwable) {}
                            btn.setOnClickListener {
                                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                toggleGroupExpand()
                            }
                        }
                    }
                }

                if (hideExpandBtn) {
                    val appNameId = sysId("app_name_text")
                    if (appNameId != 0) inflated.findViewById<View>(appNameId)?.visibility = View.GONE

                    val dividerId = sysId("header_text_divider")
                    if (dividerId != 0) inflated.findViewById<View>(dividerId)?.visibility = View.GONE

                    val iconId = sysId("icon")
                    val messagingId = sysId("notification_messaging")
                    val hasMessaging = messagingId != 0 && inflated.findViewById<View>(messagingId) != null
                    if (iconId != 0 && hasMessaging) {
                        inflated.findViewById<View>(iconId)?.let { ic ->
                            ic.visibility = View.GONE
                            val leftContainerId = sysId("left_icon")
                            if (leftContainerId != 0) {
                                inflated.findViewById<View>(leftContainerId)?.visibility = View.GONE
                            }
                        }
                        val targetPad = (14f * density).roundToInt()
                        val thresh = (28f * density).roundToInt()
                        listOf(
                            "notification_main_column",
                            "notification_messaging",
                            "notification_top_line",
                            "notification_header",
                            "actions_container"
                        ).forEach { idName ->
                            val vid = sysId(idName)
                            if (vid != 0) trimLeftIndent(inflated.findViewById(vid), targetPad, thresh)
                        }
                        if (inflated.paddingStart > thresh) {
                            inflated.setPaddingRelative(
                                targetPad,
                                (8f * density).roundToInt(),
                                inflated.paddingEnd,
                                (8f * density).roundToInt()
                            )
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        private fun toggleGroupExpand() {
            val g = boundGroup ?: return
            expandedGroupId = if (expandedGroupId == g.groupId) null else g.groupId
            syncRemoteCardsContainer()
        }

        private fun buildFallbackView(group: LockNotifAppGroup, msg: LockNotifMessage, isExpanded: Boolean): View {
            val isNight = NotificationKitRenderer.isSystemNightMode(context)
            val (primInk, secInk) = NotificationKitRenderer.resolveSurfaceTextColors(
                config = clockConfig,
                screenW = CaveArtXposedModule.screenW,
                screenH = CaveArtXposedModule.screenH,
                wallpaperBitmap = cachedWallpaperThumb,
                wallpaperZoom = CaveArtXposedModule.activeClock?.telemetryZoom ?: LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM,
                fallbackColors = cachedWallpaperColors,
                isNightMode = isNight
            )
            val pad = (16f * density).roundToInt()
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, (12f * density).roundToInt(), pad, (12f * density).roundToInt())
            }
            val iconView = ImageView(context).apply {
                val bmp = group.avatarIcon ?: group.appIcon ?: group.smallIcon
                if (bmp != null && !bmp.isRecycled) setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            row.addView(
                iconView,
                LinearLayout.LayoutParams((38f * density).roundToInt(), (38f * density).roundToInt()).apply {
                    marginEnd = (12f * density).roundToInt()
                }
            )
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(context).apply {
                text = if (msg.isRedacted) group.appName else msg.title.ifBlank { group.appName }
                setTextColor(primInk)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            col.addView(TextView(context).apply {
                text = if (msg.isRedacted) "Sensitive content hidden" else msg.text
                setTextColor(secInk)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                maxLines = if (isExpanded) 4 else 1
                ellipsize = TextUtils.TruncateAt.END
            })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            return row
        }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            if (boundExpanded && (boundGroup?.count ?: 0) > 1) {
                return false
            }
            if (cardVelocityTracker == null) cardVelocityTracker = VelocityTracker.obtain()
            cardVelocityTracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cardSwipeSpring?.cancel()
                    downX = ev.rawX
                    downY = ev.rawY
                    cardSwiping = false
                    crossedThreshold = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                        cardSwiping = true
                        downX = ev.rawX - translationX
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cardVelocityTracker?.recycle()
                    cardVelocityTracker = null
                }
            }
            return false
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (boundExpanded && (boundGroup?.count ?: 0) > 1) {
                return false
            }
            val g = boundGroup ?: return super.onTouchEvent(ev)
            if (cardVelocityTracker == null) cardVelocityTracker = VelocityTracker.obtain()
            cardVelocityTracker?.addMovement(ev)

            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cardSwipeSpring?.cancel()
                    downX = ev.rawX
                    downY = ev.rawY
                    cardSwiping = false
                    crossedThreshold = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!cardSwiping && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.15f) {
                        cardSwiping = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (cardSwiping) {
                        if (g.isClearable) {
                            translationX = dx
                            alpha = (1f - abs(dx) / (width.coerceAtLeast(1) * 0.75f)).coerceIn(0.15f, 1f)
                            val crossed = abs(dx) >= width * 0.28f
                            if (crossed != crossedThreshold) {
                                crossedThreshold = crossed
                                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        } else {
                            val maxR = 48f * density
                            translationX = (dx * 0.22f).coerceIn(-maxR, maxR)
                        }
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    cardVelocityTracker?.computeCurrentVelocity(1000)
                    val vx = cardVelocityTracker?.xVelocity ?: 0f
                    cardVelocityTracker?.recycle()
                    cardVelocityTracker = null

                    if (cardSwiping) {
                        cardSwiping = false
                        val threshold = width * 0.28f
                        val flingDismiss = abs(vx) > 1400f && ((vx > 0f && translationX > 0f) || (vx < 0f && translationX < 0f))
                        if (g.isClearable && (abs(translationX) > threshold || flingDismiss)) {
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            val endX = if (translationX >= 0f) width.toFloat() else -width.toFloat()
                            springCardX(translationX, endX, vx) {
                                translationX = 0f
                                alpha = 1f
                                CaveArtNotifSystemBridge.dismissGroup(context, g)
                            }
                        } else {
                            springCardX(translationX, 0f, vx) {
                                translationX = 0f
                                alpha = 1f
                            }
                        }
                        return true
                    } else {
                        val dx = ev.rawX - downX
                        val dy = ev.rawY - downY
                        if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (g.count > 1 && !boundExpanded) {
                                toggleGroupExpand()
                            } else {
                                CaveArtNotifSystemBridge.launchNotificationByKey(
                                    context,
                                    g.items.firstOrNull()?.key,
                                    g.primaryIntent
                                )
                            }
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    parent?.requestDisallowInterceptTouchEvent(false)
                    cardVelocityTracker?.recycle()
                    cardVelocityTracker = null
                    if (cardSwiping) {
                        cardSwiping = false
                        springCardX(translationX, 0f, 0f) {
                            translationX = 0f
                            alpha = 1f
                        }
                    }
                    return true
                }
            }
            return super.onTouchEvent(ev)
        }

        private fun springCardX(from: Float, to: Float, vx: Float, onEnd: () -> Unit) {
            cardSwipeSpring?.cancel()
            val holder = FloatValueHolder(from)
            cardSwipeSpring = SpringAnimation(holder).apply {
                setStartVelocity(vx)
                spring = SpringForce(to).apply {
                    stiffness = if (to == 0f) 460f else 620f
                    dampingRatio = if (to == 0f) 0.76f else 0.92f
                }
                addUpdateListener { _, value, _ ->
                    translationX = value
                    alpha = if (to == 0f) {
                        (1f - abs(value) / (width.coerceAtLeast(1) * 0.75f)).coerceIn(0.15f, 1f)
                    } else {
                        (1f - abs(value) / (width.coerceAtLeast(1) * 0.85f)).coerceIn(0f, 1f)
                    }
                    invalidate()
                }
                addEndListener { _, _, _, _ -> onEnd() }
                start()
            }
        }
    }
}
