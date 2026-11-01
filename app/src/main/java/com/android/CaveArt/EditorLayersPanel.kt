package com.android.CaveArt

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorLayersPanel(
    layerStack: LockscreenLayerStack,
    previewOriginal: Bitmap?,
    previewMask: Bitmap?,
    onReExtractSubject: () -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var activeTuningLayer by remember { mutableStateOf<LayerType?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Layers",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    shape = CircleShape,
                    color = if (layerStack.isDepthEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        layerStack.setDepthClockActive(!layerStack.isDepthEnabled)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            tint = if (layerStack.isDepthEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (layerStack.isDepthEnabled) "Depth: ON" else "Depth: OFF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (layerStack.isDepthEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LayerCard(
                    title = "Subject",
                    isSelected = layerStack.activeLayerId == layerStack.subjectLayer.id,
                    badgeText = if (layerStack.isDepthEnabled) "Foreground" else null,
                    thumbnail = previewMask ?: previewOriginal,
                    isMaskLayer = true,
                    isVisible = layerStack.subjectLayer.isVisible,
                    onVisibilityToggle = {
                        layerStack.subjectLayer.isVisible = !layerStack.subjectLayer.isVisible
                    },
                    onSelect = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.subjectLayer.id)
                    },
                    onTuneClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.subjectLayer.id)
                        activeTuningLayer = if (activeTuningLayer == LayerType.SUBJECT) null else LayerType.SUBJECT
                    }
                )

                LayerCard(
                    title = "Clock Face",
                    isSelected = layerStack.activeLayerId == layerStack.clockLayer.id,
                    badgeText = if (layerStack.isDepthEnabled) "Behind Subject" else "In Front",
                    thumbnail = null,
                    isClockLayer = true,
                    isVisible = layerStack.clockLayer.isVisible,
                    onVisibilityToggle = {
                        layerStack.clockLayer.isVisible = !layerStack.clockLayer.isVisible
                    },
                    onSelect = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.clockLayer.id)
                    },
                    onTuneClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.setDepthClockActive(!layerStack.isDepthEnabled)
                    }
                )

                LayerCard(
                    title = "Background",
                    isSelected = layerStack.activeLayerId == layerStack.backgroundLayer.id,
                    badgeText = null,
                    thumbnail = previewOriginal,
                    isMaskLayer = false,
                    isVisible = layerStack.backgroundLayer.isVisible,
                    onVisibilityToggle = {
                        layerStack.backgroundLayer.isVisible = !layerStack.backgroundLayer.isVisible
                    },
                    onSelect = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.backgroundLayer.id)
                    },
                    onTuneClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.backgroundLayer.id)
                        activeTuningLayer = if (activeTuningLayer == LayerType.BACKGROUND) null else LayerType.BACKGROUND
                    }
                )

                LayerCard(
                    title = "Photo",
                    isSelected = layerStack.activeLayerId == layerStack.basePhotoLayer.id,
                    badgeText = "Original",
                    thumbnail = previewOriginal,
                    isMaskLayer = false,
                    isVisible = true,
                    onVisibilityToggle = null,
                    onSelect = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        layerStack.selectLayer(layerStack.basePhotoLayer.id)
                    },
                    onTuneClick = null
                )
            }

            AnimatedVisibility(visible = activeTuningLayer != null) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeTuningLayer == LayerType.BACKGROUND) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Background Structure / Saturation",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    String.format("%.0f%%", layerStack.backgroundLayer.structure * 100),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = layerStack.backgroundLayer.structure,
                                onValueChange = { layerStack.backgroundLayer.structure = it },
                                valueRange = -1f..1f
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Background Opacity",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    String.format("%.0f%%", layerStack.backgroundLayer.opacity * 100),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = layerStack.backgroundLayer.opacity,
                                onValueChange = { layerStack.backgroundLayer.opacity = it },
                                valueRange = 0.1f..1f
                            )
                        } else if (activeTuningLayer == LayerType.SUBJECT) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Subject Opacity",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    String.format("%.0f%%", layerStack.subjectLayer.opacity * 100),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = layerStack.subjectLayer.opacity,
                                onValueChange = { layerStack.subjectLayer.opacity = it },
                                valueRange = 0.2f..1f
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ACTIONS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onReExtractSubject()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("RE-EXTRACT", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }

                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            layerStack.backgroundLayer.structure = if (layerStack.backgroundLayer.structure == 0f) 0.5f else 0f
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("VIBRANCE", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LayerCard(
    title: String,
    isSelected: Boolean,
    badgeText: String?,
    thumbnail: Bitmap?,
    isMaskLayer: Boolean = false,
    isClockLayer: Boolean = false,
    isVisible: Boolean,
    onVisibilityToggle: (() -> Unit)?,
    onSelect: () -> Unit,
    onTuneClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMaskLayer) Color(0xFFE5533D).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnail != null && !thumbnail.isRecycled) {
                        Image(
                            bitmap = thumbnail.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (isClockLayer) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeText != null) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onTuneClick != null) {
                    IconButton(onClick = onTuneClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Tune",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onVisibilityToggle != null) {
                    IconButton(onClick = onVisibilityToggle, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = if (isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "Toggle Visibility",
                            modifier = Modifier.size(18.dp),
                            tint = if (isVisible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
