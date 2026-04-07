package com.android.CaveArt.animations

import android.graphics.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry
import kotlin.math.*

class OrganicBlobAnimation : WallpaperAnimation {

    override fun needsSegmentationMask(): Boolean = true
    
    override fun supportsCenter(): Boolean = false
    override fun supportsScale(): Boolean = false
    override fun supports3DPop(): Boolean = true
    
    override fun getCustomSettings(): List<AnimSetting> = listOf(
        AnimSetting.Slider("effect_scale", "Effect Size", 0.5f, 1.5f, 0.76f),
        AnimSetting.Slider("blob_wobble_size", "Blob Wobble Size", 0.01f, 0.15f, 0.05f)
    )
    
    @Composable
    override fun CustomUI(
        params: Map<String, Float>,
        onUpdateParam: (String, Float) -> Unit
    ) {
        val settings = getCustomSettings().filterIsInstance<AnimSetting.Slider>()
        if (settings.isEmpty()) return

        var selectedIndex by remember { mutableIntStateOf(0) }
        var expanded by remember { mutableStateOf(false) }

        val activeSetting = settings[selectedIndex]
        val currentValue = params[activeSetting.id] ?: activeSetting.defaultValue

        Text("Customize Effect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { expanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeSetting.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Parameter",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            settings.forEachIndexed { index, setting ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = setting.title,
                                            color = if (index == selectedIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedIndex = index
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                settings.forEach { setting ->
                                    onUpdateParam(setting.id, setting.defaultValue)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset All",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = String.format("%.2f", currentValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(8.dp))

            Slider(
                value = currentValue,
                onValueChange = { onUpdateParam(activeSetting.id, it) },
                valueRange = activeSetting.minValue..activeSetting.maxValue
            )
        }
    }

    override fun getPreviewScaleTarget(): Float = 1.06f
    override fun getPreviewDuration(): Int = 4000

    private var timeSeconds = 0f
    private var currentProgress = 0f
    private var targetProgress = 0f
    private var velocity = 0f

    private val SPRING_TENSION = 220f
    private val SPRING_FRICTION = 18f

    private val _bodyMatrix = Matrix()
    private val _blobPath = Path()
    private val _shapeRect = RectF()
    
    private val _fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
    }
    private var _lastFadeStart = -1f
    private var _lastFadeEnd = -1f

    override fun update(deltaTime: Float) {
        timeSeconds += deltaTime
        val steps = 4
        val dt = deltaTime / steps
        for (i in 0 until steps) {
            val displacement = currentProgress - targetProgress
            val force = -SPRING_TENSION * displacement - SPRING_FRICTION * velocity
            velocity += force * dt
            currentProgress += velocity * dt
        }
        if (abs(currentProgress - targetProgress) < 0.001f && abs(velocity) < 0.001f) {
            currentProgress = targetProgress
            velocity = 0f
        }
    }

    override fun onUnlock() { targetProgress = 1f }
    override fun onLock() { targetProgress = 0f }

    override fun draw(
        canvas: Canvas,
        originalBitmap: Bitmap,
        maskBitmap: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        paint: Paint,
        maskXferPaint: Paint,
        clipPath: Path,
        screenShapeRect: RectF
    ) {
        val screenW = canvas.width.toFloat()
        val screenH = canvas.height.toFloat()

        val safeProgress = currentProgress.coerceIn(0f, 1f)
        val unlockScale = lerp(0.7f, 1.0f, safeProgress) 
        
        val maxWobbleSize = config.animParams["blob_wobble_size"] ?: 0.05f
        val customScale = config.animParams["effect_scale"] ?: 1.0f
        
        val activeWobbleSize = lerp(maxWobbleSize * 0.6f, maxWobbleSize, safeProgress)
        val currentImgScale = geo.baseScale * customScale * unlockScale

        val anchorX = geo.subjectCenterX
        val anchorY = geo.subjectCenterY

        _bodyMatrix.reset()
        _bodyMatrix.postTranslate(-anchorX, -anchorY)
        _bodyMatrix.postScale(currentImgScale, currentImgScale)
        _bodyMatrix.postTranslate(screenW / 2f, screenH / 2f)

        _shapeRect.set(geo.shapeBoundsRel)
        _bodyMatrix.mapRect(_shapeRect)
        
        val vShift = if (config.is3DPopEnabled) _shapeRect.height() * (0.12f + activeWobbleSize * 1.5f) else 0f
        
        val centerX = _shapeRect.centerX()
        val centerY = _shapeRect.centerY() + vShift
        val baseRadius = min(_shapeRect.width(), _shapeRect.height()) / 2f

        _blobPath.rewind()
        val numPoints = 120 
        for (i in 0..numPoints) {
            val angle = (i.toFloat() / numPoints) * (PI.toFloat() * 2f)
            
            val offset1 = sin(angle * 4f + timeSeconds * 2.2f) * (baseRadius * activeWobbleSize)
            val offset2 = cos(angle * 3f - timeSeconds * 1.5f) * (baseRadius * activeWobbleSize * 0.8f)
            val offset3 = sin(angle * 6f + timeSeconds * 1.0f) * (baseRadius * activeWobbleSize * 0.5f)
            
            val r = baseRadius + offset1 + offset2 + offset3
            val x = centerX + r * cos(angle)
            val y = centerY + r * sin(angle)
            
            if (i == 0) _blobPath.moveTo(x, y) else _blobPath.lineTo(x, y)
        }
        _blobPath.close()

        canvas.drawColor(config.backgroundColor)

        canvas.save()
        canvas.clipPath(_blobPath)
        paint.alpha = 255
        canvas.drawBitmap(originalBitmap, _bodyMatrix, paint)
        canvas.restore()

        val popAlpha = (safeProgress * 255).toInt().coerceIn(0, 255)
        
        if (config.is3DPopEnabled && maskBitmap != null && popAlpha > 0) {
            val popMatrix = Matrix(_bodyMatrix)
            val layerId = canvas.saveLayer(0f, 0f, screenW, screenH, null)
            
            paint.alpha = popAlpha
            canvas.drawBitmap(maskBitmap, popMatrix, paint)
            canvas.drawBitmap(originalBitmap, popMatrix, maskXferPaint)
            
            val fadeStart = _shapeRect.bottom - _shapeRect.height() * 0.35f
            val fadeEnd = _shapeRect.bottom - _shapeRect.height() * 0.05f
            
            if (fadeStart != _lastFadeStart || fadeEnd != _lastFadeEnd) {
                _fadePaint.shader = LinearGradient(
                    0f, fadeStart, 0f, fadeEnd, 
                    Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP
                )
                _lastFadeStart = fadeStart
                _lastFadeEnd = fadeEnd
            }
            canvas.drawRect(0f, 0f, screenW, screenH, _fadePaint)
            
            paint.alpha = 255 
            canvas.restoreToCount(layerId)
        }
    }

    private fun lerp(start: Float, end: Float, t: Float): Float = start + (end - start) * t
}
