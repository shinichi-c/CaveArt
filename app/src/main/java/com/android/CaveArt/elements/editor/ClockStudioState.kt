package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.CaveArt.clockkit.ClockControl
import com.android.CaveArt.clockkit.ClockKitConfig
import com.android.CaveArt.clockkit.ClockKitConfigStore
import com.android.CaveArt.clockkit.ClockKitEngine
import com.android.CaveArt.layer.LockscreenLayerStack

enum class ActiveLayerTab(val label: String) {
    ALL("All"),
    BACKGROUND("Background"),
    CLOCK("Clock"),
    SUBJECT("Subject"),
    DEPTH("Depth Vision")
}

enum class ContextualToolType {
    SLIDER,
    FONT,
    COLOR,
    OPTIONS,
    CHOICE,
    QUICK_CHIPS,
    TOGGLE
}

data class LayerAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val toolType: ContextualToolType = ContextualToolType.SLIDER,
    val getVal: () -> Float = { 0f },
    val setVal: (Float) -> Unit = {},
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val formatDisplay: (Float) -> String = { "" },
    val clockControl: ClockControl? = null
)

class ClockStudioState(viewModel: WallpaperViewModel) {
    val layerStack: LockscreenLayerStack = LockscreenLayerStack().apply {
        setDepthClockActive(viewModel.clockKitConfig.isDepthEnabled)
        depthOcclusionLimit = viewModel.clockKitConfig.depthOcclusionLimit
        backgroundLayer.structure = viewModel.layerBackgroundStructure
        subjectLayer.opacity = viewModel.layerSubjectOpacity.coerceAtLeast(1.0f)
        backgroundLayer.opacity = viewModel.layerBackgroundOpacity.coerceAtLeast(1.0f)
        backgroundLayer.isVisible = true
        subjectLayer.isVisible = true
    }

    var clockKitConfig: ClockKitConfig by mutableStateOf(viewModel.clockKitConfig)

    fun updateConfig(transform: (ClockKitConfig) -> ClockKitConfig) {
        val next = transform(clockKitConfig)
        clockKitConfig = next
        if (next.isDepthEnabled != layerStack.isDepthEnabled) {
            layerStack.setDepthClockActive(next.isDepthEnabled)
        }
        if (next.depthOcclusionLimit != layerStack.depthOcclusionLimit) {
            layerStack.depthOcclusionLimit = next.depthOcclusionLimit
        }
    }

    val dateSize: MutableFloatState = mutableFloatStateOf(
        (viewModel.clockKitConfig.getFloat("adaptive_vector", "hour_size", 100f) * 0.24f).coerceIn(14f, 34f)
    )

    val collisionMap: MutableState<String> = mutableStateOf(viewModel.clockCollisionMap)
    val udfpsX: MutableFloatState = mutableFloatStateOf(-1f)
    val udfpsY: MutableFloatState = mutableFloatStateOf(-1f)
    val udfpsRadius: MutableFloatState = mutableFloatStateOf(0f)
    val liveWallpaperZoom: MutableFloatState = mutableFloatStateOf(1.0f)
    val notificationShelfTop: MutableFloatState = mutableFloatStateOf(-1f)
}

@Composable
fun rememberClockStudioState(viewModel: WallpaperViewModel): ClockStudioState =
    remember(viewModel) { ClockStudioState(viewModel) }

fun saveAndBroadcastClockSettings(
    context: Context,
    state: ClockStudioState,
    viewModel: WallpaperViewModel,
    previewMask: Bitmap?,
    realW: Float,
    realH: Float
) {
    val safeCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.createDeviceProtectedStorageContext()
    } else context

    safeCtx.getSharedPreferences(ClockKitConfigStore.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putFloat("telemetry_zoom", state.liveWallpaperZoom.floatValue)
        .apply()

    val finalConfig = state.clockKitConfig.copy(
        isDepthEnabled = state.layerStack.isDepthEnabled,
        depthOcclusionLimit = state.layerStack.depthOcclusionLimit
    )

    val activeStyle = ClockKitEngine.getStyle(finalConfig.styleId)
    val mapToSave = if (activeStyle.needsCollisionMap(finalConfig) && previewMask != null) {
        state.collisionMap.value.ifEmpty {
            AdaptiveClockHelper.generateCollisionMap(previewMask, realW, realH, state.liveWallpaperZoom.floatValue)
        }
    } else {
        state.collisionMap.value
    }

    if (previewMask != null && !previewMask.isRecycled) {
        viewModel.saveDepthMaskToDisk(context, previewMask)
    }

    val activeRawDepth = state.layerStack.depthVisionLayer.rawDepthMap
    if (activeRawDepth != null && !activeRawDepth.isRecycled) {
        viewModel.saveRawDepthToDisk(context, activeRawDepth)
    }

    viewModel.updateClockKitConfig(context, finalConfig, mapToSave)
    viewModel.saveLayerStackSettings(context, state.layerStack)
}
