package com.example.coachai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark

class MLKitPoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pose: Pose? = null
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1
    private var rotationDegrees: Int = 0

    private val pointPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    fun setPose(
        pose: Pose,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ) {
        this.pose = pose
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.rotationDegrees = rotationDegrees
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentPose = pose ?: return

        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)

        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)

        drawLine(canvas, currentPose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)

        drawLine(canvas, currentPose, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP)

        drawLine(canvas, currentPose, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)
        drawLine(canvas, currentPose, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)

        drawLine(canvas, currentPose, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)
        drawLine(canvas, currentPose, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)

        for (landmark in currentPose.allPoseLandmarks) {
            val x = translateX(landmark.position.x)
            val y = translateY(landmark.position.y)
            canvas.drawCircle(x, y, 6f, pointPaint)
        }
    }

    private fun drawLine(
        canvas: Canvas,
        pose: Pose,
        startType: Int,
        endType: Int
    ) {
        val start = pose.getPoseLandmark(startType)
        val end = pose.getPoseLandmark(endType)

        if (start != null && end != null) {
            canvas.drawLine(
                translateX(start.position.x),
                translateY(start.position.y),
                translateX(end.position.x),
                translateY(end.position.y),
                linePaint
            )
        }
    }

    private fun translateX(x: Float): Float {
        return width - (x * width / imageWidth)
    }

    private fun translateY(y: Float): Float {
        return y * height / imageHeight
    }
}