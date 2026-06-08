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

/**
 * Activity principal da aplicação.
 * Permite selecionar vídeos para análise ou iniciar a análise em tempo real.
 */
class MainActivity : AppCompatActivity() {

    // Texto onde são apresentados os resultados da análise
    private lateinit var txtResult: TextView

    // Imagem usada para mostrar os frames processados
    private lateinit var imgPreview: ImageView

    // Launcher responsável por abrir o seletor de vídeos do dispositivo
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

        // Associa esta activity ao layout principal
        setContentView(R.layout.activity_main)

        // Liga as variáveis aos elementos definidos no layout XML
        val btnPickVideo = findViewById<Button>(R.id.btnPickVideo)
        val btnRealTime = findViewById<Button>(R.id.btnRealTime)
        txtResult = findViewById(R.id.txtResult)
        imgPreview = findViewById(R.id.imgPreview)

        // Abre o seletor de ficheiros para escolher um vídeo
        btnPickVideo.setOnClickListener {
            pickVideoLauncher.launch("video/*")
        }

        // Abre a activity de análise em tempo real
        btnRealTime.setOnClickListener {
            startActivity(Intent(this, RealTimeActivity::class.java))
        }
    }

    /**
     * Processa o vídeo selecionado pelo utilizador.
     */
    private fun processVideo(uri: Uri) {
        Thread {
            // Extrai frames do vídeo selecionado
            val extractor = VideoFrameExtractor(this)
            val frames = extractor.extractFramesEachSecond(uri)

            // Caso não seja possível extrair frames, apresenta erro
            if (frames.isEmpty()) {
                runOnUiThread {
                    txtResult.text = "Erro ao extrair frames"
                }
                return@Thread
            }

            // Inicializa o modelo de pose e o desenhador do esqueleto
            val estimator = PoseEstimator(this)
            val drawer = PoseDrawer()

            // Lista onde serão guardados os frames já processados
            val processedFrames = mutableListOf<Bitmap>()

            // StringBuilder usado para construir o texto dos resultados
            val sb = StringBuilder()
            sb.append("Frames: ${frames.size}\n\n")

            // Analisa cada frame individualmente
            frames.forEachIndexed { index, bitmap ->

                // Garante que o frame está no formato adequado para processamento
                val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                // Executa a deteção de pose no frame
                val detection = estimator.detect(safeBitmap)

                // Desenha o esqueleto caso tenham sido detetados landmarks
                val outputBitmap = if (detection.landmarks != null) {
                    drawer.drawSkeleton(safeBitmap, detection.landmarks)
                } else {
                    safeBitmap
                }

                // Guarda o frame processado
                processedFrames.add(outputBitmap)

                // Regista o número de poses detetadas neste frame
                sb.append("Frame ${index + 1}: ${detection.poseCount} pose(s)\n")
            }

            // Liberta os recursos do modelo após o processamento
            estimator.close()

            // Atualiza a interface com os resultados e mostra os frames processados
            runOnUiThread {
                txtResult.text = sb.toString()
                playProcessedFrames(processedFrames)
            }
        }.start()
    }

    /**
     * Reproduz os frames processados no ImageView.
     */
    private fun playProcessedFrames(frames: List<Bitmap>) {
        if (frames.isEmpty()) return

        // Handler associado ao thread principal para atualizar a interface
        val handler = Handler(Looper.getMainLooper())

        var index = 0

        // Runnable responsável por apresentar os frames de forma sequencial
        val runnable = object : Runnable {
            override fun run() {
                imgPreview.setImageBitmap(frames[index])
                index++

                // Continua a reprodução enquanto existirem frames
                if (index < frames.size) {
                    handler.postDelayed(this, 250)
                }
            }
        }

        // Inicia a reprodução dos frames
        handler.post(runnable)
    }

    /**
     * Obtém o nome do ficheiro selecionado a partir do respetivo URI.
     */
    private fun getFileName(uri: Uri): String {
        var name = "video"

        // Consulta os metadados do ficheiro para obter o nome original
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)

            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }

        return name
    }
}