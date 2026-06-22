package com.example.reconhecimentofacial


import android.graphics.Bitmap
// Modelo de dados de uma pessoa
data class RegisteredPerson(
    val id: String,
    val name: String,
    val faceVector: FloatArray
)

object PersonRepository {
    // Lista temporária na memória que guarda as pessoas cadastradas
    private val registeredPeople = mutableListOf<RegisteredPerson>()

    // Sensibilidade do reconhecimento (Quanto menor, mais rigoroso. 0.85f é um bom padrão)
    private const val THRESHOLD = 0.85f

    // Salva o vetor e o nome da pessoa
    fun registerNewPerson(name: String, faceVector: FloatArray) {
        val id = java.util.UUID.randomUUID().toString()
        registeredPeople.add(RegisteredPerson(id, name, faceVector))
    }


    fun findMatch(currentVector: FloatArray): RegisteredPerson? {
        var bestMatch: RegisteredPerson? = null
        var minDistance = Float.MAX_VALUE


        for (person in registeredPeople) {
            var distance = 0f


            for (i in currentVector.indices) {
                val diff = currentVector[i] - person.faceVector[i]
                distance += diff * diff
            }
            distance = Math.sqrt(distance.toDouble()).toFloat()


            if (distance < minDistance && distance < THRESHOLD) {
                minDistance = distance
                bestMatch = person
            }
        }

        return bestMatch
    }
}