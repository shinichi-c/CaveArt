package com.android.CaveArt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorDatePanel(
    viewModel: WallpaperViewModel, 
    wallpaper: Wallpaper, 
    state: ClockStudioState, 
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AmbientBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, viewModel = viewModel, currentWallpaper = wallpaper) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("At a Glance Widget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Attach to Clock", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Keep date permanently above clock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.dateAttached.value, onCheckedChange = { state.dateAttached.value = it })
            }
            Spacer(Modifier.height(24.dp))
            Text("Date Format", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val formatTitles = listOf("Standard", "Short", "CAPS Year", "Full Spell", "Modern Minimal")
                items(formatTitles.size) { index ->
                    FilterChip(
                        selected = state.dateFormat.intValue == index, onClick = { state.dateFormat.intValue = index },
                        label = { Text(formatTitles[index], fontWeight = FontWeight.Bold) }, shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
