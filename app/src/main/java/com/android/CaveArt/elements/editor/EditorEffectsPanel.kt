package com.android.CaveArt

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.android.CaveArt.animations.AnimSetting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsControlsSheet(
    viewModel: WallpaperViewModel, 
    wallpaper: Wallpaper, 
    previewMask: Bitmap? = null, 
    extractedColors: List<Int>, 
    onDismiss: () -> Unit = {} 
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    AmbientBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var localMask by remember { mutableStateOf(previewMask) }
        LaunchedEffect(wallpaper, previewMask) { if (previewMask != null) localMask = previewMask else localMask = viewModel.getMaskForClock(context, wallpaper) }

        val modelPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val safeContext = if (android.os.Build.VERSION.SDK_INT >= 24) context.createDeviceProtectedStorageContext() else context
                        context.contentResolver.openInputStream(uri)?.use { input -> java.io.File(safeContext.filesDir, "custom_model.glb").outputStream().use { output -> input.copyTo(output) } }
                        withContext(Dispatchers.Main) { viewModel.setFilamentEnabled(false); delay(100); viewModel.setFilamentEnabled(true); android.widget.Toast.makeText(context, "3D Model Imported!", android.widget.Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }

        val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle) }
        
        val tabs = remember(viewModel.isMagicShapeEnabled, viewModel.isAnimationEnabled, viewModel.isFilamentEnabled) {
            val list = mutableListOf<String>()
            if (viewModel.isMagicShapeEnabled) list.add("Shape")
            if (viewModel.isAnimationEnabled) list.add("Animation")
            if (viewModel.isFilamentEnabled) list.add("3D Engine")
            list
        }
        var selectedTab by remember { mutableStateOf("") }
        LaunchedEffect(tabs) { if (!tabs.contains(selectedTab) && tabs.isNotEmpty()) selectedTab = tabs.first() }
        
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)), horizontalAlignment = Alignment.CenterHorizontally) {
            
            if (tabs.isNotEmpty()) {
                Surface(modifier = Modifier.padding(bottom = 16.dp).height(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 0.dp) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        tabs.forEach { tab ->
                            val isSelected = selectedTab == tab
                            val tabIcon = when (tab) { "Shape" -> Icons.Default.Category; "Animation" -> Icons.Default.Animation; "3D Engine" -> Icons.Default.ViewInAr; else -> Icons.Default.Palette }
                            Box(modifier = Modifier.height(56.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable { selectedTab = tab }.padding(horizontal = if (isSelected) 24.dp else 20.dp), contentAlignment = Alignment.Center) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { if (isSelected) Icon(tabIcon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer); Text(tab, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth()) {
                when (selectedTab) {
                    "Shape" -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                                MagicShape.values().take(5).forEach { shape ->
                                    val isSelected = viewModel.currentMagicShape == shape
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(20.dp)).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) }, contentAlignment = Alignment.Center) { ShapeIcon(shape = shape, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxSize(0.45f)) }
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                                extractedColors.forEach { colorInt ->
                                    val isSelected = viewModel.currentBackgroundColor == colorInt
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                        if (isSelected) Box(modifier = Modifier.fillMaxSize().border(3.dp, Color(colorInt), CircleShape))
                                        Box(modifier = Modifier.fillMaxSize(if (isSelected) 0.65f else 1f).clip(CircleShape).background(Color(colorInt)).clickable { viewModel.updateMagicConfig(viewModel.currentMagicShape, colorInt) })
                                    }
                                }
                            }
                            
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(16.dp))
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilterChip(selected = viewModel.is3DPopEnabled, onClick = { viewModel.is3DPopEnabled = !viewModel.is3DPopEnabled }, label = { Text("3D Pop", fontSize = 15.sp, fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(vertical = 4.dp), border = null)
                                    FilterChip(selected = viewModel.isCentered, onClick = { viewModel.isCentered = !viewModel.isCentered }, label = { Text("Center", fontSize = 15.sp, fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.FilterCenterFocus, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(vertical = 4.dp), border = null)
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Effect Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (viewModel.magicScale < 1.0f) "Tight" else "Wide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                Slider(value = viewModel.magicScale, onValueChange = { viewModel.magicScale = it }, valueRange = 0.5f..1.5f)
                            }
                        }
                    }
                    "Animation" -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Engine Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.height(16.dp))
                            
                            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val styles = AnimationStyle.values()
                                items(styles.size) { index ->
                                    val style = styles[index]
                                    val isSelected = viewModel.currentAnimationStyle == style
                                    
                                    val tileScale by animateFloatAsState(if (isSelected) 1.08f else 1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "tileScale")
                                    val borderColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), spring(), label = "borderColor")
                                    
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Card(
                                            modifier = Modifier
                                                .scale(tileScale)
                                                .size(120.dp, 80.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .border(if (isSelected) 3.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
                                                .clickable { viewModel.updateAnimationStyle(style) },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                LiveAnimationTile(style)
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(style.label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            
                            val allSettings = remember(currentAnim) {
                                val list = mutableListOf<AnimSetting>()
                                if (currentAnim.supports3DPop()) list.add(AnimSetting.Toggle("global_3d_pop", "3D Pop Depth", viewModel.is3DPopEnabled))
                                if (currentAnim.supportsCenter()) list.add(AnimSetting.Toggle("global_center", "Force Center", viewModel.isCentered))
                                if (currentAnim.supportsScale()) list.add(AnimSetting.Slider("global_scale", "Engine Scale", 0.5f, 1.5f, viewModel.magicScale))
                                list.addAll(currentAnim.getCustomSettings())
                                list
                            }

                            if (allSettings.isNotEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(16.dp))
                                
                                var selectedParamId by remember(currentAnim) { mutableStateOf(allSettings.first().id) }
                                if (allSettings.none { it.id == selectedParamId }) selectedParamId = allSettings.first().id
                                
                                LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(allSettings.size) { i ->
                                        val setting = allSettings[i]
                                        FilterChip(
                                            selected = selectedParamId == setting.id,
                                            onClick = { selectedParamId = setting.id },
                                            label = { Text(setting.title, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
                                            shape = RoundedCornerShape(24.dp), modifier = Modifier.padding(vertical = 4.dp),
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer)
                                        )
                                    }
                                }
                                
                                Spacer(Modifier.height(16.dp))

                                val activeSetting = allSettings.find { it.id == selectedParamId }
                                if (activeSetting != null) {
                                    val currentValue = when (activeSetting.id) {
                                        "global_scale" -> viewModel.magicScale
                                        "global_3d_pop" -> if (viewModel.is3DPopEnabled) 1f else 0f
                                        "global_center" -> if (viewModel.isCentered) 1f else 0f
                                        else -> viewModel.currentAnimParams[activeSetting.id] ?: (activeSetting as? AnimSetting.Slider)?.defaultValue ?: 0f
                                    }

                                    val onValueChange: (Float) -> Unit = { newVal ->
                                        when (activeSetting.id) {
                                            "global_scale" -> viewModel.magicScale = newVal
                                            "global_3d_pop" -> viewModel.is3DPopEnabled = newVal > 0.5f
                                            "global_center" -> viewModel.isCentered = newVal > 0.5f
                                            else -> viewModel.updateAnimParam(activeSetting.id, newVal)
                                        }
                                    }
                                    
                                    AnimatedContent(
                                        targetState = activeSetting,
                                        transitionSpec = {
                                            slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) { height -> height } + fadeIn() togetherWith
                                            slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) { height -> -height } + fadeOut()
                                        },
                                        label = "SettingAnim"
                                    ) { setting ->
                                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                                            if (setting is AnimSetting.Slider) {
                                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                        Text(setting.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(String.format("%.2f", currentValue), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                                            Spacer(Modifier.width(8.dp))
                                                            Icon(Icons.Default.Refresh, "Reset", modifier = Modifier.size(16.dp).clickable { onValueChange(setting.defaultValue) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                    Slider(
                                                        value = currentValue, onValueChange = onValueChange,
                                                        valueRange = setting.minValue..setting.maxValue, modifier = Modifier.padding(top = 4.dp).height(24.dp)
                                                    )
                                                }
                                            } else if (setting is AnimSetting.Toggle) {
                                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(setting.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                                    Switch(checked = currentValue > 0.5f, onCheckedChange = { onValueChange(if (it) 1f else 0f) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    "3D Engine" -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Filament Engine Active", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text("Applies a physical 3D scene to your background. Import a .glb file to render it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { modelPickerLauncher.launch("*/*") }, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Icon(Icons.Default.FolderOpen, "Import"); Spacer(Modifier.width(8.dp)); Text("Import .GLB Model", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveAnimationTile(style: AnimationStyle) {
    val infiniteTransition = rememberInfiniteTransition(label = "AnimTile")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, 
        targetValue = (2.0 * Math.PI).toFloat(), 
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "TileTime"
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width / 2f
        val cy = size.height / 2f

        when (style) {
            AnimationStyle.MORPH -> {
                val cols = 8; val rows = 5
                val spacingX = size.width / cols
                val spacingY = size.height / rows
                
                for (x in 1 until cols) {
                    val path = Path()
                    for (y in 0..10) {
                        val py = (y / 10f) * size.height
                        val warpX = sin(py * 0.05f + time * 3f) * cos((x * spacingX) * 0.05f + time * 3f) * 12f
                        if (y == 0) path.moveTo(x * spacingX + warpX, py) else path.lineTo(x * spacingX + warpX, py)
                    }
                    drawPath(path, primaryColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))
                }
                for (y in 1 until rows) {
                    val path = Path()
                    for (x in 0..10) {
                        val px = (x / 10f) * size.width
                        val warpY = sin((y * spacingY) * 0.05f + time * 3f) * cos(px * 0.05f + time * 3f) * 12f
                        if (x == 0) path.moveTo(px, y * spacingY + warpY) else path.lineTo(px, y * spacingY + warpY)
                    }
                    drawPath(path, primaryColor.copy(alpha = 0.4f), style = Stroke(width = 2.dp.toPx()))
                }
            }
            AnimationStyle.NANO_ASSEMBLY -> {
                
                val rotAngle = (time / (2.0 * Math.PI).toFloat()) * 360f
                val cardW = 32.dp.toPx()
                val cardH = 48.dp.toPx()
                
                val auraPulse = (sin(time * 2f) + 1f) / 2f
                drawCircle(Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.4f * auraPulse), Color.Transparent), center = Offset(cx, cy)), radius = 50.dp.toPx())
                
                rotate(degrees = rotAngle, pivot = Offset(cx, cy)) {
                    drawRoundRect(
                        color = primaryColor.copy(alpha = 0.5f),
                        topLeft = Offset(cx - cardW/2, cy - cardH/2),
                        size = Size(cardW, cardH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                    
                    val sweepPos = (time * 1.5f % (2.0 * Math.PI).toFloat()) / (2.0 * Math.PI).toFloat() 
                    val sweepY = (sweepPos * cardH) - (cardH / 2)
                    drawLine(
                        color = secondaryColor.copy(alpha = 0.9f),
                        start = Offset(cx - cardW/2 - 4f, cy + sweepY),
                        end = Offset(cx + cardW/2 + 4f, cy + sweepY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            AnimationStyle.ORGANIC_BLOB -> {
                val path = Path()
                val baseR = 24.dp.toPx()
                for (i in 0..60) {
                    val a = (i / 60f) * 2f * PI.toFloat()
                    val offset = sin(a * 4f + time * 2.2f) * (baseR * 0.1f) + cos(a * 3f - time * 1.5f) * (baseR * 0.08f)
                    val r = baseR + offset
                    val x = cx + r * cos(a)
                    val y = cy + r * sin(a)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                drawPath(path, primaryColor.copy(alpha = 0.8f))
            }
            AnimationStyle.FLUID_MORPH -> {
                for (i in 3 downTo 1) {
                    val scale = 1f + (i * 0.2f)
                    val phase = i * 0.5f
                    val path = Path()
                    val baseR = 16.dp.toPx() * scale
                    for (j in 0..60) {
                        val a = (j / 60f) * 2f * PI.toFloat()
                        val offset = sin(a * 3f + (time - phase)) * (baseR * 0.15f) + cos(a * 2f - (time - phase)) * (baseR * 0.1f)
                        val r = baseR + offset
                        val x = cx + r * cos(a)
                        val y = cy + r * sin(a)
                        if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, primaryColor.copy(alpha = 0.25f))
                }
            }
            AnimationStyle.EXPRESSIVE_HORIZON -> {
                val pulse = (sin(time * 1.5f) + 1f) / 2f
                val bloomRadius = size.width * (0.7f + 0.3f * pulse)
                
                val bloomCenter = Offset(cx, cy * 1.1f)
                
                drawCircle(
                    brush = Brush.radialGradient(listOf(primaryColor.copy(alpha = 0.7f), Color.Transparent), center = bloomCenter),
                    radius = bloomRadius,
                    center = bloomCenter
                )
                
                val waveOffset = (time * 40f) % (size.width * 2)
                for (i in -2..2) {
                    val xBase = (i * 40f) + waveOffset - size.width
                    drawLine(
                        color = primaryColor.copy(alpha = 0.15f),
                        start = Offset(xBase, size.height),
                        end = Offset(xBase + size.width, 0f),
                        strokeWidth = 12.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
