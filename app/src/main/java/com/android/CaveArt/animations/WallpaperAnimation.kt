package com.android.CaveArt.animations

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.CaveArt.LiveWallpaperConfig
import com.android.CaveArt.UnifiedGeometry

sealed class AnimSetting {
    abstract val id: String
    abstract val title: String
    
    data class Slider(override val id: String, override val title: String, val minValue: Float, val maxValue: Float, val defaultValue: Float) : AnimSetting()
    data class Toggle(override val id: String, override val title: String, val defaultValue: Boolean) : AnimSetting()
}

interface WallpaperAnimation {
    fun needsSegmentationMask(): Boolean = false
    
    fun supports3DPop(): Boolean = true
    fun supportsCenter(): Boolean = true
    fun supportsScale(): Boolean = true
    
    fun getCustomSettings(): List<AnimSetting> = emptyList()
    
    fun hasCustomUI(): Boolean = getCustomSettings().isNotEmpty()
    
    @Composable
    fun CustomUI(
        params: Map<String, Float>,
        onUpdateParam: (String, Float) -> Unit
    ) {
        val customSettings = getCustomSettings()
        if (customSettings.isNotEmpty()) {
            Text("Customize Effect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(16.dp))
            
            customSettings.forEach { setting ->
                val currentValue = params[setting.id] ?: 
                    if (setting is AnimSetting.Slider) setting.defaultValue 
                    else if ((setting as AnimSetting.Toggle).defaultValue) 1f else 0f
                    
                when (setting) {
                    is AnimSetting.Slider -> {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(setting.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Slider(
                                value = currentValue,
                                onValueChange = { onUpdateParam(setting.id, it) },
                                valueRange = setting.minValue..setting.maxValue
                            )
                        }
                    }
                    is AnimSetting.Toggle -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(setting.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(
                                checked = currentValue > 0.5f,
                                onCheckedChange = { onUpdateParam(setting.id, if (it) 1f else 0f) }
                            )
                        }
                    }
                }
            }
        }
    }
    
    fun getPreviewScaleTarget(): Float = 1.05f
    fun getPreviewRotationTarget(): Float = 0f
    fun getPreviewDuration(): Int = 3000

    fun update(deltaTime: Float)
    fun onUnlock()
    fun onLock()
    
    fun draw(
        canvas: Canvas,
        originalBitmap: Bitmap,
        maskBitmap: Bitmap?,
        geo: UnifiedGeometry,
        config: LiveWallpaperConfig,
        paint: Paint,
        maskXferPaint: Paint,
        clipPath: Path,
        screenShapeRect: RectF
    )
}
