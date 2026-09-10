package com.android.CaveArt

import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?,
    viewModel: WallpaperViewModel,
    isDockExpanded: Boolean,
    isFloating: Boolean,
    isPinned: Boolean = false,
    onDockExpandedChange: (Boolean) -> Unit,
    onSetWallpaperClick: () -> Unit,
    onMagicClick: () -> Unit,
    onAnimationClick: () -> Unit,
    onFilamentClick: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    carouselContent: @Composable () -> Unit,
    linearBarContent: @Composable () -> Unit
) {
    val enabled = currentWallpaper != null
    val view = LocalView.current
    
    val swipeModifier = if (!isPinned) {
        Modifier.pointerInput(isPinned) {
            detectVerticalDragGestures { _, dragAmount ->
                if (dragAmount < -15f) onDockExpandedChange(true)
                if (dragAmount > 15f) onDockExpandedChange(false)
            }
        }
    } else {
        Modifier
    }

    if (isFloating) {
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .then(swipeModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            
            AnimatedVisibility(
                visible = isDockExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(tween(250)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(tween(200))
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        carouselContent()
                        Spacer(Modifier.height(6.dp))
                        linearBarContent()
                    }
                }
            }
            
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .height(64.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    ),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Magic",
                        enabled = enabled,
                        isActive = viewModel.isMagicShapeEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMagicClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.Animation,
                        label = "Motion",
                        enabled = enabled,
                        isActive = viewModel.isAnimationEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onAnimationClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.ViewInAr,
                        label = "3D",
                        enabled = enabled,
                        isActive = viewModel.isFilamentEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onFilamentClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        enabled = true,
                        isActive = false,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSettingsClick()
                        }
                    )

                    Spacer(Modifier.width(2.dp))

                    ExpressiveHighlightedApplyButton(
                        enabled = enabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onSetWallpaperClick()
                        }
                    )
                }
            }
        }
    } else {
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(swipeModifier)
        ) {
            
            AnimatedVisibility(
                visible = isDockExpanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(tween(250)),
                exit = shrinkVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(tween(200))
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    shadowElevation = 0.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        carouselContent()
                        Spacer(Modifier.height(6.dp))
                        linearBarContent()
                    }
                }
            }
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "Magic",
                        enabled = enabled,
                        isActive = viewModel.isMagicShapeEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onMagicClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.Animation,
                        label = "Motion",
                        enabled = enabled,
                        isActive = viewModel.isAnimationEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onAnimationClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.ViewInAr,
                        label = "3D",
                        enabled = enabled,
                        isActive = viewModel.isFilamentEnabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onFilamentClick()
                        }
                    )
                    ExpressiveMonetToolbarIcon(
                        icon = Icons.Rounded.Settings,
                        label = "Settings",
                        enabled = true,
                        isActive = false,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onSettingsClick()
                        }
                    )
                    ExpressiveHighlightedApplyButton(
                        enabled = enabled,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            onSetWallpaperClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveMonetToolbarIcon(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "monetIconSquish"
    )

    Box(
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(
                if (isActive) MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                else Color.Transparent
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.38f)
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ExpressiveHighlightedApplyButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "applyBtnSquish"
    )

    Surface(
        modifier = Modifier
            .size(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = CircleShape,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            ),
        shape = CircleShape,
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Set Wallpaper",
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
