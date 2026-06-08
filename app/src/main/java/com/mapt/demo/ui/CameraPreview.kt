package com.mapt.demo.ui

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.JavaCameraView
import org.opencv.core.Mat

/**
 * Generyczny podglad tylnej kamery OpenCV. Dla kazdej klatki wywoluje [onFrame]
 * z obrazem RGBA (na watku kamery). Rysowanie na [Mat] jest pokazywane w podgladzie.
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrame: (Mat) -> Unit
) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val latestOnFrame = rememberUpdatedState(onFrame)
    var cameraViewRef by remember { mutableStateOf<JavaCameraView?>(null) }

    val listener = remember {
        object : CameraBridgeViewBase.CvCameraViewListener2 {
            override fun onCameraViewStarted(width: Int, height: Int) = Unit
            override fun onCameraViewStopped() = Unit
            override fun onCameraFrame(inputFrame: CameraBridgeViewBase.CvCameraViewFrame): Mat =
                inputFrame.rgba().also { frame -> latestOnFrame.value(frame) }
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
                Lifecycle.Event.ON_STOP,
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
