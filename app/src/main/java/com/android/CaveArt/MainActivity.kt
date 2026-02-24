package com.android.CaveArt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import android.os.Build

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        
        setContent {
            WallpaperAppTheme {
                SwipableWallpaperScreen()
            }
        }
    }
}

@Composable
fun WallpaperAppTheme(content: @Composable () -> Unit) {
    val darkTheme: Boolean = isSystemInDarkTheme()
    val context = LocalContext.current
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        dynamicColorSupported && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColorSupported && !darkTheme -> dynamicLightColorScheme(context)
        else -> MaterialTheme.colorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    WallpaperAppTheme {
        SwipableWallpaperScreen()
    }
}