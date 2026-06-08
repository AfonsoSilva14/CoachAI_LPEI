package com.example.coachai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Classe responsável por desenhar o esqueleto da pose sobre uma imagem.
 */
class PoseDrawer {

    // Paint usado para desenhar os pontos dos landmarks
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
        isAntiAlias = true
    }

    // Paint usado para desenhar as linhas entre os landmarks
    private val linePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    /**
     * Desenha os landmarks e as ligações da pose numa cópia do Bitmap original.
     */
    fun drawSkeleton(
        original: Bitmap,
        landmarks: List<NormalizedLandmark>
    ): Bitmap {

        // Cria uma cópia editável da imagem original
        val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)

        // Canvas usado para desenhar sobre a imagem
        val canvas = Canvas(mutableBitmap)

        // Dimensões da imagem usadas para converter coordenadas normalizadas em píxeis
        val width = mutableBitmap.width.toFloat()
        val height = mutableBitmap.height.toFloat()

        // Ligações principais entre landmarks do modelo BlazePose/MediaPipe
        val connections = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 7,        // face esquerda
            0 to 4, 4 to 5, 5 to 6, 6 to 8,        // face direita
            9 to 10,                               // boca

            11 to 12,                              // ombros
            11 to 13, 13 to 15,                    // braço esquerdo
            12 to 14, 14 to 16,                    // braço direito

            15 to 17, 15 to 19, 15 to 21,          // mão esquerda
            16 to 18, 16 to 20, 16 to 22,          // mão direita

            11 to 23, 12 to 24, 23 to 24,          // tronco
            23 to 25, 25 to 27,                    // perna esquerda
            24 to 26, 26 to 28,                    // perna direita

            27 to 29, 29 to 31,                    // pé esquerdo
            28 to 30, 30 to 32                     // pé direito
        )

        // Desenha as linhas do esqueleto entre os landmarks definidos
        for ((start, end) in connections) {
            if (start < landmarks.size && end < landmarks.size) {
                val p1 = landmarks[start]
                val p2 = landmarks[end]

                val x1 = p1.x() * width
                val y1 = p1.y() * height
                val x2 = p2.x() * width
                val y2 = p2.y() * height

                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }

        // Desenha os pontos individuais correspondentes aos landmarks
        for (landmark in landmarks) {
            val x = landmark.x() * width
            val y = landmark.y() * height

            canvas.drawCircle(x, y, 8f, pointPaint)
        }

        // Devolve a imagem final com o esqueleto desenhado
        return mutableBitmap
    }
}