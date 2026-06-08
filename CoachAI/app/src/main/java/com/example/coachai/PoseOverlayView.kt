package com.example.coachai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * View personalizada responsável por desenhar a pose detetada sobre a imagem da câmara.
 */
class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Lista de landmarks corporais recebidos do modelo de pose
    private var landmarks: List<NormalizedLandmark>? = null

    // Indica se o corpo já foi corretamente enquadrado/calibrado
    private var isCalibrated: Boolean = false

    // Paint utilizado para desenhar o retângulo de enquadramento
    private val rectPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    // Paint utilizado para desenhar os pontos da pose
    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 10f
    }

    // Paint utilizado para desenhar as ligações entre pontos da pose
    private val linePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    /**
     * Atualiza os landmarks da pose e o estado de calibração.
     */
    fun updatePose(newLandmarks: List<NormalizedLandmark>?, calibrated: Boolean) {
        landmarks = newLandmarks
        isCalibrated = calibrated

        // Força a view a ser redesenhada com os novos dados
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Define os limites do retângulo de enquadramento
        val left = width * 0.1f
        val top = height * 0.1f
        val right = width * 0.9f
        val bottom = height * 0.9f

        // Retângulo sempre visível:
        // amarelo = posicionar corpo
        // verde = calibrado
        rectPaint.color = if (isCalibrated) Color.GREEN else Color.YELLOW
        rectPaint.strokeWidth = if (isCalibrated) 12f else 8f
        rectPaint.alpha = if (isCalibrated) 255 else 180

        // Desenha o retângulo de referência no ecrã
        canvas.drawRect(left, top, right, bottom, rectPaint)

        // Se ainda não existirem landmarks, termina o desenho
        val lm = landmarks ?: return

        // Desenha o esqueleto e os pontos corporais
        drawConnections(canvas, lm)
        drawPoints(canvas, lm)
    }

    /**
     * Desenha os pontos individuais correspondentes aos landmarks.
     */
    private fun drawPoints(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        for (landmark in landmarks) {
            val x = landmark.x() * width
            val y = landmark.y() * height

            canvas.drawCircle(x, y, 10f, pointPaint)
        }
    }

    /**
     * Desenha as ligações principais entre landmarks corporais.
     */
    private fun drawConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>) {

        // Pares de índices que representam as ligações do esqueleto
        val connections = listOf(
            11 to 12,
            11 to 13,
            13 to 15,
            12 to 14,
            14 to 16,
            11 to 23,
            12 to 24,
            23 to 24,
            23 to 25,
            25 to 27,
            24 to 26,
            26 to 28
        )

        // Percorre todas as ligações e desenha uma linha entre os respetivos pontos
        for ((start, end) in connections) {
            if (start < landmarks.size && end < landmarks.size) {
                val x1 = landmarks[start].x() * width
                val y1 = landmarks[start].y() * height
                val x2 = landmarks[end].x() * width
                val y2 = landmarks[end].y() * height

                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }
    }
}