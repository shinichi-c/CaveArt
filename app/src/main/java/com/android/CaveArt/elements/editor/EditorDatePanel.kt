package com.android.CaveArt

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.lockscreenkit.AtAGlanceDataCollector
import com.android.CaveArt.lockscreenkit.AtAGlanceStyle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorDatePanel(
    viewModel: WallpaperViewModel,
    wallpaper: Wallpaper,
    state: ClockStudioState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val snapshot = remember(state.lockscreenConfig) {
        AtAGlanceDataCollector.collect(context, state.lockscreenConfig)
    }

    AmbientBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        viewModel = viewModel,
        currentWallpaper = wallpaper
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StaggeredRow(0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "At a Glance",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Local device status, system weather & date",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.lockscreenConfig.atGlanceEnabled,
                        onCheckedChange = { enabled ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            state.updateConfig { it.copy(atGlanceEnabled = enabled) }
                        }
                    )
                }
            }

            AnimatedVisibility(visible = state.lockscreenConfig.atGlanceEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    StaggeredRow(1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Attach to Clock",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Anchor widget directly above clock",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.lockscreenConfig.dateAttached,
                                onCheckedChange = { attached ->
                                    state.updateConfig { it.copy(dateAttached = attached) }
                                }
                            )
                        }
                    }

                    StaggeredRow(2) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Widget Style",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(AtAGlanceStyle.values()) { glanceStyle ->
                                    FilterChip(
                                        selected = state.lockscreenConfig.atGlanceStyle == glanceStyle.id,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            state.updateConfig { it.copy(atGlanceStyle = glanceStyle.id) }
                                        },
                                        label = {
                                            Text(glanceStyle.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        },
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    }

                    StaggeredRow(3) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Date Format",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val formatTitles = listOf("Standard", "Short", "CAPS Year", "Full Spell", "Modern Minimal")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(formatTitles.size) { index ->
                                    FilterChip(
                                        selected = state.lockscreenConfig.dateFormat == index,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            state.updateConfig { it.copy(dateFormat = index) }
                                        },
                                        label = { Text(formatTitles[index], fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                                        shape = CircleShape
                                    )
                                }
                            }
                        }
                    }

                    StaggeredRow(4) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "On-Device Smart Signals",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceWeather,
                                    onClick = { state.updateConfig { it.copy(atGlanceWeather = !it.atGlanceWeather) } },
                                    leadingIcon = { Icon(Icons.Rounded.WbSunny, null, Modifier.size(16.dp)) },
                                    label = { Text("System Weather", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceAlarm,
                                    onClick = { state.updateConfig { it.copy(atGlanceAlarm = !it.atGlanceAlarm) } },
                                    leadingIcon = { Icon(Icons.Rounded.Alarm, null, Modifier.size(16.dp)) },
                                    label = { Text("Next Alarm", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceBattery,
                                    onClick = { state.updateConfig { it.copy(atGlanceBattery = !it.atGlanceBattery) } },
                                    leadingIcon = { Icon(Icons.Rounded.BatteryChargingFull, null, Modifier.size(16.dp)) },
                                    label = { Text("Charging", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceMedia,
                                    onClick = { state.updateConfig { it.copy(atGlanceMedia = !it.atGlanceMedia) } },
                                    leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp)) },
                                    label = { Text("Now Playing", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    shape = CircleShape
                                )
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceStatus,
                                    onClick = { state.updateConfig { it.copy(atGlanceStatus = !it.atGlanceStatus) } },
                                    leadingIcon = { Icon(Icons.Rounded.DoNotDisturbOn, null, Modifier.size(16.dp)) },
                                    label = { Text("DND / Torch", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                    shape = CircleShape
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = state.lockscreenConfig.atGlanceWeather) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val w = snapshot.weather
                                val statusText = if (w != null) {
                                    val t = if (state.lockscreenConfig.atGlanceFahrenheit) {
                                        "${((w.tempC * 9f / 5f) + 32f).roundToInt()}°F"
                                    } else {
                                        "${w.tempC.roundToInt()}°C"
                                    }
                                    "${w.source}: $t ${w.summary}"
                                } else {
                                    "Synced automatically from System Smartspace / ROM weather"
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                FilterChip(
                                    selected = state.lockscreenConfig.atGlanceFahrenheit,
                                    onClick = {
                                        state.updateConfig { it.copy(atGlanceFahrenheit = !it.atGlanceFahrenheit) }
                                    },
                                    label = {
                                        Text(
                                            if (state.lockscreenConfig.atGlanceFahrenheit) "°F" else "°C",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp
                                        )
                                    },
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
