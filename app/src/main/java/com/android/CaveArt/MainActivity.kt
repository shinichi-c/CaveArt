package com.android.CaveArt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    
    val expressiveTypography = Typography(
        displayLarge = Typography().displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-2).sp, fontSize = 64.sp, lineHeight = 72.sp),
        displayMedium = Typography().displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp, fontSize = 52.sp, lineHeight = 60.sp),
        displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp, fontSize = 44.sp, lineHeight = 52.sp),
        headlineLarge = Typography().headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
        headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = Typography().headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Bold),
        bodyLarge = Typography().bodyLarge.copy(fontWeight = FontWeight.Medium),
        labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        labelMedium = Typography().labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
        labelSmall = Typography().labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
    )
    
    val expressiveShapes = Shapes(
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(48.dp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = expressiveTypography,
        shapes = expressiveShapes,
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
