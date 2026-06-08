package com.example.coachai

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Classe responsável pela extração de frames de um vídeo.
 * Os frames são posteriormente utilizados para análise de pose
 * através do modelo MediaPipe Pose Landmarker.
 */
class VideoFrameExtractor(private val context: Context) {

    /**
     * Extrai frames do vídeo em intervalos regulares.
     *
     * @param videoUri URI do vídeo selecionado pelo utilizador.
     * @return Lista de frames extraídos sob a forma de Bitmap.
     */
    fun extractFramesEachSecond(videoUri: Uri): List<Bitmap> {

        // Objeto utilizado para aceder aos metadados e frames do vídeo
        val retriever = MediaMetadataRetriever()

        // Lista onde serão armazenados os frames extraídos
        val frames = mutableListOf<Bitmap>()

        try {
            // Define o vídeo que será processado
            retriever.setDataSource(context, videoUri)

            // Obtém a duração total do vídeo em milissegundos
            val durationMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L

            // Variável utilizada para percorrer o vídeo
            var currentMs = 0L

            // Extrai frames ao longo de toda a duração do vídeo
            while (currentMs < durationMs) {

                // Obtém o frame mais próximo do instante pretendido
                val bitmap = retriever.getFrameAtTime(
                    currentMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                // Adiciona o frame à lista caso seja válido
                if (bitmap != null) {
                    frames.add(bitmap)
                }

                // Avança 250 ms para o próximo frame
                currentMs += 250
            }

        } finally {
            // Liberta os recursos utilizados pelo retriever
            retriever.release()
        }

        // Devolve todos os frames extraídos
        return frames
    }
}