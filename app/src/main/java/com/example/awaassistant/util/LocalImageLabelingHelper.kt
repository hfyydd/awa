package com.example.awaassistant.util

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object LocalImageLabelingHelper {
    // Lazy-load the default on-device image labeler client
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Label an image offline by its Uri, returning labels with confidence >= 30%
     */
    suspend fun labelImage(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                val image = InputImage.fromFilePath(context, imageUri)
                labeler.process(image)
                    .addOnSuccessListener { labels ->
                        val result = labels
                            .filter { it.confidence >= 0.3f } // Keep labels with at least 30% confidence
                            .map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                        continuation.resume(result)
                    }
                    .addOnFailureListener { e ->
                        continuation.resume(emptyList())
                    }
            } catch (e: Exception) {
                continuation.resume(emptyList())
            }
        }
    }
}
