package com.android.CaveArt

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    val dockShape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp, bottomStart = 0.dp, bottomEnd = 0.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, dockShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(dockShape)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15f) onDockExpandedChange(true)
                    if (dragAmount > 15f) onDockExpandedChange(false)
                }
            }
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))
                .padding(top = 16.dp, bottom = 8.dp), 
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

            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp), 
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarActionButton(
                    text = "Add Photo", 
                    enabled = true, 
                    onClick = onAddClick,
                    isPrimary = true 
                )
                
                BottomBarActionButton(
                    text = "Apply", 
                    enabled = enabled, 
                    onClick = onSetWallpaperClick, 
                    isPrimary = true
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
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
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
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
    val bgColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
    }
    
    val textColor = if (!enabled) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) 
    } else if (isPrimary) {
        MaterialTheme.colorScheme.onPrimaryContainer 
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.5.sp),
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
