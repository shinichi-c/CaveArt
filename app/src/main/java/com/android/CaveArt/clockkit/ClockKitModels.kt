package com.android.CaveArt.clockkit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.android.CaveArt.GravityMode

enum class ClockDatePolicy {
    EXTERNAL_STANDARD,
    INTEGRATED_BY_STYLE,
    HIDDEN
}

enum class ClockIcon {
    STYLE, SIZE, THICKNESS, ROUNDNESS, SPACING,
    OPACITY, GRAVITY, FONT, COLOR, LAYOUT,
    OPTIONS, DEPTH, DATE, DIAL, EFFECTS, TUNE
}

sealed class ClockControl {
    abstract val id: String
    abstract val label: String
    abstract val icon: ClockIcon

    data class Slider(
        override val id: String,
        override val label: String,
        override val icon: ClockIcon = ClockIcon.TUNE,
        val min: Float,
        val max: Float,
        val defaultValue: Float,
        val formatter: (Float) -> String = { String.format("%.1f", it) }
    ) : ClockControl()

    data class Toggle(
        override val id: String,
        override val label: String,
        override val icon: ClockIcon = ClockIcon.TUNE,
        val defaultValue: Boolean
    ) : ClockControl()

    data class ChoiceOption(
        val key: String,
        val label: String,
        val icon: ClockIcon? = null
    )

    data class Choice(
        override val id: String,
        override val label: String,
        override val icon: ClockIcon = ClockIcon.LAYOUT,
        val options: List<ChoiceOption>,
        val defaultKey: String
    ) : ClockControl()

    data class FontPicker(
        override val id: String = "font",
        override val label: String = "Typography",
        override val icon: ClockIcon = ClockIcon.FONT
    ) : ClockControl()

    data class ColorPicker(
        override val id: String = "color",
        override val label: String = "Color Palette",
        override val icon: ClockIcon = ClockIcon.COLOR
    ) : ClockControl()

    data class QuickChips(
        override val id: String = "options",
        override val label: String = "Options",
        override val icon: ClockIcon = ClockIcon.OPTIONS,
        val items: List<QuickChipItem>
    ) : ClockControl()
}

sealed class QuickChipItem {
    data class ToggleChip(
        val paramId: String,
        val label: String,
        val defaultValue: Boolean,
        val icon: ClockIcon = ClockIcon.TUNE,
        val globalBinding: String? = null
    ) : QuickChipItem()

    data class CycleChip(
        val paramId: String,
        val options: List<ClockControl.ChoiceOption>,
        val defaultKey: String
    ) : QuickChipItem()

    data class ActionChip(
        val actionId: String,
        val label: String,
        val icon: ClockIcon = ClockIcon.TUNE
    ) : QuickChipItem()
}

data class ClockKitConfig(
    val styleId: String = "adaptive_vector",
    val clockX: Float = 0f,
    val clockY: Float = 110f,
    val dateX: Float = 0f,
    val dateY: Float = 75f,
    val dateAttached: Boolean = true,
    val dateFormat: Int = 0,
    val color: Int = Color.WHITE,
    val secondaryColor: Int = Color.WHITE,
    val font: String = "default",
    val scale: Float = 1.0f,
    val isDepthEnabled: Boolean = true,
    val depthOcclusionLimit: Float = 0.45f,
    val isGravityEnabled: Boolean = false,
    val gravityIntensity: Float = 1.0f,
    val gravityMode: GravityMode = GravityMode.SURFACE_PLANE,
    val styleFloats: Map<String, Map<String, Float>> = emptyMap(),
    val styleStrings: Map<String, Map<String, String>> = emptyMap()
) {
    fun getFloat(styleId: String, key: String, default: Float): Float =
        styleFloats[styleId]?.get(key) ?: default

    fun withFloat(styleId: String, key: String, value: Float): ClockKitConfig {
        val inner = (styleFloats[styleId] ?: emptyMap()).toMutableMap().apply { put(key, value) }
        return copy(styleFloats = styleFloats.toMutableMap().apply { put(styleId, inner) })
    }

    fun getBool(styleId: String, key: String, default: Boolean): Boolean =
        (styleFloats[styleId]?.get(key) ?: if (default) 1f else 0f) > 0.5f

    fun withBool(styleId: String, key: String, value: Boolean): ClockKitConfig =
        withFloat(styleId, key, if (value) 1f else 0f)

    fun getString(styleId: String, key: String, default: String): String =
        styleStrings[styleId]?.get(key) ?: default

    fun withString(styleId: String, key: String, value: String): ClockKitConfig {
        val inner = (styleStrings[styleId] ?: emptyMap()).toMutableMap().apply { put(key, value) }
        return copy(styleStrings = styleStrings.toMutableMap().apply { put(styleId, inner) })
    }
}

data class ClockMeasureContext(
    val context: Context,
    val screenW: Float,
    val screenH: Float,
    val density: Float,
    val typeface: Typeface,
    val timeString: String,
    val dateString: String,
    val notificationProgress: Float = 0f
)

data class ClockMeasureResult(
    val contentWidthPx: Float,
    val contentHeightPx: Float,
    val topPadPx: Float = 24f,
    val bottomPadPx: Float = 24f,
    val wantsFullScreenBounds: Boolean = false,
    val notificationCollapsedHeightPx: Float = contentHeightPx * 0.72f
)

data class ClockFrameContext(
    val context: Context,
    val screenW: Float,
    val screenH: Float,
    val density: Float,
    val clockCenterX: Float,
    val clockTopY: Float,
    val timeMillis: Long,
    val timeString: String,
    val dateString: String,
    val is24Hour: Boolean,
    val typeface: Typeface,
    val notificationProgress: Float = 0f,
    val collisionMap: FloatArray? = null,
    val depthMask: Bitmap? = null,
    val rawDepthMap: Bitmap? = null,
    val zoomFactor: Float = 1.10f
)

data class ClockRenderResult(
    val boundsOnScreen: RectF,
    val occlusionPath: Path? = null
)
