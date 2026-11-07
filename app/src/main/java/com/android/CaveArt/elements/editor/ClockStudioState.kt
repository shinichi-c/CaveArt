package com.android.CaveArt

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.CaveArt.layer.LockscreenLayerStack

enum class ActiveLayerTab(val label: String) {
    ALL("All"),
    BACKGROUND("Background"),
    CLOCK("Clock"),
    SUBJECT("Subject")
}

enum class ContextualToolType {
    SLIDER,
    FONT,
    COLOR,
    OPTIONS
}

data class LayerAction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val toolType: ContextualToolType = ContextualToolType.SLIDER,
    val getVal: () -> Float = { 0f },
    val setVal: (Float) -> Unit = {},
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val formatDisplay: (Float) -> String = { "" }
)

class ClockStudioState(viewModel: WallpaperViewModel) {
    val layerStack: LockscreenLayerStack = LockscreenLayerStack().apply {
        setDepthClockActive(viewModel.isClockDepthEnabled && viewModel.clockLayerOrder == 1)
        depthOcclusionLimit = viewModel.clockDepthOcclusion
        backgroundLayer.structure = viewModel.layerBackgroundStructure
        subjectLayer.opacity = viewModel.layerSubjectOpacity.coerceAtLeast(1.0f)
        backgroundLayer.opacity = viewModel.layerBackgroundOpacity.coerceAtLeast(1.0f)
        backgroundLayer.isVisible = true
        subjectLayer.isVisible = true
    }

    val clockX: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenClockOffsetX)
    val clockY: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenClockOffsetY)
    val dateX: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenDateOffsetX)
    val dateY: MutableFloatState = mutableFloatStateOf(viewModel.lockscreenDateOffsetY)
    val hourSize: MutableFloatState = mutableFloatStateOf(viewModel.clockHourSize)
    val minSize: MutableFloatState = mutableFloatStateOf(viewModel.clockMinuteSize)
    val strokeWidth: MutableFloatState = mutableFloatStateOf(viewModel.clockStrokeWidth)
    val roundness: MutableFloatState = mutableFloatStateOf(viewModel.clockRoundness)
    val stretchEnabled: MutableState<Boolean> = mutableStateOf(viewModel.isClockStretchEnabled)
    val collisionMap: MutableState<String> = mutableStateOf(viewModel.clockCollisionMap)
    val clockColor: MutableIntState = mutableIntStateOf(viewModel.clockColor)
    val dualTone: MutableState<Boolean> = mutableStateOf(viewModel.isDualToneEnabled)
    val clockLayout: MutableIntState = mutableIntStateOf(viewModel.clockLayout)
    val dateFormat: MutableIntState = mutableIntStateOf(viewModel.dateLayout)
    val dateAttached: MutableState<Boolean> = mutableStateOf(viewModel.isDateAttached)
    val clockFont: MutableState<String> = mutableStateOf(viewModel.clockFont)
    val dateSize: MutableFloatState = mutableFloatStateOf((viewModel.clockHourSize * 0.24f).coerceIn(14f, 34f))

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
    viewModel.updateClockLayout(context, state.clockLayout.intValue)
    viewModel.updateDualTone(context, state.dualTone.value)
    viewModel.updateClockFont(context, state.clockFont.value)
    viewModel.updateClockColor(context, state.clockColor.intValue)
    viewModel.updateDateLayout(context, state.dateFormat.intValue)
    viewModel.updateDateAttached(context, state.dateAttached.value)
    viewModel.updateClockStyle(
        context, state.hourSize.floatValue, state.minSize.floatValue,
        state.strokeWidth.floatValue, state.roundness.floatValue,
        state.dateSize.floatValue
    )
    viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue)
    viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue)
    viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realW, realH)

    viewModel.saveLayerStackSettings(context, state.layerStack)
}
