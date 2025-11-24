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


@Composable
fun DebugMaskImage(
    resourceId: Int,
    contentDescription: String,
    viewModel: WallpaperViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    ProcessedImageComposable(
        resourceId = resourceId,
        contentDescription = contentDescription,
        viewModel = viewModel,
        modifier = modifier
    )
}

@Composable
private fun ProcessedImageComposable(
    resourceId: Int,
    contentDescription: String,
    viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var processedBitmap by remember(resourceId, viewModel.isDebugMaskEnabled) {
        mutableStateOf<Bitmap?>(null)
    }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(resourceId, viewModel.isDebugMaskEnabled) {
        isLoading = true
        processedBitmap = viewModel.getOrCreateProcessedBitmap(context, resourceId)
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
            val originalBitmap = remember(resourceId) {
                BitmapFactory.decodeResource(context.resources, resourceId)
            }
            Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = "Original: $contentDescription",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
