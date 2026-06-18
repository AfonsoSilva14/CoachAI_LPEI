package com.example.coachai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Size
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    @OptIn(ExperimentalGetImage::class)
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

                val mediaImage = imageProxy.image

                if (mediaImage != null) {
                    isProcessing = true

                    val bitmap = imageProxy.toBitmap()

                    val keypoints = processor.detect(bitmap)

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
                            "MoveNet Real-Time\nFPS: $fps\nKeypoints: $detectedPoints\nPose utilizável: ${if (poseUtilizavel) "Sim" else "Não"}"
                    }

                    isProcessing = false
                    imageProxy.close()
                } else {
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

    override fun onDestroy() {
        super.onDestroy()
        processor.close()
        cameraExecutor.shutdown()
    }
}