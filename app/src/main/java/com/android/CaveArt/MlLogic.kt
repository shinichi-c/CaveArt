package com.android.CaveArt

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter

@Composable
fun DebugMaskImage(
    wallpaper: Wallpaper,
    contentDescription: String,
    viewModel: WallpaperViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    ProcessedImageComposable(
        wallpaper = wallpaper,
        contentDescription = contentDescription,
        viewModel = viewModel,
        modifier = modifier
    )
}

@Composable
private fun ProcessedImageComposable(
    wallpaper: Wallpaper,
    contentDescription: String,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var processedBitmap by remember(wallpaper) {
        mutableStateOf<Bitmap?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }
    
    LaunchedEffect(wallpaper) {
        isLoading = true
        processedBitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper)
        isLoading = false
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (processedBitmap != null) {
            Image(
                bitmap = processedBitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!isLoading) {
            if (wallpaper.uri != null) {
                 Image(
                    painter = rememberAsyncImagePainter(wallpaper.uri),
                    contentDescription = "Original: $contentDescription",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val originalBitmap = remember(wallpaper.resourceId) {
                    BitmapFactory.decodeResource(context.resources, wallpaper.resourceId)
                }
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Original: $contentDescription",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}