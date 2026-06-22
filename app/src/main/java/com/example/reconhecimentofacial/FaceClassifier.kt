package com.example.reconhecimentofacial

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class FaceClassifier(assetManager: AssetManager, modelPath: String = "mobile_face_net.tflite") {

    private var interpreter: Interpreter

    // Parâmetros do MobileFaceNet
    private val inputSize = 112
    private val outputSize = 192

    init {
        val model = loadModelFile(assetManager, modelPath)
        // Usamos 4 threads para acelerar o processamento no celular
        val options = Interpreter.Options().apply {
            setNumThreads(4)
        }
        interpreter = Interpreter(model, options)
    }

    // Carrega o arquivo da pasta assets para a memória
    private fun loadModelFile(assetManager: AssetManager, modelPath: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // Transforma a imagem em números
    fun recognizeFace(bitmap: Bitmap): FloatArray {
        // 1. Força a imagem a ter 112x112 pixels
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        // 2. Cria um espaço na memória para os pixels (112 * 112 * 3 cores * 4 bytes)
        val input = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }

        // 3. Lê os pixels reais da imagem
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // 4. Normaliza os pixels (Fórmula padrão do TFLite)
        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF)
            val g = (pixel shr 8 and 0xFF)
            val b = (pixel and 0xFF)

            input.putFloat((r - 127.5f) / 128f)
            input.putFloat((g - 127.5f) / 128f)
            input.putFloat((b - 127.5f) / 128f)
        }

        // 5. Prepara onde a IA vai devolver o resultado e executa
        val output = Array(1) { FloatArray(outputSize) }
        interpreter.run(input, output)

        return output[0] // Devolve a Array com os 192 números que identificam este rosto
    }
}