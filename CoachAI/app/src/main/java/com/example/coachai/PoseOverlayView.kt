package com.example.coachai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark>? = null
    private var isCalibrated: Boolean = false

    private val rectPaint = Paint().apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        strokeWidth = 10f
    }

    private val linePaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    fun updatePose(newLandmarks: List<NormalizedLandmark>?, calibrated: Boolean) {
        landmarks = newLandmarks
        isCalibrated = calibrated
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

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

        canvas.drawRect(left, top, right, bottom, rectPaint)

        val lm = landmarks ?: return

        drawConnections(canvas, lm)
        drawPoints(canvas, lm)
    }

    private fun drawPoints(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        for (landmark in landmarks) {
            val x = landmark.x() * width
            val y = landmark.y() * height
            canvas.drawCircle(x, y, 10f, pointPaint)
        }
    }

    private fun drawConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
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