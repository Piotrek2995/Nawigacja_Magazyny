package com.mapt.demo

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Dictionary
import org.opencv.objdetect.Objdetect

class ArucoEngine(
    private val markerMap: Map<Int, MarkerMapEntry>,
    private val markerLengthMeters: Double = 0.16,
    private val dictionaryTypes: List<Int> = DEFAULT_DICTIONARIES
) {
    private val detectors: List<DetectorSpec> = dictionaryTypes.map { type ->
        val dictionary: Dictionary = Objdetect.getPredefinedDictionary(type)
        DetectorSpec(type, ArucoDetector(dictionary, DetectorParameters()))
    }

    fun processFrame(rgba: Mat): PoseUiState {
        val gray = Mat()
        var detectResult: DetectResult? = null
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        detectResult = detectWithFallback(gray)
        gray.release()

        if (detectResult == null) {
            return PoseUiState(status = "Szukam markera ArUco...")
        }

        val corners = detectResult.corners
        val ids = detectResult.ids
        drawDetectedCorners(rgba, corners)

        val markerId = ids.get(0, 0)[0].toInt()
        val markerData = markerMap[markerId]
        if (markerData == null) {
            ids.release()
            corners.forEach { it.release() }
            drawStatus(rgba, "Marker ID=$markerId nie istnieje w mapie")
            return PoseUiState(
                markerId = markerId,
                status = "Marker wykryty, ale brak wpisu w marker_map"
            )
        }

        val pose = estimatePose(corners.first(), rgba.width(), rgba.height())
        if (pose == null) {
            ids.release()
            corners.forEach { it.release() }
            drawStatus(rgba, "ID=$markerId | solvePnP nieudane")
            return PoseUiState(
                markerId = markerId,
                location = markerData.location,
                status = "Marker wykryty, solvePnP nieudane"
            )
        }

        val (cameraXMarker, cameraZMarker, distanceMeters) = pose
        val (rotX, rotY) = rotate2d(cameraXMarker, cameraZMarker, markerData.yawDeg)
        val worldX = markerData.x + rotX
        val worldY = markerData.y + rotY
        val dictionaryName = dictionaryLabel(detectResult.dictionaryType)

        val text = "ID=$markerId ${markerData.location} $dictionaryName X=%.2f Y=%.2f d=%.2fm"
            .format(worldX, worldY, distanceMeters)
        drawStatus(rgba, text)
        ids.release()
        corners.forEach { it.release() }

        return PoseUiState(
            markerId = markerId,
            location = markerData.location,
            worldX = worldX,
            worldY = worldY,
            distanceMeters = distanceMeters,
            cameraXInMarker = cameraXMarker,
            cameraZInMarker = cameraZMarker,
            status = "Pozycja oszacowana z marker_map + solvePnP"
        )
    }

    private fun detectWithFallback(gray: Mat): DetectResult? {
        var compatibilityErrorType: Int? = null

        detectors.forEach { spec ->
            val corners = mutableListOf<Mat>()
            val ids = Mat()
            try {
                spec.detector.detectMarkers(gray, corners, ids)
                if (!ids.empty() && corners.isNotEmpty()) {
                    return DetectResult(spec.dictionaryType, corners, ids)
                }
            } catch (e: CvException) {
                compatibilityErrorType = compatibilityErrorType ?: parseMarkerType(e.message)
            }
            ids.release()
            corners.forEach { it.release() }
        }

        compatibilityErrorType?.let { markerType ->
            val dynamic = detectWithDictionaryType(gray, markerType)
            if (dynamic != null) return dynamic
        }

        return null
    }

    private fun detectWithDictionaryType(gray: Mat, dictionaryType: Int): DetectResult? {
        val corners = mutableListOf<Mat>()
        val ids = Mat()
        return try {
            val dictionary = Objdetect.getPredefinedDictionary(dictionaryType)
            val detector = ArucoDetector(dictionary, DetectorParameters())
            detector.detectMarkers(gray, corners, ids)
            if (ids.empty() || corners.isEmpty()) {
                ids.release()
                corners.forEach { it.release() }
                null
            } else {
                DetectResult(dictionaryType, corners, ids)
            }
        } catch (_: CvException) {
            ids.release()
            corners.forEach { it.release() }
            null
        }
    }

    private fun parseMarkerType(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        val match = Regex("marker type is not compatible:\\s*(\\d+)").find(message)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun dictionaryLabel(type: Int): String = when (type) {
        Objdetect.DICT_4X4_50 -> "4X4_50"
        Objdetect.DICT_4X4_100 -> "4X4_100"
        Objdetect.DICT_4X4_250 -> "4X4_250"
        Objdetect.DICT_4X4_1000 -> "4X4_1000"
        Objdetect.DICT_5X5_50 -> "5X5_50"
        Objdetect.DICT_5X5_100 -> "5X5_100"
        Objdetect.DICT_5X5_250 -> "5X5_250"
        Objdetect.DICT_5X5_1000 -> "5X5_1000"
        Objdetect.DICT_6X6_50 -> "6X6_50"
        Objdetect.DICT_6X6_100 -> "6X6_100"
        Objdetect.DICT_6X6_250 -> "6X6_250"
        Objdetect.DICT_6X6_1000 -> "6X6_1000"
        Objdetect.DICT_7X7_50 -> "7X7_50"
        Objdetect.DICT_7X7_100 -> "7X7_100"
        Objdetect.DICT_7X7_250 -> "7X7_250"
        Objdetect.DICT_7X7_1000 -> "7X7_1000"
        Objdetect.DICT_ARUCO_ORIGINAL -> "ARUCO_ORIGINAL"
        else -> "DICT_$type"
    }

    private data class DetectorSpec(
        val dictionaryType: Int,
        val detector: ArucoDetector
    )

    private data class DetectResult(
        val dictionaryType: Int,
        val corners: List<Mat>,
        val ids: Mat
    )

    companion object {
        private val DEFAULT_DICTIONARIES = listOf(
            Objdetect.DICT_4X4_50,
            Objdetect.DICT_5X5_100,
            Objdetect.DICT_6X6_250,
            Objdetect.DICT_7X7_250,
            Objdetect.DICT_ARUCO_ORIGINAL
        )
    }

    private fun estimatePose(cornerMat: Mat, width: Int, height: Int): Triple<Double, Double, Double>? {
        val points = extractCorners(cornerMat) ?: return null

        val imagePoints = MatOfPoint2f(*points.toTypedArray())
        val objectPoints = createObjectPoints(markerLengthMeters)
        val cameraMatrix = createApproxCameraMatrix(width, height)
        val distCoeffs = MatOfDouble(0.0, 0.0, 0.0, 0.0, 0.0)

        val rvec = Mat()
        val tvec = Mat()

        val ok = Calib3d.solvePnP(
            objectPoints,
            imagePoints,
            cameraMatrix,
            distCoeffs,
            rvec,
            tvec,
            false,
            Calib3d.SOLVEPNP_IPPE_SQUARE
        )

        if (!ok) {
            imagePoints.release()
            objectPoints.release()
            cameraMatrix.release()
            distCoeffs.release()
            rvec.release()
            tvec.release()
            return null
        }

        val rotation = Mat()
        Calib3d.Rodrigues(rvec, rotation)
        val rotationT = rotation.t()
        val cameraInMarker = Mat()
        Core.gemm(rotationT, tvec, -1.0, Mat(), 0.0, cameraInMarker)

        val cx = cameraInMarker.get(0, 0)[0]
        val cz = cameraInMarker.get(2, 0)[0]
        val distance = hypot(tvec.get(0, 0)[0], tvec.get(2, 0)[0])

        imagePoints.release()
        objectPoints.release()
        cameraMatrix.release()
        distCoeffs.release()
        rvec.release()
        tvec.release()
        rotation.release()
        rotationT.release()
        cameraInMarker.release()

        return Triple(cx, cz, distance)
    }

    private fun createApproxCameraMatrix(width: Int, height: Int): Mat {
        val f = 0.9 * maxOf(width, height)
        return Mat(3, 3, CvType.CV_64F).apply {
            put(
                0,
                0,
                f,
                0.0,
                width / 2.0,
                0.0,
                f,
                height / 2.0,
                0.0,
                0.0,
                1.0
            )
        }
    }

    private fun createObjectPoints(markerLength: Double): MatOfPoint3f {
        val half = markerLength / 2.0
        return MatOfPoint3f(
            Point3(-half, half, 0.0),
            Point3(half, half, 0.0),
            Point3(half, -half, 0.0),
            Point3(-half, -half, 0.0)
        )
    }

    private fun extractCorners(cornerMat: Mat): List<Point>? {
        if (cornerMat.empty() || cornerMat.channels() != 2) return null

        val pointCount = cornerMat.total().toInt()
        if (pointCount < 4) return null

        return when (cornerMat.depth()) {
            CvType.CV_32F -> {
                val raw = FloatArray(pointCount * 2)
                cornerMat.get(0, 0, raw)
                listOf(
                    Point(raw[0].toDouble(), raw[1].toDouble()),
                    Point(raw[2].toDouble(), raw[3].toDouble()),
                    Point(raw[4].toDouble(), raw[5].toDouble()),
                    Point(raw[6].toDouble(), raw[7].toDouble())
                )
            }

            CvType.CV_64F -> {
                val raw = DoubleArray(pointCount * 2)
                cornerMat.get(0, 0, raw)
                listOf(
                    Point(raw[0], raw[1]),
                    Point(raw[2], raw[3]),
                    Point(raw[4], raw[5]),
                    Point(raw[6], raw[7])
                )
            }

            else -> null
        }
    }

    private fun rotate2d(x: Double, y: Double, yawDeg: Double): Pair<Double, Double> {
        val rad = Math.toRadians(yawDeg)
        val c = cos(rad)
        val s = sin(rad)
        return Pair(
            x * c - y * s,
            x * s + y * c
        )
    }

    private fun drawStatus(frame: Mat, text: String) {
        Imgproc.putText(
            frame,
            text,
            Point(30.0, 60.0),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            0.9,
            Scalar(0.0, 255.0, 0.0),
            2
        )
    }

    private fun drawDetectedCorners(frame: Mat, corners: List<Mat>) {
        corners.forEach { cornerMat ->
            val points = extractCorners(cornerMat) ?: return@forEach
            for (i in points.indices) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                Imgproc.line(frame, p1, p2, Scalar(255.0, 0.0, 255.0, 255.0), 3)
            }
        }
    }
}

