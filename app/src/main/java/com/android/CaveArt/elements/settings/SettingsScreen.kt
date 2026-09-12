package com.android.CaveArt

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WallpaperViewModel,
    currentWallpaper: Wallpaper?,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
            AsyncWallpaperImage(
                wallpaper = currentWallpaper,
                contentDescription = null,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize().blur(90.dp),
                contentScale = ContentScale.Crop,
                allowMagic = false
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.78f))
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.8).sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack, modifier = Modifier.padding(start = 6.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            	
                ExpressiveSectionCard {
                    SectionHeader(
                        icon = Icons.Rounded.SwipeVertical,
                        title = "Fast Scroll Indicator",
                        subtitle = "Select dynamic scrubber physics"
                    )
                    Spacer(Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val styles = listOf("Original", "Vision Pill", "Elastic", "Hover Tooltip", "Magnetic Wave")
                        items(styles.size) { index ->
                            val isSelected = viewModel.scrollStyle == index
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Card(
                                    modifier = Modifier
                                        .size(105.dp, 64.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable { viewModel.updateScrollStyle(index) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        LiveScrollTile(index)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = styles[index],
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(14.dp))

                    SectionHeader(
                        icon = Icons.Rounded.Dock,
                        title = "Dock Style",
                        subtitle = "Choose toolbar dock elevation"
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val styles = listOf(false, true)
                        val labels = listOf("Grounded Dock", "Floating Toolbar")
                        styles.forEachIndexed { index, floating ->
                            val isSelected = viewModel.isFloatingDockEnabled == floating
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(76.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                            shape = RoundedCornerShape(22.dp)
                                        )
                                        .clickable { viewModel.setFloatingDockEnabled(floating) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        LiveDockTile(isFloating = floating)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = labels[index],
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.5.sp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                ExpressiveSectionCard {
                    ExpressiveToggleRow(
                        icon = Icons.Rounded.FitScreen,
                        title = "Fixed Alignment",
                        description = if (viewModel.isFixedAlignmentEnabled) "Static image (No scroll parallax)" else "Smooth parallax scrolling",
                        checked = viewModel.isFixedAlignmentEnabled,
                        onCheckedChange = { viewModel.setFixedAlignmentEnabled(it) }
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))

                    ExpressiveToggleRow(
                        icon = Icons.Rounded.Vibration,
                        title = "Haptic Feedback",
                        description = if (viewModel.isHapticsEnabled) "Tactile clicks active on touch" else "Vibrations disabled",
                        checked = viewModel.isHapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    Spacer(Modifier.height(12.dp))

                    ExpressiveToggleRow(
                        icon = Icons.Rounded.BlurOn,
                        title = "Ambient Glow",
                        description = if (viewModel.isAmbientBlurEnabled) "Dynamic wallpaper backlighting" else "Solid system surface",
                        checked = viewModel.isAmbientBlurEnabled,
                        onCheckedChange = { viewModel.setAmbientBlurEnabled(it) }
                    )
                }
                
                ExpressiveSectionCard {
                    Text(
                        text = "Diagnostics & Pipeline",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Real-time inspection of 3-stage neural segmentation, despilling, and matting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.runModelDiagnostics(context, currentWallpaper) },
                        enabled = !viewModel.isRunningDebug && currentWallpaper != null,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (viewModel.isRunningDebug) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White)
                        } else {
                            Text("Run Model Diagnostic", fontWeight = FontWeight.Black)
                        }
                    }

                    if (viewModel.debugResults.isNotEmpty()) {
                        viewModel.debugResults.forEach { res ->
                            Card(
                                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(res.testName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                                    Text("Input: ${res.inputType}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                    if (res.error != null) {
                                        Text("Error: ${res.error}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    } else {
                                        Text("Min: ${"%.2f".format(res.minOutput)} | Max: ${"%.2f".format(res.maxOutput)}", style = MaterialTheme.typography.bodySmall)
                                        Spacer(Modifier.height(6.dp))
                                        if (res.previewBitmap != null) {
                                            androidx.compose.foundation.Image(
                                                bitmap = res.previewBitmap.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ExpressiveToggleRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun LiveDockTile(isFloating: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiveDockTile")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "DockProgress"
    )

    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val phoneW = w * 0.45f
        val phoneH = h * 0.8f
        val phoneLeft = (w - phoneW) / 2f
        val phoneTop = (h - phoneH) / 2f

        drawRoundRect(
            color = trackColor,
            topLeft = Offset(phoneLeft, phoneTop),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(8.dp.toPx())
        )

        if (isFloating) {
            val dockW = phoneW * 0.8f
            val dockH = 8.dp.toPx()
            val hoverY = floatAnim * 3.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(phoneLeft + (phoneW - dockW) / 2f, phoneTop + phoneH - dockH - 6.dp.toPx() - hoverY),
                size = Size(dockW, dockH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
        } else {
            val dockW = phoneW
            val dockH = 12.dp.toPx() + (floatAnim * 2.dp.toPx())
            drawRoundRect(
                color = color,
                topLeft = Offset(phoneLeft, phoneTop + phoneH - dockH),
                size = Size(dockW, dockH),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRect(
                color = color,
                topLeft = Offset(phoneLeft, phoneTop + phoneH - 6.dp.toPx()),
                size = Size(dockW, 6.dp.toPx())
            )
        }
    }
}

@Composable
fun LiveScrollTile(style: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiveTile")
    val progress by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "TileProgress"
    )
    val color = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)

    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        val cy = size.height / 2f
        val w = size.width
        val cx = w / 2f
        val maxDragPx = w / 2f - 8.dp.toPx()
        val thumbX = cx + (progress * maxDragPx)

        when (style) {
            0 -> drawRoundRect(color = color, topLeft = Offset(thumbX - 10.dp.toPx(), cy - 3.dp.toPx()), size = Size(20.dp.toPx(), 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
            1 -> {
                drawRoundRect(color = trackColor, topLeft = Offset(0f, cy - 6.dp.toPx()), size = Size(w, 12.dp.toPx()), cornerRadius = CornerRadius(6.dp.toPx()))
                drawRoundRect(color = color, topLeft = Offset(thumbX - 8.dp.toPx(), cy - 4.dp.toPx()), size = Size(16.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
            }
            2 -> {
                val velocityStretch = kotlin.math.abs(progress) * 12.dp.toPx()
                drawLine(color = trackColor, start = Offset(0f, cy), end = Offset(w, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawRoundRect(color = color, topLeft = Offset(thumbX - (10.dp.toPx() + velocityStretch) / 2f, cy - 4.dp.toPx()), size = Size(10.dp.toPx() + velocityStretch, 8.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
            }
            3 -> {
                drawLine(color = trackColor, start = Offset(0f, cy), end = Offset(w, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(thumbX, cy))
                drawRoundRect(color = color.copy(alpha = 0.8f), topLeft = Offset(thumbX - 12.dp.toPx(), cy - 18.dp.toPx()), size = Size(24.dp.toPx(), 8.dp.toPx()), cornerRadius = CornerRadius(4.dp.toPx()))
            }
            4 -> {
                for (i in 0..10) {
                    val dotX = (i / 10f) * w
                    val dist = kotlin.math.abs(dotX - thumbX)
                    val pullY = if (dist < 20.dp.toPx()) (20.dp.toPx() - dist) * 0.4f else 0f
                    drawCircle(color = if (dist < 5.dp.toPx()) color else trackColor, radius = if (dist < 5.dp.toPx()) 3.dp.toPx() else 1.5.dp.toPx(), center = Offset(dotX, cy - pullY))
                }
            }
        }
    }
}
