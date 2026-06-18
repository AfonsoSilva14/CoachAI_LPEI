package com.example.coachai

import android.graphics.Bitmap
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

object RealTimeFeedbackAnalyzer {

    fun analyze(
        bitmap: Bitmap,
        landmarks: List<NormalizedLandmark>?
    ): String {

        val brightnessFeedback = analyzeBrightness(bitmap)
        if (brightnessFeedback != null) return brightnessFeedback

        if (landmarks == null || landmarks.size < 33) {
            return "Corpo pouco visível — melhora o enquadramento"
        }

        val visibilityFeedback = analyzeVisibility(landmarks)
        if (visibilityFeedback != null) return visibilityFeedback

        val framingFeedback = analyzeFraming(landmarks)
        if (framingFeedback != null) return framingFeedback

        val positionFeedback = analyzeBodyPosition(landmarks)
        if (positionFeedback != null) return positionFeedback

        val distanceFeedback = analyzeDistance(landmarks)
        if (distanceFeedback != null) return distanceFeedback

        return "Posição correta"
    }

    private fun analyzeBrightness(bitmap: Bitmap): String? {
        val resized = Bitmap.createScaledBitmap(bitmap, 64, 64, false)
        var totalBrightness = 0L
        var veryBrightPixels = 0
        val totalPixels = resized.width * resized.height

        for (x in 0 until resized.width) {
            for (y in 0 until resized.height) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff
                val brightness = (r + g + b) / 3
                totalBrightness += brightness
                if (brightness > 240) veryBrightPixels++
            }
        }

        val averageBrightness = totalBrightness / totalPixels
        val brightRatio = veryBrightPixels.toFloat() / totalPixels.toFloat()

        return when {
            averageBrightness < 45 -> "Ambiente demasiado escuro — melhora a iluminação"
            averageBrightness < 80 -> "Melhora a iluminação"
            averageBrightness > 210 || brightRatio > 0.30f -> "Luz demasiado forte — reduz a iluminação"
            else -> null
        }
    }

    private fun analyzeVisibility(landmarks: List<NormalizedLandmark>): String? {
        val importantPoints = listOf(
            0, 11, 12, 13, 14, 15, 16, 23, 24, 27, 28
        )

        val visibleCount = importantPoints.count { index ->
            landmarks[index].visibility().orElse(0f) > 0.5f
        }

        return if (visibleCount < 6) {
            "Corpo pouco visível — melhora o enquadramento"
        } else {
            null
        }
    }

    private fun analyzeFraming(landmarks: List<NormalizedLandmark>): String? {
        val importantPoints = listOf(
            0, 11, 12, 13, 14, 15, 16, 23, 24, 27, 28
        )

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

    private fun analyzeBodyPosition(landmarks: List<NormalizedLandmark>): String? {

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]

        val leftHip = landmarks[23]
        val rightHip = landmarks[24]

        val shoulderDistance = abs(leftShoulder.x() - rightShoulder.x())
        val hipDistance = abs(leftHip.x() - rightHip.x())

        val bodyWidthTooNarrow =
            shoulderDistance < 0.13f &&
                    hipDistance < 0.10f

        if (bodyWidthTooNarrow) {
            return "Coloca-te de frente para a câmara"
        }

        val facingFront =
            leftShoulder.x() > rightShoulder.x() &&
                    leftHip.x() > rightHip.x()

        if (!facingFront) {
            return "Vira-te de frente para a câmara"
        }

        return null
    }

    private fun analyzeDistance(landmarks: List<NormalizedLandmark>): String? {
        val nose = landmarks[0]
        val leftAnkle = landmarks[27]
        val rightAnkle = landmarks[28]

        val bodyHeight = maxOf(leftAnkle.y(), rightAnkle.y()) - nose.y()

        return when {
            bodyHeight < 0.45f -> "Ajusta a distância à câmara"
            bodyHeight > 0.95f -> "Ajusta a distância à câmara"
            else -> null
        }
    }
}