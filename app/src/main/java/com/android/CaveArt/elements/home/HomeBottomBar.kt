package com.android.CaveArt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectedWallpaperActions(
    currentWallpaper: Wallpaper?, onSetWallpaperClick: () -> Unit, onMagicClick: () -> Unit,
    onAnimationClick: () -> Unit, onFilamentClick: () -> Unit, onLockscreenClick: () -> Unit, onAddClick: () -> Unit
) {
    val enabled = currentWallpaper != null
    val configuration = LocalConfiguration.current
    val scaleFactor = (configuration.screenWidthDp / 360f).coerceIn(0.82f, 1.05f)
    
    val toolbarHeight = (56.dp * scaleFactor)
    val iconButtonSize = (44.dp * scaleFactor)
    val iconSize = (22.dp * scaleFactor)
    val fabSize = (52.dp * scaleFactor)
    val toolbarSpacing = (4.dp * scaleFactor)

    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { if (enabled) onSetWallpaperClick() },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.size(fabSize), shape = RoundedCornerShape(16.dp * scaleFactor)
            ) { Icon(Icons.Default.Wallpaper, "Apply", modifier = Modifier.size(iconSize)) }
        },
        modifier = Modifier.wrapContentWidth().height(toolbarHeight),
        colors = FloatingToolbarColors(toolbarContainerColor = MaterialTheme.colorScheme.primaryContainer, toolbarContentColor = MaterialTheme.colorScheme.onPrimaryContainer, fabContainerColor = MaterialTheme.colorScheme.primary, fabContentColor = MaterialTheme.colorScheme.onPrimary)
    ) {
        IconButton(onClick = onAddClick, modifier = Modifier.size(iconButtonSize)) { Icon(Icons.Default.AddPhotoAlternate, "Add", modifier = Modifier.size(iconSize)) }
        Spacer(Modifier.width(toolbarSpacing))
        IconButton(onClick = onLockscreenClick, modifier = Modifier.size(iconButtonSize)) { Icon(Icons.Default.AccessTime, "Lockscreen", modifier = Modifier.size(iconSize)) }
        Spacer(Modifier.width(toolbarSpacing))
        IconButton(onClick = onFilamentClick, modifier = Modifier.size(iconButtonSize)) { Icon(Icons.Default.ViewInAr, "3D Filament", modifier = Modifier.size(iconSize)) }
        Spacer(Modifier.width(toolbarSpacing))
        IconButton(onClick = onAnimationClick, modifier = Modifier.size(iconButtonSize)) { Icon(Icons.Default.Animation, "Animation", modifier = Modifier.size(iconSize)) }
        Spacer(Modifier.width(toolbarSpacing))
        IconButton(onClick = onMagicClick, modifier = Modifier.size(iconButtonSize)) { Icon(Icons.Default.AutoAwesome, "Magic Shape", modifier = Modifier.size(iconSize)) }
    }
}
