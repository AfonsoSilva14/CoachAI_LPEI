package com.example.coachai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

/**
 * Estrutura usada para devolver o resultado da deteção de pose.
 */
data class PoseDetectionResult(
    val poseCount: Int,
    val landmarks: List<NormalizedLandmark>?
)

/**
 * Classe responsável por carregar e executar o modelo MediaPipe Pose Landmarker.
 */
class PoseEstimator(context: Context) {

    // Instância do modelo de estimativa de pose
    private val poseLandmarker: PoseLandmarker

    init {
        // Define o ficheiro do modelo presente na pasta assets
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .build()

        // Configura o modelo para processar imagens individuais
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()

        // Cria o Pose Landmarker com as opções definidas
        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * Executa a deteção de pose sobre um Bitmap.
     */
    fun detect(bitmap: Bitmap): PoseDetectionResult {

        // Garante que a imagem está no formato ARGB_8888
        val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        // Converte o Bitmap para o formato de imagem utilizado pelo MediaPipe
        val mpImage = BitmapImageBuilder(argbBitmap).build()

        // Executa a deteção de pose
        val result = poseLandmarker.detect(mpImage)

        // Obtém a lista de poses detetadas
        val poses = result.landmarks()

        // Seleciona apenas a primeira pose, caso exista
        val firstPose = if (poses.isNotEmpty()) poses[0] else null

        // Devolve o número de poses e os landmarks da primeira pose
        return PoseDetectionResult(
            poseCount = poses.size,
            landmarks = firstPose
        )
    }

    /**
     * Liberta os recursos associados ao modelo.
     */
    fun close() {
        poseLandmarker.close()
    }
}