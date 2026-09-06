package dev.spatial.android.hand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.geometry.Offset
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import dev.spatial.android.SettingsStore
import dev.spatial.android.spatial.SpatialController

class HandLandmarkAnalyzer(
    private val context: Context,
    private val settings: SettingsStore,
    private val controller: SpatialController
) : ImageAnalysis.Analyzer {

    private var landmarker: HandLandmarker? = null
    private var failed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .setDelegate(Delegate.CPU)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.VIDEO)
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            landmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Throwable) {
            failed = true
            mainHandler.post {
                controller.onCameraError("Hand tracking model failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (failed || !settings.trackingEnabled) {
            imageProxy.close()
            return
        }

        val start = SystemClock.uptimeMillis()
        var bitmap: Bitmap? = null
        var rotatedBitmap: Bitmap? = null

        try {
            bitmap = imageProxy.toBitmap()

            // Rotate the camera frame before handing it to MediaPipe. This matches
            // the orientation shown by CameraX and avoids relying on unavailable
            // BitmapImageBuilder rotation APIs.
            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (settings.useFrontCamera) {
                    postScale(-1f, 1f, bitmap.width.toFloat(), bitmap.height.toFloat())
                }
            }

            rotatedBitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            val timestamp = SystemClock.uptimeMillis()
            val result = landmarker?.detectForVideo(mpImage, timestamp)
            val processMs = SystemClock.uptimeMillis() - start

            val first = result?.landmarks()?.firstOrNull()
            val landmarks = first?.map { Offset(it.x(), it.y()) }
            val count = result?.landmarks()?.size ?: 0

            mainHandler.post {
                controller.onHandFrame(landmarks, count, processMs)
            }
        } catch (e: Throwable) {
            mainHandler.post {
                controller.onCameraError(e.message ?: "Tracking error")
            }
        } finally {
            if (rotatedBitmap != null && rotatedBitmap !== bitmap) {
                rotatedBitmap.recycle()
            }
            if (bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
            imageProxy.close()
        }
    }
}
