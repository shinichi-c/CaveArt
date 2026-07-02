package com.android.CaveArt

import android.graphics.Bitmap
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.CaveArt.animations.AnimationFactory
import com.android.CaveArt.animations.AnimationStyle
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import com.materialkolor.hct.Hct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EffectsControlsSheet(wallpaper: Wallpaper, viewModel: WallpaperViewModel, previewMask: Bitmap? = null) {
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

    var extractedColors by remember { mutableStateOf(listOf<Int>()) }
    var sliderPosition by remember { mutableFloatStateOf(viewModel.magicScale) }
    val currentAnim = remember(viewModel.currentAnimationStyle) { AnimationFactory.getAnimation(viewModel.currentAnimationStyle) }
    
    val tabs = remember(viewModel.isMagicShapeEnabled, viewModel.isAnimationEnabled, viewModel.isFilamentEnabled, currentAnim) {
        val list = mutableListOf<String>()
        if (viewModel.isMagicShapeEnabled) { list.add("Shape"); list.add("Style") }
        if (viewModel.isAnimationEnabled) { list.add("Animation"); if (currentAnim.supports3DPop() || currentAnim.supportsCenter() || currentAnim.supportsScale() || currentAnim.hasCustomUI()) list.add("Style") }
        if (viewModel.isFilamentEnabled) list.add("3D Engine")
        list
    }
    var selectedTab by remember { mutableStateOf("") }
    LaunchedEffect(tabs) { if (!tabs.contains(selectedTab) && tabs.isNotEmpty()) selectedTab = tabs.first() }

    LaunchedEffect(wallpaper) {
        withContext(Dispatchers.IO) {
            val bitmap = if(wallpaper.uri != null) BitmapHelper.decodeSampledBitmapFromUri(context, wallpaper.uri, 500) else BitmapHelper.decodeSampledBitmapFromResource(context.resources, wallpaper.resourceId, 500)
            if (bitmap != null) {
                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 112, 112, true)
                val pixels = IntArray(scaledBitmap.width * scaledBitmap.height)
                scaledBitmap.getPixels(pixels, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
                val quantizerResult = QuantizerCelebi.quantize(pixels, 128)
                val rankedColors = Score.score(quantizerResult)
                var finalColors = rankedColors.distinct().take(5)
                if (finalColors.size < 5 && finalColors.isNotEmpty()) {
                    val hct = Hct.fromInt(finalColors.first())
                    val generated = listOf(Hct.from(hct.hue + 60.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue - 60.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue + 180.0, hct.chroma, hct.tone).toInt(), Hct.from(hct.hue, hct.chroma, 30.0).toInt(), Hct.from(hct.hue, hct.chroma, 80.0).toInt())
                    finalColors = (finalColors + generated).distinct().take(5)
                } else if (finalColors.isEmpty()) finalColors = listOf(android.graphics.Color.parseColor("#4CAF50"), android.graphics.Color.parseColor("#2196F3"), android.graphics.Color.parseColor("#FF9800"), android.graphics.Color.parseColor("#E91E63"), android.graphics.Color.parseColor("#9C27B0"))
                withContext(Dispatchers.Main) { extractedColors = finalColors; if (finalColors.isNotEmpty() && !finalColors.contains(viewModel.currentBackgroundColor)) viewModel.updateMagicConfig(viewModel.currentMagicShape, finalColors.first()) }
                scaledBitmap.recycle(); bitmap.recycle()
            }
        }
    }
    
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (tabs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(48.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background), elevation = CardDefaults.cardElevation(0.dp)) {
                Box(modifier = Modifier.padding(vertical = 28.dp).animateContentSize()) {
                    when (selectedTab) {
                        "Shape" -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                                    MagicShape.values().take(5).forEach { shape ->
                                        val isSelected = viewModel.currentMagicShape == shape
                                        Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant).clickable { viewModel.updateMagicConfig(shape, viewModel.currentBackgroundColor) }, contentAlignment = Alignment.Center) { ShapeIcon(shape = shape, color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxSize(0.45f)) }
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
                            }
                        }
                        "Animation" -> {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Animation Style", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AnimationStyle.values().forEach { style ->
                                        val isSelected = viewModel.currentAnimationStyle == style
                                        FilterChip(selected = isSelected, onClick = { viewModel.updateAnimationStyle(style) }, label = { Text(style.label, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer), border = FilterChipDefaults.filterChipBorder(enabled = true, selected = isSelected, borderColor = Color.Transparent))
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
                        "Style" -> {
                            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                                val showPop = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supports3DPop())
                                val showCenter = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supportsCenter())
                                val showScale = viewModel.isMagicShapeEnabled || (viewModel.isAnimationEnabled && currentAnim.supportsScale())
                                if (showPop || showCenter) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        if (showPop) FilterChip(selected = viewModel.is3DPopEnabled, onClick = { viewModel.toggle3DPop() }, label = { Text("3D Pop", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(16.dp), border = null)
                                        if (showCenter) FilterChip(selected = viewModel.isCentered, onClick = { viewModel.toggleCentered() }, label = { Text("Center", fontWeight = FontWeight.Bold) }, leadingIcon = { Icon(Icons.Default.FilterCenterFocus, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(16.dp), border = null)
                                    }
                                }
                                if (showScale) {
                                    if (showPop || showCenter) Spacer(Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Effect Size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(if (sliderPosition < 1.0f) "Tight" else "Wide", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                                    Slider(value = sliderPosition, onValueChange = { sliderPosition = it }, onValueChangeFinished = { viewModel.updateMagicScale(sliderPosition) }, valueRange = 0.5f..1.5f, steps = 5)
                                }
                                if (viewModel.isAnimationEnabled && currentAnim.hasCustomUI()) {
                                    if (showPop || showCenter || showScale) { Spacer(Modifier.height(16.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); Spacer(Modifier.height(16.dp)) }
                                    currentAnim.CustomUI(params = viewModel.currentAnimParams, onUpdateParam = { id, value -> viewModel.updateAnimParam(id, value) })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (tabs.isNotEmpty()) {
            Surface(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp).height(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.background, shadowElevation = 0.dp) {
                Row(modifier = Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val tabIcon = when (tab) { "Shape" -> Icons.Default.Category; "Animation" -> Icons.Default.Animation; "3D Engine" -> Icons.Default.ViewInAr; else -> Icons.Default.Palette }
                        Box(modifier = Modifier.height(48.dp).clip(CircleShape).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent).clickable { selectedTab = tab }.padding(horizontal = if (isSelected) 20.dp else 16.dp), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { if (isSelected) Icon(tabIcon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer); Text(tab, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
