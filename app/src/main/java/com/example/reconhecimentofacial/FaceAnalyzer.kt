package com.example.reconhecimentofacial

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class FaceAnalyzer(
    private val classifier: FaceClassifier,
    private val onMatchFound: (String) -> Unit
) : ImageAnalysis.Analyzer {

    // Configura o ML Kit para focar em VELOCIDADE (não precisamos dos contornos perfeitos, só da posição)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        // Converte o frame da câmera para um Bitmap rotacionado corretamente
                        val frameBitmap = imageProxy.toBitmap().rotate(rotationDegrees.toFloat())

                        for (face in faces) {
                            val bounds = face.boundingBox

                            // Cria uma margem de segurança para o recorte não dar erro (sair da tela)
                            val safeRect = Rect(
                                Math.max(0, bounds.left),
                                Math.max(0, bounds.top),
                                Math.min(frameBitmap.width, bounds.right),
                                Math.min(frameBitmap.height, bounds.bottom)
                            )

                            // Se o quadrado for válido, recorta
                            if (safeRect.width() > 0 && safeRect.height() > 0) {
                                val faceCropped = Bitmap.createBitmap(
                                    frameBitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height()
                                )

                                // Pede para a IA converter o recorte numérico
                                val faceVector = classifier.recognizeFace(faceCropped)

                                // Procura esse vetor na lista de pessoas cadastradas (Seu PersonRepository)
                                val match = PersonRepository.findMatch(faceVector)

                                if (match != null) {
                                    // Se achou alguém parecido, chama o evento que vai piscar a tela de verde e chamar a URL
                                    onMatchFound(match.name)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                }
                .addOnCompleteListener {
                    // OBRIGATÓRIO: Libera o frame atual para a câmera mandar o próximo.
                    // Se não fechar, a câmera congela no primeiro frame!
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    // Função auxiliar para rotacionar a imagem da câmera e deixá-la em pé
    private fun Bitmap.rotate(degrees: Float): Bitmap {
        if (degrees == 0f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }
}