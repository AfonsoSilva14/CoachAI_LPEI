package com.example.coachai

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker

data class PoseDetectionResult(
    val poseCount: Int,
    val landmarks: List<NormalizedLandmark>?
)

class PoseEstimator(context: Context) {

    private val poseLandmarker: PoseLandmarker

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.IMAGE)
            .setNumPoses(1)
            .build()

        poseLandmarker = PoseLandmarker.createFromOptions(context, options)
    }

    fun detect(bitmap: Bitmap): PoseDetectionResult {
        val argbBitmap = if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        val mpImage = BitmapImageBuilder(argbBitmap).build()
        val result = poseLandmarker.detect(mpImage)

        val poses = result.landmarks()
        val firstPose = if (poses.isNotEmpty()) poses[0] else null

        return PoseDetectionResult(
            poseCount = poses.size,
            landmarks = firstPose
        )
    }

    fun close() {
        poseLandmarker.close()
    }
}