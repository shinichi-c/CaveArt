package com.android.CaveArt

import android.app.Activity
import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.absoluteValue
import androidx.core.view.WindowCompat

private const val FLAG_HOME_SCREEN = 1
private const val FLAG_LOCK_SCREEN = 2
private const val FLAG_BOTH = FLAG_HOME_SCREEN or FLAG_LOCK_SCREEN
private const val WALLPAPER_PREFIX = "wp_"

data class Wallpaper(
    val id: String,
    val resourceId: Int,
    val title: String,
    val tag: String
)

class WallpaperViewModel(application: Application) : AndroidViewModel(application) {

    private fun loadWallpapersDynamically(context: Context): List<Wallpaper> {
    	
        val drawableFields = R.drawable::class.java.fields
        val wallpaperList = mutableListOf<Wallpaper>()

        drawableFields.filter { it.name.startsWith(WALLPAPER_PREFIX) }.forEach { field ->
            try {
                val resourceId = field.getInt(null)
                val rawName = field.name
                val parts = rawName.substring(WALLPAPER_PREFIX.length).split("_")

                if (parts.size >= 2) {
                    val tag = parts.last().replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase() else it.toString()
                    }
                    val title = parts.subList(0, parts.size - 1)
                        .joinToString(" ") { word ->
                            word.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase() else it.toString()
                            }
                        }

                    wallpaperList.add(
                        Wallpaper(
                            id = rawName,
                            resourceId = resourceId,
                            title = title,
                            tag = tag
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return wallpaperList.sortedBy { it.title }
    }

    
    val baseWallpapers: List<Wallpaper> = loadWallpapersDynamically(application.applicationContext)

    
    val allWallpapers: List<Wallpaper> = List(4) { baseWallpapers }.flatten()

    var selectedTag by mutableStateOf("All")
        private set

    val allTags: List<String> = listOf("All") + baseWallpapers.map { it.tag }.distinct().sorted()

    
    val filteredWallpapers by derivedStateOf {
        if (selectedTag == "All") {
            allWallpapers 
        } else {
           
            baseWallpapers.filter { it.tag == selectedTag }
        }
    }

    fun selectTag(tag: String) {
        selectedTag = tag
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WallpaperAppTheme {
                SwipableWallpaperScreen()
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SwipableWallpaperScreen(viewModel: WallpaperViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()

    
    val window = (view.context as Activity).window
    val systemBarsColor = MaterialTheme.colorScheme.background

    SideEffect {
        
        window.statusBarColor = systemBarsColor.toArgb()
        window.navigationBarColor = systemBarsColor.toArgb()

      
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
    }

    var isSettingWallpaper by remember { mutableStateOf(false) }
    var showDestinationSheet by remember { mutableStateOf(false) }
    
    var wallpaperToApplyState by remember { mutableStateOf<Wallpaper?>(null) }
    
    var selectedWallpaperIndex by remember { mutableIntStateOf(-1) }
    var showFilterPanel by remember { mutableStateOf(false) }

    val isDetailViewActive = selectedWallpaperIndex != -1

    BackHandler(enabled = isDetailViewActive) {
        selectedWallpaperIndex = -1
    }

    val filteredWallpapers = viewModel.filteredWallpapers
    val selectedTag = viewModel.selectedTag

    val mainPagerState = rememberPagerState(
        pageCount = { filteredWallpapers.size }
    )

    LaunchedEffect(selectedTag) {
        if (filteredWallpapers.isNotEmpty()) {
            mainPagerState.scrollToPage(0)
        }
    }
    
    val currentWallpaper by remember {
        derivedStateOf {
            if (filteredWallpapers.isNotEmpty()) {
                filteredWallpapers.getOrNull(mainPagerState.currentPage)
            } else {
                null
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
    
        AnimatedVisibility(
            visible = isDetailViewActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
        	
            if (filteredWallpapers.isNotEmpty()) {
                WallpaperDetailScreen(
                    wallpapers = filteredWallpapers,
                    initialPageIndex = selectedWallpaperIndex,
                    onClose = { selectedWallpaperIndex = -1 },
                    
                    onApplyClick = { wallpaper ->
                        wallpaperToApplyState = wallpaper
                        showDestinationSheet = true
                    }
                )
            }
        }
        
        AnimatedVisibility(
            visible = !isDetailViewActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
            	
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Button(
                        onClick = {
                            if (currentWallpaper != null) {
                                wallpaperToApplyState = currentWallpaper 
                                showDestinationSheet = true
                            } else {
                                Toast.makeText(context, "Please select a wallpaper first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .widthIn(min = 180.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Image, contentDescription = "Set Wallpaper")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SET WALLPAPER",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                if (filteredWallpapers.isEmpty()) {
                    Text(
                        "No Wallpapers Found.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                } else {
                	
                    HorizontalPager(
                        state = mainPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .align(Alignment.Center),
                        contentPadding = PaddingValues(horizontal = 80.dp)
                    ) { pageIndex ->
                        val wallpaper = filteredWallpapers[pageIndex]

                        val pageOffset = (
                                (mainPagerState.currentPage - pageIndex) + mainPagerState.currentPageOffsetFraction
                                ).absoluteValue.coerceIn(0f, 1f)

                        val scale = lerp(
                            start = 0.85f,
                            stop = 1f,
                            fraction = 1f - pageOffset
                        )

                        val alpha = lerp(
                            start = 0.6f,
                            stop = 1f,
                            fraction = 1f - pageOffset
                        )

                        WallpaperPreviewCard(
                            wallpaper = wallpaper,
                            
                            onClick = { selectedWallpaperIndex = pageIndex },
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            }
                        )
                    }

                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 72.dp), 
                        contentAlignment = Alignment.Center
                    ) {
                        FixedTransitionIndicator(
                            pagerState = mainPagerState,
                            modifier = Modifier
                        )
                    }

                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryChip(
                            title = "Filter: $selectedTag",
                            isSelected = true,
                            onClick = { showFilterPanel = true },
                            icon = Icons.Default.FilterList
                        )
                    }
                }
            }
        }

        
        if (showFilterPanel) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showFilterPanel = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Wallpaper Category",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        viewModel.allTags.forEach { tag ->
                            CategoryChip(
                                title = tag,
                                isSelected = tag == selectedTag,
                                onClick = {
                                    viewModel.selectTag(tag)
                                    showFilterPanel = false
                                },
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }


        
        if (showDestinationSheet && wallpaperToApplyState != null) {
            val wallpaperToApply = wallpaperToApplyState!!

            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showDestinationSheet = false; wallpaperToApplyState = null },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Apply Wallpaper To",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 24.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val applyWallpaperAction: (Int) -> Unit = { destination ->
                        isSettingWallpaper = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                setDeviceWallpaper(context, wallpaperToApply.resourceId, wallpaperToApply.title, destination)
                            }
                            showDestinationSheet = false
                            isSettingWallpaper = false
                            wallpaperToApplyState = null 
                        }
                    }

                    DestinationButton(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Home & Lock Screens",
                        subtitle = "Set as both your main and lock screen wallpaper.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(FLAG_BOTH) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    DestinationButton(
                        icon = Icons.Default.PhotoLibrary,
                        title = "Home Screen Only",
                        subtitle = "Set only for your main home screen.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(FLAG_HOME_SCREEN) }
                    )

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)

                    DestinationButton(
                        icon = Icons.Default.Lock,
                        title = "Lock Screen Only",
                        subtitle = "Set only for your device's lock screen.",
                        isSetting = isSettingWallpaper,
                        onClick = { applyWallpaperAction(FLAG_LOCK_SCREEN) }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        
        AnimatedVisibility(
            visible = isSettingWallpaper,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LoadingOverlay(title = "Setting Wallpaper...")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FixedTransitionIndicator(
    pagerState: PagerState,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
    trackWidth: Dp = 80.dp,
    baseTrackHeight: Dp = 4.dp
) {
    if (pagerState.pageCount <= 1) return

  
    val absoluteTransitionFraction = pagerState.currentPageOffsetFraction.absoluteValue
    val transitionFraction = pagerState.currentPageOffsetFraction

    
    val heightIncrease = 2.dp * absoluteTransitionFraction
    val activeTrackHeight = baseTrackHeight + heightIncrease

    
    val activeWidth = trackWidth * absoluteTransitionFraction
    
    val offsetX: Dp = if (transitionFraction < 0) {
        
        0.dp
    } else {
        
        trackWidth - activeWidth
    }

    
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(baseTrackHeight)
            .background(inactiveColor, RoundedCornerShape(50))
    ) {
        
        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .width(activeWidth)
                .height(activeTrackHeight) 
                .background(activeColor, RoundedCornerShape(50))
                .align(Alignment.CenterStart) 
        )
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WallpaperDetailScreen(
    wallpapers: List<Wallpaper>,
    initialPageIndex: Int,
    onClose: () -> Unit,
    onApplyClick: (Wallpaper) -> Unit
) {
    
    val detailPagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { wallpapers.size }
    )

 
    val currentWallpaper = wallpapers.getOrNull(detailPagerState.currentPage)

    if (currentWallpaper == null) {
        onClose()
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        
        HorizontalPager(
            state = detailPagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
           
            Image(
                painter = painterResource(id = wallpapers[pageIndex].resourceId),
                contentDescription = "Fullscreen: ${wallpapers[pageIndex].title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close Preview",
                    tint = Color.White
                )
            }

            
            Button(
              
                onClick = { onApplyClick(currentWallpaper) },
                modifier = Modifier
                    .height(48.dp) 
                    .widthIn(min = 120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, contentDescription = "Set", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "APPLY",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 0f,
                        endY = 400f
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = currentWallpaper.tag,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp
            )
            Text(
                text = currentWallpaper.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                lineHeight = 38.sp
            )
        }
    }
}


@Composable
fun WallpaperPreviewCard(wallpaper: Wallpaper, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
      
        Image(
            painter = painterResource(id = wallpaper.resourceId),
            contentDescription = "Preview: ${wallpaper.title}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CategoryChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text = title, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DestinationButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSetting: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isSetting,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        contentPadding = PaddingValues(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.size(18.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(text = title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = subtitle, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (isSetting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}


@Composable
fun LoadingOverlay(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .padding(32.dp)
                .widthIn(min = 250.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "This might take a moment.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun setDeviceWallpaper(context: Context, resourceId: Int, title: String, destination: Int) {
    val wallpaperManager = WallpaperManager.getInstance(context)
    val destinationText = when (destination) {
        FLAG_HOME_SCREEN -> "Home Screen"
        FLAG_LOCK_SCREEN -> "Lock Screen"
        FLAG_BOTH -> "Home and Lock Screens"
        else -> "Unknown Destination"
    }

    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
        
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wallpaperManager.setBitmap(bitmap, null, true, destination)
        } else {
            
            wallpaperManager.setBitmap(bitmap)
        }

        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Wallpaper '$title' set successfully to $destinationText!", Toast.LENGTH_LONG).show()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Failed to set wallpaper: ${e.message}", Toast.LENGTH_LONG).show()
        }
    } catch (e: SecurityException) {
        e.printStackTrace()
        android.os.Handler(context.mainLooper).post {
            Toast.makeText(context, "Permission error. Ensure SET_WALLPAPER is in AndroidManifest.", Toast.LENGTH_LONG).show()
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
