package com.example.reconhecimentofacial

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class RegisterState {
    object Idle : RegisterState()
    object Processing : RegisterState()
    object Success : RegisterState()
    data class Error(val theMessage: String) : RegisterState()
}

class RegisterViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val uiState: StateFlow<RegisterState> = _uiState

    // Função chamada quando o usuário clica em "Salvar Cadastro"
    fun registerPerson(name: String, faceBitmap: Bitmap?, classifier: FaceClassifier?) {
        if (name.isBlank()) {
            _uiState.value = RegisterState.Error("O nome não pode estar vazio.")
            return
        }
        if (faceBitmap == null) {
            _uiState.value = RegisterState.Error("Nenhuma foto de rosto capturada.")
            return
        }
        if (classifier == null) {
            _uiState.value = RegisterState.Error("Modelo de IA não inicializado.")
            return
        }

        viewModelScope.launch {
            _uiState.value = RegisterState.Processing
            try {
                // 1. A IA lê o bitmap do rosto e extrai o vetor numérico (embedding)
                val vector = classifier.recognizeFace(faceBitmap)

                // 2. Salva no nosso repositório
                PersonRepository.registerNewPerson(name, vector)

                _uiState.value = RegisterState.Success
            } catch (e: Exception) {
                _uiState.value = RegisterState.Error("Erro ao processar rosto: ${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterState.Idle
    }
}