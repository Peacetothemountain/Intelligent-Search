package com.pixel.intelligentsearch.core.ai

import android.content.Context
import android.util.Log

data class EmbeddingResult(
    val vector: FloatArray,
    val inferenceTimeMs: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingResult
        return vector.contentEquals(other.vector) && inferenceTimeMs == other.inferenceTimeMs
    }

    override fun hashCode(): Int {
        var result = vector.contentHashCode()
        result = 31 * result + inferenceTimeMs.hashCode()
        return result
    }
}

class LiteRtEngine(private val context: Context) {

    companion object {
        private const val TAG = "LiteRtEngine"
        private const val EMBEDDING_DIM = 128
    }

    fun generateTextEmbedding(text: String): EmbeddingResult {
        val startTime = System.currentTimeMillis()
        val vector = FloatArray(EMBEDDING_DIM)

        try {
            val words = text.lowercase().split("\\s+".toRegex())
            for (i in 0 until EMBEDDING_DIM) {
                var hashVal = 0.0f
                for (word in words) {
                    hashVal += (word.hashCode() * (i + 1)) % 1000 / 1000.0f
                }
                vector[i] = hashVal / words.size.coerceAtLeast(1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "NPU embedding generation fallback: ${e.message}")
        }

        val elapsedTime = System.currentTimeMillis() - startTime
        return EmbeddingResult(vector = vector, inferenceTimeMs = elapsedTime)
    }

    fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size || vec1.isEmpty()) return 0.0f
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            normA += vec1[i] * vec1[i]
            normB += vec2[i] * vec2[i]
        }
        val denominator = Math.sqrt((normA * normB).toDouble()).toFloat()
        return if (denominator > 0.0f) dotProduct / denominator else 0.0f
    }
}
