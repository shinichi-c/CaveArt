package com.android.CaveArt

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import kotlin.random.Random

data class Particle(
    val initialX: Float, val initialY: Float, val radius: Float,
    val speed: Float, val swaySpeed: Float, val initialAlpha: Float
)

fun safeColor(colorInt: Int): Color {
    return Color(
        alpha = (colorInt ushr 24) and 0xFF,
        red = (colorInt ushr 16) and 0xFF,
        green = (colorInt ushr 8) and 0xFF,
        blue = colorInt and 0xFF
    )
}

fun isDefaultColor(colorInt: Int): Boolean {
    return colorInt == 0xFF1A1C1E.toInt() || 
           colorInt == 0xFF1E2022.toInt() || 
           colorInt == 0xFF4CAF50.toInt() || 
           colorInt == 0
}

@Composable
fun AsyncWallpaperImage(
    wallpaper: Wallpaper,
    contentDescription: String?,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    allowMagic: Boolean = true
) {
    if (!allowMagic) {
        val model = wallpaper.uri ?: wallpaper.resourceId
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        val context = LocalContext.current
        var bitmap by remember(wallpaper) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(wallpaper) {
            bitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, true)
        }

        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier
            )
        } else {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
fun ExpressiveSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun ExpressiveCapsuleSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String = ""
) {
    val view = LocalView.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (label.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = String.format("%.1f", value),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Slider(
                value = value.coerceIn(valueRange.start, valueRange.endInclusive),
                onValueChange = {
                    onValueChange(it)
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                },
                valueRange = valueRange,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )
        }
    }
}

@Composable
fun ExpressiveChunkySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    label: String = ""
) {
    ExpressiveCapsuleSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        icon = icon,
        label = label
    )
}

@Composable
fun <T> ExpressiveSegmentedPill(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    iconProvider: ((T) -> ImageVector?)? = null
) {
    val view = LocalView.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / items.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = spring(stiffness = 500f, dampingRatio = 0.75f),
            label = "tabIndicator"
        )

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        )

        Row(modifier = Modifier.fillMaxSize()) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                val icon = iconProvider?.invoke(item)

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (!isSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = labelProvider(item),
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveColorHaloSelector(
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showCustomPicker by remember { mutableStateOf(false) }

    val eyeDropperLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedColor = result.data?.getIntExtra("android.intent.extra.COLOR", selectedColor)
                ?: selectedColor
            onColorSelected(pickedColor)
        }
    }

    val displayColors = remember(colors, selectedColor) {
        if (selectedColor != 0 && !isDefaultColor(selectedColor) && !colors.contains(selectedColor)) {
            listOf(selectedColor) + colors
        } else {
            colors
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    val eyeDropperIntent = Intent("android.intent.action.OPEN_EYE_DROPPER")
                    if (context.packageManager.resolveActivity(eyeDropperIntent, 0) != null) {
                        eyeDropperLauncher.launch(eyeDropperIntent)
                    } else {
                        showCustomPicker = true
                    }
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Colorize,
                    contentDescription = "Pick Custom Color",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(displayColors) { colorInt ->
                val isSelected = colorInt == selectedColor
                val composeColor = safeColor(colorInt)
                val isBright = composeColor.luminance() > 0.5f

                val innerScale by animateFloatAsState(
                    targetValue = if (isSelected) 0.78f else 0.94f,
                    animationSpec = spring(stiffness = 600f, dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "haloScale"
                )

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
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
                                .border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = innerScale
                                scaleY = innerScale
                            }
                            .clip(CircleShape)
                            .background(composeColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (isBright) Color.Black else Color.White,
                                modifier = Modifier.size(16.dp)
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
fun ExpressiveCustomColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onColorConfirmed: (Int) -> Unit
) {
    val hsv = remember(initialColor) {
        val array = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, array)
        array
    }

    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(if (hsv[1] == 0f) 0.8f else hsv[1]) }
    var value by remember { mutableFloatStateOf(if (hsv[2] == 0f) 0.9f else hsv[2]) }

    val currentColorInt = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Custom Color", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 90.dp, height = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(safeColor(currentColorInt))
                            .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = String.format("#%06X", (0xFFFFFF and currentColorInt)),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                Column {
                    Text("Hue", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Red, Color.Yellow, Color.Green,
                                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                    )
                                )
                            )
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = safeColor(currentColorInt),
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }

                Column {
                    Text("Vibrancy / Saturation", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0.05f..1f
                    )
                }

                Column {
                    Text("Luminance / Tone", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = value,
                        onValueChange = { value = it },
                        valueRange = 0.1f..1f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onColorConfirmed(currentColorInt) },
                shape = CircleShape
            ) {
                Text("Apply Color", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun StaggeredRow(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 35L)
        isVisible = true
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { 60 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn(tween(300)),
        modifier = modifier
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    viewModel: WallpaperViewModel,
    currentWallpaper: Wallpaper?,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)))
        ) {
            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                AsyncWallpaperImage(
                    wallpaper = currentWallpaper,
                    contentDescription = null,
                    viewModel = viewModel,
                    modifier = Modifier.matchParentSize().blur(100.dp),
                    allowMagic = false
                )
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)))
            } else {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.width(42.dp).height(5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape))
                }
                content()
            }
        }
    }
}

/**
 * Upgraded Material 3 Expressive Loading Overlay using Morphing Shapes
 */
@Composable
fun LoadingOverlay(title: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 24.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            modifier = Modifier
                .wrapContentSize()
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                M3ExpressiveMorphLoadingIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    sizeDp = 54.dp
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Material 3 Expressive Shape-Morphing Loading Animation
 */
@Composable
fun M3ExpressiveMorphLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    sizeDp: Dp = 48.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "M3Loading")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "MorphProgress"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    val shapes = listOf(
        MagicShape.CIRCLE,
        MagicShape.SQUIRCLE,
        MagicShape.COOKIE_4,
        MagicShape.COOKIE_9,
        MagicShape.CIRCLE
    )

    val livePath = remember { Path() }
    val rect = remember { RectF() }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
        shadowElevation = 8.dp,
        modifier = modifier.size(sizeDp + 16.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(sizeDp)
                    .graphicsLayer { rotationZ = rotation }
            ) {
                val stage = progress.toInt().coerceIn(0, 3)
                val stageProgress = progress - stage
                val from = shapes[stage]
                val to = shapes[stage + 1]

                rect.set(0f, 0f, size.width, size.height)
                PixelShapeMorpher.buildMorphedPath(from, to, stageProgress, rect, livePath)

                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        this.color = color.toArgb()
                        this.style = android.graphics.Paint.Style.FILL
                        this.isAntiAlias = true
                    }
                    canvas.nativeCanvas.drawPath(livePath, paint)
                }
            }
        }
    }
}

@Composable
fun ParticleLoadingOverlay(color: Color) {
    val density = LocalDensity.current
    val particles = remember {
        List(350) {
            Particle(
                Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 3f + 1f,
                Random.nextFloat() * 0.05f + 0.01f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 0.7f + 0.1f
            )
        }
    }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameTime -> time = (frameTime - startTime) / 1_000_000_000f }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEachIndexed { index, p ->
            var yProgress = (p.initialY - (p.speed * time)) % 1f
            if (yProgress < 0) yProgress += 1f
            val rawSway = kotlin.math.sin((time * p.swaySpeed + index).toDouble()).toFloat()
            val blinkFactor = ((kotlin.math.sin((time * p.swaySpeed * 3f + index).toDouble()).toFloat() + 1) / 2f).let { it * it }
            drawCircle(
                color = color,
                radius = p.radius * density.density,
                center = Offset((p.initialX * size.width) + (rawSway * 15.dp.toPx()), yProgress * size.height),
                alpha = (p.initialAlpha * blinkFactor).coerceIn(0f, 1f)
            )
        }
    }
}

@Composable
fun ShapeIcon(shape: MagicShape, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rect = RectF(0f, 0f, size.width, size.height)
        drawPath(path = ShapePathProvider.getPathForShape(shape, rect).asComposePath(), color = color)
    }
}

@Composable
fun DestinationButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSetting: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "destScale")

    Button(
        onClick = onClick,
        enabled = !isSetting,
        modifier = Modifier.fillMaxWidth().height(96.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(icon, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
            }
            if (isSetting) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun CategoryChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(title, fontWeight = FontWeight.Bold) },
        shape = CircleShape,
        modifier = Modifier.padding(4.dp)
    )
}
