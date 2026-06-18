package com.example.coachai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MoveNetProcessor(context: Context) {

    private val interpreter: Interpreter

    init {
        val model = context.assets.open("movenet_lightning.tflite").readBytes()

        val buffer = ByteBuffer.allocateDirect(model.size)
        buffer.order(ByteOrder.nativeOrder())
        buffer.put(model)
        buffer.rewind()

        interpreter = Interpreter(buffer)
    }

    fun detect(bitmap: Bitmap): Array<FloatArray> {
        val inputSize = 192

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val input = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        input.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            input.putInt((pixel shr 16) and 0xFF)
            input.putInt((pixel shr 8) and 0xFF)
            input.putInt(pixel and 0xFF)
        }

        input.rewind()

        val output = Array(1) {
            Array(1) {
                Array(17) {
                    FloatArray(3)
                }
            }
        }

        interpreter.run(input, output)

        return output[0][0]
    }

    fun close() {
        interpreter.close()
    }
}