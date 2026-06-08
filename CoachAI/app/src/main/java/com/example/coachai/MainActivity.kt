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

            frames.forEachIndexed { index, bitmap ->
                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val detection = estimator.detect(safeBitmap)

                val outputBitmap = if (detection.landmarks != null) {
                    drawer.drawSkeleton(safeBitmap, detection.landmarks)
                } else {
                    safeBitmap
                }

                processedFrames.add(outputBitmap)
                sb.append("Frame ${index + 1}: ${detection.poseCount} pose(s)\n")
            }

            estimator.close()

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