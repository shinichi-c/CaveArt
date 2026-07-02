package com.android.CaveArt

import android.graphics.Bitmap
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import kotlin.random.Random
import android.graphics.RectF

data class Particle(
    val initialX: Float, val initialY: Float, val radius: Float,
    val speed: Float, val swaySpeed: Float, val initialAlpha: Float
)

@Composable
fun AsyncWallpaperImage(
    wallpaper: Wallpaper, contentDescription: String?, viewModel: WallpaperViewModel,
    modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, allowMagic: Boolean = true
) {
    if (!allowMagic) {
        val model = wallpaper.uri ?: wallpaper.resourceId
        androidx.compose.foundation.Image(
            painter = rememberAsyncImagePainter(model),
            contentDescription = contentDescription, contentScale = contentScale, modifier = modifier
        )
    } else {
        val context = LocalContext.current
        var bitmap by remember(wallpaper) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(wallpaper) { bitmap = viewModel.getOrCreateProcessedBitmap(context, wallpaper, true) }

        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap!!.asImageBitmap(), contentDescription = contentDescription,
                contentScale = contentScale, modifier = modifier
            )
        } else {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmbientBottomSheet(
    onDismissRequest: () -> Unit, sheetState: SheetState,
    viewModel: WallpaperViewModel, currentWallpaper: Wallpaper?,
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest, sheetState = sheetState,
        containerColor = Color.Transparent, dragHandle = null
    ) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))) {
            if (viewModel.isAmbientBlurEnabled && currentWallpaper != null) {
                AsyncWallpaperImage(wallpaper = currentWallpaper, contentDescription = null, viewModel = viewModel, modifier = Modifier.matchParentSize().blur(80.dp), allowMagic = false)
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)))
            } else {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainerHigh))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                content()
            }
        }
    }
}

@Composable
fun LoadingOverlay(title: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.6f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(32.dp), elevation = CardDefaults.cardElevation(0.dp)) {
            Column(Modifier.padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(strokeWidth = 6.dp, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(24.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
fun ParticleLoadingOverlay(color: Color) {
    val density = LocalDensity.current
    val particles = remember { List(350) { Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 3f + 1f, Random.nextFloat() * 0.05f + 0.01f, Random.nextFloat() * 2f + 1f, Random.nextFloat() * 0.7f + 0.1f) } }
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val startTime = withFrameNanos { it }; while (true) { withFrameNanos { frameTime -> time = (frameTime - startTime) / 1_000_000_000f } } }

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEachIndexed { index, p ->
            var yProgress = (p.initialY - (p.speed * time)) % 1f
            if (yProgress < 0) yProgress += 1f
            val rawSway = kotlin.math.sin((time * p.swaySpeed + index).toDouble()).toFloat()
            val blinkFactor = ((kotlin.math.sin((time * p.swaySpeed * 3f + index).toDouble()).toFloat() + 1) / 2f).let { it * it }
            drawCircle(color = color, radius = p.radius * density.density, center = Offset((p.initialX * size.width) + (rawSway * 15.dp.toPx()), yProgress * size.height), alpha = (p.initialAlpha * blinkFactor).coerceIn(0f, 1f))
        }
    }
}

@Composable
fun ShapeIcon(shape: MagicShape, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val rect = RectF(0f, 0f, size.width, size.height)
        drawPath(path = ShapePathProvider.getPathForShape(shape, rect).asComposePath(), color = color)
    }
}

@Composable
fun DestinationButton(icon: ImageVector, title: String, subtitle: String, isSetting: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = !isSetting, modifier = Modifier.fillMaxWidth().height(104.dp),
        shape = RoundedCornerShape(32.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(24.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(32.dp)); Spacer(Modifier.size(20.dp))
                Column { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }
            }
            if (isSetting) CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun CategoryChip(title: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected, onClick = onClick, label = { Text(title, fontWeight = FontWeight.Bold) },
        shape = RoundedCornerShape(16.dp), modifier = Modifier.padding(4.dp)
    )
}
