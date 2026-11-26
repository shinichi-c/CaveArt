package com.android.CaveArt

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.lockscreenkit.NotificationStyle
import com.android.CaveArt.lockscreenkit.NotificationSurfaceMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorNotificationsPanel(
    viewModel: WallpaperViewModel,
    wallpaper: Wallpaper,
    state: ClockStudioState,
    isPreviewingNotifications: Boolean,
    onTogglePreview: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentStyle = NotificationStyle.fromId(state.lockscreenConfig.notificationStyle)

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
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            "Lockscreen Notifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            when (currentStyle) {
                                NotificationStyle.STOCK -> "Use native Android notification stack"
                                NotificationStyle.ICONS_ONLY -> "Compact app icons — keeps full-size depth clock"
                                NotificationStyle.FROSTED_PILL -> "Single frosted summary pill — keeps full-size clock"
                                NotificationStyle.GROUPED_CARDS -> "Per-app frosted cards — collapses clock when active"
                                NotificationStyle.HIDDEN -> "Hide notifications on lockscreen (shade still works)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isPreviewingNotifications,
                        onCheckedChange = { enabled ->
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onTogglePreview(enabled)
                        }
                    )
                }
            }

            StaggeredRow(1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Notification Style",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NotificationStyle.values()) { style ->
                            FilterChip(
                                selected = state.lockscreenConfig.notificationStyle == style.id,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    state.updateConfig { it.copy(notificationStyle = style.id) }
                                },
                                label = {
                                    Text(style.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = currentStyle == NotificationStyle.GROUPED_CARDS ||
                    currentStyle == NotificationStyle.FROSTED_PILL ||
                    (currentStyle == NotificationStyle.ICONS_ONLY && state.lockscreenConfig.notificationIconsBackdrop)
            ) {
                StaggeredRow(2) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Card Surface",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(NotificationSurfaceMode.values()) { mode ->
                                FilterChip(
                                    selected = state.lockscreenConfig.notificationSurfaceMode == mode.id,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        state.updateConfig { it.copy(notificationSurfaceMode = mode.id) }
                                    },
                                    label = {
                                        Text(mode.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    },
                                    shape = CircleShape
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = currentStyle == NotificationStyle.ICONS_ONLY) {
                StaggeredRow(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Capsule Backdrop", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Wrap icons in a subtle frosted pill", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.lockscreenConfig.notificationIconsBackdrop,
                            onCheckedChange = { enabled ->
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                state.updateConfig { it.copy(notificationIconsBackdrop = enabled) }
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(visible = currentStyle == NotificationStyle.GROUPED_CARDS) {
                StaggeredRow(3) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Max App Cards",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items((1..5).toList()) { count ->
                                    FilterChip(
                                        selected = state.lockscreenConfig.notificationMaxCards == count,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            state.updateConfig { it.copy(notificationMaxCards = count) }
                                        },
                                        label = {
                                            Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Black)
                                        },
                                        shape = CircleShape
                                    )
                                }
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = state.lockscreenConfig.notificationShowAvatars,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    state.updateConfig { it.copy(notificationShowAvatars = !it.notificationShowAvatars) }
                                },
                                label = { Text("Contact Avatars", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                shape = CircleShape
                            )
                            FilterChip(
                                selected = state.lockscreenConfig.notificationShowActions,
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    state.updateConfig { it.copy(notificationShowActions = !it.notificationShowActions) }
                                },
                                label = { Text("Action Pills", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                shape = CircleShape
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = currentStyle != NotificationStyle.STOCK && currentStyle != NotificationStyle.HIDDEN
            ) {
                StaggeredRow(4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Silent & Downloads", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Show Chrome/Play Store progress & silent alerts on lockscreen", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = state.lockscreenConfig.notificationIncludeSilent,
                            onCheckedChange = { enabled ->
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                state.updateConfig { it.copy(notificationIncludeSilent = enabled) }
                            }
                        )
                    }
                }
            }
        }
    }
}
