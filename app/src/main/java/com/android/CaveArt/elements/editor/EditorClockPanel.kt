package com.android.CaveArt

import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class SliderParameter { THICKNESS, ROUNDNESS }

fun sanitizeFontLabel(filename: String): String {
    val base = filename.substringBeforeLast(".")
        .replace("_", " ")
        .replace("-", " ")
        .trim()
    val lower = base.lowercase()
    return when {
        lower.contains("oneui") || lower.contains("one ui") -> "Modern Curved"
        lower.contains("pixel") || lower.contains("google") -> "Geometric"
        lower.contains("nothing") -> "Matrix Dot"
        lower.contains("apple") || lower.contains("sf") || lower.contains("san francisco") -> "Neo Grotesque"
        lower.contains("samsung") -> "Rounded Sans"
        lower.contains("miui") || lower.contains("hyper") -> "Dynamic Sans"
        base.equals("default", ignoreCase = true) || base.isEmpty() -> "Default"
        else -> base.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorClockPanel(
    viewModel: WallpaperViewModel,
    wallpaper: Wallpaper,
    state: ClockStudioState,
    extractedColors: List<Int>,
    availableFonts: List<String>,
    previewMask: android.graphics.Bitmap?,
    realScreenW: Float,
    realScreenH: Float,
    densityVal: Float,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var activeSliderParam by remember { mutableStateOf(SliderParameter.THICKNESS) }

    var hoursString by remember { mutableStateOf("08") }
    var minsString by remember { mutableStateOf("33") }

    LaunchedEffect(Unit) {
        while (true) {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            val now = java.util.Date()
            hoursString = java.text.SimpleDateFormat(if (is24) "HH" else "hh", java.util.Locale.getDefault()).format(now)
            minsString = java.text.SimpleDateFormat("mm", java.util.Locale.getDefault()).format(now)
            delay(1000L)
        }
    }

    AmbientBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        viewModel = viewModel,
        currentWallpaper = wallpaper
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Clock Face",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = CircleShape,
                    color = if (state.dualTone.value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.clip(CircleShape).clickable {
                        state.dualTone.value = !state.dualTone.value
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (state.dualTone.value) Icons.Rounded.Contrast else Icons.Rounded.Circle,
                            contentDescription = null,
                            tint = if (state.dualTone.value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Dual Tone",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (state.dualTone.value) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(availableFonts) { fontFile ->
                    val isSelected = state.clockFont.value == fontFile

                    val androidTypeface = remember(fontFile) {
                        if (fontFile == "default" || fontFile.isEmpty()) {
                            Typeface.create("sans-serif-bold", Typeface.NORMAL)
                        } else {
                            try {
                                Typeface.createFromAsset(context.assets, "fonts/$fontFile")
                            } catch (e: Exception) {
                                Typeface.create("sans-serif-bold", Typeface.NORMAL)
                            }
                        }
                    }
                    val composeFontFamily = remember(androidTypeface) {
                        FontFamily(androidTypeface)
                    }

                    LiveExpressiveClockTile(
                        hours = hoursString,
                        mins = minsString,
                        isVertical = state.clockLayout.intValue == 1,
                        fontFamily = composeFontFamily,
                        isSelected = isSelected,
                        clockColor = state.clockColor.intValue,
                        isDualTone = state.dualTone.value,
                        onClick = { state.clockFont.value = fontFile }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .height(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(2.dp)
                    ) {
                        val indicatorOffset by animateDpAsState(
                            targetValue = if (state.clockLayout.intValue == 0) 0.dp else 56.dp,
                            animationSpec = spring(stiffness = 650f, dampingRatio = 0.78f),
                            label = "layoutIndicator"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .fillMaxWidth(0.5f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        )

                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .clickable { state.clockLayout.intValue = 0 },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapHoriz,
                                    contentDescription = "Horizontal",
                                    tint = if (state.clockLayout.intValue == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .clickable { state.clockLayout.intValue = 1 },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SwapVert,
                                    contentDescription = "Vertical",
                                    tint = if (state.clockLayout.intValue == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    ExpressiveCommandPill(
                        icon = Icons.Rounded.AutoFixHigh,
                        label = "Auto Fit",
                        isActive = false,
                        enabled = previewMask != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (previewMask != null) scope.launch(Dispatchers.Default) {
                            val result = AdaptiveClockHelper.calculateAutoFit(
                                previewMask, realScreenW, realScreenH,
                                state.hourSize.floatValue, state.minSize.floatValue,
                                densityVal, viewModel.isMagicShapeEnabled,
                                viewModel.magicScale, viewModel.isCentered
                            )
                            if (result != null) withContext(Dispatchers.Main) {
                                state.hourSize.floatValue = result.hourSize
                                state.minSize.floatValue = result.minSize
                                state.clockX.floatValue = 0f
                                state.clockY.floatValue = result.yDp
                            }
                        }
                    }

                    ExpressiveCommandPill(
                        icon = Icons.Rounded.Transform,
                        label = "Adaptive",
                        isActive = state.stretchEnabled.value,
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    ) {
                        state.stretchEnabled.value = !state.stretchEnabled.value
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                .padding(3.dp)
                        ) {
                            CompactTabChip(
                                title = "Thickness",
                                isSelected = activeSliderParam == SliderParameter.THICKNESS,
                                onClick = { activeSliderParam = SliderParameter.THICKNESS }
                            )
                            CompactTabChip(
                                title = "Roundness",
                                isSelected = activeSliderParam == SliderParameter.ROUNDNESS,
                                onClick = { activeSliderParam = SliderParameter.ROUNDNESS }
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (activeSliderParam == SliderParameter.THICKNESS) {
                                    String.format("%.1f", state.strokeWidth.floatValue)
                                } else {
                                    String.format("%.1f", state.roundness.floatValue)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black, fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    if (activeSliderParam == SliderParameter.THICKNESS) {
                        ExpressiveCapsuleSlider(
                            value = state.strokeWidth.floatValue,
                            onValueChange = { state.strokeWidth.floatValue = it },
                            valueRange = 0f..25f,
                            icon = Icons.Default.LineWeight,
                            label = ""
                        )
                    } else {
                        ExpressiveCapsuleSlider(
                            value = state.roundness.floatValue,
                            onValueChange = { state.roundness.floatValue = it },
                            valueRange = 0f..80f,
                            icon = Icons.Default.RoundedCorner,
                            label = ""
                        )
                    }
                }
            }

            ExpressiveColorHaloSelector(
                colors = extractedColors,
                selectedColor = state.clockColor.intValue,
                onColorSelected = { state.clockColor.intValue = it }
            )
        }
    }
}

@Composable
private fun LiveExpressiveClockTile(
    hours: String,
    mins: String,
    isVertical: Boolean,
    fontFamily: FontFamily,
    isSelected: Boolean,
    clockColor: Int,
    isDualTone: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.93f else 1f, spring(stiffness = 600f), label = "tileScale")

    val minuteColor = safeColor(clockColor)
    val hourColor = if (isDualTone) {
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    } else minuteColor

    Card(
        modifier = Modifier
            .size(width = 92.dp, height = 62.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = if (isSelected) 2.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isVertical) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = hours,
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 14.sp),
                        color = hourColor
                    )
                    Text(
                        text = mins,
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 14.sp),
                        color = minuteColor
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = hours,
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, fontSize = 18.sp),
                        color = hourColor
                    )
                    Text(
                        text = ":",
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                        color = hourColor
                    )
                    Text(
                        text = mins,
                        fontFamily = fontFamily,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black, fontSize = 18.sp),
                        color = minuteColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCommandPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = CircleShape,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CompactTabChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                fontSize = 12.sp
            ),
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
