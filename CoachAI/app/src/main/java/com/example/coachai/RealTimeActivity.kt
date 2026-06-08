package com.example.coachai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Activity responsável pela análise de pose em tempo real através da câmara.
 */
class RealTimeActivity : AppCompatActivity() {

    // Vista onde é apresentada a pré-visualização da câmara
    private lateinit var previewView: PreviewView

    // Vista personalizada onde são desenhados os landmarks da pose
    private lateinit var poseOverlay: PoseOverlayView

    // Texto utilizado para apresentar feedback ao utilizador
    private lateinit var txtStatus: TextView

    // Executor usado para processar os frames da câmara em segundo plano
    private lateinit var cameraExecutor: ExecutorService

    // Objeto responsável pela deteção da pose com MediaPipe
    private lateinit var estimator: PoseEstimator

    // Indica se o corpo já foi detetado corretamente pelo menos uma vez
    private var calibratedOnce = false

    // Contador de frames consecutivos em que o corpo não foi detetado corretamente
    private var missedFrames = 0

    companion object {
        // Código usado para identificar o pedido de permissão da câmara
        private const val CAMERA_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Associa esta activity ao layout de análise em tempo real
        setContentView(R.layout.activity_realtime)

        // Liga as variáveis aos elementos definidos no ficheiro XML
        previewView = findViewById(R.id.previewView)
        poseOverlay = findViewById(R.id.poseOverlay)
        txtStatus = findViewById(R.id.txtStatus)

        // Inicializa o estimador de pose
        estimator = PoseEstimator(this)

        // Cria um executor com uma única thread para análise dos frames
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Verifica se a aplicação já tem permissão para usar a câmara
        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    /**
     * Verifica se a permissão da câmara foi concedida.
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Recebe o resultado do pedido de permissão da câmara.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Se a permissão foi aceite, inicia a câmara
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            // Caso contrário, informa o utilizador
            txtStatus.text = "Permissão da câmara negada"
        }
    }

    /**
     * Inicializa a câmara e configura a pré-visualização e análise de imagem.
     */
    private fun startCamera() {

        // Obtém o fornecedor da câmara associado ao ciclo de vida da activity
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Configura a pré-visualização da câmara
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Configura a análise dos frames capturados pela câmara
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processFrame(imageProxy)
                    }
                }

            // Seleciona a câmara traseira do dispositivo
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Remove ligações anteriores antes de associar novas configurações
            cameraProvider.unbindAll()

            // Associa a câmara ao ciclo de vida da activity
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Processa cada frame recebido da câmara.
     */
    private fun processFrame(imageProxy: ImageProxy) {
        try {
            // Converte o frame da câmara para Bitmap
            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                return
            }

            // Garante que o Bitmap está num formato seguro para processamento
            val safeBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            // Executa a deteção de pose no frame atual
            val detection = estimator.detect(safeBitmap)

            // Obtém os landmarks resultantes da deteção
            val landmarks = detection.landmarks

            // Verifica se o corpo está suficientemente visível
            val bodyVisible = isBodyVisibleEnough(landmarks)

            // Atualiza o estado de calibração consoante a visibilidade do corpo
            if (bodyVisible) {
                calibratedOnce = true
                missedFrames = 0
            } else {
                missedFrames++
            }

            // Mantém a pose calibrada durante alguns frames mesmo com pequenas falhas
            val calibrated = calibratedOnce && missedFrames < 15

            // Gera a mensagem de feedback com base na pose e na imagem
            val feedbackMessage = RealTimeFeedbackAnalyzer.analyze(
                safeBitmap,
                landmarks
            )

            // Atualiza a interface no thread principal
            runOnUiThread {
                txtStatus.text = feedbackMessage
                poseOverlay.updatePose(landmarks, calibrated)
            }

        } catch (e: Exception) {
            // Apresenta erro caso ocorra alguma falha no processamento
            runOnUiThread {
                txtStatus.text = "Erro no processamento: ${e.message}"
            }
        } finally {
            // Fecha sempre o frame para evitar bloqueios na câmara
            imageProxy.close()
        }
    }

    /**
     * Verifica se os principais pontos do corpo estão visíveis e dentro da imagem.
     */
    private fun isBodyVisibleEnough(
        landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>?
    ): Boolean {

        // Caso não existam landmarks suficientes, considera que o corpo não está visível
        if (landmarks == null || landmarks.size < 25) return false

        // Pontos principais usados na validação da visibilidade corporal
        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        // Verifica se nariz, ombros e anca estão dentro de zonas aceitáveis da imagem
        return nose.x() in 0.05f..0.95f &&
                nose.y() in 0.02f..0.45f &&
                leftShoulder.x() in 0.02f..0.98f &&
                rightShoulder.x() in 0.02f..0.98f &&
                leftHip.x() in 0.02f..0.98f &&
                rightHip.x() in 0.02f..0.98f
    }

    /**
     * Converte um ImageProxy da câmara para Bitmap.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {

        // Obtém os planos Y, U e V da imagem capturada
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        // Calcula o tamanho de cada plano
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // Cria um array no formato NV21
        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copia os dados YUV para o array NV21
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Cria uma imagem YUV a partir dos dados da câmara
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        // Converte a imagem YUV para JPEG em memória
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )

        // Converte os bytes JPEG para Bitmap
        val imageBytes = out.toByteArray()
        var bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Obtém a rotação da imagem capturada pela câmara
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        // Corrige a orientação do Bitmap caso seja necessário
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

        // Devolve o Bitmap final corrigido
        return bitmap
    }

    override fun onDestroy() {
        super.onDestroy()

        // Liberta os recursos usados pelo modelo de pose
        estimator.close()

        // Encerra o executor da câmara
        cameraExecutor.shutdown()
    }
}