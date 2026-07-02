package com.android.CaveArt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorClockPanel(viewModel: WallpaperViewModel, wallpaper: Wallpaper, state: LockscreenEditorState, extractedColors: List<Int>, availableFonts: List<String>, previewMask: android.graphics.Bitmap?, realScreenW: Float, realScreenH: Float, densityVal: Float, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    AmbientBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Clock Theme", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Dual Tone", style = MaterialTheme.typography.labelSmall); Spacer(Modifier.width(8.dp)); Switch(checked = state.dualTone.value, onCheckedChange = { state.dualTone.value = it }) }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                extractedColors.forEach { colorInt ->
                    val isSelected = state.clockColor.intValue == colorInt
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        if (isSelected) Box(modifier = Modifier.fillMaxSize().border(3.dp, Color(colorInt), CircleShape))
                        Box(modifier = Modifier.fillMaxSize(if (isSelected) 0.65f else 1f).clip(CircleShape).background(Color(colorInt)).clickable { state.clockColor.intValue = colorInt })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Layout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = state.clockLayout.intValue == 0, onClick = { state.clockLayout.intValue = 0 }, label = { Text("Horizontal") }, shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                FilterChip(selected = state.clockLayout.intValue == 1, onClick = { state.clockLayout.intValue = 1 }, label = { Text("Vertical Magazine") }, shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
            }
            Spacer(Modifier.height(24.dp))
            Text("Typography", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(availableFonts) { fontFile ->
                    val displayName = if (fontFile == "default") "System Default" else fontFile.substringBeforeLast(".").split("_", "-").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() } }
                    FilterChip(selected = state.clockFont.value == fontFile, onClick = { state.clockFont.value = fontFile }, label = { Text(displayName, fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(16.dp), colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Clock Style", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(24.dp))
            Text("Thickness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.LineWeight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)); Slider(value = state.strokeWidth.floatValue, onValueChange = { state.strokeWidth.floatValue = it }, valueRange = 0f..25f, modifier = Modifier.weight(1f)) }
            Spacer(Modifier.height(16.dp))
            Text("Corner Roundness", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.RoundedCorner, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(16.dp)); Slider(value = state.roundness.floatValue, onValueChange = { state.roundness.floatValue = it }, valueRange = 0f..80f, modifier = Modifier.weight(1f)) }
            Spacer(Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = {
                        if (previewMask != null) scope.launch(Dispatchers.Default) {
                            val result = AdaptiveClockHelper.calculateAutoFit(previewMask, realScreenW, realScreenH, state.hourSize.floatValue, state.minSize.floatValue, densityVal, viewModel.isMagicShapeEnabled, viewModel.magicScale, viewModel.isCentered)
                            if (result != null) withContext(Dispatchers.Main) { state.hourSize.floatValue = result.hourSize; state.minSize.floatValue = result.minSize; state.clockX.floatValue = 0f; state.clockY.floatValue = result.yDp }
                        }
                    }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), enabled = previewMask != null
                ) { Icon(Icons.Default.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text("Smart Fit", fontWeight = FontWeight.Bold) }
                Button(onClick = { state.stretchEnabled.value = !state.stretchEnabled.value }, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), colors = if (state.stretchEnabled.value) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()) { Icon(Icons.Default.Transform, null); Spacer(Modifier.width(8.dp)); Text("Adaptive", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorApplyPanel(viewModel: WallpaperViewModel, wallpaper: Wallpaper, state: LockscreenEditorState, previewMask: android.graphics.Bitmap?, realScreenW: Float, realScreenH: Float, context: android.content.Context, onApplyClockAndWallpaperClick: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AmbientBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding()) {
            Text("Apply Lockscreen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 24.dp))
            DestinationButton(Icons.Default.ScreenLockPortrait, "Clock Layout Only", "Update clock position and style", false) {
                viewModel.updateClockLayout(context, state.clockLayout.intValue); viewModel.updateDualTone(context, state.dualTone.value); viewModel.updateClockFont(context, state.clockFont.value); viewModel.updateClockColor(context, state.clockColor.intValue); viewModel.updateDateLayout(context, state.dateFormat.intValue); viewModel.updateDateAttached(context, state.dateAttached.value); viewModel.updateClockStyle(context, state.hourSize.floatValue, state.minSize.floatValue, state.strokeWidth.floatValue, state.roundness.floatValue); viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue); viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue); viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
                viewModel.isLockscreenClockPreviewVisible = false
                android.widget.Toast.makeText(context, "Clock Layout Applied", android.widget.Toast.LENGTH_SHORT).show(); onDismiss()
            }
            Spacer(Modifier.height(12.dp))
            DestinationButton(Icons.Default.Wallpaper, "Clock + Wallpaper", "Update layout and set static wallpaper", false) {
                viewModel.updateClockLayout(context, state.clockLayout.intValue); viewModel.updateDualTone(context, state.dualTone.value); viewModel.updateClockFont(context, state.clockFont.value); viewModel.updateClockColor(context, state.clockColor.intValue); viewModel.updateDateLayout(context, state.dateFormat.intValue); viewModel.updateDateAttached(context, state.dateAttached.value); viewModel.updateClockStyle(context, state.hourSize.floatValue, state.minSize.floatValue, state.strokeWidth.floatValue, state.roundness.floatValue); viewModel.updateLockscreenClockPosition(context, state.clockX.floatValue, state.clockY.floatValue); viewModel.updateLockscreenDatePosition(context, state.dateX.floatValue, state.dateY.floatValue); viewModel.toggleClockStretch(context, state.stretchEnabled.value, previewMask, realScreenW, realScreenH)
                onApplyClockAndWallpaperClick(); onDismiss()
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
