package com.example.coachai

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MLKitRealTimeActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: MLKitPoseOverlayView
    private lateinit var txtResult: TextView
    private lateinit var cameraExecutor: ExecutorService

    private var isProcessing = false
    private var lastTime = System.currentTimeMillis()
    private var frameCount = 0
    private var fps = 0

    private val poseDetector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()

        PoseDetection.getClient(options)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mlkit_realtime)

        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.poseOverlay)
        txtResult = findViewById(R.id.txtMlKitResult)

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

                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    poseDetector.process(image)
                        .addOnSuccessListener { pose ->
                            frameCount++

                            val now = System.currentTimeMillis()
                            if (now - lastTime >= 1000) {
                                fps = frameCount
                                frameCount = 0
                                lastTime = now
                            }

                            overlay.setPose(
                                pose = pose,
                                imageWidth = mediaImage.width,
                                imageHeight = mediaImage.height,
                                rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            )

                            val landmarksDetected = pose.allPoseLandmarks.size

                            txtResult.text =
                                "ML Kit Pose\nLandmarks detetados: $landmarksDetected\nFPS aproximado: $fps"
                        }
                        .addOnFailureListener {
                            txtResult.text = "Erro no ML Kit: ${it.message}"
                        }
                        .addOnCompleteListener {
                            isProcessing = false
                            imageProxy.close()
                        }
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
        poseDetector.close()
        cameraExecutor.shutdown()
    }
}