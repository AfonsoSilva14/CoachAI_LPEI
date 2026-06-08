package com.example.coachai

import android.graphics.Bitmap
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * Objeto responsável por analisar a imagem e os landmarks detetados
 * para gerar feedback em tempo real ao utilizador.
 */
object RealTimeFeedbackAnalyzer {

    /**
     * Analisa as condições da imagem e da pose corporal.
     *
     * @param bitmap Frame capturado pela câmara.
     * @param landmarks Lista de landmarks corporais detetados pelo MediaPipe.
     * @return Mensagem de feedback para o utilizador.
     */
    fun analyze(
        bitmap: Bitmap,
        landmarks: List<NormalizedLandmark>?
    ): String {

        // Verifica primeiro se a iluminação é adequada
        val brightnessFeedback = analyzeBrightness(bitmap)
        if (brightnessFeedback != null) return brightnessFeedback

        // Verifica se existem landmarks suficientes para analisar o corpo
        if (landmarks == null || landmarks.size < 33) {
            return "Corpo pouco visível — melhora o enquadramento"
        }

        // Verifica se os pontos principais do corpo estão visíveis
        val visibilityFeedback = analyzeVisibility(landmarks)
        if (visibilityFeedback != null) return visibilityFeedback

        // Verifica se o corpo está bem enquadrado na imagem
        val framingFeedback = analyzeFraming(landmarks)
        if (framingFeedback != null) return framingFeedback

        // Verifica se o utilizador está orientado corretamente para a câmara
        val positionFeedback = analyzeBodyPosition(landmarks)
        if (positionFeedback != null) return positionFeedback

        // Verifica se a distância à câmara é adequada
        val distanceFeedback = analyzeDistance(landmarks)
        if (distanceFeedback != null) return distanceFeedback

        // Caso todas as verificações estejam corretas
        return "Posição correta"
    }

    /**
     * Analisa a luminosidade média do frame.
     */
    private fun analyzeBrightness(bitmap: Bitmap): String? {

        // Reduz o tamanho da imagem para tornar a análise mais leve
        val resized = Bitmap.createScaledBitmap(bitmap, 64, 64, false)

        var totalBrightness = 0L
        var veryBrightPixels = 0
        val totalPixels = resized.width * resized.height

        // Percorre todos os píxeis da imagem reduzida
        for (x in 0 until resized.width) {
            for (y in 0 until resized.height) {

                // Obtém os valores RGB do píxel
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                // Calcula uma aproximação simples do brilho
                val brightness = (r + g + b) / 3

                totalBrightness += brightness

                // Conta píxeis com luminosidade demasiado elevada
                if (brightness > 240) veryBrightPixels++
            }
        }

        // Calcula o brilho médio e a percentagem de píxeis muito claros
        val averageBrightness = totalBrightness / totalPixels
        val brightRatio = veryBrightPixels.toFloat() / totalPixels.toFloat()

        // Devolve feedback caso a iluminação esteja inadequada
        return when {
            averageBrightness < 45 -> "Ambiente demasiado escuro — melhora a iluminação"
            averageBrightness < 80 -> "Melhora a iluminação"
            averageBrightness > 210 || brightRatio > 0.30f -> "Luz demasiado forte — reduz a iluminação"
            else -> null
        }
    }

    /**
     * Verifica se os landmarks corporais mais importantes estão visíveis.
     */
    private fun analyzeVisibility(landmarks: List<NormalizedLandmark>): String? {

        // Índices dos landmarks principais usados na análise
        val importantPoints = listOf(
            0, 11, 12, 13, 14, 15, 16, 23, 24, 27, 28
        )

        // Conta quantos pontos principais têm visibilidade suficiente
        val visibleCount = importantPoints.count { index ->
            landmarks[index].visibility().orElse(0f) > 0.5f
        }

        // Caso existam poucos pontos visíveis, o corpo pode estar mal enquadrado
        return if (visibleCount < 6) {
            "Corpo pouco visível — melhora o enquadramento"
        } else {
            null
        }
    }

    /**
     * Verifica se os principais pontos corporais estão dentro dos limites da imagem.
     */
    private fun analyzeFraming(landmarks: List<NormalizedLandmark>): String? {

        // Landmarks usados para validar o enquadramento do corpo
        val importantPoints = listOf(
            0, 11, 12, 13, 14, 15, 16, 23, 24, 27, 28
        )

        // Verifica se algum ponto importante está demasiado perto das margens
        for (index in importantPoints) {
            val lm = landmarks[index]

            if (lm.x() < 0.03f || lm.x() > 0.97f ||
                lm.y() < 0.03f || lm.y() > 0.97f
            ) {
                return "Enquadra melhor o corpo"
            }
        }

        return null
    }

    /**
     * Analisa a orientação do corpo relativamente à câmara.
     */
    private fun analyzeBodyPosition(landmarks: List<NormalizedLandmark>): String? {

        // Landmarks dos ombros
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        // Landmarks da anca
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        // Calcula a largura aparente dos ombros e da anca
        val shoulderDistance = abs(leftShoulder.x() - rightShoulder.x())
        val hipDistance = abs(leftHip.x() - rightHip.x())

        // Verifica se o corpo aparenta estar demasiado estreito na imagem
        val bodyWidthTooNarrow =
            shoulderDistance < 0.13f &&
                    hipDistance < 0.10f

        if (bodyWidthTooNarrow) {
            return "Coloca-te de frente para a câmara"
        }

        // Verifica se a orientação dos ombros e da anca corresponde a uma posição frontal
        val facingFront =
            leftShoulder.x() > rightShoulder.x() &&
                    leftHip.x() > rightHip.x()

        if (!facingFront) {
            return "Vira-te de frente para a câmara"
        }

        return null
    }

    /**
     * Verifica se o utilizador está a uma distância adequada da câmara.
     */
    private fun analyzeDistance(landmarks: List<NormalizedLandmark>): String? {

        // Landmarks usados para estimar a altura visível do corpo
        val nose = landmarks[0]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        // Estima a altura do corpo na imagem com base no nariz e tornozelos
        val bodyHeight = maxOf(leftAnkle.y(), rightAnkle.y()) - nose.y()

        // Avalia se o utilizador está demasiado perto ou demasiado longe
        return when {
            bodyHeight < 0.45f -> "Ajusta a distância à câmara"
            bodyHeight > 0.95f -> "Ajusta a distância à câmara"
            else -> null
        }
    }
}