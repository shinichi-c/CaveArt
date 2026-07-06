package com.android.CaveArt

import androidx.compose.animation.core.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: WallpaperViewModel, currentWallpaper: Wallpaper?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AmbientBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, viewModel = viewModel, currentWallpaper = currentWallpaper) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("App Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 16.dp), color = MaterialTheme.colorScheme.onSurface)
            
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text("Fast Scroll Style", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val styles = listOf("Original", "Vision Pill", "Elastic", "Hover Tooltip", "Magnetic Wave")
                    items(styles.size) { index ->
                        val isSelected = viewModel.scrollStyle == index
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(
                                modifier = Modifier
                                    .size(100.dp, 60.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.updateScrollStyle(index) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    LiveScrollTile(index)
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(styles[index], style = MaterialTheme.typography.labelSmall, color = if(isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fixed Wallpaper Alignment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (viewModel.isFixedAlignmentEnabled) "Image remains static (No Parallax)" else "Image scrolls with screen (Parallax)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = viewModel.isFixedAlignmentEnabled, onCheckedChange = { viewModel.setFixedAlignmentEnabled(it) })
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fast Scroll Haptics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (viewModel.isHapticsEnabled) "Tap/vibration feedback is ON" else "Tap/vibration feedback is OFF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = viewModel.isHapticsEnabled, onCheckedChange = { viewModel.setHapticsEnabled(it) })
            }

            HorizontalDivider(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            
            Row(modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ambient Background", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (viewModel.isAmbientBlurEnabled) "Dynamic blurred wallpaper behind UI" else "Solid background color", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Switch(checked = viewModel.isAmbientBlurEnabled, onCheckedChange = { viewModel.setAmbientBlurEnabled(it) })
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { viewModel.runModelDiagnostics(context, currentWallpaper) }, enabled = !viewModel.isRunningDebug && currentWallpaper != null, modifier = Modifier.fillMaxWidth()) {
                if (viewModel.isRunningDebug) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White) else Text("RUN MODEL DIAGNOSTIC")
            }
            
            if (viewModel.debugResults.isNotEmpty()) {
                viewModel.debugResults.forEach { res ->
                    Card(modifier = Modifier.padding(top = 16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))) {
                        Column(Modifier.padding(16.dp)) {
                            Text(res.testName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Input: ${res.inputType}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            if (res.error != null) Text("Error: ${res.error}", color = Color.Red)
                            else {
                                Text("Min: ${"%.2f".format(res.minOutput)} Max: ${"%.2f".format(res.maxOutput)}")
                                Spacer(Modifier.height(8.dp))
                                if (res.previewBitmap != null) androidx.compose.foundation.Image(bitmap = res.previewBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.Black))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
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
            0 -> {
                drawRoundRect(color = color, topLeft = Offset(thumbX - 10.dp.toPx(), cy - 3.dp.toPx()), size = Size(20.dp.toPx(), 6.dp.toPx()), cornerRadius = CornerRadius(3.dp.toPx()))
            }
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
