package com.mapt.demo

import android.Manifest
import android.location.LocationManager
import android.location.LocationListener
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.mapt.demo.ui.theme.DemoTheme
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
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

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasLocationPermission = granted
    }

    DisposableEffect(Unit) {
        if (!hasCameraPermission) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
        }
        if (!hasLocationPermission) {
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        onDispose { }
    }

    val arucoEngine = remember(openCvReady) {
        if (openCvReady) ArucoEngine(MarkerMapRepository.markerMap) else null
    }

    var currentLocation by remember {
        mutableStateOf<Pair<Double, Double>?>(null)
    }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
            val locationListener = LocationListener { location ->
                currentLocation = Pair(location.latitude, location.longitude)
            }

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000, // update every 1 second
                    0f,   // no minimum distance
                    locationListener,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                // Handle permission error
            }

            onDispose {
                locationManager.removeUpdates(locationListener)
            }
        } else {
            onDispose { }
        }
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

        // Camera View - główny element
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
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

        // MapLibre card - mały, pod kamerą
        MapLibreMapCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f),
            userLocation = currentLocation
        )

        // Dwa panele obok siebie na dole
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.8f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PoseInfoCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                state = poseUiState
            )

            RoomMapCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
                state = poseUiState,
                markerMap = MarkerMapRepository.markerMap,
                roomConfig = MarkerMapRepository.roomConfig
            )
        }
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
            style = MaterialTheme.typography.labelSmall
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StatColumn(label = "Marker", value = state.markerId?.toString() ?: "-")
            StatColumn(label = "Lokacja", value = state.location)
            StatColumn(
                label = "Dystans",
                value = state.distanceMeters?.let { "%.1f m".format(it) } ?: "-"
            )
        }

        Text(
            text = "X: ${state.worldX?.let { "%.2f".format(it) } ?: "-"}, Y: ${state.worldY?.let { "%.2f".format(it) } ?: "-"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp
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
                .aspectRatio(1.2f)
                .padding(top = 8.dp)
        ) {
            val padding = 12.dp.toPx()
            val roomLeft = padding
            val roomTop = padding
            val roomWidthPx = size.width - 2 * padding
            val roomHeightPx = size.height - 2 * padding
            val roomBottom = roomTop + roomHeightPx

            drawRect(
                color = Color(0xFF607D8B),
                topLeft = Offset(roomLeft, roomTop),
                size = Size(roomWidthPx, roomHeightPx),
                style = Stroke(width = 2f)
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
                    radius = 6f,
                    center = worldToCanvas(marker.x, marker.y)
                )
            }

            if (state.markerId != null && state.worldX != null && state.worldY != null) {
                drawCircle(
                    color = Color(0xFFD32F2F),
                    radius = 8f,
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(10.dp), content = content)
    }
}

@Composable
private fun MapDisposableEffect(
    lifecycleOwner: LifecycleOwner,
    mapView: MapView
) = DisposableEffect(lifecycleOwner, mapView) {
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            mapView.onStart()
        }

        override fun onResume(owner: LifecycleOwner) {
            mapView.onResume()
        }

        override fun onPause(owner: LifecycleOwner) {
            mapView.onPause()
        }

        override fun onStop(owner: LifecycleOwner) {
            mapView.onStop()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            mapView.onDestroy()
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}

@Composable
private fun MapLibreMapCard(modifier: Modifier = Modifier, userLocation: Pair<Double, Double>? = null) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context)
    }

    // Przechowuj referencję do mapy, aby dodać marker po załadowaniu stylu
    var mapLibreMapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }

    val tileSet = remember {
        TileSet("2.2.0", "https://tile.openstreetmap.org/{z}/{x}/{y}.png").apply {
            minZoom = 0f
            maxZoom = 19f
        }
    }

    val mapStyle = remember {
        Style.Builder()
            .withSource(RasterSource("osm-raster-source", tileSet, 256))
            .withLayer(RasterLayer("osm-raster-layer", "osm-raster-source"))
    }

    DisposableEffect(Unit) {
        mapView.getMapAsync(object : OnMapReadyCallback {
            override fun onMapReady(mapLibreMap: MapLibreMap) {
                mapLibreMapRef = mapLibreMap
                mapLibreMap.setStyle(mapStyle) {
                    styleLoaded = true
                    // Po załadowaniu stylu, jeśli mamy lokalizację, dodaj marker i przesuń kamerę
                    if (userLocation != null) {
                        val (lat, lon) = userLocation
                        val point = LatLng(lat, lon)
                        // Dodaj marker
                        mapLibreMap.clear() // Usuwa poprzednie markery
                        mapLibreMap.addMarker(org.maplibre.android.annotations.MarkerOptions().position(point).title("Twoja lokalizacja"))
                        // Przesuń kamerę
                        mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 18.0))
                    }
                }
            }
        })
        onDispose { }
    }

    // Jeśli lokalizacja się zmieniła i styl jest już załadowany, aktualizuj marker i kamerę
    DisposableEffect(userLocation, styleLoaded) {
        val mapLibreMap = mapLibreMapRef
        if (mapLibreMap != null && styleLoaded && userLocation != null) {
            val (lat, lon) = userLocation
            val point = LatLng(lat, lon)
            mapLibreMap.clear()
            mapLibreMap.addMarker(org.maplibre.android.annotations.MarkerOptions().position(point).title("Twoja lokalizacja"))
            mapLibreMap.moveCamera(CameraUpdateFactory.newLatLngZoom(point, 18.0))
        }
        onDispose { }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Text(
                text = "Moja lokalizacja: " + if (userLocation != null) {
                    "Lat: %.4f, Lon: %.4f".format(userLocation.first, userLocation.second)
                } else {
                    "Oczekiwanie na GPS..."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 6.dp),
                factory = { mapView }
            )
        }
    }

    MapDisposableEffect(lifecycleOwner, mapView)
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
