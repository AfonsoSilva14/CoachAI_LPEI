package com.example.coachai

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var txtResult: TextView
    private lateinit var imgPreview: ImageView

    private val pickVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                txtResult.text = "Vídeo selecionado: ${getFileName(uri)}\nA processar vídeo..."
                processVideo(uri)
            } else {
                txtResult.text = "Nenhum vídeo selecionado"
            }
        }

    private val pickVideoMlKitLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                txtResult.text = "Vídeo selecionado ML Kit: ${getFileName(uri)}\nA processar vídeo..."
                processVideoMlKit(uri)
            } else {
                txtResult.text = "Nenhum vídeo selecionado"
            }
        }

    private val pickVideoMoveNetLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                txtResult.text = "Vídeo selecionado MoveNet: ${getFileName(uri)}\nA processar vídeo..."
                processVideoMoveNet(uri)
            } else {
                txtResult.text = "Nenhum vídeo selecionado"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnPickVideo = findViewById<Button>(R.id.btnPickVideo)
        val btnRealTime = findViewById<Button>(R.id.btnRealTime)
        txtResult = findViewById(R.id.txtResult)
        imgPreview = findViewById(R.id.imgPreview)

        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        btnRealTime.setOnClickListener {
            startActivity(Intent(this, RealTimeActivity::class.java))
        }

        findViewById<Button>(R.id.btnMlKitRealTime).setOnClickListener {
            val intent = Intent(this, MLKitRealTimeActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnMoveNetRealTime).setOnClickListener {
            startActivity(Intent(this, MoveNetRealTimeActivity::class.java))
        }

        val btnPickVideoMlKit = findViewById<Button>(R.id.btnPickVideoMlKit)

        btnPickVideoMlKit.setOnClickListener {
            pickVideoMlKitLauncher.launch("video/*")
        }



        val btnPickVideoMoveNet = findViewById<Button>(R.id.btnPickVideoMoveNet)

        btnPickVideoMoveNet.setOnClickListener {
            pickVideoMoveNetLauncher.launch("video/*")
        }
    }

    private fun processVideo(uri: Uri) {
        Thread {
            val extractor = VideoFrameExtractor(this)
            val frames = extractor.extractFramesEachSecond(uri)

            if (frames.isEmpty()) {
                runOnUiThread {
                    txtResult.text = "Erro ao extrair frames"
                }
                return@Thread
            }

            val estimator = PoseEstimator(this)
            val drawer = PoseDrawer()
            val processedFrames = mutableListOf<Bitmap>()

            val sb = StringBuilder()
            sb.append("Frames: ${frames.size}\n\n")

            val startTime = System.currentTimeMillis()
            var framesComPoseUtilizavel = 0

            frames.forEachIndexed { index, bitmap ->
                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val detection = estimator.detect(safeBitmap)

                if (detection.landmarks != null && detection.landmarks.size >= 14) {
                    framesComPoseUtilizavel++
                }

                val outputBitmap = if (detection.landmarks != null) {
                    drawer.drawSkeleton(safeBitmap, detection.landmarks)
                } else {
                    safeBitmap
                }

                processedFrames.add(outputBitmap)
                val keypointsDetectados = detection.landmarks?.size ?: 0
                sb.append("Frame ${index + 1}: $keypointsDetectados keypoints\n")
            }

            val endTime = System.currentTimeMillis()

            val totalTimeSeconds =
                (endTime - startTime) / 1000.0

            val fps =
                if (totalTimeSeconds > 0)
                    frames.size / totalTimeSeconds
                else
                    0.0

            val detectionRate =
                if (frames.isNotEmpty())
                    (framesComPoseUtilizavel.toDouble() / frames.size) * 100.0
                else
                    0.0

            sb.append("\nFPS Médio MediaPipe: %.2f".format(fps))
            sb.append("\nDetection Rate: %.2f%%".format(detectionRate))

            estimator.close()

            runOnUiThread {
                txtResult.text = sb.toString()
                playProcessedFrames(processedFrames)
            }
        }.start()
    }

    private fun processVideoMlKit(uri: Uri) {
        Thread {
            val extractor = VideoFrameExtractor(this)
            val frames = extractor.extractFramesEachSecond(uri)

            if (frames.isEmpty()) {
                runOnUiThread {
                    txtResult.text = "Erro ao extrair frames"
                }
                return@Thread
            }

            val processor = MLKitVideoPoseProcessor()
            val drawer = MLKitPoseBitmapDrawer()

            val processedFrames = mutableListOf<Bitmap>()
            val sb = StringBuilder()

            sb.append("Modelo: ML Kit Pose Detection\n")
            sb.append("Frames: ${frames.size}\n\n")

            val startTime = System.currentTimeMillis()

            var framesComPoseUtilizavel = 0

            frames.forEachIndexed { index, bitmap ->
                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                val pose = processor.detectPose(safeBitmap)

                val poseUtilizavel =
                    pose != null && pose.allPoseLandmarks.size >= 14

                if (poseUtilizavel) {
                    framesComPoseUtilizavel++
                }

                val outputBitmap = if (pose != null && pose.allPoseLandmarks.isNotEmpty()) {
                    drawer.drawSkeleton(safeBitmap, pose)
                } else {
                    safeBitmap
                }

                processedFrames.add(outputBitmap)

                sb.append("Frame ${index + 1}: ")
                sb.append("${pose?.allPoseLandmarks?.size ?: 0} keypoints\n")
            }

            val endTime = System.currentTimeMillis()

            val totalTimeSeconds =
                (endTime - startTime) / 1000.0

            val fps =
                if (totalTimeSeconds > 0)
                    frames.size / totalTimeSeconds
                else
                    0.0


            val detectionRate =
                if (frames.isNotEmpty())
                    (framesComPoseUtilizavel.toDouble() / frames.size) * 100.0
                else
                    0.0

            sb.append("\nFPS Médio ML Kit: %.2f".format(fps))

            sb.append("\nDetection Rate: %.2f%%".format(detectionRate))

            processor.close()

            runOnUiThread {
                txtResult.text = sb.toString()
                playProcessedFrames(processedFrames)
            }
        }.start()
    }


    private fun processVideoMoveNet(uri: Uri) {
        Thread {
            val extractor = VideoFrameExtractor(this)
            val frames = extractor.extractFramesEachSecond(uri)

            if (frames.isEmpty()) {
                runOnUiThread {
                    txtResult.text = "Erro ao extrair frames"
                }
                return@Thread
            }

            val processor = MoveNetProcessor(this)
            val drawer = MoveNetPoseDrawer()

            val processedFrames = mutableListOf<Bitmap>()
            val sb = StringBuilder()

            sb.append("Modelo: MoveNet Lightning\n")
            sb.append("Frames: ${frames.size}\n\n")

            val startTime = System.currentTimeMillis()
            var framesComPoseUtilizavel = 0

            frames.forEachIndexed { index, bitmap ->

                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                val keypoints = processor.detect(safeBitmap)

                val poseUtilizavel =
                    keypoints.count { it[2] > 0.3f } >= 14

                if (poseUtilizavel) {
                    framesComPoseUtilizavel++
                }

                val outputBitmap =
                    drawer.drawSkeleton(safeBitmap, keypoints)

                processedFrames.add(outputBitmap)

                val detectedPoints =
                    keypoints.count { it[2] > 0.1f }

                sb.append("Frame ${index + 1}: ")
                sb.append("$detectedPoints keypoints\n")
            }

            val endTime = System.currentTimeMillis()

            val totalTimeSeconds =
                (endTime - startTime) / 1000.0

            val fps =
                if (totalTimeSeconds > 0)
                    frames.size / totalTimeSeconds
                else
                    0.0

            val detectionRate =
                if (frames.isNotEmpty())
                    (framesComPoseUtilizavel.toDouble() / frames.size) * 100.0
                else
                    0.0

            sb.append("\nFPS Médio MoveNet: %.2f".format(fps))
            sb.append("\nDetection Rate: %.2f%%".format(detectionRate))

            processor.close()

            runOnUiThread {
                txtResult.text = sb.toString()
                playProcessedFrames(processedFrames)
            }

        }.start()
    }

    private fun playProcessedFrames(frames: List<Bitmap>) {
        if (frames.isEmpty()) return

        val handler = Handler(Looper.getMainLooper())
        var index = 0

        val runnable = object : Runnable {
            override fun run() {
                imgPreview.setImageBitmap(frames[index])
                index++

                if (index < frames.size) {
                    handler.postDelayed(this, 250)
                }
            }
        }

        handler.post(runnable)
    }

    private fun getFileName(uri: Uri): String {
        var name = "video"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}