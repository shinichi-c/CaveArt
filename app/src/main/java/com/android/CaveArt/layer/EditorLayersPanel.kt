package com.android.CaveArt

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.CaveArt.layer.LayerType
import com.android.CaveArt.layer.LockscreenLayerStack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorLayersPanel(
    layerStack: LockscreenLayerStack,
    previewOriginal: Bitmap?,
    previewMask: Bitmap?,
    activeLayerTab: ActiveLayerTab,
    onSelectLayerTab: (ActiveLayerTab) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spatial Layers",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            val layersReversed = remember(layerStack, layerStack.clockLayer.zIndex, layerStack.subjectLayer.zIndex, layerStack.depthVisionLayer.isVisible) {
                layerStack.getOrderedLayers()
                    .filter { it.type != LayerType.BASE_PHOTO }
                    .reversed()
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                layersReversed.forEach { layer ->
                    val isSelected = (layer.type == LayerType.CLOCK && activeLayerTab == ActiveLayerTab.CLOCK) ||
                            (layer.type == LayerType.BACKGROUND && activeLayerTab == ActiveLayerTab.BACKGROUND) ||
                            (layer.type == LayerType.SUBJECT && activeLayerTab == ActiveLayerTab.SUBJECT) ||
                            (layer.id == layerStack.depthVisionLayer.id && activeLayerTab == ActiveLayerTab.DEPTH)

                    val badgeText = when (layer.id) {
                        layerStack.subjectLayer.id -> if (layerStack.isDepthEnabled) "Foreground" else null
                        layerStack.clockLayer.id -> if (layerStack.isDepthEnabled) "Behind Subject" else "In Front"
                        layerStack.depthVisionLayer.id -> "518x518 DA3 Geometry"
                        else -> null
                    }

                    val thumbnail = when (layer.id) {
                        layerStack.subjectLayer.id -> previewMask ?: previewOriginal
                        layerStack.backgroundLayer.id -> previewOriginal
                        layerStack.depthVisionLayer.id -> layerStack.depthVisionLayer.depthHeatMap
                        else -> null
                    }

                    LayerCard(
                        title = layer.name,
                        isSelected = isSelected,
                        badgeText = badgeText,
                        thumbnail = thumbnail,
                        isMaskLayer = layer.id == layerStack.subjectLayer.id,
                        isClockLayer = layer.id == layerStack.clockLayer.id,
                        isDepthLayer = layer.id == layerStack.depthVisionLayer.id,
                        isVisible = layer.isVisible,
                        onVisibilityToggle = { layer.isVisible = !layer.isVisible },
                        onSelect = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            layerStack.selectLayer(layer.id)
                            when (layer.id) {
                                layerStack.subjectLayer.id -> onSelectLayerTab(ActiveLayerTab.SUBJECT)
                                layerStack.clockLayer.id -> onSelectLayerTab(ActiveLayerTab.CLOCK)
                                layerStack.backgroundLayer.id -> onSelectLayerTab(ActiveLayerTab.BACKGROUND)
                                layerStack.depthVisionLayer.id -> onSelectLayerTab(ActiveLayerTab.DEPTH)
                                else -> {}
                            }
                            onDismiss()
                        }
                    )
                }
            }
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
    isDepthLayer: Boolean = false,
    isVisible: Boolean,
    onVisibilityToggle: () -> Unit,
    onSelect: () -> Unit
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
                        .background(
                            when {
                                isDepthLayer -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                                isMaskLayer -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        ),
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
                    } else if (isDepthLayer) {
                        Icon(
                            imageVector = Icons.Rounded.ViewInAr,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
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
                            color = if (isDepthLayer) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

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
