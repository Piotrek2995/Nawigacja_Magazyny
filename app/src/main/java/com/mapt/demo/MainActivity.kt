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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Column(modifier = modifier.fillMaxSize()) {
        when {
            !openCvReady -> {
                CenterMessage("OpenCV nie zostal zainicjalizowany")
            }

            !hasCameraPermission -> {
                CenterMessage("Brak uprawnienia do kamery")
            }

            arucoEngine == null -> {
                CenterMessage("Silnik ArUco niedostepny")
            }

            else -> {
                ArucoCameraView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp),
                    arucoEngine = arucoEngine,
                    onPoseDetected = onPoseDetected
                )
            }
        }

        PoseInfoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            state = poseUiState
        )
    }
}

@Composable
private fun ArucoCameraView(
    modifier: Modifier,
    arucoEngine: ArucoEngine,
    onPoseDetected: (PoseUiState) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
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
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Status: ${state.status}")
            Text(text = "Marker ID: ${state.markerId ?: "-"}")
            Text(text = "Lokacja: ${state.location}")
            Text(text = "Pozycja mapy X: ${state.worldX?.let { "%.2f".format(it) } ?: "-"}")
            Text(text = "Pozycja mapy Y: ${state.worldY?.let { "%.2f".format(it) } ?: "-"}")
            Text(text = "Dystans do markera: ${state.distanceMeters?.let { "%.2f m".format(it) } ?: "-"}")
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, textAlign = TextAlign.Center)
    }
}