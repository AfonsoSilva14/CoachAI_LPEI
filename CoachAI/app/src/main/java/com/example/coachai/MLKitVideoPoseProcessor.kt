package com.example.coachai

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

class MLKitVideoPoseProcessor {

    private val detector by lazy {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()

        PoseDetection.getClient(options)
    }

    fun detectPose(bitmap: Bitmap): Pose? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            Tasks.await(detector.process(image))
        } catch (e: Exception) {
            null
        }
    }

    fun close() {
        detector.close()
    }
}