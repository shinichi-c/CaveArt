package com.android.CaveArt

import android.content.Intent
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.clockkit.*
import kotlin.math.abs

fun clockIconToImageVector(icon: ClockIcon): ImageVector = when (icon) {
    ClockIcon.STYLE -> Icons.Rounded.AutoAwesome
    ClockIcon.SIZE -> Icons.Rounded.ZoomIn
    ClockIcon.THICKNESS -> Icons.Rounded.LineWeight
    ClockIcon.ROUNDNESS -> Icons.Rounded.RoundedCorner
    ClockIcon.SPACING -> Icons.Rounded.SwapHoriz
    ClockIcon.OPACITY -> Icons.Rounded.Opacity
    ClockIcon.GRAVITY -> Icons.Rounded.Explore
    ClockIcon.FONT -> Icons.Rounded.Title
    ClockIcon.COLOR -> Icons.Rounded.Palette
    ClockIcon.LAYOUT -> Icons.Rounded.DashboardCustomize
    ClockIcon.OPTIONS -> Icons.Rounded.Tune
    ClockIcon.DEPTH -> Icons.Rounded.Layers
    ClockIcon.DATE -> Icons.Rounded.Schedule
    ClockIcon.DIAL -> Icons.Rounded.Schedule
    ClockIcon.EFFECTS -> Icons.Rounded.AutoFixHigh
    ClockIcon.TUNE -> Icons.Rounded.Tune
}

@Composable
fun VerticalLayerActionSwitcher(
    actions: List<LayerAction>,
    selectedIndex: Int,
    onActionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .wrapContentSize()
            .pointerInput(actions.size, selectedIndex) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumulator += dragAmount
                        val thresholdPx = 32f
                        if (abs(dragAccumulator) > thresholdPx) {
                            val step = if (dragAccumulator > 0) 1 else -1
                            val newIdx = (selectedIndex + step).coerceIn(0, actions.size - 1)
                            if (newIdx != selectedIndex) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onActionSelected(newIdx)
                            }
                            dragAccumulator = 0f
                        }
                    }
                )
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        actions.forEachIndexed { index, action ->
            val isSelected = index == selectedIndex
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.14f else 0.94f,
                animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
                label = "actionIconScale"
            )
            val iconTint by animateColorAsState(
                targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
                label = "actionIconTint"
            )
            val dotProgress by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0f,
                animationSpec = spring(stiffness = 520f, dampingRatio = 0.7f),
                label = "actionDotScale"
            )

            Column(
                modifier = Modifier
                    .size(width = 36.dp, height = 38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (!isSelected) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onActionSelected(index)
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.label,
                    tint = iconTint,
                    modifier = Modifier
                        .size(21.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .size(4.5.dp)
                        .graphicsLayer {
                            scaleX = dotProgress
                            scaleY = dotProgress
                            alpha = dotProgress
                        }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun CompactChoiceStrip(
    options: List<ClockControl.ChoiceOption>,
    selectedKey: String,
    onSelected: (String) -> Unit
) {
    val view = LocalView.current
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(options, key = { it.key }) { opt ->
            FilterChip(
                selected = opt.key == selectedKey,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onSelected(opt.key)
                },
                leadingIcon = opt.icon?.let { ic ->
                    {
                        Icon(
                            imageVector = clockIconToImageVector(ic),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                label = { Text(opt.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
    }
}

@Composable
fun CompactQuickChipsStrip(
    items: List<QuickChipItem>,
    styleId: String,
    config: ClockKitConfig,
    onConfigChange: (ClockKitConfig) -> Unit,
    onCustomAction: (String) -> Unit
) {
    val view = LocalView.current
    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items.size) { idx ->
            when (val chip = items[idx]) {
                is QuickChipItem.ToggleChip -> {
                    val isChecked = when (chip.globalBinding) {
                        "depth" -> config.isDepthEnabled
                        "gravity" -> config.isGravityEnabled
                        else -> config.getBool(styleId, chip.paramId, chip.defaultValue)
                    }
                    FilterChip(
                        selected = isChecked,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val next = !isChecked
                            val updated = when (chip.globalBinding) {
                                "depth" -> config.copy(isDepthEnabled = next)
                                "gravity" -> config.copy(isGravityEnabled = next)
                                else -> config.withBool(styleId, chip.paramId, next)
                            }
                            onConfigChange(updated)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = when (chip.globalBinding) {
                                    "depth" -> Icons.Rounded.Layers
                                    "gravity" -> Icons.Rounded.Explore
                                    else -> clockIconToImageVector(chip.icon)
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
                is QuickChipItem.CycleChip -> {
                    val currentKey = config.getString(styleId, chip.paramId, chip.defaultKey)
                    val curIdx = chip.options.indexOfFirst { it.key == currentKey }.coerceAtLeast(0)
                    val currentOpt = chip.options[curIdx]
                    FilterChip(
                        selected = curIdx > 0,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            val nextOpt = chip.options[(curIdx + 1) % chip.options.size]
                            onConfigChange(config.withString(styleId, chip.paramId, nextOpt.key))
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (currentOpt.key == "vertical") Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(currentOpt.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
                is QuickChipItem.ActionChip -> {
                    FilterChip(
                        selected = false,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onCustomAction(chip.actionId)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(chip.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        shape = CircleShape
                    )
                }
            }
        }
    }
}

@Composable
fun CompactSingleToggleBar(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val view = LocalView.current
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCheckedChange(it)
            }
        )
    }
}

@Composable
fun CompactFontSelectorStrip(
    availableFonts: List<String>,
    selectedFont: String,
    onFontSelected: (String) -> Unit,
    hours: String,
    mins: String
) {
    val context = LocalContext.current
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(availableFonts) { fontFile ->
            val isSelected = selectedFont == fontFile
            val androidTypeface = remember(fontFile) {
                if (fontFile == "default" || fontFile.isEmpty()) {
                    Typeface.create("sans-serif-bold", Typeface.NORMAL)
                } else {
                    try {
                        Typeface.createFromAsset(context.assets, "fonts/$fontFile")
                    } catch (_: Exception) {
                        Typeface.create("sans-serif-bold", Typeface.NORMAL)
                    }
                }
            }
            val composeFont = remember(androidTypeface) { FontFamily(androidTypeface) }

            Surface(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onFontSelected(fontFile)
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$hours:$mins",
                        fontFamily = composeFont,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun CompactColorSelectorStrip(
    extractedColors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit
) {
    val view = LocalView.current
    var showCustomPicker by remember { mutableStateOf(false) }

    val eyeDropperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val colorInt = result.data?.getIntExtra("android.intent.extra.COLOR", selectedColor)
                ?: result.data?.getIntExtra(Intent.EXTRA_COLOR, selectedColor)
                ?: selectedColor
            onColorSelected(colorInt)
        }
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val intent = Intent("android.intent.action.OPEN_EYE_DROPPER")
                    try {
                        eyeDropperLauncher.launch(intent)
                    } catch (_: Exception) {
                        showCustomPicker = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Colorize,
                    contentDescription = "Pick Custom Color",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(extractedColors) { colorInt ->
                val isSelected = colorInt == selectedColor
                val composeColor = safeColor(colorInt)
                val isBright = composeColor.luminance() > 0.5f

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                onColorSelected(colorInt)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize(if (isSelected) 0.8f else 0.95f)
                            .clip(CircleShape)
                            .background(composeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isBright) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCustomPicker) {
        ExpressiveCustomColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showCustomPicker = false },
            onColorConfirmed = { chosen ->
                onColorSelected(chosen)
                showCustomPicker = false
            }
        )
    }
}

@Composable
fun CompactClockOptionsStrip(
    isVertical: Boolean,
    onToggleLayout: () -> Unit,
    isDualTone: Boolean,
    onToggleDualTone: () -> Unit,
    isDepthEnabled: Boolean,
    onToggleDepth: () -> Unit,
    isAdaptive: Boolean,
    onToggleAdaptive: () -> Unit,
    isGravity: Boolean,
    onToggleGravity: () -> Unit,
    onAutoFit: () -> Unit
) {
    val view = LocalView.current

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            FilterChip(
                selected = isVertical,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleLayout()
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (isVertical) Icons.Rounded.SwapVert else Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text(if (isVertical) "Vertical" else "Horizontal", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isGravity,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onToggleGravity()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Explore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Gravity 3D", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }

        item {
            FilterChip(
                selected = isDualTone,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleDualTone()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Contrast,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Dual Tone", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isDepthEnabled,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onToggleDepth()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Depth", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = isAdaptive,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onToggleAdaptive()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Transform,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Adaptive", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }

        item {
            FilterChip(
                selected = false,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onAutoFit()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Auto Fit", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                shape = CircleShape
            )
        }
    }
}

@Composable
fun SlimRulerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    icon: ImageVector,
    label: String,
    valueDisplay: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val view = LocalView.current
    var lastHapticTickIndex by remember { mutableIntStateOf(-1) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = accentColor
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = valueDisplay,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 12.5.sp
                ),
                color = accentColor
            )
        }

        Spacer(Modifier.height(3.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        val widthPx = size.width.toFloat()
                        val newProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)

                        val tickSpacingPx = 5f * view.resources.displayMetrics.density
                        val totalTicks = (widthPx / tickSpacingPx).toInt().coerceAtLeast(1)
                        val currentTick = (newProgress * totalTicks).toInt()

                        if (currentTick != lastHapticTickIndex) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            }
                            lastHapticTickIndex = currentTick
                        }
                    }
                }
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val newValue = valueRange.start + newProgress * (valueRange.endInclusive - valueRange.start)
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onValueChange(newValue)
                    }
                }
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val indicatorXPx = progress * widthPx

            val normalTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            val majorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val tickSpacing = 5.dp.toPx()
                val totalTicks = (size.width / tickSpacing).toInt()
                val centerY = size.height / 2f

                for (i in 0..totalTicks) {
                    val x = i * tickSpacing
                    val isMajor = (i % 5 == 0)
                    val tickHeight = if (isMajor) 11.dp.toPx() else 6.dp.toPx()
                    val tickColor = if (isMajor) majorTickColor else normalTickColor

                    drawLine(
                        color = tickColor,
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 15.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(indicatorXPx - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = Size(thumbWidth, thumbHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                val underlineWidth = 24.dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.8f),
                    start = Offset((indicatorXPx - underlineWidth / 2f).coerceAtLeast(0f), size.height - 1.dp.toPx()),
                    end = Offset((indicatorXPx + underlineWidth / 2f).coerceAtMost(size.width), size.height - 1.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
