package com.mapt.demo

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mapt.demo.ui.theme.DemoTheme
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    private var poseState by mutableStateOf(PoseUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openCvReady = OpenCVLoader.initLocal()

        setContent {
            DemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ArucoScreen(
                        modifier = Modifier.padding(innerPadding),
                        openCvReady = openCvReady,
                        poseUiState = poseState,
                        onPoseDetected = { poseState = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ArucoScreen(
    modifier: Modifier = Modifier,
    openCvReady: Boolean,
    poseUiState: PoseUiState,
    onPoseDetected: (PoseUiState) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
        onDispose { }
    }

    val arucoEngine = remember(openCvReady) {
        if (openCvReady) ArucoEngine(MarkerMapRepository.markerMap) else null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Nawigacja",
            style = MaterialTheme.typography.titleMedium
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                !openCvReady -> CenterMessage("OpenCV nie zostal zainicjalizowany")
                !hasCameraPermission -> CenterMessage("Brak uprawnienia do kamery")
                arucoEngine == null -> CenterMessage("Silnik ArUco niedostepny")
                else -> ArucoCameraView(
                    modifier = Modifier.fillMaxSize(),
                    arucoEngine = arucoEngine,
                    onPoseDetected = onPoseDetected
                )
            }
        }

        PoseInfoCard(
            modifier = Modifier.fillMaxWidth(),
            state = poseUiState
        )

        Spacer(modifier = Modifier.weight(1f))

        RoomMapCard(
            modifier = Modifier.fillMaxWidth(),
            state = poseUiState,
            markerMap = MarkerMapRepository.markerMap,
            roomConfig = MarkerMapRepository.roomConfig
        )
    }
}

@Composable
private fun ArucoCameraView(
    modifier: Modifier,
    arucoEngine: ArucoEngine,
    onPoseDetected: (PoseUiState) -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latestCallback = rememberUpdatedState(onPoseDetected)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var cameraViewRef by remember { mutableStateOf<JavaCameraView?>(null) }

    val listener = remember(arucoEngine) {
        object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) = Unit

            override fun onCameraViewStopped() = Unit

            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame) =
                inputFrame.rgba().also { frame ->
                    val pose = runCatching { arucoEngine.processFrame(frame) }
                        .getOrElse { PoseUiState(status = "Blad przetwarzania klatki: ${it.message}") }
                    mainHandler.post { latestCallback.value(pose) }
                }
        }
    }

    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            JavaCameraView(ctx, CameraBridgeViewBase.CAMERA_ID_BACK).apply {
                visibility = SurfaceView.VISIBLE
                setCameraPermissionGranted()
                setCvCameraViewListener(listener)
                cameraViewRef = this
                enableView()
            }
        }
    )

    DisposableEffect(lifecycleOwner, cameraViewRef) {
        val observer = LifecycleEventObserver { _, event ->
            val view = cameraViewRef ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_RESUME -> view.enableView()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> view.disableView()
                Lifecycle.Event.ON_DESTROY -> view.disableView()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraViewRef?.disableView()
        }
    }
}

@Composable
private fun PoseInfoCard(modifier: Modifier = Modifier, state: PoseUiState) {
    MinimalPanel(modifier = modifier) {
        Text(
            text = state.status,
            style = MaterialTheme.typography.titleSmall
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatColumn(label = "Marker", value = state.markerId?.toString() ?: "-")
            StatColumn(label = "Lokacja", value = state.location)
            StatColumn(
                label = "Dystans",
                value = state.distanceMeters?.let { "%.2f m".format(it) } ?: "-"
            )
        }

        Text(
            text = "Pozycja: X ${state.worldX?.let { "%.2f".format(it) } ?: "-"}, Y ${state.worldY?.let { "%.2f".format(it) } ?: "-"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun CenterMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun RoomMapCard(
    modifier: Modifier = Modifier,
    state: PoseUiState,
    markerMap: Map<Int, MarkerMapEntry>,
    roomConfig: RoomMapConfig
) {
    MinimalPanel(modifier = modifier) {
        Text(
            text = "Mapa sali",
            style = MaterialTheme.typography.titleSmall
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.8f)
                .padding(top = 12.dp)
        ) {
            val padding = 18.dp.toPx()
            val roomLeft = padding
            val roomTop = padding
            val roomWidthPx = size.width - 2 * padding
            val roomHeightPx = size.height - 2 * padding
            val roomBottom = roomTop + roomHeightPx

            drawRect(
                color = Color(0xFF607D8B),
                topLeft = Offset(roomLeft, roomTop),
                size = Size(roomWidthPx, roomHeightPx),
                style = Stroke(width = 3f)
            )

            fun worldToCanvas(xMeters: Double, yMeters: Double): Offset {
                val normalizedX = (xMeters / roomConfig.widthMeters).toFloat().coerceIn(0f, 1f)
                val normalizedY = (yMeters / roomConfig.heightMeters).toFloat().coerceIn(0f, 1f)
                return Offset(
                    x = roomLeft + normalizedX * roomWidthPx,
                    y = roomBottom - normalizedY * roomHeightPx
                )
            }

            markerMap.values.forEach { marker ->
                drawCircle(
                    color = Color(0xFF2E7D32),
                    radius = 8f,
                    center = worldToCanvas(marker.x, marker.y)
                )
            }

            if (state.markerId != null && state.worldX != null && state.worldY != null) {
                drawCircle(
                    color = Color(0xFFD32F2F),
                    radius = 11f,
                    center = worldToCanvas(state.worldX, state.worldY)
                )
            }
        }
    }
}

@Composable
private fun MinimalPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

@Preview(showBackground = true, name = "PoseInfoCard - wykryto marker")
@Composable
private fun PoseInfoCardPreviewDetected() {
    DemoTheme(dynamicColor = false) {
        PoseInfoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            state = PoseUiState(
                markerId = 42,
                location = "B1",
                worldX = 2.35,
                worldY = 6.90,
                distanceMeters = 1.27,
                status = "Wykryto marker"
            )
        )
    }
}

@Preview(showBackground = true, name = "PoseInfoCard - brak detekcji")
@Composable
private fun PoseInfoCardPreviewSearching() {
    DemoTheme(dynamicColor = false) {
        PoseInfoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            state = PoseUiState()
        )
    }
}

@Preview(showBackground = true, name = "RoomMapCard")
@Composable
private fun RoomMapCardPreview() {
    DemoTheme(dynamicColor = false) {
        RoomMapCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            state = PoseUiState(
                markerId = 2,
                location = "A4",
                worldX = 7.10,
                worldY = 3.60,
                distanceMeters = 0.92,
                status = "Wykryto marker"
            ),
            markerMap = MarkerMapRepository.markerMap,
            roomConfig = MarkerMapRepository.roomConfig
        )
    }
}

@Preview(showBackground = true, name = "CenterMessage")
@Composable
private fun CenterMessagePreview() {
    DemoTheme(dynamicColor = false) {
        CenterMessage("Brak uprawnienia do kamery")
    }
}

