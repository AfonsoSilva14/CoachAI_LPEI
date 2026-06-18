package com.example.coachai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class MoveNetOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var keypoints: Array<FloatArray>? = null

    private val pointPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.MAGENTA
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val connections = listOf(
        5 to 6,
        5 to 7,
        7 to 9,
        6 to 8,
        8 to 10,
        5 to 11,
        6 to 12,
        11 to 12,
        11 to 13,
        13 to 15,
        12 to 14,
        14 to 16
    )

    fun setKeypoints(keypoints: Array<FloatArray>) {
        this.keypoints = keypoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val points = keypoints ?: return
        val threshold = 0.1f

        for ((startIndex, endIndex) in connections) {
            val start = points[startIndex]
            val end = points[endIndex]

            if (start[2] > threshold && end[2] > threshold) {
                canvas.drawLine(
                    start[1] * width,
                    start[0] * height,
                    end[1] * width,
                    end[0] * height,
                    linePaint
                )
            }
        }

        for (point in points) {
            if (point[2] > threshold) {
                canvas.drawCircle(
                    point[1] * width,
                    point[0] * height,
                    6f,
                    pointPaint
                )
            }
        }
    }
}