package com.example.coachai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

class MoveNetPoseDrawer {

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

    fun drawSkeleton(bitmap: Bitmap, keypoints: Array<FloatArray>): Bitmap {
        val canvas = Canvas(bitmap)
        val width = bitmap.width
        val height = bitmap.height

        for ((startIndex, endIndex) in connections) {
            val start = keypoints[startIndex]
            val end = keypoints[endIndex]

            if (start[2] > 0.1f && end[2] > 0.1f) {
                canvas.drawLine(
                    start[1] * width,
                    start[0] * height,
                    end[1] * width,
                    end[0] * height,
                    linePaint
                )
            }
        }

        for (point in keypoints) {
            if (point[2] > 0.1f) {
                canvas.drawCircle(
                    point[1] * width,
                    point[0] * height,
                    6f,
                    pointPaint
                )
            }
        }

        return bitmap
    }
}