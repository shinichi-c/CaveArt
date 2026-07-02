package com.android.CaveArt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
