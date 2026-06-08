package com.mapt.demo.warehouse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mapt.demo.ArucoEngine
import com.mapt.demo.ui.CameraPreview

@Composable
fun WarehouseScreen(
    modifier: Modifier = Modifier,
    openCvReady: Boolean,
    apiStatusText: String,
    onEvent: (WarehouseEvent) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) cameraLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    val arucoEngine = remember(openCvReady) {
        if (openCvReady) ArucoEngine(com.mapt.demo.MarkerMapRepository.markerMap) else null
    }
    val tracker = remember { WarehouseTracker() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var uiState by remember { mutableStateOf(WarehouseUiState()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Magazyn — rotacja towaru", style = MaterialTheme.typography.titleMedium)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                !openCvReady -> CenterText("OpenCV nie zostal zainicjalizowany")
                !hasCameraPermission -> CenterText("Brak uprawnienia do kamery")
                arucoEngine == null -> CenterText("Silnik ArUco niedostepny")
                else -> CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onFrame = { frame ->
                        val now = System.currentTimeMillis()
                        val ids = runCatching { arucoEngine.detectMarkerIds(frame) }.getOrDefault(emptySet())
                        val events = tracker.onFrame(ids, now)
                        val snapshot = tracker.snapshot(now)
                        mainHandler.post {
                            uiState = snapshot
                            events.forEach(onEvent)
                        }
                    }
                )
            }
        }

        StatusPanel(state = uiState, apiStatusText = apiStatusText)

        EventLogPanel(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            events = uiState.recentEvents
        )
    }
}

@Composable
private fun StatusPanel(state: WarehouseUiState, apiStatusText: String) {
    Panel {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Bieżąca strefa", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.currentLocation ?: "—", style = MaterialTheme.typography.titleSmall)
            }
            Column {
                Text("Towar na wózku", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (state.presentItems.isEmpty()) "—" else state.presentItems.joinToString(", "),
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(state.status, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(apiStatusText, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EventLogPanel(modifier: Modifier = Modifier, events: List<WarehouseEvent>) {
    val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    Panel(modifier = modifier) {
        Text("Log zdarzeń", style = MaterialTheme.typography.titleSmall)
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        if (events.isEmpty()) {
            Text("Brak zdarzeń — przesuń towar przed kamerą.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(events) { event ->
                    val typ = if (event.type == WarehouseEventType.PICKED_UP) "POBRANO" else "ZŁOŻONO"
                    Text(
                        "${timeFmt.format(Date(event.epochMs))}  $typ  ${event.itemLabel} @ ${event.locationLabel}",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
    ) {
        Column(modifier = Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun CenterText(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
    }
}
