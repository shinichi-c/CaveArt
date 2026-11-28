package com.android.CaveArt.lockscreenkit

import android.app.PendingIntent
import android.app.WallpaperColors
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import android.service.notification.StatusBarNotification
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class NotificationStyle(
    val id: Int,
    val label: String,
    val hidesStockNsslOnKeyguard: Boolean,
    val collapsesClockWhenNotifsPresent: Boolean
) {
    STOCK(0, "Stock", hidesStockNsslOnKeyguard = false, collapsesClockWhenNotifsPresent = true),
    ICONS_ONLY(1, "Icons Only", hidesStockNsslOnKeyguard = true, collapsesClockWhenNotifsPresent = false),
    FROSTED_PILL(2, "Compact Pill", hidesStockNsslOnKeyguard = true, collapsesClockWhenNotifsPresent = false),
    GROUPED_CARDS(3, "Grouped Cards", hidesStockNsslOnKeyguard = true, collapsesClockWhenNotifsPresent = true),
    HIDDEN(4, "Hidden", hidesStockNsslOnKeyguard = true, collapsesClockWhenNotifsPresent = false);

    companion object {
        fun fromId(id: Int): NotificationStyle =
            values().firstOrNull { it.id == id } ?: GROUPED_CARDS
    }
}

enum class NotificationSurfaceMode(val id: Int, val label: String) {
    AUTO_CONTRAST(0, "Solid"),
    FROSTED_GLASS(1, "Blur Glass"),
    MONET_TINTED(2, "Monet"),
    AMBIENT_GLOW(3, "Ambient Glow");

    companion object {
        fun fromId(id: Int): NotificationSurfaceMode =
            values().firstOrNull { it.id == id } ?: FROSTED_GLASS
    }
}

object NotificationThemeSpec {
    const val LIGHT_WALLPAPER_THRESHOLD = 0.66f

    const val WHITE_SURFACE_ALPHA = 0.90f
    const val WHITE_SURFACE_PRIMARY_INK = 0xFF14161A.toInt()
    const val WHITE_SURFACE_SECONDARY_INK = 0xFF4A515B.toInt()
    const val WHITE_SURFACE_MUTED_INK = 0xFF727A86.toInt()

    const val DARK_SURFACE_RGB_R = 26
    const val DARK_SURFACE_RGB_G = 28
    const val DARK_SURFACE_RGB_B = 34
    const val DARK_SURFACE_ALPHA = 0.84f
    const val DARK_SURFACE_PRIMARY_INK = Color.WHITE
    const val DARK_SURFACE_SECONDARY_INK = 0xFFD6DCE6.toInt()
    const val DARK_SURFACE_MUTED_INK = 0xFF9AA3B2.toInt()

    const val FROST_DARK_FILL_ALPHA = 0.46f
    const val FROST_LIGHT_FILL_ALPHA = 0.66f

    const val CARD_CORNER_RADIUS_DP = 24f
    const val CARD_SIDE_PADDING_DP = 16f
    const val CARD_GAP_DP = 10f
}

data class LockNotifAction(
    val title: String,
    val intent: PendingIntent? = null,
    val remoteInputs: List<android.app.RemoteInput> = emptyList(),
    val smartChoices: List<String> = emptyList(),
    val isAuthenticationRequired: Boolean = false,
    val semanticAction: Int = 0
) {
    val hasRemoteInput: Boolean get() = remoteInputs.isNotEmpty()
}

data class LockNotifMessage(
    val key: String,
    val title: String,
    val text: String,
    val postTimeMs: Long,
    val contentIntent: PendingIntent? = null,
    val isClearable: Boolean = true,
    val isRedacted: Boolean = false,
    val avatarIcon: Bitmap? = null,
    val pictureBitmap: Bitmap? = null,
    val progress: Int = -1,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    val actions: List<LockNotifAction> = emptyList(),
    val sbn: StatusBarNotification? = null,
    val usesChronometer: Boolean = false,
    val chronometerBaseMs: Long = 0L,
    val chronometerCountDown: Boolean = false
) {
    val hasProgress: Boolean get() = !isRedacted && (progressIndeterminate || (progressMax > 0 && progress >= 0))
    val isExpandableSingle: Boolean get() = !isRedacted && (text.length > 52 || actions.isNotEmpty() || (pictureBitmap != null && !pictureBitmap.isRecycled))
}

data class LockNotifAppGroup(
    val groupId: String,
    val packageName: String,
    val appName: String,
    val smallIcon: Bitmap? = null,
    val appIcon: Bitmap? = null,
    val avatarIcon: Bitmap? = null,
    val pictureBitmap: Bitmap? = null,
    val accentColor: Int = Color.WHITE,
    val items: List<LockNotifMessage>,
    val latestPostTimeMs: Long,
    val primaryTitle: String,
    val primaryText: String,
    val primaryIntent: PendingIntent? = null,
    val isRedacted: Boolean = false,
    val isClearable: Boolean = true,
    val leadSbn: StatusBarNotification? = null
) {
    val count: Int get() = items.size
    val canExpand: Boolean get() = count > 1 || (items.firstOrNull()?.isExpandableSingle == true)
}

enum class NotificationHitKind {
    ICON_LAUNCH,
    ICON_PEEK,
    PEEK_CARD,
    GROUP_CARD,
    MESSAGE_ROW,
    ACTION_PILL,
    ICONS_CONTAINER,
    CLEAR_ALL
}

data class NotificationHitTarget(
    val kind: NotificationHitKind,
    val bounds: RectF,
    val groupId: String? = null,
    val messageIndex: Int = -1,
    val actionIndex: Int = -1,
    val choiceIndex: Int = -1
)

data class NotificationHitLayout(
    val totalBounds: RectF = RectF(),
    val targets: List<NotificationHitTarget> = emptyList()
)

object NotificationKitSampleProvider {

    private class SampleAssets {
        val waApp = makeAppTile(0xFF25D366.toInt()) { c, s -> drawChatGlyph(c, s, Color.WHITE, fill = true) }
        val waSmall = makeSmallGlyph { c, s -> drawChatGlyph(c, s, Color.WHITE, fill = false) }
        val alexAvatar = makeSampleAvatar()

        val gmApp = makeAppTile(Color.WHITE) { c, s -> drawMailGlyph(c, s, 0xFFEA4335.toInt()) }
        val gmSmall = makeSmallGlyph { c, s -> drawMailGlyph(c, s, Color.WHITE) }

        val playApp = makeAppTile(0xFF0B57D0.toInt()) { c, s -> drawPlayGlyph(c, s, Color.WHITE) }
        val playSmall = makeSmallGlyph { c, s -> drawPlayGlyph(c, s, Color.WHITE) }

        val igApp = makeGradientTile(0xFF833AB4.toInt(), 0xFFE1306C.toInt(), 0xFFF77737.toInt()) { c, s ->
            drawCamGlyph(c, s, Color.WHITE)
        }
        val igSmall = makeSmallGlyph { c, s -> drawCamGlyph(c, s, Color.WHITE) }

        private fun makeAppTile(bg: Int, drawGlyph: (Canvas, Float) -> Unit): Bitmap {
            val sz = 84
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(bg)
            drawGlyph(c, sz.toFloat())
            return bmp
        }

        private fun makeGradientTile(c1: Int, c2: Int, c3: Int, drawGlyph: (Canvas, Float) -> Unit): Bitmap {
            val sz = 84
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, sz.toFloat(), sz.toFloat(), 0f, intArrayOf(c1, c2, c3), null, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, sz.toFloat(), sz.toFloat(), p)
            drawGlyph(c, sz.toFloat())
            return bmp
        }

        private fun makeSmallGlyph(drawGlyph: (Canvas, Float) -> Unit): Bitmap {
            val sz = 48
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            drawGlyph(Canvas(bmp), sz.toFloat())
            return bmp
        }

        private fun makeSampleAvatar(): Bitmap {
            val sz = 84
            val s = sz.toFloat()
            val bmp = Bitmap.createBitmap(sz, sz, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, 0f, s, s, intArrayOf(0xFF4F8FF7.toInt(), 0xFF1E3C72.toInt()), null, Shader.TileMode.CLAMP)
            }
            c.drawRect(0f, 0f, s, s, bg)
            val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 240, 246, 255) }
            c.drawCircle(s * 0.5f, s * 0.38f, s * 0.17f, fg)
            c.drawOval(RectF(s * 0.21f, s * 0.60f, s * 0.79f, s * 1.05f), fg)
            return bmp
        }

        private fun drawChatGlyph(c: Canvas, s: Float, color: Int, fill: Boolean) {
            val u = s / 24f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = if (fill) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = 2.1f * u
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            val path = Path().apply {
                moveTo(5.5f * u, 18.5f * u)
                lineTo(6.2f * u, 15.2f * u)
                cubicTo(4.6f * u, 12.6f * u, 5.4f * u, 6.2f * u, 12f * u, 5.8f * u)
                cubicTo(18.6f * u, 5.4f * u, 19.6f * u, 12.4f * u, 16.4f * u, 16.0f * u)
                cubicTo(14.2f * u, 18.2f * u, 10.6f * u, 18.5f * u, 8.6f * u, 17.6f * u)
                close()
            }
            c.drawPath(path, p)
        }

        private fun drawMailGlyph(c: Canvas, s: Float, color: Int) {
            val u = s / 24f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 2.1f * u
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
            c.drawRoundRect(RectF(4.2f * u, 6.2f * u, 19.8f * u, 17.8f * u), 2.2f * u, 2.2f * u, p)
            c.drawPath(Path().apply {
                moveTo(4.8f * u, 7.2f * u); lineTo(12f * u, 13.0f * u); lineTo(19.2f * u, 7.2f * u)
            }, p)
        }

        private fun drawPlayGlyph(c: Canvas, s: Float, color: Int) {
            val u = s / 24f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            c.drawPath(Path().apply {
                moveTo(7.5f * u, 5.5f * u)
                lineTo(18.5f * u, 12f * u)
                lineTo(7.5f * u, 18.5f * u)
                close()
            }, p)
        }

        private fun drawCamGlyph(c: Canvas, s: Float, color: Int) {
            val u = s / 24f
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 2.0f * u
            }
            c.drawRoundRect(RectF(4.8f * u, 4.8f * u, 19.2f * u, 19.2f * u), 4.2f * u, 4.2f * u, p)
            c.drawCircle(12f * u, 12f * u, 3.6f * u, p)
        }
    }

    private val assets by lazy { SampleAssets() }

    fun getSamples(nowMs: Long = System.currentTimeMillis()): List<LockNotifAppGroup> = listOf(
        LockNotifAppGroup(
            groupId = "com.whatsapp:0",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            smallIcon = assets.waSmall,
            appIcon = assets.waApp,
            avatarIcon = assets.alexAvatar,
            accentColor = 0xFF25D366.toInt(),
            items = listOf(
                LockNotifMessage(
                    key = "sample_wa_1",
                    title = "Alex",
                    text = "See you at 7:30 near the metro station!",
                    postTimeMs = nowMs - 2 * 60_000L,
                    avatarIcon = assets.alexAvatar,
                    actions = listOf(LockNotifAction("Mark read"))
                ),
                LockNotifMessage(
                    key = "sample_wa_2",
                    title = "Alex",
                    text = "Sent a photo",
                    postTimeMs = nowMs - 4 * 60_000L,
                    avatarIcon = assets.alexAvatar
                )
            ),
            latestPostTimeMs = nowMs - 2 * 60_000L,
            primaryTitle = "Alex",
            primaryText = "See you at 7:30 near the metro station!"
        ),
        LockNotifAppGroup(
            groupId = "com.google.android.gm:0",
            packageName = "com.google.android.gm",
            appName = "Gmail",
            smallIcon = assets.gmSmall,
            appIcon = assets.gmApp,
            accentColor = 0xFFEA4335.toInt(),
            items = listOf(
                LockNotifMessage(
                    key = "sample_gm_1",
                    title = "Design Review",
                    text = "Updated lockscreen specs and high-res assets attached for final design review before tonight's build.",
                    postTimeMs = nowMs - 14 * 60_000L,
                    actions = listOf(LockNotifAction("Archive"), LockNotifAction("Mark read"))
                )
            ),
            latestPostTimeMs = nowMs - 14 * 60_000L,
            primaryTitle = "Design Review",
            primaryText = "Updated lockscreen specs and high-res assets attached for final design review before tonight's build."
        ),
        LockNotifAppGroup(
            groupId = "com.android.vending:0",
            packageName = "com.android.vending",
            appName = "Play Store",
            smallIcon = assets.playSmall,
            appIcon = assets.playApp,
            accentColor = 0xFF0B57D0.toInt(),
            items = listOf(
                LockNotifMessage(
                    key = "sample_dl_1",
                    title = "Downloading Resources",
                    text = "68% • 42 MB of 62 MB",
                    postTimeMs = nowMs - 1 * 60_000L,
                    isClearable = false,
                    progress = 68,
                    progressMax = 100
                )
            ),
            latestPostTimeMs = nowMs - 1 * 60_000L,
            primaryTitle = "Downloading Resources",
            primaryText = "68% • 42 MB of 62 MB",
            isClearable = false
        ),
        LockNotifAppGroup(
            groupId = "com.instagram.android:0",
            packageName = "com.instagram.android",
            appName = "Instagram",
            smallIcon = assets.igSmall,
            appIcon = assets.igApp,
            accentColor = 0xFFE1306C.toInt(),
            items = listOf(
                LockNotifMessage(
                    key = "sample_ig_1",
                    title = "maya_k",
                    text = "liked your story",
                    postTimeMs = nowMs - 38 * 60_000L
                )
            ),
            latestPostTimeMs = nowMs - 38 * 60_000L,
            primaryTitle = "maya_k",
            primaryText = "liked your story"
        )
    )
}

object NotificationKitRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glyphStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val badgeTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val clipPath = Path()
    private val scratchMatrix = Matrix()
    private val scratchRect = RectF()
    private val scratchRect2 = RectF()

    @Volatile
    private var frostSourceRef: WeakReference<Bitmap>? = null

    @Volatile
    private var cachedFrostBitmap: Bitmap? = null

    @Volatile
    private var ambientSourceRef: WeakReference<Bitmap>? = null

    @Volatile
    private var cachedAmbientBitmap: Bitmap? = null

    fun isSystemNightMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    @Synchronized
    private fun getOrCreateFrostBitmap(src: Bitmap?): Bitmap? {
        if (src == null || src.isRecycled) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            return null
        }
        val existing = cachedFrostBitmap
        if (frostSourceRef?.get() === src && existing != null && !existing.isRecycled) {
            return existing
        }
        val generated = LockscreenSceneRenderer.createNotificationFrostBitmap(src)
        frostSourceRef = WeakReference(src)
        if (existing != null && existing !== generated && !existing.isRecycled) {
            existing.recycle()
        }
        cachedFrostBitmap = generated
        return generated
    }

    @Synchronized
    private fun getOrCreateAmbientBitmap(src: Bitmap?): Bitmap? {
        if (src == null || src.isRecycled) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
            return null
        }
        val existing = cachedAmbientBitmap
        if (ambientSourceRef?.get() === src && existing != null && !existing.isRecycled) {
            return existing
        }
        val generated = LockscreenSceneRenderer.createNotificationAmbientBitmap(src)
        ambientSourceRef = WeakReference(src)
        if (existing != null && existing !== generated && !existing.isRecycled) {
            existing.recycle()
        }
        cachedAmbientBitmap = generated
        return generated
    }

    private data class ResolvedPalette(
        val isLightWallpaper: Boolean,
        val isLightBackdrop: Boolean,
        val frostActive: Boolean,
        val surfaceColor: Int,
        val lipColor: Int,
        val borderColor: Int,
        val primaryInk: Int,
        val secondaryInk: Int,
        val mutedInk: Int,
        val badgeBg: Int,
        val dividerColor: Int,
        val shadowColor: Int,
        val accentColor: Int,
        val glyphInk: Int,
        val glowColor: Int = 0
    )

    private fun resolveMonetSeedColor(
        config: LockscreenConfig,
        fallbackColors: WallpaperColors?,
        wallpaperBitmap: Bitmap?
    ): Int {
        val c = config.color
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        if (c != 0 && (max(r, max(g, b)) - min(r, min(g, b))) >= 28) return c
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && fallbackColors != null) {
            try {
                return fallbackColors.primaryColor.toArgb()
            } catch (_: Throwable) {}
        }
        if (wallpaperBitmap != null && !wallpaperBitmap.isRecycled &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wallpaperBitmap.config == Bitmap.Config.HARDWARE)
        ) {
            val cx = (wallpaperBitmap.width / 2).coerceIn(0, wallpaperBitmap.width - 1)
            val cy = (wallpaperBitmap.height / 2).coerceIn(0, wallpaperBitmap.height - 1)
            return wallpaperBitmap.getPixel(cx, cy)
        }
        return 0xFF4A80D4.toInt()
    }

    private fun resolvePalette(
        surfaceMode: NotificationSurfaceMode,
        isNightMode: Boolean,
        isLightWallpaper: Boolean,
        alphaInt: Int,
        hasBackdropBitmap: Boolean,
        config: LockscreenConfig,
        fallbackColors: WallpaperColors?,
        wallpaperBitmap: Bitmap?
    ): ResolvedPalette {
        val alphaFrac = alphaInt / 255f
        val bareGlyphInk = if (!isLightWallpaper) {
            Color.argb(alphaInt, 255, 255, 255)
        } else {
            scaleAlpha(AtAGlanceColorResolver.DARK_INK_COLOR, alphaFrac)
        }
        val seed = resolveMonetSeedColor(config, fallbackColors, wallpaperBitmap)

        return when (surfaceMode) {
            NotificationSurfaceMode.FROSTED_GLASS -> {
                if (isNightMode) {
                    val baseA = if (hasBackdropBitmap) 0.42f else 0.72f
                    ResolvedPalette(
                        isLightWallpaper = false,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = hasBackdropBitmap,
                        surfaceColor = Color.argb((alphaInt * baseA).roundToInt(), 16, 18, 24),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.24f).roundToInt(), 255, 255, 255),
                        primaryInk = Color.argb(alphaInt, 255, 255, 255),
                        secondaryInk = Color.argb((alphaInt * 0.85f).roundToInt(), 230, 235, 244),
                        mutedInk = Color.argb((alphaInt * 0.64f).roundToInt(), 196, 204, 218),
                        badgeBg = Color.argb((alphaInt * 0.17f).roundToInt(), 255, 255, 255),
                        dividerColor = Color.argb((alphaInt * 0.12f).roundToInt(), 255, 255, 255),
                        shadowColor = Color.argb((alphaInt * 0.18f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 168, 208, 255),
                        glyphInk = bareGlyphInk,
                        glowColor = if (!hasBackdropBitmap) seed else 0
                    )
                } else {
                    val baseA = if (hasBackdropBitmap) 0.56f else 0.86f
                    ResolvedPalette(
                        isLightWallpaper = true,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = hasBackdropBitmap,
                        surfaceColor = Color.argb((alphaInt * baseA).roundToInt(), 246, 248, 252),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.20f).roundToInt(), 255, 255, 255),
                        primaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_PRIMARY_INK, alphaFrac),
                        secondaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_SECONDARY_INK, alphaFrac),
                        mutedInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_MUTED_INK, alphaFrac),
                        badgeBg = Color.argb((alphaInt * 0.09f).roundToInt(), 20, 22, 26),
                        dividerColor = Color.argb((alphaInt * 0.08f).roundToInt(), 20, 22, 26),
                        shadowColor = Color.argb((alphaInt * 0.11f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 24, 92, 188),
                        glyphInk = bareGlyphInk,
                        glowColor = if (!hasBackdropBitmap) seed else 0
                    )
                }
            }
            NotificationSurfaceMode.AMBIENT_GLOW -> {
                if (isNightMode) {
                    val baseA = if (hasBackdropBitmap) BackdropSpec.AMBIENT_SCRIM_ALPHA_DARK else 0.78f
                    ResolvedPalette(
                        isLightWallpaper = false,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = hasBackdropBitmap,
                        surfaceColor = Color.argb((alphaInt * baseA).roundToInt(), 20, 22, 28),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.20f).roundToInt(), 255, 255, 255),
                        primaryInk = Color.argb(alphaInt, 255, 255, 255),
                        secondaryInk = Color.argb((alphaInt * 0.86f).roundToInt(), 228, 232, 240),
                        mutedInk = Color.argb((alphaInt * 0.65f).roundToInt(), 190, 198, 212),
                        badgeBg = Color.argb((alphaInt * 0.16f).roundToInt(), 255, 255, 255),
                        dividerColor = Color.argb((alphaInt * 0.11f).roundToInt(), 255, 255, 255),
                        shadowColor = Color.argb((alphaInt * 0.18f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 168, 199, 250),
                        glyphInk = bareGlyphInk,
                        glowColor = seed
                    )
                } else {
                    val baseA = if (hasBackdropBitmap) BackdropSpec.AMBIENT_SCRIM_ALPHA_LIGHT else 0.86f
                    ResolvedPalette(
                        isLightWallpaper = true,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = hasBackdropBitmap,
                        surfaceColor = Color.argb((alphaInt * baseA).roundToInt(), 244, 246, 250),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.16f).roundToInt(), 20, 22, 26),
                        primaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_PRIMARY_INK, alphaFrac),
                        secondaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_SECONDARY_INK, alphaFrac),
                        mutedInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_MUTED_INK, alphaFrac),
                        badgeBg = Color.argb((alphaInt * 0.08f).roundToInt(), 20, 22, 26),
                        dividerColor = Color.argb((alphaInt * 0.08f).roundToInt(), 20, 22, 26),
                        shadowColor = Color.argb((alphaInt * 0.11f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 26, 115, 232),
                        glyphInk = bareGlyphInk,
                        glowColor = seed
                    )
                }
            }
            NotificationSurfaceMode.MONET_TINTED -> {
                val hsv = FloatArray(3)
                Color.colorToHSV(seed, hsv)
                val hue = hsv[0]
                if (isNightMode) {
                    val surfCol = Color.HSVToColor((alphaInt * 0.90f).roundToInt(), floatArrayOf(hue, hsv[1].coerceIn(0.18f, 0.34f), 0.17f))
                    val primInk = Color.HSVToColor(alphaInt, floatArrayOf(hue, 0.08f, 0.98f))
                    val secInk = Color.HSVToColor((alphaInt * 0.86f).roundToInt(), floatArrayOf(hue, 0.14f, 0.86f))
                    val mutInk = Color.HSVToColor((alphaInt * 0.68f).roundToInt(), floatArrayOf(hue, 0.16f, 0.70f))
                    val accent = Color.HSVToColor(alphaInt, floatArrayOf(hue, hsv[1].coerceIn(0.40f, 0.72f), 0.88f))
                    ResolvedPalette(
                        isLightWallpaper = false,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = false,
                        surfaceColor = surfCol,
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.18f).roundToInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                        primaryInk = primInk,
                        secondaryInk = secInk,
                        mutedInk = mutInk,
                        badgeBg = Color.argb((alphaInt * 0.16f).roundToInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                        dividerColor = Color.argb((alphaInt * 0.11f).roundToInt(), 255, 255, 255),
                        shadowColor = Color.argb((alphaInt * 0.18f).roundToInt(), 0, 0, 0),
                        accentColor = accent,
                        glyphInk = bareGlyphInk
                    )
                } else {
                    val surfCol = Color.HSVToColor((alphaInt * 0.94f).roundToInt(), floatArrayOf(hue, hsv[1].coerceIn(0.08f, 0.18f), 0.96f))
                    val primInk = Color.HSVToColor(alphaInt, floatArrayOf(hue, 0.25f, 0.13f))
                    val secInk = Color.HSVToColor((alphaInt * 0.84f).roundToInt(), floatArrayOf(hue, 0.22f, 0.30f))
                    val mutInk = Color.HSVToColor((alphaInt * 0.68f).roundToInt(), floatArrayOf(hue, 0.18f, 0.44f))
                    val accent = Color.HSVToColor(alphaInt, floatArrayOf(hue, hsv[1].coerceIn(0.50f, 0.82f), 0.48f))
                    ResolvedPalette(
                        isLightWallpaper = true,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = false,
                        surfaceColor = surfCol,
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.18f).roundToInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                        primaryInk = primInk,
                        secondaryInk = secInk,
                        mutedInk = mutInk,
                        badgeBg = Color.argb((alphaInt * 0.12f).roundToInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
                        dividerColor = Color.argb((alphaInt * 0.09f).roundToInt(), Color.red(primInk), Color.green(primInk), Color.blue(primInk)),
                        shadowColor = Color.argb((alphaInt * 0.12f).roundToInt(), 0, 0, 0),
                        accentColor = accent,
                        glyphInk = bareGlyphInk
                    )
                }
            }
            NotificationSurfaceMode.AUTO_CONTRAST -> {
                if (isNightMode) {
                    ResolvedPalette(
                        isLightWallpaper = false,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = false,
                        surfaceColor = Color.argb(
                            (alphaInt * 0.92f).roundToInt(),
                            NotificationThemeSpec.DARK_SURFACE_RGB_R,
                            NotificationThemeSpec.DARK_SURFACE_RGB_G,
                            NotificationThemeSpec.DARK_SURFACE_RGB_B
                        ),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.14f).roundToInt(), 255, 255, 255),
                        primaryInk = Color.argb(alphaInt, 255, 255, 255),
                        secondaryInk = scaleAlpha(NotificationThemeSpec.DARK_SURFACE_SECONDARY_INK, alphaFrac),
                        mutedInk = scaleAlpha(NotificationThemeSpec.DARK_SURFACE_MUTED_INK, alphaFrac),
                        badgeBg = Color.argb((alphaInt * 0.15f).roundToInt(), 255, 255, 255),
                        dividerColor = Color.argb((alphaInt * 0.11f).roundToInt(), 255, 255, 255),
                        shadowColor = Color.argb((alphaInt * 0.18f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 168, 199, 250),
                        glyphInk = bareGlyphInk
                    )
                } else {
                    ResolvedPalette(
                        isLightWallpaper = true,
                        isLightBackdrop = isLightWallpaper,
                        frostActive = false,
                        surfaceColor = Color.argb((alphaInt * 0.95f).roundToInt(), 246, 248, 251),
                        lipColor = Color.TRANSPARENT,
                        borderColor = Color.argb((alphaInt * 0.11f).roundToInt(), 20, 22, 26),
                        primaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_PRIMARY_INK, alphaFrac),
                        secondaryInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_SECONDARY_INK, alphaFrac),
                        mutedInk = scaleAlpha(NotificationThemeSpec.WHITE_SURFACE_MUTED_INK, alphaFrac),
                        badgeBg = Color.argb((alphaInt * 0.08f).roundToInt(), 20, 22, 26),
                        dividerColor = Color.argb((alphaInt * 0.08f).roundToInt(), 20, 22, 26),
                        shadowColor = Color.argb((alphaInt * 0.11f).roundToInt(), 0, 0, 0),
                        accentColor = Color.argb(alphaInt, 26, 115, 232),
                        glyphInk = bareGlyphInk
                    )
                }
            }
        }
    }

    private fun buildBodyLayout(text: String, maxWidthPx: Int, maxLines: Int): StaticLayout {
        val safeW = maxWidthPx.coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, safeW)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.08f)
            .setIncludePad(false)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun measureCardHeightPx(
        group: LockNotifAppGroup,
        cardW: Float,
        config: LockscreenConfig,
        density: Float,
        cardExpandP: Float
    ): Float {
        val lead = group.items.firstOrNull()
        val hasLeadProgress = lead?.hasProgress == true
        val collapsedH = (if (hasLeadProgress) 86f else 74f) * density
        if (cardExpandP <= 0.01f) return collapsedH

        val padH = 16f * density
        val iconSz = 40f * density
        val textLeftOffset = padH + iconSz + 13f * density
        val bodyMaxW = (cardW - padH - textLeftOffset).toInt().coerceAtLeast((80f * density).toInt())

        val expandedH = if (group.count > 1) {
            val visibleMsgs = group.items.take(3)
            var h = 46f * density
            visibleMsgs.forEach { msg -> h += (if (msg.hasProgress) 52f else 42f) * density }
            if (config.notificationShowActions && lead != null && !lead.isRedacted && lead.actions.isNotEmpty()) {
                h += 40f * density
            }
            h + 10f * density
        } else if (lead != null) {
            bodyPaint.textSize = 13.5f * density
            val bodyText = if (lead.isRedacted) "Sensitive content hidden" else lead.text.ifBlank { lead.title }
            val layout = buildBodyLayout(bodyText, bodyMaxW, maxLines = 4)
            var h = 44f * density + layout.height.toFloat()
            if (hasLeadProgress) h += 16f * density
            val hasPic = !group.isRedacted && ((group.pictureBitmap ?: lead.pictureBitmap)?.let { !it.isRecycled } == true)
            if (hasPic) h += 106f * density
            if (config.notificationShowActions && !lead.isRedacted && lead.actions.isNotEmpty()) {
                h += 40f * density
            }
            max(collapsedH, h + 14f * density)
        } else {
            collapsedH
        }

        return collapsedH + (expandedH - collapsedH) * cardExpandP.coerceIn(0f, 1f)
    }

    fun measureTotalHeightPx(
        groups: List<LockNotifAppGroup>,
        config: LockscreenConfig,
        density: Float,
        expandedGroupId: String? = null,
        expandProgress: Float = 1f,
        dismissingGroupId: String? = null,
        dismissCollapseScale: Float = 1f,
        screenW: Float = 1080f
    ): Float {
        if (groups.isEmpty()) return 0f
        return when (NotificationStyle.fromId(config.notificationStyle)) {
            NotificationStyle.STOCK, NotificationStyle.HIDDEN -> 0f
            NotificationStyle.ICONS_ONLY -> if (config.notificationIconsBackdrop) 36f * density else 30f * density
            NotificationStyle.FROSTED_PILL -> 38f * density
            NotificationStyle.GROUPED_CARDS -> {
                val sidePad = NotificationThemeSpec.CARD_SIDE_PADDING_DP * density
                val cardW = (screenW - sidePad * 2f).coerceAtLeast(120f * density)
                val maxCards = config.notificationMaxCards.coerceIn(1, 5)
                val visible = groups.take(maxCards)
                val cardGap = NotificationThemeSpec.CARD_GAP_DP * density
                var totalH = 0f
                visible.forEachIndexed { idx, group ->
                    val cardExpandP = if (group.groupId == expandedGroupId) expandProgress.coerceIn(0f, 1f) else 0f
                    val baseH = measureCardHeightPx(group, cardW, config, density, cardExpandP)
                    if (group.groupId == dismissingGroupId) {
                        totalH += (baseH + if (idx < visible.lastIndex) cardGap else 0f) * dismissCollapseScale.coerceIn(0f, 1f)
                    } else {
                        totalH += baseH
                        if (idx < visible.lastIndex) totalH += cardGap
                    }
                }
                if (groups.size > maxCards || groups.any { it.isClearable }) {
                    totalH += 10f * density + 30f * density
                }
                totalH
            }
        }
    }

    fun draw(
        canvas: Canvas,
        groups: List<LockNotifAppGroup>,
        config: LockscreenConfig,
        screenW: Float,
        screenH: Float,
        topY: Float,
        maxBottomY: Float,
        density: Float,
        nowMillis: Long = System.currentTimeMillis(),
        wallpaperBitmap: Bitmap? = null,
        wallpaperZoom: Float = LockscreenLayoutSpec.DEFAULT_WALLPAPER_ZOOM,
        bgOpacity: Float = 1f,
        fallbackColors: WallpaperColors? = null,
        expandedGroupId: String? = null,
        swipingGroupId: String? = null,
        swipeOffsetPx: Float = 0f,
        isDozing: Boolean = false,
        alphaScale: Float = 1f,
        expandProgress: Float = 1f,
        collapsingGroupId: String? = null,
        dismissingGroupId: String? = null,
        dismissCollapseScale: Float = 1f,
        peekGroupId: String? = null,
        peekProgress: Float = 0f,
        peekSwipeOffsetPx: Float = 0f,
        dozeAmount: Float = 0f,
        pressedTarget: NotificationHitTarget? = null,
        pressScale: Float = 1f,
        newGroupIds: Set<String> = emptySet(),
        entryProgress: Float = 1f,
        isNightMode: Boolean = true,
        clockAvoidRect: RectF? = null
    ): NotificationHitLayout {
        val style = NotificationStyle.fromId(config.notificationStyle)
        if (groups.isEmpty() || style == NotificationStyle.STOCK || style == NotificationStyle.HIDDEN) {
            return NotificationHitLayout()
        }
        val availH = (maxBottomY - topY).coerceAtLeast(0f)
        if (availH < 24f * density) return NotificationHitLayout()

        val effDoze = if (isDozing) 1f else dozeAmount.coerceIn(0f, 1f)
        val effectiveAlphaScale = if (effDoze > 0.96f) (alphaScale * 0.78f) else alphaScale
        val alphaInt = (effectiveAlphaScale.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 2) return NotificationHitLayout()

        val activeExpanded = expandedGroupId ?: collapsingGroupId
        val centerX = screenW / 2f
        val estHeight = measureTotalHeightPx(
            groups, config, density, activeExpanded, expandProgress, dismissingGroupId, dismissCollapseScale, screenW
        ).coerceAtMost(availH)

        val surfaceMode = NotificationSurfaceMode.fromId(config.notificationSurfaceMode)
        val backdropBmp = if (effDoze < 0.98f) {
            when (surfaceMode) {
                NotificationSurfaceMode.FROSTED_GLASS -> getOrCreateFrostBitmap(wallpaperBitmap)
                NotificationSurfaceMode.AMBIENT_GLOW -> getOrCreateAmbientBitmap(wallpaperBitmap)
                else -> null
            }
        } else null

        val backdropMatrix: Matrix? = if (backdropBmp != null && wallpaperBitmap != null && !wallpaperBitmap.isRecycled) {
            val bw = wallpaperBitmap.width.toFloat().coerceAtLeast(1f)
            val bh = wallpaperBitmap.height.toFloat().coerceAtLeast(1f)
            val baseScale = max(screenW / bw, screenH / bh) * wallpaperZoom.coerceAtLeast(0.5f)
            val destW = bw * baseScale
            val destH = bh * baseScale
            val tx = (screenW - destW) / 2f
            val ty = (screenH - destH) / 2f
            scratchMatrix.reset()
            scratchMatrix.postScale(destW / backdropBmp.width.toFloat(), destH / backdropBmp.height.toFloat())
            scratchMatrix.postTranslate(tx, ty)
            scratchMatrix
        } else null

        val isLightWallpaper = if (effDoze >= 0.98f) {
            false
        } else {
            val resolvedInk = AtAGlanceColorResolver.resolveForWallpaper(
                wallpaperBitmap = wallpaperBitmap,
                screenW = screenW,
                screenH = screenH,
                zoom = wallpaperZoom,
                centerX = centerX,
                topY = topY,
                widthPx = (screenW - 44f * density).coerceAtLeast(160f * density),
                heightPx = estHeight,
                bgOpacity = bgOpacity,
                fallbackColors = fallbackColors,
                lightThreshold = NotificationThemeSpec.LIGHT_WALLPAPER_THRESHOLD
            )
            resolvedInk == AtAGlanceColorResolver.DARK_INK_COLOR
        }

        val palette = resolvePalette(
            surfaceMode = surfaceMode,
            isNightMode = isNightMode,
            isLightWallpaper = isLightWallpaper,
            alphaInt = alphaInt,
            hasBackdropBitmap = backdropBmp != null && backdropMatrix != null,
            config = config,
            fallbackColors = fallbackColors,
            wallpaperBitmap = wallpaperBitmap
        )

        if (effDoze >= 0.98f || style == NotificationStyle.ICONS_ONLY) {
            return drawIconsOnly(
                canvas, groups, config, screenW, centerX, topY, maxBottomY, density,
                nowMillis, palette, backdropBmp, backdropMatrix, effDoze >= 0.5f,
                peekGroupId, peekProgress, peekSwipeOffsetPx, pressedTarget, clockAvoidRect
            )
        }

        if (effDoze > 0.02f) {
            val awakeAlpha = (1f - effDoze).coerceIn(0f, 1f)
            val aodAlpha = (effDoze * 0.78f).coerceIn(0f, 1f)
            val awakePalette = resolvePalette(
                surfaceMode, isNightMode, isLightWallpaper,
                (alphaInt * awakeAlpha).roundToInt(),
                backdropBmp != null && backdropMatrix != null, config, fallbackColors, wallpaperBitmap
            )
            val aodPalette = resolvePalette(
                surfaceMode, true, false,
                (alphaInt * aodAlpha).roundToInt(),
                false, config, fallbackColors, wallpaperBitmap
            )
            val out = when (style) {
                NotificationStyle.FROSTED_PILL -> drawCompactPill(
                    canvas, groups, config, screenW, centerX, topY, density, awakePalette, backdropBmp, backdropMatrix, pressedTarget, pressScale
                )
                NotificationStyle.GROUPED_CARDS -> drawGroupedCards(
                    canvas, groups, config, screenW, topY, maxBottomY, density, nowMillis,
                    awakePalette, backdropBmp, backdropMatrix,
                    expandedGroupId, collapsingGroupId, expandProgress,
                    swipingGroupId, swipeOffsetPx,
                    dismissingGroupId, dismissCollapseScale,
                    pressedTarget, pressScale, newGroupIds, entryProgress
                )
                else -> NotificationHitLayout()
            }
            drawIconsOnly(
                canvas, groups, config, screenW, centerX, topY, maxBottomY, density,
                nowMillis, aodPalette, null, null, true,
                null, 0f, 0f, null
            )
            return out
        }

        return when (style) {
            NotificationStyle.FROSTED_PILL -> drawCompactPill(
                canvas, groups, config, screenW, centerX, topY, density, palette, backdropBmp, backdropMatrix, pressedTarget, pressScale
            )
            NotificationStyle.GROUPED_CARDS -> drawGroupedCards(
                canvas, groups, config, screenW, topY, maxBottomY, density, nowMillis,
                palette, backdropBmp, backdropMatrix,
                expandedGroupId, collapsingGroupId, expandProgress,
                swipingGroupId, swipeOffsetPx,
                dismissingGroupId, dismissCollapseScale,
                pressedTarget, pressScale, newGroupIds, entryProgress
            )
            else -> NotificationHitLayout()
        }
    }

    fun drawSingleCardSurface(
        canvas: Canvas,
        width: Float,
        height: Float,
        cornerRadius: Float,
        config: LockscreenConfig,
        screenW: Float,
        screenH: Float,
        viewLocX: Float,
        viewLocY: Float,
        density: Float,
        wallpaperBitmap: Bitmap?,
        wallpaperZoom: Float,
        fallbackColors: WallpaperColors?,
        isNightMode: Boolean,
        alphaScale: Float = 1f,
        isPressed: Boolean = false
    ) {
        val alphaInt = (alphaScale.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 2 || width < 4f || height < 4f) return

        val surfaceMode = NotificationSurfaceMode.fromId(config.notificationSurfaceMode)
        val backdropBmp = when (surfaceMode) {
            NotificationSurfaceMode.FROSTED_GLASS -> getOrCreateFrostBitmap(wallpaperBitmap)
            NotificationSurfaceMode.AMBIENT_GLOW -> getOrCreateAmbientBitmap(wallpaperBitmap)
            else -> null
        }

        val hasMatrix = backdropBmp != null && wallpaperBitmap != null && !wallpaperBitmap.isRecycled && screenW > 0f && screenH > 0f
        if (hasMatrix && backdropBmp != null && wallpaperBitmap != null) {
            val bw = wallpaperBitmap.width.toFloat().coerceAtLeast(1f)
            val bh = wallpaperBitmap.height.toFloat().coerceAtLeast(1f)
            val baseScale = max(screenW / bw, screenH / bh) * wallpaperZoom.coerceAtLeast(0.5f)
            val destW = bw * baseScale
            val destH = bh * baseScale
            val tx = (screenW - destW) / 2f
            val ty = (screenH - destH) / 2f
            scratchMatrix.reset()
            scratchMatrix.postScale(destW / backdropBmp.width.toFloat(), destH / backdropBmp.height.toFloat())
            scratchMatrix.postTranslate(tx - viewLocX, ty - viewLocY)
        }

        val resolvedInk = AtAGlanceColorResolver.resolveForWallpaper(
            wallpaperBitmap = wallpaperBitmap,
            screenW = screenW,
            screenH = screenH,
            zoom = wallpaperZoom,
            centerX = viewLocX + width * 0.5f,
            topY = viewLocY,
            widthPx = width.coerceAtLeast(120f * density),
            heightPx = height.coerceAtLeast(36f * density),
            bgOpacity = 1f,
            fallbackColors = fallbackColors,
            lightThreshold = NotificationThemeSpec.LIGHT_WALLPAPER_THRESHOLD
        )
        val isLightWp = resolvedInk == AtAGlanceColorResolver.DARK_INK_COLOR

        val palette = resolvePalette(
            surfaceMode = surfaceMode,
            isNightMode = isNightMode,
            isLightWallpaper = isLightWp,
            alphaInt = alphaInt,
            hasBackdropBitmap = hasMatrix,
            config = config,
            fallbackColors = fallbackColors,
            wallpaperBitmap = wallpaperBitmap
        )

        scratchRect.set(0f, 0f, width, height)
        clipPath.rewind()
        clipPath.addRoundRect(scratchRect, cornerRadius, cornerRadius, Path.Direction.CW)

        if (palette.frostActive && backdropBmp != null && !backdropBmp.isRecycled && hasMatrix) {
            canvas.save()
            canvas.clipPath(clipPath)
            iconPaint.colorFilter = null
            iconPaint.alpha = alphaInt
            canvas.drawBitmap(backdropBmp, scratchMatrix, iconPaint)
            fillPaint.clearShadowLayer()
            fillPaint.shader = null
            fillPaint.color = palette.surfaceColor
            canvas.drawRect(scratchRect, fillPaint)
            canvas.restore()
        } else {
            fillPaint.clearShadowLayer()
            fillPaint.shader = null
            fillPaint.color = palette.surfaceColor
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, fillPaint)

            if (palette.glowColor != 0) {
                val gAlpha = (alphaInt * (if (palette.isLightWallpaper) 0.18f else 0.24f)).roundToInt()
                val glowCol = Color.argb(
                    gAlpha,
                    Color.red(palette.glowColor),
                    Color.green(palette.glowColor),
                    Color.blue(palette.glowColor)
                )
                fillPaint.shader = RadialGradient(
                    scratchRect.left + scratchRect.width() * 0.22f,
                    scratchRect.top + scratchRect.height() * 0.25f,
                    max(scratchRect.width(), scratchRect.height()).coerceAtLeast(1f) * 0.85f,
                    glowCol,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, fillPaint)
                fillPaint.shader = null
            }
        }

        if (isPressed) {
            fillPaint.shader = null
            fillPaint.color = if (palette.isLightWallpaper) {
                Color.argb((alphaInt * 0.08f).roundToInt(), 0, 0, 0)
            } else {
                Color.argb((alphaInt * 0.12f).roundToInt(), 255, 255, 255)
            }
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, fillPaint)
        }

        if (palette.frostActive) {
            borderStrokePaint.strokeWidth = 0.9f * density
            val topHighlight = if (palette.isLightWallpaper) 0.34f else 0.36f
            borderStrokePaint.shader = LinearGradient(
                scratchRect.left, scratchRect.top, scratchRect.left, scratchRect.bottom,
                intArrayOf(
                    Color.argb((alphaInt * topHighlight).roundToInt(), 255, 255, 255),
                    Color.argb((alphaInt * 0.11f).roundToInt(), 255, 255, 255),
                    Color.argb((alphaInt * 0.04f).roundToInt(), 255, 255, 255)
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, borderStrokePaint)
            borderStrokePaint.shader = null
        } else if (Color.alpha(palette.borderColor) > 0) {
            borderStrokePaint.shader = null
            borderStrokePaint.strokeWidth = 0.75f * density
            borderStrokePaint.color = palette.borderColor
            canvas.drawRoundRect(scratchRect, cornerRadius, cornerRadius, borderStrokePaint)
        }
    }

    fun resolveSurfaceTextColors(
        config: LockscreenConfig,
        screenW: Float,
        screenH: Float,
        wallpaperBitmap: Bitmap?,
        wallpaperZoom: Float,
        fallbackColors: WallpaperColors?,
        isNightMode: Boolean
    ): Pair<Int, Int> {
        val surfaceMode = NotificationSurfaceMode.fromId(config.notificationSurfaceMode)
        val palette = resolvePalette(
            surfaceMode = surfaceMode,
            isNightMode = isNightMode,
            isLightWallpaper = !isNightMode,
            alphaInt = 255,
            hasBackdropBitmap = wallpaperBitmap != null,
            config = config,
            fallbackColors = fallbackColors,
            wallpaperBitmap = wallpaperBitmap
        )
        return palette.primaryInk to palette.secondaryInk
    }

    private fun drawSurfaceRect(
        canvas: Canvas,
        rect: RectF,
        cornerRadius: Float,
        palette: ResolvedPalette,
        frostBitmap: Bitmap?,
        frostMatrix: Matrix?,
        alphaScale: Float,
        density: Float,
        dxShift: Float = 0f,
        isPressed: Boolean = false
    ) {
        val alphaInt = (alphaScale.coerceIn(0f, 1f) * 255).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 2) return

        if (alphaScale > 0.3f && Color.alpha(palette.shadowColor) > 0) {
            fillPaint.color = Color.argb((alphaInt * 0.10f).roundToInt(), 0, 0, 0)
            fillPaint.setShadowLayer(10f * density, 0f, 3f * density, scaleAlpha(palette.shadowColor, alphaScale))
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
            fillPaint.clearShadowLayer()
        }

        if (palette.frostActive && frostBitmap != null && !frostBitmap.isRecycled && frostMatrix != null) {
            clipPath.rewind()
            clipPath.addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            if (dxShift != 0f) canvas.translate(-dxShift, 0f)
            iconPaint.colorFilter = null
            iconPaint.alpha = alphaInt
            canvas.drawBitmap(frostBitmap, frostMatrix, iconPaint)
            if (dxShift != 0f) canvas.translate(dxShift, 0f)
            fillPaint.color = scaleAlpha(palette.surfaceColor, alphaScale)
            canvas.drawRect(rect, fillPaint)
            canvas.restore()
        } else {
            fillPaint.color = scaleAlpha(palette.surfaceColor, alphaScale)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)

            if (palette.glowColor != 0) {
                val gAlpha = (alphaInt * (if (palette.isLightWallpaper) 0.18f else 0.24f)).roundToInt()
                val glowCol = Color.argb(gAlpha, Color.red(palette.glowColor), Color.green(palette.glowColor), Color.blue(palette.glowColor))
                fillPaint.shader = RadialGradient(
                    rect.left + rect.width() * 0.22f,
                    rect.top + rect.height() * 0.25f,
                    max(rect.width(), rect.height()).coerceAtLeast(1f) * 0.85f,
                    glowCol,
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
                fillPaint.shader = null
            }
        }

        if (isPressed) {
            fillPaint.color = if (palette.isLightWallpaper) {
                Color.argb((alphaInt * 0.07f).roundToInt(), 0, 0, 0)
            } else {
                Color.argb((alphaInt * 0.10f).roundToInt(), 255, 255, 255)
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        }

        if (palette.frostActive) {
            borderStrokePaint.strokeWidth = 0.9f * density
            val topHighlight = if (palette.isLightWallpaper) 0.34f else 0.36f
            val baseCol = if (palette.isLightWallpaper) Color.WHITE else Color.WHITE
            borderStrokePaint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(
                    Color.argb((alphaInt * topHighlight).roundToInt(), Color.red(baseCol), Color.green(baseCol), Color.blue(baseCol)),
                    Color.argb((alphaInt * 0.11f).roundToInt(), Color.red(baseCol), Color.green(baseCol), Color.blue(baseCol)),
                    Color.argb((alphaInt * 0.04f).roundToInt(), Color.red(baseCol), Color.green(baseCol), Color.blue(baseCol))
                ),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderStrokePaint)
            borderStrokePaint.shader = null
        } else if (Color.alpha(palette.borderColor) > 0) {
            borderStrokePaint.shader = null
            borderStrokePaint.strokeWidth = 0.75f * density
            borderStrokePaint.color = scaleAlpha(palette.borderColor, alphaScale)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderStrokePaint)
        }
    }

    private fun drawCenterCroppedPicture(canvas: Canvas, bmp: Bitmap, dst: RectF, radius: Float, alphaInt: Int) {
        if (bmp.isRecycled || alphaInt <= 2) return
        clipPath.rewind()
        clipPath.addRoundRect(dst, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        val bw = bmp.width.toFloat().coerceAtLeast(1f)
        val bh = bmp.height.toFloat().coerceAtLeast(1f)
        val scale = max(dst.width() / bw, dst.height() / bh)
        val dx = dst.left + (dst.width() - bw * scale) * 0.5f
        val dy = dst.top + (dst.height() - bh * scale) * 0.5f
        scratchMatrix.reset()
        scratchMatrix.postScale(scale, scale)
        scratchMatrix.postTranslate(dx, dy)
        iconPaint.colorFilter = null
        iconPaint.alpha = alphaInt
        canvas.drawBitmap(bmp, scratchMatrix, iconPaint)
        canvas.restore()
    }

    private fun drawIconsOnly(
        canvas: Canvas,
        groups: List<LockNotifAppGroup>,
        config: LockscreenConfig,
        screenW: Float,
        centerX: Float,
        topY: Float,
        maxBottomY: Float,
        density: Float,
        nowMillis: Long,
        palette: ResolvedPalette,
        frostBitmap: Bitmap?,
        frostMatrix: Matrix?,
        isDozing: Boolean,
        peekGroupId: String?,
        peekProgress: Float,
        peekSwipeOffsetPx: Float,
        pressedTarget: NotificationHitTarget?,
        clockAvoidRect: RectF? = null
    ): NotificationHitLayout {
        val visible = groups.take(5)
        val overflowCount = (groups.size - visible.size).coerceAtLeast(0)
        val useBackdrop = config.notificationIconsBackdrop && !isDozing
        val iconSz = 19f * density
        val itemGap = 15f * density
        val rowH = if (useBackdrop) 36f * density else 30f * density
        val padH = if (useBackdrop) 16f * density else 10f * density

        metaPaint.textSize = 12f * density
        metaPaint.clearShadowLayer()
        val overflowStr = if (overflowCount > 0) "+$overflowCount" else null
        val overflowW = if (overflowStr != null) metaPaint.measureText(overflowStr) + 8f * density else 0f

        val iconsW = visible.size * iconSz + (visible.size - 1).coerceAtLeast(0) * itemGap
        val totalW = iconsW + overflowW
        val rect = RectF(centerX - totalW / 2f - padH, topY, centerX + totalW / 2f + padH, topY + rowH)

        if (useBackdrop) {
            drawSurfaceRect(canvas, rect, rowH / 2f, palette, frostBitmap, frostMatrix, 1f, density)
        }

        var cursorX = centerX - totalW / 2f
        val iconTop = rect.centerY() - iconSz / 2f
        val iconTint = if (useBackdrop) palette.primaryInk else palette.glyphInk
        val shadowCol = if (isDozing || useBackdrop) Color.TRANSPARENT else if (palette.isLightBackdrop) {
            Color.argb(85, 255, 255, 255)
        } else {
            Color.argb(140, 0, 0, 0)
        }

        val targets = mutableListOf<NotificationHitTarget>()
        val totalBounds = RectF(rect)

        visible.forEach { group ->
            val isPeeked = !isDozing && group.groupId == peekGroupId && peekProgress > 0.02f
            val isIconPressed = pressedTarget?.kind == NotificationHitKind.ICON_PEEK && pressedTarget.groupId == group.groupId
            val cx = cursorX + iconSz / 2f
            val cy = rect.centerY()

            if (isPeeked || isIconPressed) {
                val p = if (isPeeked) peekProgress.coerceIn(0f, 1f) else 0.7f
                fillPaint.clearShadowLayer()
                fillPaint.color = scaleAlpha(palette.badgeBg, p * 1.4f)
                canvas.drawCircle(cx, cy, (iconSz * 0.72f) * (0.85f + 0.15f * p), fillPaint)
            }

            val iconScale = if (isPeeked) 1f + 0.14f * peekProgress.coerceIn(0f, 1f) else if (isIconPressed) 0.90f else 1f
            canvas.save()
            if (iconScale != 1f) canvas.scale(iconScale, iconScale, cx, cy)
            drawMonochromeAppGlyph(canvas, group, cursorX, iconTop, iconSz, iconTint, density, shadowCol)
            canvas.restore()

            if (group.count > 1) {
                val badgeTxt = if (group.count > 9) "9+" else "${group.count}"
                badgeTextPaint.textSize = 8.5f * density
                badgeTextPaint.textAlign = Paint.Align.CENTER
                val badgeR = if (badgeTxt.length > 1) 7.5f * density else 6.4f * density
                val bx = cursorX + iconSz + 1.5f * density
                val by = iconTop + 2f * density

                fillPaint.clearShadowLayer()
                fillPaint.color = if (useBackdrop) palette.surfaceColor else Color.argb(160, 0, 0, 0)
                canvas.drawCircle(bx, by, badgeR + 1.2f * density, fillPaint)

                fillPaint.color = if (!isDozing && group.accentColor != 0 && group.accentColor != Color.WHITE) {
                    group.accentColor
                } else {
                    palette.accentColor
                }
                canvas.drawCircle(bx, by, badgeR, fillPaint)

                badgeTextPaint.color = Color.WHITE
                val bTy = by - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
                canvas.drawText(badgeTxt, bx, bTy, badgeTextPaint)
            }

            if (!isDozing) {
                targets.add(
                    NotificationHitTarget(
                        kind = NotificationHitKind.ICON_PEEK,
                        bounds = RectF(cursorX - 6f * density, rect.top, cursorX + iconSz + 6f * density, rect.bottom),
                        groupId = group.groupId
                    )
                )
            }
            cursorX += iconSz + itemGap
        }

        if (overflowStr != null) {
            metaPaint.color = iconTint
            if (shadowCol != Color.TRANSPARENT) metaPaint.setShadowLayer(4f * density, 0f, 1.5f * density, shadowCol)
            val ty = rect.centerY() - (metaPaint.descent() + metaPaint.ascent()) / 2f
            canvas.drawText(overflowStr, cursorX - 4f * density, ty, metaPaint)
            metaPaint.clearShadowLayer()
        }

        val peekGroup = if (!isDozing && peekGroupId != null && peekProgress > 0.02f) {
            groups.firstOrNull { it.groupId == peekGroupId }
        } else null
        if (peekGroup != null) {
            val peekTarget = drawPeekCard(
                canvas, peekGroup, config, screenW, centerX, rect, maxBottomY,
                density, nowMillis, palette, frostBitmap, frostMatrix, peekProgress,
                peekSwipeOffsetPx, pressedTarget, clockAvoidRect
            )
            targets.add(peekTarget)
            totalBounds.union(peekTarget.bounds)
        }

        targets.add(NotificationHitTarget(NotificationHitKind.ICONS_CONTAINER, RectF(rect)))
        return NotificationHitLayout(totalBounds = totalBounds, targets = targets)
    }

    private fun drawPeekCard(
        canvas: Canvas,
        group: LockNotifAppGroup,
        config: LockscreenConfig,
        screenW: Float,
        centerX: Float,
        anchorRowRect: RectF,
        maxBottomY: Float,
        density: Float,
        nowMillis: Long,
        palette: ResolvedPalette,
        frostBitmap: Bitmap?,
        frostMatrix: Matrix?,
        peekProgress: Float,
        peekSwipeOffsetPx: Float,
        pressedTarget: NotificationHitTarget?,
        clockAvoidRect: RectF? = null
    ): NotificationHitTarget {
        val p = peekProgress.coerceIn(0f, 1f)
        val cardW = min(screenW - 32f * density, 344f * density)
        val lead = group.items.firstOrNull()
        val hasProg = lead?.hasProgress == true
        val cardH = (if (hasProg) 86f else 74f) * density
        val gap = 10f * density
        val belowTop = anchorRowRect.bottom + gap
        val aboveTop = anchorRowRect.top - gap - cardH
        val belowFits = (belowTop + cardH) <= maxBottomY
        val aboveFits = aboveTop >= 44f * density
        val belowHitsClock = clockAvoidRect != null && !clockAvoidRect.isEmpty &&
            RectF.intersects(RectF(centerX - cardW / 2f, belowTop, centerX + cardW / 2f, belowTop + cardH), clockAvoidRect)
        val aboveHitsClock = clockAvoidRect != null && !clockAvoidRect.isEmpty &&
            RectF.intersects(RectF(centerX - cardW / 2f, aboveTop, centerX + cardW / 2f, aboveTop + cardH), clockAvoidRect)

        val placeBelow = when {
            belowFits && !belowHitsClock -> true
            aboveFits && !aboveHitsClock -> false
            else -> belowFits
        }
        val cardTop = if (placeBelow) {
            belowTop
        } else {
            aboveTop.coerceAtLeast(44f * density)
        }
        val slideOffset = (1f - p) * (if (placeBelow) -8f * density else 8f * density)
        val peekRect = RectF(centerX - cardW / 2f, cardTop + slideOffset, centerX + cardW / 2f, cardTop + slideOffset + cardH)
        val cardRadius = NotificationThemeSpec.CARD_CORNER_RADIUS_DP * density
        val swipeAlpha = if (group.isClearable && peekSwipeOffsetPx != 0f) {
            (1f - (abs(peekSwipeOffsetPx) / (cardW * 0.72f))).coerceIn(0.12f, 1f) * p
        } else p

        val isPressed = pressedTarget?.kind == NotificationHitKind.PEEK_CARD && pressedTarget.groupId == group.groupId
        canvas.save()
        if (peekSwipeOffsetPx != 0f) canvas.translate(peekSwipeOffsetPx, 0f)
        if (isPressed) canvas.scale(0.975f, 0.975f, peekRect.centerX(), peekRect.centerY())

        drawSurfaceRect(canvas, peekRect, cardRadius, palette, frostBitmap, frostMatrix, swipeAlpha, density, dxShift = peekSwipeOffsetPx, isPressed = isPressed)

        val padH = 16f * density
        val iconSz = 38f * density
        val iconLeft = peekRect.left + padH
        val iconTop = peekRect.centerY() - iconSz / 2f
        drawGroupCardIcon(canvas, group, iconLeft, iconTop, iconSz, config, palette, swipeAlpha, density)

        val textLeft = iconLeft + iconSz + 13f * density
        val timeStr = formatRelativeTime(nowMillis, group.latestPostTimeMs)
        metaPaint.textSize = 11.5f * density
        metaPaint.color = scaleAlpha(palette.mutedInk, swipeAlpha)
        val timeW = metaPaint.measureText(timeStr)
        val topY = peekRect.top + 29f * density
        canvas.drawText(timeStr, peekRect.right - padH - timeW, topY, metaPaint)

        val headline = if (group.isRedacted) group.appName else group.primaryTitle.ifBlank { group.appName }
        val subline = if (group.isRedacted) "New notification" else group.primaryText.ifBlank { group.appName }

        titlePaint.textSize = 14.8f * density
        titlePaint.color = scaleAlpha(palette.primaryInk, swipeAlpha)
        val titleEllip = TextUtils.ellipsize(
            headline, titlePaint, (peekRect.right - padH - timeW - 8f * density - textLeft).coerceAtLeast(60f * density), TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(titleEllip, textLeft, topY, titlePaint)

        bodyPaint.textSize = 13.2f * density
        bodyPaint.color = scaleAlpha(palette.secondaryInk, swipeAlpha)
        val subEllip = TextUtils.ellipsize(
            subline, bodyPaint, (peekRect.right - padH - textLeft).coerceAtLeast(80f * density), TextUtils.TruncateAt.END
        ).toString()
        canvas.drawText(subEllip, textLeft, peekRect.top + 51f * density, bodyPaint)

        if (hasProg && lead != null) {
            drawProgressBar(canvas, lead, group, textLeft, peekRect.bottom - 14f * density, peekRect.right - padH, density, nowMillis, palette, swipeAlpha)
        }
        canvas.restore()

        return NotificationHitTarget(NotificationHitKind.PEEK_CARD, peekRect, groupId = group.groupId)
    }

    private fun drawCompactPill(
        canvas: Canvas,
        groups: List<LockNotifAppGroup>,
        config: LockscreenConfig,
        screenW: Float,
        centerX: Float,
        topY: Float,
        density: Float,
        palette: ResolvedPalette,
        frostBitmap: Bitmap?,
        frostMatrix: Matrix?,
        pressedTarget: NotificationHitTarget?,
        pressScale: Float
    ): NotificationHitLayout {
        val lead = groups.first()
        val extraGroups = groups.drop(1).take(3)

        val labelText = buildString {
            append(lead.appName)
            if (lead.count > 1) append(" (${lead.count})")
            else if (lead.primaryTitle.isNotBlank() && !lead.primaryTitle.equals(lead.appName, ignoreCase = true)) {
                append(": ")
                append(lead.primaryTitle)
            }
        }

        val maxPillW = (screenW - 44f * density).coerceAtLeast(180f * density)
        val padH = 14f * density
        val pillH = 38f * density
        val iconSz = 20f * density
        val iconGap = 9f * density
        val miniSz = 16f * density
        val miniStep = 10f * density
        val stackW = if (extraGroups.isNotEmpty()) (miniSz + (extraGroups.size - 1) * miniStep + 10f * density) else 0f

        titlePaint.textSize = 13f * density
        titlePaint.color = palette.primaryInk
        val maxTextW = (maxPillW - padH * 2f - iconSz - iconGap - stackW).coerceAtLeast(80f * density)
        val ellipsized = TextUtils.ellipsize(labelText, titlePaint, maxTextW, TextUtils.TruncateAt.END).toString()
        val textW = titlePaint.measureText(ellipsized)
        val pillW = (textW + iconSz + iconGap + stackW + padH * 2f).coerceAtMost(maxPillW)
        val rect = RectF(centerX - pillW / 2f, topY, centerX + pillW / 2f, topY + pillH)

        val isPressed = pressedTarget?.kind == NotificationHitKind.GROUP_CARD && pressedTarget.groupId == lead.groupId
        canvas.save()
        if (isPressed && pressScale != 1f) canvas.scale(pressScale, pressScale, rect.centerX(), rect.centerY())

        drawSurfaceRect(canvas, rect, pillH / 2f, palette, frostBitmap, frostMatrix, 1f, density, isPressed = isPressed)

        drawGroupCardIcon(
            canvas, lead, rect.left + padH, rect.centerY() - iconSz / 2f,
            iconSz, config, palette, 1f, density, showCornerBadge = false
        )
        val ty = rect.centerY() - (titlePaint.descent() + titlePaint.ascent()) / 2f
        canvas.drawText(ellipsized, rect.left + padH + iconSz + iconGap, ty, titlePaint)

        if (extraGroups.isNotEmpty()) {
            val rightStart = rect.right - padH - miniSz
            for (i in extraGroups.indices.reversed()) {
                val g = extraGroups[i]
                val mx = rightStart - i * miniStep
                val my = rect.centerY() - miniSz / 2f
                fillPaint.clearShadowLayer()
                fillPaint.color = palette.surfaceColor
                canvas.drawCircle(mx + miniSz / 2f, rect.centerY(), miniSz / 2f + 1.2f * density, fillPaint)
                drawGroupCardIcon(canvas, g, mx, my, miniSz, config, palette, 1f, density, showCornerBadge = false)
            }
        }
        canvas.restore()

        return NotificationHitLayout(
            totalBounds = RectF(rect),
            targets = listOf(NotificationHitTarget(NotificationHitKind.GROUP_CARD, RectF(rect), groupId = lead.groupId))
        )
    }

    private fun drawGroupedCards(
        canvas: Canvas,
        groups: List<LockNotifAppGroup>,
        config: LockscreenConfig,
        screenW: Float,
        topY: Float,
        maxBottomY: Float,
        density: Float,
        nowMillis: Long,
        palette: ResolvedPalette,
        frostBitmap: Bitmap?,
        frostMatrix: Matrix?,
        expandedGroupId: String?,
        collapsingGroupId: String?,
        expandProgress: Float,
        swipingGroupId: String?,
        swipeOffsetPx: Float,
        dismissingGroupId: String?,
        dismissCollapseScale: Float,
        pressedTarget: NotificationHitTarget?,
        pressScale: Float,
        newGroupIds: Set<String>,
        entryProgress: Float
    ): NotificationHitLayout {
        val sidePad = NotificationThemeSpec.CARD_SIDE_PADDING_DP * density
        val cardLeft = sidePad
        val cardRight = (screenW - sidePad).coerceAtLeast(cardLeft + 120f * density)
        val cardW = cardRight - cardLeft
        val cardRadius = NotificationThemeSpec.CARD_CORNER_RADIUS_DP * density
        val cardGap = NotificationThemeSpec.CARD_GAP_DP * density
        val maxCards = config.notificationMaxCards.coerceIn(1, 5)
        val footerReserve = if (groups.size > maxCards || groups.any { it.isClearable }) 40f * density else 0f

        val targets = mutableListOf<NotificationHitTarget>()
        val totalBounds = RectF()
        var cursorY = topY
        var renderedCount = 0

        for (group in groups) {
            if (renderedCount >= maxCards) break
            val cardExpandP = when (group.groupId) {
                expandedGroupId, collapsingGroupId -> expandProgress.coerceIn(0f, 1f)
                else -> 0f
            }
            val cardH = measureCardHeightPx(group, cardW, config, density, cardExpandP)

            if (group.groupId == dismissingGroupId) {
                cursorY += (cardH + cardGap) * dismissCollapseScale.coerceIn(0f, 1f)
                renderedCount++
                continue
            }

            if (cursorY + cardH > maxBottomY - footerReserve) break

            val isSwiping = group.groupId == swipingGroupId
            val dx = if (isSwiping) swipeOffsetPx else 0f
            val entryP = if (newGroupIds.contains(group.groupId)) entryProgress.coerceIn(0f, 1f) else 1f
            val swipeAlpha = (if (isSwiping && group.isClearable) {
                (1f - (abs(dx) / (cardW * 0.72f))).coerceIn(0.12f, 1f)
            } else 1f) * entryP

            val cardRect = RectF(cardLeft, cursorY, cardRight, cursorY + cardH)
            if (totalBounds.isEmpty) totalBounds.set(cardRect) else totalBounds.union(cardRect)

            if (isSwiping && group.isClearable && abs(dx) > 12f * density) {
                val threshold = cardW * 0.28f
                val crossed = abs(dx) >= threshold
                val hintFrac = (abs(dx) / threshold).coerceIn(0f, 1f)
                val pillH = 40f * density
                val cy = cardRect.centerY()
                val capsuleSpan = max(pillH, (abs(dx) - 8f * density).coerceAtLeast(pillH))
                val capRect = if (dx > 0f) {
                    RectF(cardRect.left + 4f * density, cy - pillH / 2f, (cardRect.left + 4f * density + capsuleSpan).coerceAtMost(cardRect.right), cy + pillH / 2f)
                } else {
                    RectF((cardRect.right - 4f * density - capsuleSpan).coerceAtLeast(cardRect.left), cy - pillH / 2f, cardRect.right - 4f * density, cy + pillH / 2f)
                }
                fillPaint.clearShadowLayer()
                fillPaint.color = if (crossed) {
                    Color.argb(232, 217, 48, 37)
                } else {
                    Color.argb((120 + 80 * hintFrac).roundToInt(), 120, 48, 52)
                }
                canvas.drawRoundRect(capRect, pillH / 2f, pillH / 2f, fillPaint)

                val iconSz = (17f + 3f * hintFrac) * density
                val iconCx = if (dx > 0f) capRect.left + 22f * density else capRect.right - 22f * density
                canvas.save()
                val tilt = if (crossed) 0f else if (dx > 0f) -10f * (1f - hintFrac) else 10f * (1f - hintFrac)
                if (tilt != 0f) canvas.rotate(tilt, iconCx, cy)
                drawTrashHintGlyph(canvas, iconCx - iconSz / 2f, cy - iconSz / 2f, iconSz, Color.argb((hintFrac * 255).roundToInt(), 255, 255, 255), density)
                canvas.restore()
            }

            val isCardPressed = pressedTarget?.kind == NotificationHitKind.GROUP_CARD && pressedTarget.groupId == group.groupId
            val entryShiftY = (1f - entryP) * (-10f * density)
            val entryScale = 0.95f + 0.05f * entryP
            val totalCardScale = entryScale * (if (isCardPressed) pressScale else 1f)

            canvas.save()
            if (dx != 0f || entryShiftY != 0f) canvas.translate(dx, entryShiftY)
            if (totalCardScale != 1f) canvas.scale(totalCardScale, totalCardScale, cardRect.centerX(), cardRect.centerY())

            drawSurfaceRect(canvas, cardRect, cardRadius, palette, frostBitmap, frostMatrix, swipeAlpha, density, dxShift = dx, isPressed = isCardPressed)

            val padH = 16f * density
            val iconSz = 40f * density
            val iconLeft = cardRect.left + padH
            val expandedIconTop = cardRect.top + 13f * density
            val collapsedIconTop = cardRect.centerY() - iconSz / 2f
            val iconTop = collapsedIconTop + (expandedIconTop - collapsedIconTop) * cardExpandP

            drawGroupCardIcon(canvas, group, iconLeft, iconTop, iconSz, config, palette, swipeAlpha, density)

            if (!isSwiping) {
                targets.add(
                    NotificationHitTarget(
                        kind = NotificationHitKind.ICON_LAUNCH,
                        bounds = RectF(iconLeft - 6f * density, iconTop - 6f * density, iconLeft + iconSz + 6f * density, iconTop + iconSz + 6f * density),
                        groupId = group.groupId
                    )
                )
            }

            val textLeft = iconLeft + iconSz + 13f * density
            val hasExpandBadge = group.canExpand
            val badgeReserve = if (group.count > 1) 46f * density else if (hasExpandBadge) 30f * density else 0f
            val timeStr = formatRelativeTime(nowMillis, group.latestPostTimeMs)
            metaPaint.textSize = 11.5f * density
            val timeW = metaPaint.measureText(timeStr)
            val lead = group.items.firstOrNull()
            val pic = if (!group.isRedacted) (group.pictureBitmap ?: lead?.pictureBitmap)?.takeIf { !it.isRecycled } else null

            if (cardExpandP > 0.35f) {
                val contentAlpha = ((cardExpandP - 0.35f) / 0.65f).coerceIn(0f, 1f) * swipeAlpha
                clipPath.rewind()
                clipPath.addRoundRect(cardRect, cardRadius, cardRadius, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)

                if (group.count > 1) {
                    val visibleMsgs = group.items.take(3)
                    titlePaint.textSize = 14.8f * density
                    titlePaint.color = scaleAlpha(palette.primaryInk, contentAlpha)
                    val headerMaxW = (cardRect.right - padH - badgeReserve - timeW - 10f * density - textLeft).coerceAtLeast(60f * density)
                    val appHeader = TextUtils.ellipsize(group.appName, titlePaint, headerMaxW, TextUtils.TruncateAt.END).toString()
                    val headerY = cardRect.top + 30f * density
                    canvas.drawText(appHeader, textLeft, headerY, titlePaint)

                    val timeRightX = cardRect.right - padH - badgeReserve
                    metaPaint.color = scaleAlpha(palette.mutedInk, contentAlpha)
                    canvas.drawText(timeStr, timeRightX - timeW, headerY, metaPaint)

                    drawExpandPill(
                        canvas = canvas,
                        count = group.count,
                        isExpanded = true,
                        rightX = cardRect.right - padH,
                        centerY = headerY - 4f * density,
                        density = density,
                        palette = palette,
                        alphaScale = contentAlpha
                    )

                    var msgTop = cardRect.top + 46f * density
                    visibleMsgs.forEachIndexed { msgIdx, msg ->
                        val rowH = (if (msg.hasProgress) 52f else 42f) * density
                        if (msgIdx > 0) {
                            dividerPaint.color = scaleAlpha(palette.dividerColor, contentAlpha)
                            canvas.drawRect(textLeft, msgTop, cardRect.right - padH, msgTop + 1f * density, dividerPaint)
                        }

                        val rowRect = RectF(cardRect.left + 8f * density, msgTop, cardRect.right - 8f * density, msgTop + rowH)
                        if (!isSwiping && cardExpandP > 0.8f) {
                            targets.add(NotificationHitTarget(NotificationHitKind.MESSAGE_ROW, rowRect, group.groupId, msgIdx))
                        }

                        val msgTime = formatRelativeTime(nowMillis, msg.postTimeMs)
                        metaPaint.textSize = 10.5f * density
                        metaPaint.color = scaleAlpha(palette.mutedInk, contentAlpha)
                        val msgTimeW = metaPaint.measureText(msgTime)
                        val msgTextTop = if (msg.hasProgress) msgTop + 18f * density else msgTop + (rowH / 2f) - (bodyPaint.descent() + bodyPaint.ascent()) / 2f
                        canvas.drawText(msgTime, cardRect.right - padH - msgTimeW, msgTextTop, metaPaint)

                        val msgText = if (msg.isRedacted) {
                            "${msg.title}: Sensitive content hidden"
                        } else if (msg.title.isNotBlank() && !msg.title.equals(group.appName, ignoreCase = true)) {
                            "${msg.title}: ${msg.text}"
                        } else {
                            msg.text.ifBlank { msg.title }
                        }
                        bodyPaint.textSize = 13.2f * density
                        bodyPaint.color = scaleAlpha(palette.secondaryInk, contentAlpha)
                        val ellip = TextUtils.ellipsize(
                            msgText, bodyPaint,
                            (cardRect.right - padH - msgTimeW - 8f * density - textLeft).coerceAtLeast(80f * density),
                            TextUtils.TruncateAt.END
                        ).toString()
                        canvas.drawText(ellip, textLeft, msgTextTop, bodyPaint)

                        if (msg.hasProgress) {
                            drawProgressBar(canvas, msg, group, textLeft, msgTop + rowH - 12f * density, cardRect.right - padH, density, nowMillis, palette, contentAlpha)
                        }
                        msgTop += rowH
                    }

                    if (config.notificationShowActions && lead != null && !lead.isRedacted && lead.actions.isNotEmpty() && cardExpandP > 0.85f) {
                        val actAlpha = ((cardExpandP - 0.85f) / 0.15f).coerceIn(0f, 1f) * contentAlpha
                        val actTop = max(msgTop + 4f * density, cardRect.bottom - 38f * density)
                        val actionTargets = drawActionPills(
                            canvas, lead.actions, group.groupId, 0,
                            textLeft, cardRect.right - padH, actTop,
                            density, palette, actAlpha, pressedTarget
                        )
                        if (!isSwiping) targets.addAll(actionTargets)
                    }
                } else if (lead != null) {
                    val topLineY = cardRect.top + 29f * density
                    val singleBadgeReserve = if (group.canExpand) 30f * density else 0f
                    val singleTimeRightX = cardRect.right - padH - singleBadgeReserve
                    metaPaint.color = scaleAlpha(palette.mutedInk, contentAlpha)
                    canvas.drawText(timeStr, singleTimeRightX - timeW, topLineY, metaPaint)

                    if (group.canExpand) {
                        drawExpandPill(
                            canvas = canvas,
                            count = 1,
                            isExpanded = true,
                            rightX = cardRect.right - padH,
                            centerY = topLineY - 4f * density,
                            density = density,
                            palette = palette,
                            alphaScale = contentAlpha
                        )
                    }

                    val headline = if (group.isRedacted) group.appName else group.primaryTitle.ifBlank { group.appName }
                    titlePaint.textSize = 14.8f * density
                    titlePaint.color = scaleAlpha(palette.primaryInk, contentAlpha)
                    val titleMaxW = (singleTimeRightX - timeW - 10f * density - textLeft).coerceAtLeast(60f * density)
                    val ellipTitle = TextUtils.ellipsize(headline, titlePaint, titleMaxW, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipTitle, textLeft, topLineY, titlePaint)

                    val bodyText = if (lead.isRedacted) "Sensitive content hidden" else lead.text.ifBlank { lead.title }
                    bodyPaint.textSize = 13.5f * density
                    bodyPaint.color = scaleAlpha(palette.secondaryInk, contentAlpha)
                    val bodyMaxW = (cardRect.right - padH - textLeft).toInt().coerceAtLeast((80f * density).toInt())
                    val layout = buildBodyLayout(bodyText, bodyMaxW, maxLines = 4)
                    val bodyTop = cardRect.top + 39f * density
                    canvas.save()
                    canvas.translate(textLeft, bodyTop)
                    layout.draw(canvas)
                    canvas.restore()

                    var nextY = bodyTop + layout.height + 8f * density
                    if (lead.hasProgress) {
                        drawProgressBar(canvas, lead, group, textLeft, nextY + 2f * density, cardRect.right - padH, density, nowMillis, palette, contentAlpha)
                        nextY += 16f * density
                    }

                    if (pic != null && cardExpandP > 0.65f) {
                        val picAlpha = (((cardExpandP - 0.65f) / 0.35f) * contentAlpha * 255).roundToInt().coerceIn(0, 255)
                        val bannerRect = RectF(textLeft, nextY, cardRect.right - padH, nextY + 96f * density)
                        drawCenterCroppedPicture(canvas, pic, bannerRect, 14f * density, picAlpha)
                    }

                    if (config.notificationShowActions && !lead.isRedacted && lead.actions.isNotEmpty() && cardExpandP > 0.85f) {
                        val actAlpha = ((cardExpandP - 0.85f) / 0.15f).coerceIn(0f, 1f) * contentAlpha
                        val contentEnd = nextY + if (pic != null) 102f * density else 0f
                        val actTop = max(contentEnd, cardRect.bottom - 38f * density)
                        val actionTargets = drawActionPills(
                            canvas, lead.actions, group.groupId, 0,
                            textLeft, cardRect.right - padH, actTop,
                            density, palette, actAlpha, pressedTarget
                        )
                        if (!isSwiping) targets.addAll(actionTargets)
                    }
                }

                canvas.restore()
                if (!isSwiping) {
                    targets.add(NotificationHitTarget(NotificationHitKind.GROUP_CARD, RectF(cardRect), groupId = group.groupId))
                }
            } else {
                val collapsedAlpha = (1f - (cardExpandP / 0.35f)).coerceIn(0f, 1f) * swipeAlpha
                val topLineY = cardRect.top + 29f * density
                val subLineY = cardRect.top + 51f * density

                val thumbSz = 44f * density
                val showThumb = pic != null && lead?.hasProgress != true
                val thumbReserve = if (showThumb) (thumbSz + 10f * density) else 0f
                if (showThumb && pic != null) {
                    val thumbLeft = cardRect.right - padH - thumbSz
                    val thumbTop = cardRect.centerY() - thumbSz / 2f
                    val thumbRect = RectF(thumbLeft, thumbTop, thumbLeft + thumbSz, thumbTop + thumbSz)
                    drawCenterCroppedPicture(canvas, pic, thumbRect, 12f * density, (collapsedAlpha * 255).roundToInt().coerceIn(0, 255))
                }

                val showPctBadge = lead?.hasProgress == true && !lead.progressIndeterminate && lead.progressMax > 0
                val pctReserve = if (showPctBadge && lead != null) {
                    val pct = ((lead.progress * 100f) / lead.progressMax).roundToInt().coerceIn(0, 100)
                    val pctLabel = "$pct%"
                    badgeTextPaint.textSize = 10.5f * density
                    badgeTextPaint.textAlign = Paint.Align.CENTER
                    val pw = (badgeTextPaint.measureText(pctLabel) + 12f * density).coerceAtLeast(36f * density)
                    val ph = 20f * density
                    val px = cardRect.right - padH - badgeReserve
                    val pr = RectF(px - pw, topLineY - 4f * density - ph / 2f, px, topLineY - 4f * density + ph / 2f)
                    fillPaint.clearShadowLayer()
                    fillPaint.color = scaleAlpha(palette.badgeBg, collapsedAlpha)
                    canvas.drawRoundRect(pr, ph / 2f, ph / 2f, fillPaint)
                    badgeTextPaint.color = scaleAlpha(palette.accentColor, collapsedAlpha)
                    val pty = pr.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
                    canvas.drawText(pctLabel, pr.centerX(), pty, badgeTextPaint)
                    pw + 6f * density
                } else 0f

                metaPaint.color = scaleAlpha(palette.mutedInk, collapsedAlpha)
                val timeRightX = cardRect.right - padH - badgeReserve - thumbReserve - pctReserve
                if (!showPctBadge) {
                    canvas.drawText(timeStr, timeRightX - timeW, topLineY, metaPaint)
                }

                if (hasExpandBadge) {
                    drawExpandPill(
                        canvas = canvas,
                        count = group.count,
                        isExpanded = false,
                        rightX = cardRect.right - padH - thumbReserve,
                        centerY = topLineY - 4f * density,
                        density = density,
                        palette = palette,
                        alphaScale = collapsedAlpha
                    )
                }

                val headline = if (group.isRedacted) group.appName else group.primaryTitle.ifBlank { group.appName }
                val subline = if (group.isRedacted) {
                    if (group.count > 1) "${group.count} new notifications" else "New notification"
                } else {
                    group.primaryText.ifBlank { group.appName }
                }

                titlePaint.textSize = 15.0f * density
                titlePaint.color = scaleAlpha(palette.primaryInk, collapsedAlpha)
                val titleUsedRight = if (showPctBadge) timeRightX else (timeRightX - timeW - 8f * density)
                val titleMaxW = (titleUsedRight - textLeft).coerceAtLeast(60f * density)
                val ellipTitle = TextUtils.ellipsize(headline, titlePaint, titleMaxW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipTitle, textLeft, topLineY, titlePaint)

                bodyPaint.textSize = 13.5f * density
                bodyPaint.color = scaleAlpha(palette.secondaryInk, collapsedAlpha)
                val bodyMaxW = (cardRect.right - padH - thumbReserve - textLeft).coerceAtLeast(80f * density)
                val ellipBody = TextUtils.ellipsize(subline, bodyPaint, bodyMaxW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipBody, textLeft, subLineY, bodyPaint)

                if (lead?.hasProgress == true) {
                    drawProgressBar(canvas, lead, group, textLeft, cardRect.bottom - 14f * density, cardRect.right - padH, density, nowMillis, palette, collapsedAlpha)
                }

                if (!isSwiping) {
                    targets.add(NotificationHitTarget(NotificationHitKind.GROUP_CARD, RectF(cardRect), groupId = group.groupId))
                }
            }

            canvas.restore()
            cursorY += cardH + cardGap
            renderedCount++
        }

        val remainingApps = (groups.size - renderedCount).coerceAtLeast(0)
        val hasClearable = groups.any { it.isClearable }
        if ((remainingApps > 0 || hasClearable) && cursorY + 30f * density <= maxBottomY) {
            val pillH = 29f * density
            val padH = 14f * density
            val pillGap = 8f * density
            metaPaint.textSize = 11.5f * density

            val moreLabel = if (remainingApps > 0) "+$remainingApps more" else null
            val clearLabel = if (hasClearable) "Clear all" else null
            val moreW = if (moreLabel != null) metaPaint.measureText(moreLabel) + padH * 2f else 0f
            val clearW = if (clearLabel != null) metaPaint.measureText(clearLabel) + padH * 2f else 0f
            val totalRowW = moreW + clearW + if (moreLabel != null && clearLabel != null) pillGap else 0f
            var px = (screenW / 2f) - (totalRowW / 2f)

            if (moreLabel != null) {
                val moreRect = RectF(px, cursorY, px + moreW, cursorY + pillH)
                val isMorePressed = pressedTarget?.kind == NotificationHitKind.ICONS_CONTAINER
                canvas.save()
                if (isMorePressed) canvas.scale(0.95f, 0.95f, moreRect.centerX(), moreRect.centerY())
                drawSurfaceRect(canvas, moreRect, pillH / 2f, palette, frostBitmap, frostMatrix, 0.92f, density, isPressed = isMorePressed)
                metaPaint.color = palette.primaryInk
                val ty = moreRect.centerY() - (metaPaint.descent() + metaPaint.ascent()) / 2f
                canvas.drawText(moreLabel, moreRect.left + padH, ty, metaPaint)
                canvas.restore()
                totalBounds.union(moreRect)
                targets.add(NotificationHitTarget(NotificationHitKind.ICONS_CONTAINER, moreRect))
                px += moreW + pillGap
            }

            if (clearLabel != null) {
                val clearRect = RectF(px, cursorY, px + clearW, cursorY + pillH)
                val isClearPressed = pressedTarget?.kind == NotificationHitKind.CLEAR_ALL
                canvas.save()
                if (isClearPressed) canvas.scale(0.95f, 0.95f, clearRect.centerX(), clearRect.centerY())
                drawSurfaceRect(canvas, clearRect, pillH / 2f, palette, frostBitmap, frostMatrix, 0.92f, density, isPressed = isClearPressed)
                metaPaint.color = palette.primaryInk
                val ty = clearRect.centerY() - (metaPaint.descent() + metaPaint.ascent()) / 2f
                canvas.drawText(clearLabel, clearRect.left + padH, ty, metaPaint)
                canvas.restore()
                totalBounds.union(clearRect)
                targets.add(NotificationHitTarget(NotificationHitKind.CLEAR_ALL, clearRect))
            }
        }

        return NotificationHitLayout(totalBounds = totalBounds, targets = targets)
    }

    private fun drawProgressBar(
        canvas: Canvas,
        msg: LockNotifMessage,
        group: LockNotifAppGroup,
        left: Float,
        topY: Float,
        right: Float,
        density: Float,
        nowMillis: Long,
        palette: ResolvedPalette,
        alphaScale: Float
    ) {
        val barH = 4.8f * density
        val trackW = (right - left).coerceAtLeast(40f * density)
        scratchRect.set(left, topY, left + trackW, topY + barH)
        fillPaint.clearShadowLayer()
        fillPaint.color = scaleAlpha(palette.badgeBg, alphaScale)
        canvas.drawRoundRect(scratchRect, barH / 2f, barH / 2f, fillPaint)

        val barColor = if (group.accentColor != 0 && group.accentColor != Color.WHITE && group.accentColor != Color.BLACK) {
            scaleAlpha(group.accentColor, alphaScale)
        } else {
            scaleAlpha(palette.accentColor, alphaScale)
        }
        fillPaint.color = barColor
        fillPaint.setShadowLayer(6f * density, 0f, 0f, scaleAlpha(barColor, 0.45f))

        if (msg.progressIndeterminate) {
            val phase = ((nowMillis % 1600L) / 1600f)
            val wave = (1f - kotlin.math.cos(phase * Math.PI * 2.0).toFloat()) * 0.5f
            val segW = trackW * 0.34f
            val startX = left + (trackW - segW) * wave
            scratchRect2.set(startX, topY, (startX + segW).coerceAtMost(right), topY + barH)
            canvas.drawRoundRect(scratchRect2, barH / 2f, barH / 2f, fillPaint)
        } else {
            val frac = (msg.progress.toFloat() / msg.progressMax.coerceAtLeast(1)).coerceIn(0f, 1f)
            val fillW = max(barH, trackW * frac)
            scratchRect2.set(left, topY, (left + fillW).coerceAtMost(right), topY + barH)
            canvas.drawRoundRect(scratchRect2, barH / 2f, barH / 2f, fillPaint)
        }
        fillPaint.clearShadowLayer()
    }

    private fun drawActionPills(
        canvas: Canvas,
        actions: List<LockNotifAction>,
        groupId: String,
        messageIdx: Int,
        startX: Float,
        maxRightX: Float,
        topY: Float,
        density: Float,
        palette: ResolvedPalette,
        alphaScale: Float,
        pressedTarget: NotificationHitTarget? = null
    ): List<NotificationHitTarget> {
        val out = mutableListOf<NotificationHitTarget>()
        val pillH = 26f * density
        val padH = 12f * density
        val gap = 8f * density
        var cx = startX

        badgeTextPaint.textSize = 11.5f * density
        badgeTextPaint.textAlign = Paint.Align.LEFT

        actions.take(3).forEachIndexed { idx, action ->
            val label = TextUtils.ellipsize(action.title, badgeTextPaint, 110f * density, TextUtils.TruncateAt.END).toString()
            val labelW = badgeTextPaint.measureText(label)
            val pillW = labelW + padH * 2f
            if (cx + pillW > maxRightX) return@forEachIndexed

            val pillRect = RectF(cx, topY, cx + pillW, topY + pillH)
            val isPressed = pressedTarget?.kind == NotificationHitKind.ACTION_PILL &&
                pressedTarget.groupId == groupId && pressedTarget.actionIndex == idx

            fillPaint.clearShadowLayer()
            val tintBase = Color.argb((if (isPressed) 62 else 36), Color.red(palette.accentColor), Color.green(palette.accentColor), Color.blue(palette.accentColor))
            fillPaint.color = scaleAlpha(tintBase, alphaScale)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, fillPaint)

            borderStrokePaint.shader = null
            borderStrokePaint.strokeWidth = 0.75f * density
            borderStrokePaint.color = scaleAlpha(Color.argb(if (isPressed) 140 else 75, Color.red(palette.accentColor), Color.green(palette.accentColor), Color.blue(palette.accentColor)), alphaScale)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, borderStrokePaint)

            badgeTextPaint.color = scaleAlpha(palette.primaryInk, alphaScale)
            val ty = pillRect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(label, pillRect.left + padH, ty, badgeTextPaint)

            out.add(NotificationHitTarget(NotificationHitKind.ACTION_PILL, pillRect, groupId, messageIdx, idx))
            cx += pillW + gap
        }
        badgeTextPaint.textAlign = Paint.Align.CENTER
        return out
    }

    private fun drawExpandPill(
        canvas: Canvas,
        count: Int,
        isExpanded: Boolean,
        rightX: Float,
        centerY: Float,
        density: Float,
        palette: ResolvedPalette,
        alphaScale: Float
    ) {
        val countStr = if (count > 1) "$count" else null
        badgeTextPaint.textAlign = Paint.Align.LEFT
        badgeTextPaint.textSize = 11f * density
        val txtW = if (countStr != null) badgeTextPaint.measureText(countStr) else 0f
        val chevW = 7f * density
        val gap = if (countStr != null) 4.5f * density else 0f
        val padH = if (countStr != null) 8.5f * density else 0f
        val pillH = 22f * density
        val pillW = if (countStr != null) (padH * 2f + txtW + gap + chevW).coerceAtLeast(36f * density) else pillH
        val rect = RectF(rightX - pillW, centerY - pillH / 2f, rightX, centerY + pillH / 2f)

        fillPaint.clearShadowLayer()
        fillPaint.color = scaleAlpha(palette.badgeBg, alphaScale)
        canvas.drawRoundRect(rect, pillH / 2f, pillH / 2f, fillPaint)

        val ink = scaleAlpha(palette.primaryInk, alphaScale)
        if (countStr != null) {
            badgeTextPaint.color = ink
            val ty = rect.centerY() - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(countStr, rect.left + padH, ty, badgeTextPaint)
        }

        val chevCx = if (countStr != null) (rect.left + padH + txtW + gap + chevW / 2f) else rect.centerX()
        val chevCy = rect.centerY()
        val halfW = 3.1f * density
        val halfH = 1.6f * density
        glyphStrokePaint.clearShadowLayer()
        glyphStrokePaint.color = ink
        glyphStrokePaint.strokeWidth = 1.55f * density
        clipPath.rewind()
        if (isExpanded) {
            clipPath.moveTo(chevCx - halfW, chevCy + halfH)
            clipPath.lineTo(chevCx, chevCy - halfH)
            clipPath.lineTo(chevCx + halfW, chevCy + halfH)
        } else {
            clipPath.moveTo(chevCx - halfW, chevCy - halfH)
            clipPath.lineTo(chevCx, chevCy + halfH)
            clipPath.lineTo(chevCx + halfW, chevCy - halfH)
        }
        canvas.drawPath(clipPath, glyphStrokePaint)
        badgeTextPaint.textAlign = Paint.Align.CENTER
    }

    private fun drawGroupCardIcon(
        canvas: Canvas,
        group: LockNotifAppGroup,
        left: Float,
        top: Float,
        size: Float,
        config: LockscreenConfig,
        palette: ResolvedPalette,
        alphaScale: Float,
        density: Float,
        showCornerBadge: Boolean = true
    ) {
        val dst = RectF(left, top, left + size, top + size)
        val avatar = if (config.notificationShowAvatars && !group.isRedacted) {
            group.avatarIcon ?: group.items.firstOrNull()?.avatarIcon
        } else null
        val appBmp = group.appIcon

        if (avatar != null && !avatar.isRecycled) {
            val layerId = canvas.saveLayer(dst.left - 4f * density, dst.top - 4f * density, dst.right + 6f * density, dst.bottom + 6f * density, null)
            clipPath.rewind()
            clipPath.addOval(dst, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            iconPaint.colorFilter = null
            iconPaint.alpha = (255 * alphaScale).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(avatar, null, dst, iconPaint)
            canvas.restore()

            if (showCornerBadge && appBmp != null && !appBmp.isRecycled) {
                val badgeSz = (size * 0.40f).coerceAtLeast(14f * density)
                val bLeft = dst.right - badgeSz + 2f * density
                val bTop = dst.bottom - badgeSz + 2f * density
                val bRect = RectF(bLeft, bTop, bLeft + badgeSz, bTop + badgeSz)

                fillPaint.clearShadowLayer()
                fillPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                fillPaint.color = Color.BLACK
                canvas.drawCircle(bRect.centerX(), bRect.centerY(), (badgeSz / 2f) + 1.8f * density, fillPaint)
                fillPaint.xfermode = null

                clipPath.rewind()
                clipPath.addOval(bRect, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(clipPath)
                iconPaint.colorFilter = null
                iconPaint.alpha = (255 * alphaScale).roundToInt().coerceIn(0, 255)
                canvas.drawBitmap(appBmp, null, bRect, iconPaint)
                canvas.restore()
            }
            canvas.restoreToCount(layerId)
            return
        }

        val cornerR = size * 0.30f
        if (appBmp != null && !appBmp.isRecycled) {
            clipPath.rewind()
            clipPath.addRoundRect(dst, cornerR, cornerR, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)
            iconPaint.colorFilter = null
            iconPaint.alpha = (255 * alphaScale).roundToInt().coerceIn(0, 255)
            canvas.drawBitmap(appBmp, null, dst, iconPaint)
            canvas.restore()
            return
        }

        val bgCol = if (group.accentColor != 0 && group.accentColor != Color.WHITE) {
            scaleAlpha(group.accentColor, alphaScale)
        } else {
            scaleAlpha(palette.badgeBg, alphaScale)
        }
        fillPaint.clearShadowLayer()
        fillPaint.color = bgCol
        canvas.drawRoundRect(dst, cornerR, cornerR, fillPaint)

        val glyphSz = size * 0.56f
        val glyphInk = if (group.accentColor != 0 && group.accentColor != Color.WHITE) {
            scaleAlpha(Color.WHITE, alphaScale)
        } else {
            scaleAlpha(palette.primaryInk, alphaScale)
        }
        drawMonochromeAppGlyph(
            canvas, group,
            dst.centerX() - glyphSz / 2f, dst.centerY() - glyphSz / 2f,
            glyphSz, glyphInk, density, Color.TRANSPARENT
        )
    }

    private fun drawMonochromeAppGlyph(
        canvas: Canvas,
        group: LockNotifAppGroup,
        left: Float,
        top: Float,
        size: Float,
        tint: Int,
        density: Float,
        shadowColor: Int
    ) {
        val bmp = group.smallIcon
        if (bmp != null && !bmp.isRecycled) {
            if (shadowColor != Color.TRANSPARENT) {
                scratchRect2.set(left, top + 1.2f * density, left + size, top + size + 1.2f * density)
                iconPaint.colorFilter = PorterDuffColorFilter(scaleAlpha(shadowColor, 0.65f), PorterDuff.Mode.SRC_IN)
                iconPaint.alpha = Color.alpha(shadowColor)
                canvas.drawBitmap(bmp, null, scratchRect2, iconPaint)
            }
            scratchRect.set(left, top, left + size, top + size)
            iconPaint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
            iconPaint.alpha = Color.alpha(tint)
            canvas.drawBitmap(bmp, null, scratchRect, iconPaint)
            iconPaint.colorFilter = null
            return
        }

        val u = size / 24f
        glyphStrokePaint.color = tint
        glyphStrokePaint.strokeWidth = (1.8f * density).coerceAtMost(size * 0.14f)
        if (shadowColor != Color.TRANSPARENT) {
            glyphStrokePaint.setShadowLayer(4f * density, 0f, 1.5f * density, shadowColor)
        } else {
            glyphStrokePaint.clearShadowLayer()
        }

        canvas.save()
        canvas.translate(left, top)
        val pkg = group.packageName.lowercase()
        when {
            pkg.contains("whatsapp") || pkg.contains("messaging") || pkg.contains("telegram") -> {
                val chat = Path().apply {
                    moveTo(5f * u, 18.5f * u)
                    lineTo(5f * u, 7f * u)
                    cubicTo(5f * u, 5.2f * u, 6.2f * u, 4f * u, 8f * u, 4f * u)
                    lineTo(17f * u, 4f * u)
                    cubicTo(18.8f * u, 4f * u, 20f * u, 5.2f * u, 20f * u, 7f * u)
                    lineTo(20f * u, 14.5f * u)
                    cubicTo(20f * u, 16.3f * u, 18.8f * u, 17.5f * u, 17f * u, 17.5f * u)
                    lineTo(9.5f * u, 17.5f * u)
                    close()
                }
                canvas.drawPath(chat, glyphStrokePaint)
            }
            pkg.contains("gm") || pkg.contains("mail") -> {
                canvas.drawRoundRect(3.5f * u, 5.5f * u, 20.5f * u, 18.5f * u, 2.5f * u, 2.5f * u, glyphStrokePaint)
                canvas.drawPath(Path().apply {
                    moveTo(4f * u, 6.5f * u); lineTo(12f * u, 13f * u); lineTo(20f * u, 6.5f * u)
                }, glyphStrokePaint)
            }
            pkg.contains("instagram") || pkg.contains("camera") -> {
                canvas.drawRoundRect(4f * u, 4f * u, 20f * u, 20f * u, 5f * u, 5f * u, glyphStrokePaint)
                canvas.drawCircle(12f * u, 12f * u, 3.8f * u, glyphStrokePaint)
            }
            else -> {
                val bell = Path().apply {
                    moveTo(6.5f * u, 16.5f * u); lineTo(17.5f * u, 16.5f * u)
                    lineTo(16.2f * u, 11f * u)
                    cubicTo(16f * u, 7.2f * u, 8f * u, 7.2f * u, 7.8f * u, 11f * u)
                    close()
                }
                canvas.drawPath(bell, glyphStrokePaint)
                canvas.drawLine(10.5f * u, 19.5f * u, 13.5f * u, 19.5f * u, glyphStrokePaint)
            }
        }
        canvas.restore()
        glyphStrokePaint.clearShadowLayer()
    }

    private fun drawTrashHintGlyph(
        canvas: Canvas,
        left: Float,
        top: Float,
        size: Float,
        tint: Int,
        density: Float
    ) {
        val u = size / 24f
        glyphStrokePaint.color = tint
        glyphStrokePaint.strokeWidth = 1.8f * density
        glyphStrokePaint.clearShadowLayer()
        canvas.save()
        canvas.translate(left, top)
        canvas.drawLine(4.5f * u, 6.5f * u, 19.5f * u, 6.5f * u, glyphStrokePaint)
        canvas.drawPath(Path().apply {
            moveTo(9f * u, 6.5f * u); lineTo(9.5f * u, 4f * u); lineTo(14.5f * u, 4f * u); lineTo(15f * u, 6.5f * u)
        }, glyphStrokePaint)
        canvas.drawRoundRect(6.5f * u, 6.5f * u, 17.5f * u, 19.5f * u, 2.5f * u, 2.5f * u, glyphStrokePaint)
        canvas.drawLine(10f * u, 10f * u, 10f * u, 16f * u, glyphStrokePaint)
        canvas.drawLine(14f * u, 10f * u, 14f * u, 16f * u, glyphStrokePaint)
        canvas.restore()
    }

    private fun scaleAlpha(color: Int, factor: Float): Int {
        val a = (Color.alpha(color) * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun formatRelativeTime(nowMs: Long, postTimeMs: Long): String {
        val diffMin = ((nowMs - postTimeMs) / 60_000L).coerceAtLeast(0L)
        return when {
            diffMin < 1L -> "now"
            diffMin < 60L -> "${diffMin}m"
            diffMin < 1440L -> "${diffMin / 60L}h"
            else -> "${diffMin / 1440L}d"
        }
    }
}
