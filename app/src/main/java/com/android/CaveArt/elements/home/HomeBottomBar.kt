package com.android.CaveArt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?,
    viewModel: WallpaperViewModel,
    isDockExpanded: Boolean,
    isFloating: Boolean,
    onDockExpandedChange: (Boolean) -> Unit,
    onSetWallpaperClick: () -> Unit,
    onMagicClick: () -> Unit,
    onAnimationClick: () -> Unit,
    onFilamentClick: () -> Unit,
    onLockscreenClick: () -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    carouselContent: @Composable () -> Unit,
    linearBarContent: @Composable () -> Unit 
) {
    val enabled = currentWallpaper != null
    
    val dockShape = if (isFloating) MaterialTheme.shapes.extraLarge else RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val outerPadding = if (isFloating) PaddingValues(horizontal = 16.dp, vertical = 16.dp) else PaddingValues(0.dp)
    val innerPadding = if (isFloating) PaddingValues(top = 28.dp, bottom = 24.dp) else PaddingValues(top = 28.dp, bottom = 12.dp)
    
    val shadowMod = if (isFloating) {
        Modifier.shadow(12.dp, dockShape, ambientColor = Color.Black.copy(alpha=0.03f), spotColor = Color.Black.copy(alpha=0.08f))
    } else {
        Modifier.shadow(24.dp, dockShape, ambientColor = Color.Black.copy(alpha=0.08f), spotColor = Color.Black.copy(alpha=0.15f))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isFloating) it.windowInsetsPadding(WindowInsets.navigationBars) else it }
            .padding(outerPadding)
            .then(shadowMod)
            .clip(dockShape)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> change.consume() }
            }
            
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15f) onDockExpandedChange(true)
                    if (dragAmount > 15f) onDockExpandedChange(false)
                }
            }
            
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
    	
        if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
            AsyncWallpaperImage(
                wallpaper = currentWallpaper,
                contentDescription = null,
                viewModel = viewModel,
                modifier = Modifier.matchParentSize().blur(80.dp),
                allowMagic = false
            )
            
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)))
        } else {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (!isFloating) it.windowInsetsPadding(WindowInsets.navigationBars) else it }
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
                .padding(innerPadding), 
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            AnimatedVisibility(
                visible = isDockExpanded,
                enter = expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top) + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 12.dp)) {
                    carouselContent()
                    Spacer(Modifier.height(12.dp))
                    linearBarContent() 
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarToolItem(Icons.Default.AccessTime, "Clock", enabled, onLockscreenClick)
                BottomBarToolItem(Icons.Default.ViewInAr, "3D", enabled, onFilamentClick)
                BottomBarToolItem(Icons.Default.Animation, "Animate", enabled, onAnimationClick)
                BottomBarToolItem(Icons.Default.AutoAwesome, "Magic", enabled, onMagicClick)
                BottomBarToolItem(Icons.Default.Settings, "Settings", true, onSettingsClick)
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarActionButton("Add Photo", true, onAddClick, true)
                BottomBarActionButton("Apply", enabled, onSetWallpaperClick, true)
            }
        }
    }
}

@Composable
fun RowScope.BottomBarToolItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.large)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RowScope.BottomBarActionButton(
    text: String, 
    enabled: Boolean, 
    onClick: () -> Unit, 
    isPrimary: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f, 
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bounce"
    )

    val bgColor = if (isPrimary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (isPrimary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Box(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (enabled) bgColor else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled) { onClick() }
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.5.sp),
            color = textColor
        )
    }
}
