package com.android.CaveArt.xposed

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.*
import android.widget.*
import com.android.CaveArt.MonetEngine
import com.android.CaveArt.clockkit.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

internal object LockscreenOverlayPanels {

    private fun dp(d: Float, v: Float): Int = (v * d).roundToInt()
    private fun dpf(d: Float, v: Float): Float = v * d

    fun resolveTheme(
        context: Context,
        currentPalette: List<Int>,
        backdropStyle: BackdropStyle = BackdropStyle.BLUR_BACKGROUND
    ): Pair<OverlayTheme, List<Int>> {
        val isNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val savedList = getSettingString(context, "palette_colors", "")
            .split(",").mapNotNull { it.trim().toIntOrNull() }
        var palette = if (savedList.isNotEmpty()) savedList else currentPalette

        val seed = palette.firstOrNull() ?: run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                try {
                    WallpaperManager.getInstance(context)
                        .getWallpaperColors(WallpaperManager.FLAG_LOCK)
                        ?.primaryColor?.toArgb() ?: Color.parseColor("#0F5D73")
                } catch (_: Throwable) {
                    Color.parseColor("#0F5D73")
                }
            } else {
                Color.parseColor("#0F5D73")
            }
        }

        val dynamicTheme = MonetEngine.generatePaletteFromSeed(seed, isNight)
        if (palette.isEmpty()) palette = dynamicTheme.allColors
        val accent = dynamicTheme.tonalSpot.firstOrNull() ?: seed

        val theme = if (isNight) {
            val overlayAlpha = when (backdropStyle) {
                BackdropStyle.OFF -> 255
                BackdropStyle.AMBIENT_GLOW -> (BackdropSpec.AMBIENT_SCRIM_ALPHA_DARK * 255).roundToInt()
                BackdropStyle.BLUR_BACKGROUND -> (BackdropSpec.BLUR_BG_SCRIM_ALPHA_DARK * 255).roundToInt()
            }
            val overlayRgb = if (backdropStyle == BackdropStyle.OFF) Triple(29, 31, 36)
            else if (backdropStyle == BackdropStyle.BLUR_BACKGROUND) Triple(0, 0, 0)
            else Triple(18, 20, 24)

            OverlayTheme(
                isNight = true,
                accentColor = accent,
                surfaceOverlayColor = Color.argb(overlayAlpha, overlayRgb.first, overlayRgb.second, overlayRgb.third),
                onSurfaceColor = Color.parseColor("#F1F3F5"),
                onSurfaceMuted = Color.argb(165, 241, 243, 245),
                activePillBg = Color.argb(44, 255, 255, 255),
                sheetBgColor = Color.parseColor("#1D1F24"),
                chipBgColor = Color.argb(36, 255, 255, 255)
            )
        } else {
            val overlayAlpha = when (backdropStyle) {
                BackdropStyle.OFF -> 255
                BackdropStyle.AMBIENT_GLOW -> (BackdropSpec.AMBIENT_SCRIM_ALPHA_LIGHT * 255).roundToInt()
                BackdropStyle.BLUR_BACKGROUND -> (BackdropSpec.BLUR_BG_SCRIM_ALPHA_LIGHT * 255).roundToInt()
            }
            val overlayRgb = if (backdropStyle == BackdropStyle.OFF) Triple(242, 245, 248)
            else Triple(232, 238, 242)

            OverlayTheme(
                isNight = false,
                accentColor = accent,
                surfaceOverlayColor = Color.argb(overlayAlpha, overlayRgb.first, overlayRgb.second, overlayRgb.third),
                onSurfaceColor = Color.parseColor("#191C1E"),
                onSurfaceMuted = Color.argb(175, 25, 28, 30),
                activePillBg = Color.argb(34, 25, 28, 30),
                sheetBgColor = Color.parseColor("#F2F5F8"),
                chipBgColor = Color.argb(26, 25, 28, 30)
            )
        }
        return theme to palette
    }

    fun buildBackdropOverlayDrawable(theme: OverlayTheme, backdropStyle: BackdropStyle): GradientDrawable {
        if (backdropStyle != BackdropStyle.BLUR_BACKGROUND) {
            return GradientDrawable().apply { setColor(theme.surfaceOverlayColor) }
        }
        val top = theme.surfaceOverlayColor
        val bottomAlpha = if (theme.isNight) {
            (BackdropSpec.BLUR_BG_BOTTOM_SCRIM_DARK * 255).roundToInt()
        } else {
            (BackdropSpec.BLUR_BG_BOTTOM_SCRIM_LIGHT * 255).roundToInt()
        }
        val bottom = if (theme.isNight) {
            Color.argb(bottomAlpha, 0, 0, 0)
        } else {
            Color.argb(bottomAlpha, 242, 245, 248)
        }
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, top, bottom))
    }

    fun decodeStreamBitmap(context: Context, uriStr: String): Bitmap? = try {
        context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) { null }

    fun loadLockWallpaperBitmap(context: Context): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val wm = WallpaperManager.getInstance(context)
                val lockFd = wm.getWallpaperFile(WallpaperManager.FLAG_LOCK)
                if (lockFd != null) {
                    lockFd.use { pfd -> decodeSampledFd(pfd.fileDescriptor, 1280)?.let { return it } }
                } else if (wm.wallpaperInfo == null) {
                    wm.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.use { pfd ->
                        decodeSampledFd(pfd.fileDescriptor, 1280)?.let { return it }
                    }
                }
            } catch (_: Throwable) {}
        }

        try {
            context.contentResolver.openFileDescriptor(
                Uri.parse("content://com.android.CaveArt.settings/lock_wallpaper"), "r"
            )?.use { pfd -> decodeSampledFd(pfd.fileDescriptor, 1280)?.let { return it } }
        } catch (_: Exception) {}

        return null
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

    fun buildActions(
        context: Context,
        scope: EditorLayerScope,
        config: ClockKitConfig,
        statusBarPadPx: Int,
        getDepthLimit: () -> Float,
        setDepthLimit: (Float) -> Unit,
        getZoom: () -> Float,
        setZoom: (Float) -> Unit,
        getBgVibrance: () -> Float,
        setBgVibrance: (Float) -> Unit,
        getBgOpacity: () -> Float,
        setBgOpacity: (Float) -> Unit,
        getSubjectOpacity: () -> Float,
        setSubjectOpacity: (Float) -> Unit,
        onConfigChange: (ClockKitConfig) -> Unit
    ): List<OverlayAction> {
        val density = context.resources.displayMetrics.density
        val clockStyle = ClockKitEngine.getStyle(config.styleId)

        return when (scope) {
            EditorLayerScope.ALL -> listOf(
                OverlayAction(
                    "all_occlusion", "Depth Occlusion", ToolbarIconKind.DEPTH, ActionKind.SLIDER,
                    getDepthLimit, setDepthLimit,
                    LockscreenControlSpec.OCCLUSION_RANGE, LockscreenControlSpec::formatPercent
                ),
                OverlayAction(
                    "all_zoom", "Wallpaper Zoom", ToolbarIconKind.ZOOM, ActionKind.SLIDER,
                    getZoom, setZoom,
                    LockscreenControlSpec.ZOOM_RANGE, LockscreenControlSpec::formatZoom
                )
            )
            EditorLayerScope.BACKGROUND -> listOf(
                OverlayAction(
                    "bg_vibrance", "Background Vibrance", ToolbarIconKind.VIBRANCE, ActionKind.SLIDER,
                    getBgVibrance, setBgVibrance,
                    LockscreenControlSpec.VIBRANCE_RANGE, LockscreenControlSpec::formatVibrance
                ),
                OverlayAction(
                    "bg_opacity", "Background Opacity", ToolbarIconKind.OPACITY, ActionKind.SLIDER,
                    getBgOpacity, setBgOpacity,
                    LockscreenControlSpec.OPACITY_RANGE, LockscreenControlSpec::formatPercent
                )
            )
            EditorLayerScope.SUBJECT -> listOf(
                OverlayAction(
                    "subject_opacity", "Subject Opacity", ToolbarIconKind.OPACITY, ActionKind.SLIDER,
                    getSubjectOpacity, setSubjectOpacity,
                    LockscreenControlSpec.OPACITY_RANGE, LockscreenControlSpec::formatPercent
                )
            )
            EditorLayerScope.CLOCK -> buildList {
                clockStyle.describeControls(config).forEach { ctrl ->
                    val kind = when (ctrl) {
                        is ClockControl.Slider -> ActionKind.SLIDER
                        is ClockControl.Choice -> ActionKind.CHOICE
                        is ClockControl.QuickChips -> ActionKind.QUICK_CHIPS
                        is ClockControl.Toggle -> ActionKind.TOGGLE
                        is ClockControl.FontPicker -> ActionKind.FONT
                        is ClockControl.ColorPicker -> ActionKind.COLOR
                    }
                    val iconKind = ClockToolbarIcons.preferredKind(
                        id = ctrl.id,
                        label = ctrl.label,
                        clockIcon = ctrl.icon,
                        isFont = ctrl is ClockControl.FontPicker,
                        isColor = ctrl is ClockControl.ColorPicker,
                        isQuickChips = ctrl is ClockControl.QuickChips
                    )
                    if (ctrl is ClockControl.Slider) {
                        add(OverlayAction(
                            "ck_${clockStyle.id}_${ctrl.id}", ctrl.label, iconKind, kind,
                            { config.getFloat(clockStyle.id, ctrl.id, ctrl.defaultValue) },
                            { v ->
                                val next = LockscreenSceneRenderer.syncAttachedDatePosition(
                                    context, config.withFloat(clockStyle.id, ctrl.id, v),
                                    statusBarPadPx, density,
                                    CaveArtXposedModule.screenW, CaveArtXposedModule.screenH
                                )
                                onConfigChange(next)
                            },
                            ctrl.min..ctrl.max, ctrl.formatter, ctrl
                        ))
                    } else {
                        add(OverlayAction("ck_${clockStyle.id}_${ctrl.id}", ctrl.label, iconKind, kind, control = ctrl))
                    }
                }
                if (config.isGravityEnabled) {
                    add(OverlayAction(
                        "clock_gravity_intensity", "Gravity Slant", ToolbarIconKind.SLANT, ActionKind.SLIDER,
                        { config.gravityIntensity },
                        { v -> onConfigChange(config.copy(gravityIntensity = v)) },
                        LockscreenControlSpec.GRAVITY_INTENSITY_RANGE, LockscreenControlSpec::formatGravity
                    ))
                }
            }
        }
    }

    fun populateContextualSlot(
        context: Context,
        slot: FrameLayout,
        action: OverlayAction,
        editConfig: ClockKitConfig,
        theme: OverlayTheme,
        paletteColors: List<Int>,
        isDepthEnabled: Boolean,
        depthMaskBitmap: Bitmap?,
        statusBarPadPx: Int,
        onConfigUpdated: (ClockKitConfig, rebuildToolbar: Boolean) -> Unit,
        onDepthToggled: (Boolean) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        slot.removeAllViews()

        when (action.kind) {
            ActionKind.SLIDER -> {
                val slider = SlimRulerSliderView(context).apply {
                    configure(
                        iconKind = action.iconKind,
                        label = action.label,
                        value = action.getVal(),
                        min = action.range.start,
                        max = action.range.endInclusive,
                        formatter = action.formatter,
                        accent = theme.accentColor,
                        textColor = theme.onSurfaceColor,
                        tickMuted = Color.argb(65, Color.red(theme.onSurfaceColor), Color.green(theme.onSurfaceColor), Color.blue(theme.onSurfaceColor)),
                        tickMajor = Color.argb(155, Color.red(theme.onSurfaceColor), Color.green(theme.onSurfaceColor), Color.blue(theme.onSurfaceColor))
                    )
                    onValueChanged = { v -> action.setVal(v) }
                }
                slot.addView(slider, FrameLayout.LayoutParams(-1, -1))
            }
            ActionKind.CHOICE -> {
                val ctrl = action.control as? ClockControl.Choice ?: return
                val styleId = editConfig.styleId
                val cur = editConfig.getString(styleId, ctrl.id, ctrl.defaultKey)
                addHorizontalChipsRow(context, slot) { row ->
                    ctrl.options.forEach { opt ->
                        row.addView(
                            makeFilterChip(context, opt.label, opt.key == cur, theme) {
                                onConfigUpdated(editConfig.withString(styleId, ctrl.id, opt.key), false)
                            },
                            LinearLayout.LayoutParams(-2, dp(d, 36f)).apply { marginEnd = dp(d, 8f) }
                        )
                    }
                }
            }
            ActionKind.QUICK_CHIPS -> {
                val ctrl = action.control as? ClockControl.QuickChips ?: return
                val styleId = editConfig.styleId
                addHorizontalChipsRow(context, slot) { row ->
                    ctrl.items.forEach { item ->
                        when (item) {
                            is QuickChipItem.ToggleChip -> {
                                val checked = when (item.globalBinding) {
                                    "depth" -> isDepthEnabled
                                    "gravity" -> editConfig.isGravityEnabled
                                    else -> editConfig.getBool(styleId, item.paramId, item.defaultValue)
                                }
                                row.addView(
                                    makeFilterChip(context, item.label, checked, theme) {
                                        val next = !checked
                                        when (item.globalBinding) {
                                            "depth" -> {
                                                onDepthToggled(next)
                                                onConfigUpdated(editConfig.copy(isDepthEnabled = next), false)
                                            }
                                            "gravity" -> onConfigUpdated(editConfig.copy(isGravityEnabled = next), true)
                                            else -> onConfigUpdated(editConfig.withBool(styleId, item.paramId, next), false)
                                        }
                                    },
                                    LinearLayout.LayoutParams(-2, dp(d, 36f)).apply { marginEnd = dp(d, 8f) }
                                )
                            }
                            is QuickChipItem.CycleChip -> {
                                val cur = editConfig.getString(styleId, item.paramId, item.defaultKey)
                                val idx = item.options.indexOfFirst { it.key == cur }.coerceAtLeast(0)
                                row.addView(
                                    makeFilterChip(context, item.options[idx].label, idx > 0, theme) {
                                        val nextOpt = item.options[(idx + 1) % item.options.size]
                                        onConfigUpdated(editConfig.withString(styleId, item.paramId, nextOpt.key), false)
                                    },
                                    LinearLayout.LayoutParams(-2, dp(d, 36f)).apply { marginEnd = dp(d, 8f) }
                                )
                            }
                            is QuickChipItem.ActionChip -> {
                                row.addView(
                                    makeFilterChip(context, item.label, false, theme) {
                                        depthMaskBitmap?.let { mask ->
                                            val clockStyle = ClockKitEngine.getStyle(styleId)
                                            val fitted = clockStyle.onAction(
                                                item.actionId, editConfig, mask,
                                                CaveArtXposedModule.screenW, CaveArtXposedModule.screenH, d
                                            )
                                            val synced = LockscreenSceneRenderer.syncAttachedDatePosition(
                                                context, fitted, statusBarPadPx, d,
                                                CaveArtXposedModule.screenW, CaveArtXposedModule.screenH
                                            )
                                            onConfigUpdated(synced, false)
                                        }
                                    },
                                    LinearLayout.LayoutParams(-2, dp(d, 36f)).apply { marginEnd = dp(d, 8f) }
                                )
                            }
                        }
                    }
                }
            }
            ActionKind.TOGGLE -> {
                val ctrl = action.control as? ClockControl.Toggle ?: return
                val styleId = editConfig.styleId
                val checked = editConfig.getBool(styleId, ctrl.id, ctrl.defaultValue)
                addHorizontalChipsRow(context, slot) { row ->
                    row.addView(
                        makeFilterChip(context, ctrl.label, checked, theme) {
                            onConfigUpdated(editConfig.withBool(styleId, ctrl.id, !checked), false)
                        },
                        LinearLayout.LayoutParams(-2, dp(d, 36f))
                    )
                }
            }
            ActionKind.FONT -> {
                val fonts = try {
                    val assetCtx = context.createPackageContext("com.android.CaveArt", Context.CONTEXT_IGNORE_SECURITY)
                    val list = assetCtx.assets.list("fonts")?.filter { it.endsWith(".ttf") || it.endsWith(".otf") } ?: emptyList()
                    listOf("default") + list
                } catch (_: Exception) {
                    listOf("default")
                }
                val is24 = DateFormat.is24HourFormat(context)
                val sampleTime = SimpleDateFormat(if (is24) "HH:mm" else "hh:mm", Locale.getDefault()).format(Date())

                addHorizontalChipsRow(context, slot) { row ->
                    fonts.forEach { fontFile ->
                        val selected = editConfig.font == fontFile
                        row.addView(TextView(context).apply {
                            text = sampleTime
                            typeface = ClockTypefaceResolver.resolve(context, fontFile)
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                            setTextColor(if (selected) Color.WHITE else theme.onSurfaceColor)
                            setPadding(dp(d, 14f), 0, dp(d, 14f), 0)
                            background = GradientDrawable().apply {
                                setColor(if (selected) theme.accentColor else theme.chipBgColor)
                                cornerRadius = dpf(d, 12f)
                                if (selected) setStroke(dp(d, 1.5f), theme.accentColor)
                            }
                            setOnClickListener { v ->
                                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onConfigUpdated(editConfig.copy(font = fontFile), false)
                            }
                        }, LinearLayout.LayoutParams(-2, dp(d, 40f)).apply { marginEnd = dp(d, 8f) })
                    }
                }
            }
            ActionKind.COLOR -> {
                val colors = (listOf(Color.WHITE) + paletteColors + listOf(Color.BLACK)).distinct()
                addHorizontalChipsRow(context, slot) { row ->
                    colors.forEach { colorInt ->
                        val selected = editConfig.color == colorInt
                        val container = FrameLayout(context)
                        if (selected) {
                            container.addView(View(context).apply {
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(Color.TRANSPARENT)
                                    setStroke(dp(d, 2f), theme.accentColor)
                                }
                            }, FrameLayout.LayoutParams(-1, -1))
                        }
                        val sz = if (selected) dp(d, 30f) else dp(d, 36f)
                        container.addView(View(context).apply {
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(colorInt)
                            }
                        }, FrameLayout.LayoutParams(sz, sz, Gravity.CENTER))
                        container.setOnClickListener { v ->
                            v.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onConfigUpdated(editConfig.copy(color = colorInt), false)
                        }
                        row.addView(container, LinearLayout.LayoutParams(dp(d, 38f), dp(d, 38f)).apply { marginEnd = dp(d, 8f) })
                    }
                }
            }
        }
    }

    private fun addHorizontalChipsRow(context: Context, slot: FrameLayout, populate: (LinearLayout) -> Unit) {
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        populate(row)
        scroll.addView(row, FrameLayout.LayoutParams(-2, -1))
        slot.addView(scroll, FrameLayout.LayoutParams(-1, -1))
    }

    private fun makeFilterChip(context: Context, label: String, selected: Boolean, theme: OverlayTheme, onClick: () -> Unit): TextView {
        val d = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (selected) Color.WHITE else theme.onSurfaceColor)
            setPadding(dp(d, 14f), 0, dp(d, 14f), 0)
            background = GradientDrawable().apply {
                setColor(if (selected) theme.accentColor else theme.chipBgColor)
                cornerRadius = dpf(d, 18f)
            }
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    fun populateBottomToolbarStrip(
        context: Context,
        container: FrameLayout,
        actions: List<OverlayAction>,
        showStyle: Boolean,
        isStyleBrowseMode: Boolean,
        activeActionIndex: Int,
        theme: OverlayTheme,
        onSelectStyle: () -> Unit,
        onSelectAction: (Int) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        container.removeAllViews()
        val uniqueKinds = ClockToolbarIcons.buildUniqueKinds(actions.map { it.iconKind }, reserveStyle = showStyle)
        val totalCount = actions.size + if (showStyle) 1 else 0

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        if (showStyle) {
            val lp = if (totalCount <= 5) LinearLayout.LayoutParams(0, -2, 1f)
            else LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(d, 6f) }
            row.addView(makeBottomToolbarItem(context, ToolbarIconKind.STYLE, "Style", isStyleBrowseMode, theme, onSelectStyle), lp)
        }

        actions.forEachIndexed { idx, action ->
            val lp = if (totalCount <= 5) LinearLayout.LayoutParams(0, -2, 1f)
            else LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(d, 6f) }
            row.addView(
                makeBottomToolbarItem(
                    context,
                    uniqueKinds[idx],
                    ClockToolbarIcons.shortLabel(action.label),
                    !isStyleBrowseMode && idx == activeActionIndex,
                    theme
                ) { onSelectAction(idx) },
                lp
            )
        }

        if (totalCount <= 5) {
            row.setPadding(dp(d, 12f), 0, dp(d, 12f), 0)
            container.addView(row, FrameLayout.LayoutParams(-1, -1))
        } else {
            row.setPadding(dp(d, 14f), 0, dp(d, 14f), 0)
            val scroll = HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            scroll.addView(row, FrameLayout.LayoutParams(-2, -1))
            container.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        }
    }

    private fun makeBottomToolbarItem(
        context: Context,
        kind: ToolbarIconKind,
        label: String,
        selected: Boolean,
        theme: OverlayTheme,
        onClick: () -> Unit
    ): View {
        val d = context.resources.displayMetrics.density
        val tint = if (selected) theme.onSurfaceColor else theme.onSurfaceMuted
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(d, 62f)
            setPadding(dp(d, 6f), dp(d, 4f), dp(d, 6f), dp(d, 4f))
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(if (selected) theme.activePillBg else Color.TRANSPARENT)
                    cornerRadius = dpf(d, 14f)
                }
                addView(
                    UnifiedLineIconView(context, kind, tint, dp(d, 22f)),
                    FrameLayout.LayoutParams(dp(d, 22f), dp(d, 22f), Gravity.CENTER)
                )
            }, LinearLayout.LayoutParams(dp(d, 48f), dp(d, 28f)))
            addView(TextView(context).apply {
                text = label
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setTextColor(tint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                typeface = Typeface.create("sans-serif", if (selected) Typeface.BOLD else Typeface.NORMAL)
                setPadding(0, dp(d, 4f), 0, 0)
            }, LinearLayout.LayoutParams(-2, -2))
            setOnClickListener { v ->
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    fun populateLayersSheet(
        context: Context,
        sheetCard: LinearLayout,
        theme: OverlayTheme,
        isDepthEnabled: Boolean,
        activeScope: EditorLayerScope,
        onDiscard: () -> Unit,
        onToggleDepth: () -> Unit,
        onSelectScope: (EditorLayerScope) -> Unit
    ) {
        val d = context.resources.displayMetrics.density
        sheetCard.removeAllViews()
        sheetCard.background = GradientDrawable().apply {
            setColor(theme.sheetBgColor)
            cornerRadii = floatArrayOf(dpf(d, 28f), dpf(d, 28f), dpf(d, 28f), dpf(d, 28f), 0f, 0f, 0f, 0f)
        }

        sheetCard.addView(View(context).apply {
            background = GradientDrawable().apply { setColor(theme.onSurfaceMuted); cornerRadius = dpf(d, 2f) }
        }, LinearLayout.LayoutParams(dp(d, 36f), dp(d, 4f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(d, 12f) })

        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "Spatial Layers"
            setTextColor(theme.onSurfaceColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(context).apply {
            text = "Discard"
            setTextColor(Color.parseColor("#D32F2F"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(d, 12f), dp(d, 6f), dp(d, 12f), dp(d, 6f))
            background = GradientDrawable().apply { setColor(Color.argb(26, 211, 47, 47)); cornerRadius = dpf(d, 16f) }
            setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onDiscard() }
        })
        sheetCard.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(d, 12f) })

        fun addLayerRow(
            name: String, subtitle: String?, iconKind: ToolbarIconKind,
            selected: Boolean, toggleChecked: Boolean? = null,
            onToggle: (() -> Unit)? = null, onSelect: () -> Unit
        ) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(d, 14f), 0, dp(d, 10f), 0)
                background = GradientDrawable().apply {
                    setColor(
                        if (selected) Color.argb(38, Color.red(theme.accentColor), Color.green(theme.accentColor), Color.blue(theme.accentColor))
                        else theme.chipBgColor
                    )
                    cornerRadius = dpf(d, 16f)
                    if (selected) setStroke(dp(d, 1.5f), theme.accentColor)
                }
                setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onSelect() }
            }
            card.addView(
                UnifiedLineIconView(context, iconKind, if (selected) theme.accentColor else theme.onSurfaceColor, dp(d, 22f)),
                LinearLayout.LayoutParams(dp(d, 22f), dp(d, 22f)).apply { marginEnd = dp(d, 12f) }
            )

            val textCol = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            textCol.addView(TextView(context).apply {
                text = name; setTextColor(theme.onSurfaceColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            })
            if (subtitle != null) {
                textCol.addView(TextView(context).apply {
                    text = subtitle; setTextColor(theme.accentColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                })
            }
            card.addView(textCol, LinearLayout.LayoutParams(0, -2, 1f))

            if (toggleChecked != null && onToggle != null) {
                card.addView(TextView(context).apply {
                    text = if (toggleChecked) "ON" else "OFF"
                    setTextColor(if (toggleChecked) Color.WHITE else theme.onSurfaceMuted)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    setPadding(dp(d, 12f), dp(d, 5f), dp(d, 12f), dp(d, 5f))
                    background = GradientDrawable().apply {
                        setColor(if (toggleChecked) theme.accentColor else theme.chipBgColor)
                        cornerRadius = dpf(d, 14f)
                    }
                    setOnClickListener { v -> v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); onToggle() }
                })
            }
            sheetCard.addView(card, LinearLayout.LayoutParams(-1, dp(d, 58f)).apply { bottomMargin = dp(d, 8f) })
        }

        addLayerRow("Subject", if (isDepthEnabled) "Foreground Depth Active" else "Depth Disabled", ToolbarIconKind.DEPTH,
            activeScope == EditorLayerScope.SUBJECT, isDepthEnabled, onToggleDepth
        ) { onSelectScope(EditorLayerScope.SUBJECT) }
        addLayerRow("Clock Face", if (isDepthEnabled) "Behind Subject" else "In Front", ToolbarIconKind.CLOCK,
            activeScope == EditorLayerScope.CLOCK) { onSelectScope(EditorLayerScope.CLOCK) }
        addLayerRow("Background", "Vibrance & Opacity", ToolbarIconKind.VIBRANCE,
            activeScope == EditorLayerScope.BACKGROUND) { onSelectScope(EditorLayerScope.BACKGROUND) }
        addLayerRow("Depth & Zoom", "Occlusion Threshold & Framing", ToolbarIconKind.ZOOM,
            activeScope == EditorLayerScope.ALL) { onSelectScope(EditorLayerScope.ALL) }
    }

    fun persistAllToCaveArt(
        context: Context,
        cfg: ClockKitConfig,
        collMap: FloatArray?,
        isDepthEnabled: Boolean,
        depthOcclusionLimit: Float,
        bgVibrance: Float,
        subjectOpacity: Float,
        bgOpacity: Float
    ) {
        Thread {
            try {
                context.contentResolver.call(Uri.parse("content://com.android.CaveArt.settings"), "saveClockKitConfig", null,
                    Bundle().apply {
                        putString("clockkit_config", ClockKitConfigStore.toJson(cfg))
                        if (collMap != null) putString("collision_map", collMap.joinToString(","))
                    })
                context.contentResolver.call(Uri.parse("content://com.android.CaveArt.settings"), "updateDepth", null,
                    Bundle().apply {
                        putBoolean("clock_depth_enabled", isDepthEnabled)
                        putInt("clock_layer_order", if (isDepthEnabled) 1 else 0)
                        putFloat("clock_depth_occlusion", depthOcclusionLimit)
                        putFloat("layer_structure", bgVibrance)
                        putFloat("layer_subject_opacity", subjectOpacity)
                        putFloat("layer_bg_opacity", bgOpacity)
                    })
            } catch (_: Exception) {}
        }.start()
    }
}
