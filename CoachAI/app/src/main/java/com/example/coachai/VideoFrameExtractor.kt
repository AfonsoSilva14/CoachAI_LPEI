package com.example.coachai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

class VideoFrameExtractor(private val context: Context) {

    fun extractFramesEachSecond(videoUri: Uri): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        val frames = mutableListOf<Bitmap>()

        try {
            retriever.setDataSource(context, videoUri)

            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

            var currentMs = 0L
            while (currentMs < durationMs) {
                val bitmap = retriever.getFrameAtTime(
                    currentMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                if (bitmap != null) {
                    frames.add(bitmap)
                }
                currentMs += 250
            }
        } finally {
            retriever.release()
        }

        return frames
    }
}