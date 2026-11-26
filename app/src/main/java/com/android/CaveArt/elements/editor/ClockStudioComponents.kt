package com.android.CaveArt

import android.content.Intent
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.lockscreenkit.*
import kotlin.math.abs

fun clockIconToImageVector(icon: ClockIcon): ImageVector = when (icon) {
    ClockIcon.STYLE -> Icons.Outlined.Style
    ClockIcon.SIZE -> Icons.Outlined.AspectRatio
    ClockIcon.THICKNESS -> Icons.Outlined.LineWeight
    ClockIcon.ROUNDNESS -> Icons.Outlined.RoundedCorner
    ClockIcon.SPACING -> Icons.Outlined.SwapHoriz
    ClockIcon.OPACITY -> Icons.Outlined.Opacity
    ClockIcon.GRAVITY -> Icons.Outlined.Explore
    ClockIcon.FONT -> Icons.Outlined.TextFields
    ClockIcon.COLOR -> Icons.Outlined.Palette
    ClockIcon.LAYOUT -> Icons.Outlined.DashboardCustomize
    ClockIcon.OPTIONS -> Icons.Outlined.Tune
    ClockIcon.DEPTH -> Icons.Outlined.Layers
    ClockIcon.DATE -> Icons.Outlined.CalendarToday
    ClockIcon.DIAL -> Icons.Outlined.Schedule
    ClockIcon.EFFECTS -> Icons.Outlined.BlurOn
    ClockIcon.TUNE -> Icons.Outlined.Straighten
}

@Composable
private fun UnifiedLineIcon(
    kind: ToolbarIconKind,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        drawIntoCanvas { canvas ->
            LockscreenToolbarIcons.draw(
                canvas = canvas.nativeCanvas,
                kind = kind,
                tint = tint.toArgb(),
                boxSizePx = size.minDimension,
                strokeWidthPx = 1.85.dp.toPx()
            )
        }
    }
}

@Composable
fun HorizontalBottomToolbarStrip(
    actions: List<LayerAction>,
    selectedIndex: Int,
    isStyleSelected: Boolean,
    showStyleItem: Boolean = true,
    onStyleSelected: () -> Unit,
    onActionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val uniqueKinds = remember(actions, showStyleItem) {
        val candidates = actions.map { action ->
            LockscreenToolbarIcons.preferredKind(
                id = action.id,
                label = action.label,
                clockIcon = action.clockControl?.icon,
                isFont = action.toolType == ContextualToolType.FONT,
                isColor = action.toolType == ContextualToolType.COLOR,
                isQuickChips = action.toolType == ContextualToolType.QUICK_CHIPS
            )
        }
        LockscreenToolbarIcons.buildUniqueKinds(candidates, reserveStyle = showStyleItem)
    }
    val totalCount = actions.size + if (showStyleItem) 1 else 0

    if (totalCount <= 5) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStyleItem) {
                BottomToolbarItem(
                    iconKind = ToolbarIconKind.STYLE,
                    label = "Style",
                    isSelected = isStyleSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onStyleSelected()
                    }
                )
            }
            actions.forEachIndexed { index, action ->
                val isSelected = !isStyleSelected && index == selectedIndex
                BottomToolbarItem(
                    iconKind = uniqueKinds[index],
                    label = LockscreenToolbarIcons.shortLabel(action.label),
                    isSelected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onActionSelected(index)
                    }
                )
            }
        }
    } else {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStyleItem) {
                item(key = "tb_style") {
                    BottomToolbarItem(
                        iconKind = ToolbarIconKind.STYLE,
                        label = "Style",
                        isSelected = isStyleSelected,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onStyleSelected()
                        }
                    )
                }
            }
            itemsIndexed(actions, key = { _, a -> a.id }) { index, action ->
                val isSelected = !isStyleSelected && index == selectedIndex
                BottomToolbarItem(
                    iconKind = uniqueKinds[index],
                    label = LockscreenToolbarIcons.shortLabel(action.label),
                    isSelected = isSelected,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onActionSelected(index)
                    }
                )
            }
        }
    }
}

@Composable
private fun BottomToolbarItem(
    iconKind: ToolbarIconKind,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(stiffness = 500f, dampingRatio = 0.72f),
        label = "bottomStripScale"
    )
    val contentTint by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
        label = "bottomStripTint"
    )
    val pillBgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
        else Color.Transparent,
        label = "bottomStripPillBg"
    )

    Column(
        modifier = Modifier
            .widthIn(min = 62.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(CircleShape)
                .background(pillBgColor),
            contentAlignment = Alignment.Center
        ) {
            UnifiedLineIcon(
                kind = iconKind,
                tint = contentTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = contentTint
        )
    }
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
    config: LockscreenConfig,
    onConfigChange: (LockscreenConfig) -> Unit,
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
                                    "depth" -> Icons.Outlined.Layers
                                    "gravity" -> Icons.Outlined.Explore
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
                                imageVector = Icons.Outlined.AutoFixHigh,
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
            val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
            val normalTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            val majorTickColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    LockscreenToolbarIcons.drawRulerTrack(
                        canvas = canvas.nativeCanvas,
                        width = size.width,
                        trackTop = 0f,
                        trackHeight = size.height,
                        density = view.resources.displayMetrics.density,
                        progress = progress,
                        accent = accentColor.toArgb(),
                        normalTickColor = normalTickColor.toArgb(),
                        majorTickColor = majorTickColor.toArgb()
                    )
                }
            }
        }
    }
}
