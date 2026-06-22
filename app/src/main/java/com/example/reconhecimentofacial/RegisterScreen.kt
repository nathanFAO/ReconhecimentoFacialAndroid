package com.example.reconhecimentofacial

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    classifier: FaceClassifier?,
    onBackToRecognition: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var nameInput by remember { mutableStateOf("") }

    // Guarda o Bitmap da foto real tirada
    var capturedFaceBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Guarda a referência da View da câmera para capturar a foto depois
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Gerenciamento de Permissão da Câmera
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasCameraPermission = isGranted }
    )

    // Pede permissão assim que a tela abre, se necessário
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Observa o estado do processamento do Backend
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        when (uiState) {
            is RegisterState.Success -> {
                Toast.makeText(context, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                nameInput = ""
                capturedFaceBitmap = null
                viewModel.resetState()
                onBackToRecognition()
            }
            is RegisterState.Error -> {
                val errorMsg = (uiState as RegisterState.Error).theMessage
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadastro de Face") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Campo de Input do Nome
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Nome da Pessoa") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = uiState !is RegisterState.Processing
            )

            // 2. Área do Preview da Foto / Câmera Ativa
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .border(2.dp, Color.Gray, RoundedCornerShape(12.dp))
                    .background(Color.DarkGray, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp)), // Garante que a câmera respeite os cantos arredondados
                contentAlignment = Alignment.Center
            ) {
                if (capturedFaceBitmap != null) {
                    // Se o usuário já tirou a foto, mostra a imagem estática capturada
                    Image(
                        bitmap = capturedFaceBitmap!!.asImageBitmap(),
                        contentDescription = "Rosto capturado",
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (hasCameraPermission) {
                    // CÂMERA REAL EM TEMPO REAL (Se tiver permissão e não tiver foto capturada ainda)
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                previewViewRef = this // Salva a referência para sabermos de onde tirar a foto

                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(surfaceProvider)
                                    }
                                    // Forçando o uso da Câmera Frontal no Cadastro
                                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                                    try {
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            cameraSelector,
                                            preview
                                        )
                                    } catch (exc: Exception) {
                                        Log.e("RegisterScreen", "Erro ao iniciar câmera frontal", exc)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "Sem permissão de câmera",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // 3. Botão Dinâmico (Capturar Foto / Tirar Outra)
            Button(
                onClick = {
                    if (capturedFaceBitmap != null) {
                        // Se já tem uma foto, esse botão limpa ela para reabrir a câmera
                        capturedFaceBitmap = null
                    } else {
                        // Captura o frame atual direto da nossa View da Câmera!
                        val bitmap = previewViewRef?.bitmap
                        if (bitmap != null) {
                            capturedFaceBitmap = bitmap
                            Toast.makeText(context, "Foto capturada com sucesso!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Erro ao capturar imagem. Tente novamente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (capturedFaceBitmap != null) Color.Red else MaterialTheme.colorScheme.secondary
                ),
                enabled = uiState !is RegisterState.Processing && (hasCameraPermission || capturedFaceBitmap != null)
            ) {
                Text(if (capturedFaceBitmap != null) "Tirar Outra Foto" else "Capturar Rosto da Câmera")
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Botão de Salvar/Enviar dados para o Backend
            if (uiState is RegisterState.Processing) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (nameInput.isBlank()) {
                            Toast.makeText(context, "Por favor, digite um nome", Toast.LENGTH_SHORT).show()
                        } else if (capturedFaceBitmap == null) {
                            Toast.makeText(context, "Por favor, capture o rosto primeiro", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.registerPerson(nameInput, capturedFaceBitmap, classifier)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Salvar Cadastro")
                }
            }

            TextButton(onClick = onBackToRecognition) {
                Text("Voltar para Reconhecimento")
            }
        }
    }
}