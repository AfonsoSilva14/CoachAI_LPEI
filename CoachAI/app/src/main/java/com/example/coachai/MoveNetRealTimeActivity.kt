package com.example.coachai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
class MoveNetRealTimeActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: MoveNetOverlayView
    private lateinit var txtResult: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var processor: MoveNetProcessor

    private var isProcessing = false
    private var lastTime = System.currentTimeMillis()
    private var frameCount = 0
    private var fps = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movenet_realtime)

        previewView = findViewById(R.id.previewViewMoveNet)
        overlay = findViewById(R.id.moveNetOverlay)
        txtResult = findViewById(R.id.txtMoveNetResult)

        processor = MoveNetProcessor(this)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                1001
            )
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalyzer.setAnalyzer(cameraExecutor) { imageProxy ->

                if (isProcessing) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                isProcessing = true

                try {
                    val bitmap = imageProxy.toBitmap()

                    val rotatedBitmap = rotateBitmap(
                        bitmap,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    val keypoints = processor.detect(rotatedBitmap)

                    val detectedPoints = keypoints.count { it[2] > 0.1f }

                    val poseUtilizavel =
                        keypoints.count { it[2] > 0.3f } >= 14

                    frameCount++

                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 1000) {
                        fps = frameCount
                        frameCount = 0
                        lastTime = now
                    }

                    runOnUiThread {
                        overlay.setKeypoints(keypoints)

                        txtResult.text =
                            "MoveNet Real-Time\n" +
                                    "FPS: $fps\n" +
                                    "Keypoints: $detectedPoints\n" +
                                    "Pose utilizável: ${if (poseUtilizavel) "Sim" else "Não"}"
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        txtResult.text = "Erro MoveNet: ${e.message}"
                    }
                } finally {
                    isProcessing = false
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun rotateBitmap(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        processor.close()
        cameraExecutor.shutdown()
    }
}