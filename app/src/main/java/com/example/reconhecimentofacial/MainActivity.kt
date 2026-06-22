package com.example.reconhecimentofacial

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import okhttp3.*
import java.io.IOException
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private var faceClassifier: FaceClassifier? = null

    // Solicitador de permissão de câmera nativo do Android
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupApp()
        } else {
            Toast.makeText(this, "Permissão de câmera negada. O app não funcionará.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verifica se já tem permissão da câmera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupApp()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupApp() {
        // Inicializa o TFLite usando o arquivo da pasta assets
        try {
            faceClassifier = FaceClassifier(assets, "mobile_face_net.tflite")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Erro ao carregar o modelo de IA.", Toast.LENGTH_LONG).show()
        }

        setContent {
            MaterialTheme {
                AppNavigator(faceClassifier)
            }
        }
    }
}

// --- NAVEGAÇÃO ENTRE TELAS ---
@Composable
fun AppNavigator(classifier: FaceClassifier?) {
    var currentScreen by remember { mutableStateOf("recognition") }

    if (currentScreen == "recognition") {
        RecognitionScreen(
            classifier = classifier,
            onNavigateToRegister = { currentScreen = "register" }
        )
    } else {
        RegisterScreen(
            classifier = classifier,
            onBackToRecognition = { currentScreen = "recognition" }
        )
    }
}

// --- TELA PRINCIPAL (RECONHECIMENTO & BORDA VERDE) ---
@Composable
fun RecognitionScreen(classifier: FaceClassifier?, onNavigateToRegister: () -> Unit) {
    var isRecognized by remember { mutableStateOf(false) }
    var recognizedPersonName by remember { mutableStateOf("") }

    // Animação da borda piscando
    val infiniteTransition = rememberInfiniteTransition()
    val borderColor by infiniteTransition.animateColor(
        initialValue = Color.Green,
        targetValue = Color.Transparent,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "BordaVerde"
    )

    // Efeito colateral: Quando reconhecer alguém, chama URL, espera 2s e desliga
    LaunchedEffect(isRecognized) {
        if (isRecognized) {
            notifyLocalServer() // Bate na URL 192.168.1.3/found
            delay(2000)         // Mantém a borda por 2 segundos
            isRecognized = false
            recognizedPersonName = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isRecognized) Modifier.border(8.dp, borderColor) else Modifier
            )
    ) {
        // Câmera ao vivo rodando a IA em background
        CameraPreviewBox(classifier) { personName ->
            // Esse callback é chamado pelo FaceAnalyzer quando dá match!
            if (!isRecognized) { // Evita chamar a URL repetidas vezes num mesmo segundo
                recognizedPersonName = personName
                isRecognized = true
            }
        }

        // Nome da pessoa reconhecida (Opcional, bom para feedback visual)
        if (isRecognized) {
            Text(
                text = "Bem-vindo(a), $recognizedPersonName!",
                color = Color.Green,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp)
            )
        }

        // Botão para ir para o Cadastro
        Button(
            onClick = onNavigateToRegister,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Text("Cadastrar Nova Pessoa")
        }
    }
}

// --- INTEGRAÇÃO DA CÂMERAX ---
@Composable
fun CameraPreviewBox(classifier: FaceClassifier?, onRecognized: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        if (classifier != null) {
                            analysis.setAnalyzer(
                                cameraExecutor,
                                FaceAnalyzer(classifier) { personName ->
                                    onRecognized(personName)
                                }
                            )
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("CameraX", "Erro ao vincular câmera", exc)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- FUNÇÃO DE REDE (OKHTTP) ---
fun notifyLocalServer() {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("http://192.168.4.1/found")
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("Rede", "Falha ao notificar o servidor IoT: ${e.message}")
        }

        override fun onResponse(call: Call, response: Response) {
            Log.d("Rede", "Servidor notificado com sucesso!")
            response.close()
        }
    })
}