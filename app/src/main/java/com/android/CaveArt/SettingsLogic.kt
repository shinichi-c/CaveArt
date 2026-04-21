package com.android.CaveArt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.foundation.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    viewModel: WallpaperViewModel,
    currentWallpaper: Wallpaper?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    AmbientBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        viewModel = viewModel,
        currentWallpaper = currentWallpaper
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Fixed Wallpaper Alignment",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (viewModel.isFixedAlignmentEnabled) "Image remains static (No Parallax)" else "Image scrolls with screen (Parallax)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = viewModel.isFixedAlignmentEnabled,
                    onCheckedChange = { viewModel.setFixedAlignmentEnabled(it) }
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Fast Scroll Haptics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (viewModel.isHapticsEnabled) "Tap/vibration feedback is ON" else "Tap/vibration feedback is OFF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = viewModel.isHapticsEnabled,
                    onCheckedChange = { viewModel.setHapticsEnabled(it) }
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "Ambient Background",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (viewModel.isAmbientBlurEnabled) "Dynamic blurred wallpaper behind UI" else "Solid background color",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = viewModel.isAmbientBlurEnabled,
                    onCheckedChange = { viewModel.setAmbientBlurEnabled(it) }
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "Lockscreen Clock Size",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${viewModel.lockscreenClockSize.toInt()} dp",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                androidx.compose.material3.Slider(
                    value = viewModel.lockscreenClockSize,
                    onValueChange = { viewModel.updateLockscreenClockSize(context, it) },
                    valueRange = 50f..150f 
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { viewModel.runModelDiagnostics(context, currentWallpaper) }, 
                enabled = !viewModel.isRunningDebug && currentWallpaper != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (viewModel.isRunningDebug) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White)
                } else {
                    Text("RUN MODEL DIAGNOSTIC")
                }
            }
            
            if (viewModel.debugResults.isNotEmpty()) {
                viewModel.debugResults.forEach { res ->
                    Card(
                        modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = res.testName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Input: ${res.inputType}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            if (res.error != null) {
                                Text("Error: ${res.error}", color = Color.Red)
                            } else {
                                Text("Min: ${"%.2f".format(res.minOutput)} Max: ${"%.2f".format(res.maxOutput)}")
                                Spacer(Modifier.height(8.dp))
                                
                                if (res.previewBitmap != null) {
                                    Image(
                                        bitmap = res.previewBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .background(Color.Black)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
