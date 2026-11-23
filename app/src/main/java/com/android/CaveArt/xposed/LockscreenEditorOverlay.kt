package com.android.CaveArt.xposed

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.android.CaveArt.AdaptiveClockHelper
import com.android.CaveArt.MonetEngine
import com.android.CaveArt.clockkit.*
import kotlin.math.max
import kotlin.math.roundToInt

class LockscreenEditorOverlay(context: Context) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).roundToInt()
    private fun dpf(v: Float): Float = v * density

    private var editConfig = ClockKitConfig()
    private var initialConfig = ClockKitConfig()
    private var editCollisionMap: FloatArray? = null

    private var activeLayerScope = EditorLayerScope.CLOCK
    private var isStyleBrowseMode = false
    private var activeActionIndex = 0
    private var isLayersSheetVisible = false
    private var fastScrollStyle = 0
    private var backdropStyle = BackdropStyle.BLUR_BACKGROUND

    private var isDepthEnabled = true
    private var depthOcclusionLimit = 0.45f
    private var bgVibrance = 0f
    private var bgOpacity = 1f
    private var subjectOpacity = 1f
    private var wallpaperZoom = 1.10f

    private var wallpaperBitmap: Bitmap? = null
    private var blurredBackdrop: Bitmap? = null
    private var depthMaskBitmap: Bitmap? = null
    private var rawDepthBitmap: Bitmap? = null
    private var paletteColors: List<Int> = emptyList()
    private var theme = LockscreenOverlayPanels.resolveTheme(context, emptyList()).first
    private var currentActions: List<OverlayAction> = emptyList()

    private var statusBarPadPx: Int = run {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (id > 0) resources.getDimensionPixelSize(id) else dp(32f)
    }
    private var navBarPadPx: Int = run {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id > 0) resources.getDimensionPixelSize(id) else dp(24f)
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private var editorWakeLock: PowerManager.WakeLock? = null
    private val keepAwakeHandler = Handler(Looper.getMainLooper())
    private val keepAwakeRunnable = object : Runnable {
        override fun run() {
            if (!CaveArtXposedModule.isEditModeActive) return
            pokeUserActivity()
            keepScreenOn = true
            CaveArtXposedModule.activeRootLayout?.let { CaveArtXposedModule.hideStockSettingsPopupIfPresent(it) }
            keepAwakeHandler.postDelayed(this, 3000L)
        }
    }

    private var frameLoopRunning = false
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            previewCanvasView.invalidate()
            styleCarouselView.invalidateActiveCard()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val backdropImageView = ImageView(context)
    private val backdropTintView = View(context)
    private val rootColumn = LinearLayout(context)
    private val previewAreaContainer = FrameLayout(context)
    private val previewCardFrame = FrameLayout(context)
    private val previewCanvasView = LockscreenPreviewCanvasView(context)
    private val styleCarouselView = StyleCarouselPagerView(context)
    private val styleNamePill = TextView(context)
    private val contextualSlot = FrameLayout(context)
    private val fastScrollView = FastScrollIndicatorView(context)
    private val toolbarContainer = FrameLayout(context)
    private val unlockPromptPill = TextView(context)
    private val layersScrim = FrameLayout(context)
    private val layersSheetCard = LinearLayout(context)

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
                statusBarPadPx = max(bars.top, statusBarPadPx)
                navBarPadPx = max(bars.bottom, navBarPadPx)
                applyRootPadding()
            }
            insets
        }
        buildUi()
    }

    private fun applyRootPadding() {
        previewAreaContainer.setPadding(0, statusBarPadPx + dp(10f), 0, dp(6f))
        rootColumn.setPadding(0, 0, 0, navBarPadPx + dp(10f))
        layersSheetCard.setPadding(dp(20f), dp(14f), dp(20f), navBarPadPx + dp(20f))
    }

    private fun buildUi() {
        backdropImageView.scaleType = ImageView.ScaleType.CENTER_CROP
        addView(backdropImageView, LayoutParams(-1, -1))
        addView(backdropTintView, LayoutParams(-1, -1))

        rootColumn.orientation = LinearLayout.VERTICAL
        rootColumn.gravity = Gravity.CENTER_HORIZONTAL
        rootColumn.clipChildren = false
        rootColumn.clipToPadding = false
        previewAreaContainer.clipChildren = false
        previewAreaContainer.clipToPadding = false

        previewCardFrame.apply {
            elevation = 0f
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dpf(28f))
                }
            }
            clipToOutline = true
        }
        previewCanvasView.apply {
            sceneProvider = { sceneParamsFor(editConfig, showUdfps = true) }
            getConfig = { editConfig }
            getStatusBarPadPx = { statusBarPadPx }
            getNavBarPadPx = { navBarPadPx }
            isClockFocused = { activeLayerScope == EditorLayerScope.CLOCK }
            onConfigChanged = { next -> editConfig = next; onConfigMutated() }
            onTapClockFocus = { focusScope(EditorLayerScope.CLOCK) }
        }
        previewCardFrame.addView(previewCanvasView, LayoutParams(-1, -1))

        val pillBg = {
            GradientDrawable().apply {
                setColor(Color.argb(115, 0, 0, 0))
                cornerRadius = dpf(18f)
                setStroke(dp(1f), Color.argb(60, 255, 255, 255))
            }
        }
        val layersBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12f), 0, dp(14f), 0)
            background = pillBg()
            addView(UnifiedLineIconView(context, ToolbarIconKind.DEPTH, Color.WHITE, dp(15f)), LinearLayout.LayoutParams(dp(15f), dp(15f)))
            addView(TextView(context).apply {
                text = "Layers"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(dp(5f), 0, 0, 0)
            })
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); showLayersSheet() }
        }
        previewCardFrame.addView(layersBtn, LayoutParams(-2, dp(34f), Gravity.TOP or Gravity.START).apply { setMargins(dp(14f), dp(14f), dp(14f), dp(14f)) })

        val doneBtn = TextView(context).apply {
            text = "Done"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(dp(14f), 0, dp(14f), 0)
            background = pillBg()
            setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CONFIRM); dismiss(save = true) }
        }
        previewCardFrame.addView(doneBtn, LayoutParams(-2, dp(34f), Gravity.TOP or Gravity.END).apply { setMargins(dp(14f), dp(14f), dp(14f), dp(14f)) })
        previewAreaContainer.addView(previewCardFrame, LayoutParams(-1, -1, Gravity.CENTER))

        styleCarouselView.apply {
            visibility = View.GONE
            sceneProviderForStyle = { id -> sceneParamsFor(syncDate(editConfig.copy(styleId = id)), showUdfps = false) }
            onScrollProgress = { page, frac -> fastScrollView.setPage(page, frac) }
            onStyleSettled = { st ->
                if (st.id != editConfig.styleId) {
                    editConfig = syncDate(editConfig.copy(styleId = st.id))
                    styleNamePill.text = st.name
                    rebuildActionsList()
                }
            }
            onStyleConfirmed = { st ->
                editConfig = syncDate(editConfig.copy(styleId = st.id))
                isStyleBrowseMode = false
                rebuildActionsList()
                syncModeViews(animate = true)
            }
        }
        previewAreaContainer.addView(styleCarouselView, LayoutParams(-1, -1, Gravity.CENTER))

        styleNamePill.apply {
            gravity = Gravity.CENTER; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(dp(14f), dp(6f), dp(14f), dp(6f))
            background = pillBg(); visibility = View.GONE
        }
        previewAreaContainer.addView(styleNamePill, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(24f) })

        rootColumn.addView(previewAreaContainer, LinearLayout.LayoutParams(-1, 0, 1f))
        contextualSlot.setPadding(dp(18f), 0, dp(18f), 0)
        contextualSlot.clipChildren = false
        contextualSlot.clipToPadding = false
        rootColumn.addView(contextualSlot, LinearLayout.LayoutParams(-1, dp(52f)))
        toolbarContainer.setPadding(0, dp(6f), 0, 0)
        rootColumn.addView(toolbarContainer, LinearLayout.LayoutParams(-1, dp(64f)))
        addView(rootColumn, LayoutParams(-1, -1))

        layersScrim.apply { setBackgroundColor(Color.argb(130, 0, 0, 0)); visibility = View.GONE; isClickable = true; setOnClickListener { hideLayersSheet() } }
        layersSheetCard.apply { orientation = LinearLayout.VERTICAL; isClickable = true }
        layersScrim.addView(layersSheetCard, LayoutParams(-1, -2, Gravity.BOTTOM))
        addView(layersScrim, LayoutParams(-1, -1))

        unlockPromptPill.apply {
            text = "Unlock device to customize clock"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            background = GradientDrawable().apply { setColor(Color.argb(215, 24, 26, 32)); cornerRadius = dpf(24f); setStroke(dp(1f), Color.argb(65, 255, 255, 255)) }
            visibility = View.GONE
        }
        addView(unlockPromptPill, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = navBarPadPx + dp(72f) })
        applyRootPadding()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val areaW = previewAreaContainer.width - previewAreaContainer.paddingLeft - previewAreaContainer.paddingRight
        val areaH = previewAreaContainer.height - previewAreaContainer.paddingTop - previewAreaContainer.paddingBottom
        if (areaW > 0 && areaH > 0) {
            val aspect = (CaveArtXposedModule.screenW / CaveArtXposedModule.screenH.coerceAtLeast(1f)).coerceIn(0.35f, 0.75f)
            val maxW = areaW - dp(32f)
            var h = areaH
            var w = (h * aspect).roundToInt()
            if (w > maxW) { w = maxW; h = (w / aspect).roundToInt() }
            val lp = previewCardFrame.layoutParams
            if (lp.width != w || lp.height != h) { lp.width = w; lp.height = h; previewCardFrame.layoutParams = lp }
        }
    }

    fun showUnlockPromptPill() {
        visibility = View.VISIBLE
        rootColumn.visibility = View.GONE; layersScrim.visibility = View.GONE
        backdropImageView.visibility = View.GONE; backdropTintView.visibility = View.GONE
        unlockPromptPill.visibility = View.VISIBLE; unlockPromptPill.alpha = 0f
        unlockPromptPill.animate().alpha(1f).setDuration(180L).withEndAction {
            postDelayed({
                if (!CaveArtXposedModule.isEditModeActive) {
                    unlockPromptPill.animate().alpha(0f).setDuration(200L).withEndAction {
                        unlockPromptPill.visibility = View.GONE; visibility = View.GONE
                    }.start()
                }
            }, 2200L)
        }.start()
    }

    fun openEditor() {
        val clock = CaveArtXposedModule.activeClock ?: return
        initialConfig = clock.clockConfig
        editConfig = syncDate(initialConfig)
        editCollisionMap = clock.collisionMap
        isDepthEnabled = editConfig.isDepthEnabled; depthOcclusionLimit = editConfig.depthOcclusionLimit
        bgVibrance = getSettingFloat(context, "layer_structure", 0f)
        bgOpacity = getSettingFloat(context, "layer_bg_opacity", 1f)
        subjectOpacity = getSettingFloat(context, "layer_subject_opacity", 1f)
        wallpaperZoom = clock.telemetryZoom
        fastScrollStyle = getSettingInt(context, "scroll_style", 0)
        backdropStyle = BackdropStyle.fromId(
            getSettingInt(context, "backdrop_style", BackdropStyle.BLUR_BACKGROUND.id)
        )

        activeLayerScope = EditorLayerScope.CLOCK; isStyleBrowseMode = false; activeActionIndex = 0
        isLayersSheetVisible = false; layersScrim.visibility = View.GONE; unlockPromptPill.visibility = View.GONE
        backdropImageView.visibility = if (backdropStyle == BackdropStyle.OFF) View.GONE else View.VISIBLE
        backdropTintView.visibility = View.VISIBLE
        rootColumn.visibility = View.VISIBLE; visibility = View.VISIBLE

        val (resolvedTheme, resolvedPalette) = LockscreenOverlayPanels.resolveTheme(context, paletteColors, backdropStyle)
        theme = resolvedTheme; paletteColors = resolvedPalette
        backdropTintView.background = LockscreenOverlayPanels.buildBackdropOverlayDrawable(theme, backdropStyle)

        loadAssetsAsync()
        rebuildActionsList()
        syncModeViews(animate = false)

        CaveArtXposedModule.setEditMode(true)
        startKeepAwake(); syncContinuousFrameLoop()

        alpha = 0f; previewCardFrame.scaleX = 1.08f; previewCardFrame.scaleY = 1.08f; rootColumn.translationY = dpf(36f)
        animate().alpha(1f).setDuration(220L).start()
        previewCardFrame.animate().scaleX(1f).scaleY(1f).setDuration(280L).setInterpolator(DecelerateInterpolator()).start()
        rootColumn.animate().translationY(0f).setDuration(280L).setInterpolator(DecelerateInterpolator()).start()
        requestApplyInsets(); requestFocus()
    }

    fun handleBackPressed(): Boolean {
        if (isLayersSheetVisible) { hideLayersSheet(); return true }
        dismiss(save = false)
        return true
    }

    fun dismiss(save: Boolean) {
        if (!CaveArtXposedModule.isEditModeActive) return
        stopKeepAwake(); stopContinuousFrameLoop()
        isLayersSheetVisible = false; layersScrim.visibility = View.GONE

        if (save) {
            val finalCfg = syncDate(editConfig.copy(isDepthEnabled = isDepthEnabled, depthOcclusionLimit = depthOcclusionLimit))
            CaveArtXposedModule.applyLiveEditPreview(finalCfg, editCollisionMap)
            LockscreenOverlayPanels.persistAllToCaveArt(
                context, finalCfg, editCollisionMap, isDepthEnabled, depthOcclusionLimit, bgVibrance, subjectOpacity, bgOpacity
            )
        } else {
            CaveArtXposedModule.applyLiveEditPreview(initialConfig, CaveArtXposedModule.activeClock?.collisionMap)
        }

        CaveArtXposedModule.setEditMode(false)
        animate().alpha(0f).setDuration(190L).withEndAction {
            visibility = View.GONE
            backdropImageView.setImageDrawable(null)
            blurredBackdrop?.takeIf { !it.isRecycled }?.recycle(); blurredBackdrop = null
            wallpaperBitmap?.takeIf { !it.isRecycled }?.recycle(); wallpaperBitmap = null
        }.start()
    }

    private fun loadAssetsAsync() {
        val clock = CaveArtXposedModule.activeClock
        val styleNow = backdropStyle
        depthMaskBitmap = clock?.depthMaskBitmap; rawDepthBitmap = clock?.rawDepthBitmap
        Thread {
            val latestScroll = getSettingInt(context, "scroll_style", fastScrollStyle)
            val wp = LockscreenOverlayPanels.loadLockWallpaperBitmap(context)
            val blurred = if (styleNow != BackdropStyle.OFF) {
                wp?.let { LockscreenSceneRenderer.createBlurredBackdrop(it, styleNow) }
            } else null
            val mask = depthMaskBitmap ?: LockscreenOverlayPanels.decodeStreamBitmap(context, "content://com.android.CaveArt.settings/depth_mask")
            val raw = rawDepthBitmap ?: LockscreenOverlayPanels.decodeStreamBitmap(context, "content://com.android.CaveArt.settings/raw_depth")
            val extracted = if (paletteColors.isEmpty() && wp != null) MonetEngine.extractPaletteFromBitmap(wp, theme.isNight).allColors else null

            post {
                if (!CaveArtXposedModule.isEditModeActive) { wp?.recycle(); blurred?.recycle(); return@post }
                fastScrollStyle = latestScroll
                fastScrollView.setScrollStyle(latestScroll)
                if (wp != null) wallpaperBitmap = wp
                if (blurred != null && styleNow != BackdropStyle.OFF) {
                    blurredBackdrop = blurred; backdropImageView.setImageBitmap(blurred)
                    backdropImageView.alpha = 0f; backdropImageView.animate().alpha(1f).setDuration(240L).start()
                }
                if (mask != null) depthMaskBitmap = mask
                if (raw != null) rawDepthBitmap = raw
                if (!extracted.isNullOrEmpty()) {
                    paletteColors = extracted
                    theme = theme.copy(accentColor = extracted.first())
                    refreshContextualSlot()
                }
                previewCanvasView.invalidate(); styleCarouselView.notifyAssetsLoaded()
            }
        }.start()
    }

    private fun focusScope(scope: EditorLayerScope) {
        activeLayerScope = scope; isStyleBrowseMode = false; activeActionIndex = 0
        rebuildActionsList(); syncModeViews(animate = false)
    }

    private fun rebuildActionsList() {
        currentActions = LockscreenOverlayPanels.buildActions(
            context, activeLayerScope, editConfig, statusBarPadPx,
            { depthOcclusionLimit }, { v -> depthOcclusionLimit = v; editConfig = editConfig.copy(depthOcclusionLimit = v); previewCanvasView.invalidate() },
            { wallpaperZoom }, { v -> wallpaperZoom = v; previewCanvasView.invalidate() },
            { bgVibrance }, { v -> bgVibrance = v; previewCanvasView.invalidate() },
            { bgOpacity }, { v -> bgOpacity = v; previewCanvasView.invalidate() },
            { subjectOpacity }, { v -> subjectOpacity = v; previewCanvasView.invalidate() }
        ) { next -> editConfig = next; onConfigMutated() }
        if (activeActionIndex >= currentActions.size) activeActionIndex = 0
    }

    private fun syncModeViews(animate: Boolean) {
        if (isStyleBrowseMode) {
            previewCardFrame.visibility = View.GONE; styleCarouselView.visibility = View.VISIBLE
            styleCarouselView.setCurrentStyle(editConfig.styleId)
            styleNamePill.visibility = View.VISIBLE; styleNamePill.text = ClockKitEngine.getStyle(editConfig.styleId).name
        } else {
            styleCarouselView.visibility = View.GONE; styleNamePill.visibility = View.GONE
            previewCardFrame.visibility = View.VISIBLE
            if (animate) {
                previewCardFrame.alpha = 0f; previewCardFrame.scaleX = 0.94f; previewCardFrame.scaleY = 0.94f
                previewCardFrame.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200L).start()
            }
            previewCanvasView.invalidate()
        }
        refreshContextualSlot(); refreshBottomToolbarStrip()
    }

    private fun refreshContextualSlot() {
        if (isStyleBrowseMode) {
            contextualSlot.removeAllViews()
            fastScrollView.apply {
                setScrollStyle(fastScrollStyle)
                setPageCount(ClockKitEngine.styles.size)
                setPage(ClockKitEngine.styles.indexOfFirst { it.id == editConfig.styleId }.coerceAtLeast(0), 0f)
                setColors(theme.accentColor, theme.onSurfaceColor)
                onPageSelected = { page ->
                    ClockKitEngine.styles.getOrNull(page)?.let { settled ->
                        if (settled.id != editConfig.styleId) {
                            editConfig = syncDate(editConfig.copy(styleId = settled.id))
                            styleNamePill.text = settled.name
                            styleCarouselView.smoothScrollToPage(page)
                            onConfigMutated()
                        }
                    }
                }
            }
            contextualSlot.addView(fastScrollView, LayoutParams(dp(190f), dp(32f), Gravity.CENTER))
            return
        }
        val action = currentActions.getOrNull(activeActionIndex) ?: return
        LockscreenOverlayPanels.populateContextualSlot(
            context, contextualSlot, action, editConfig, theme, paletteColors, isDepthEnabled, depthMaskBitmap, statusBarPadPx,
            onConfigUpdated = { next, rebuild ->
                editConfig = syncDate(next)
                if (rebuild) { rebuildActionsList(); refreshBottomToolbarStrip() }
                refreshContextualSlot(); onConfigMutated()
            },
            onDepthToggled = { next -> isDepthEnabled = next }
        )
    }

    private fun refreshBottomToolbarStrip() {
        LockscreenOverlayPanels.populateBottomToolbarStrip(
            context, toolbarContainer, currentActions, activeLayerScope == EditorLayerScope.CLOCK,
            isStyleBrowseMode, activeActionIndex, theme,
            onSelectStyle = { activeLayerScope = EditorLayerScope.CLOCK; isStyleBrowseMode = true; rebuildActionsList(); syncModeViews(animate = true) },
            onSelectAction = { idx -> isStyleBrowseMode = false; activeActionIndex = idx; syncModeViews(animate = false) }
        )
    }

    private fun showLayersSheet() {
        isLayersSheetVisible = true
        LockscreenOverlayPanels.populateLayersSheet(
            context, layersSheetCard, theme, isDepthEnabled, activeLayerScope,
            onDiscard = { dismiss(save = false) },
            onToggleDepth = {
                isDepthEnabled = !isDepthEnabled; editConfig = editConfig.copy(isDepthEnabled = isDepthEnabled)
                previewCanvasView.invalidate(); showLayersSheet()
            },
            onSelectScope = { scope -> focusScope(scope); hideLayersSheet() }
        )
        layersScrim.visibility = View.VISIBLE; layersScrim.alpha = 0f; layersSheetCard.translationY = dpf(140f)
        layersScrim.animate().alpha(1f).setDuration(180L).start()
        layersSheetCard.animate().translationY(0f).setDuration(240L).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun hideLayersSheet() {
        if (!isLayersSheetVisible) return
        isLayersSheetVisible = false
        layersSheetCard.animate().translationY(dpf(140f)).setDuration(170L).start()
        layersScrim.animate().alpha(0f).setDuration(170L).withEndAction { layersScrim.visibility = View.GONE }.start()
    }

    private fun onConfigMutated() {
        editConfig = syncDate(editConfig)
        syncContinuousFrameLoop()
        val style = ClockKitEngine.getStyle(editConfig.styleId)
        if (style.needsCollisionMap(editConfig) && editCollisionMap == null && depthMaskBitmap != null) {
            val mask = depthMaskBitmap!!; val zoom = wallpaperZoom
            Thread {
                val arr = AdaptiveClockHelper.generateCollisionMap(mask, CaveArtXposedModule.screenW, CaveArtXposedModule.screenH, zoom)
                    .split(",").mapNotNull { it.toFloatOrNull() }.toFloatArray()
                post { editCollisionMap = arr; previewCanvasView.invalidate(); styleCarouselView.invalidateActiveCard() }
            }.start()
        }
        previewCanvasView.invalidate(); styleCarouselView.invalidateActiveCard()
    }

    private fun syncDate(cfg: ClockKitConfig) = LockscreenSceneRenderer.clampConfigToScreen(
        context, cfg, CaveArtXposedModule.screenW, CaveArtXposedModule.screenH, density, statusBarPadPx, navBarPadPx
    )

    private fun sceneParamsFor(cfg: ClockKitConfig, showUdfps: Boolean) = LockscreenSceneParams(
        CaveArtXposedModule.screenW, CaveArtXposedModule.screenH, density,
        wallpaperBitmap, depthMaskBitmap, rawDepthBitmap, editCollisionMap,
        cfg, isDepthEnabled, wallpaperZoom, bgVibrance, bgOpacity, subjectOpacity,
        showUdfps, CaveArtXposedModule.cachedUdfpsX, CaveArtXposedModule.cachedUdfpsY, CaveArtXposedModule.cachedUdfpsRadius
    )

    private fun syncContinuousFrameLoop() {
        val run = CaveArtXposedModule.isEditModeActive && ClockKitEngine.getStyle(editConfig.styleId).needsContinuousFrames(editConfig, 0f)
        if (run && !frameLoopRunning) { frameLoopRunning = true; Choreographer.getInstance().postFrameCallback(frameCallback) }
        else if (!run && frameLoopRunning) stopContinuousFrameLoop()
    }

    private fun stopContinuousFrameLoop() { frameLoopRunning = false; Choreographer.getInstance().removeFrameCallback(frameCallback) }

    @Suppress("DEPRECATION")
    private fun startKeepAwake() {
        keepScreenOn = true; CaveArtXposedModule.activeRootLayout?.keepScreenOn = true; pokeUserActivity()
        try {
            if (editorWakeLock == null && powerManager != null) {
                editorWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, "CaveArt:LockscreenEditor")
                    .apply { setReferenceCounted(false) }
            }
            editorWakeLock?.acquire(10 * 60 * 1000L)
        } catch (_: Throwable) {}
        keepAwakeHandler.removeCallbacks(keepAwakeRunnable); keepAwakeHandler.postDelayed(keepAwakeRunnable, 3000L)
    }

    private fun stopKeepAwake() {
        keepAwakeHandler.removeCallbacks(keepAwakeRunnable); keepScreenOn = false
        CaveArtXposedModule.activeRootLayout?.keepScreenOn = false
        try { if (editorWakeLock?.isHeld == true) editorWakeLock?.release() } catch (_: Throwable) {}
    }

    private fun pokeUserActivity() {
        try {
            powerManager?.javaClass?.getMethod("userActivity", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                ?.invoke(powerManager, SystemClock.uptimeMillis(), 2, 0)
        } catch (_: Throwable) {}
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (CaveArtXposedModule.isEditModeActive) pokeUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) return handleBackPressed()
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() { stopKeepAwake(); stopContinuousFrameLoop(); super.onDetachedFromWindow() }
}
