package com.gazai.fuel.ai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** On-device OCR (ML Kit). Returns "" when nothing could be read; never throws. */
object Ocr {
    suspend fun read(ctx: Context, uri: Uri): String {
        val bitmap = try {
            withContext(Dispatchers.IO) { Images.decode(ctx, uri, 1600) }
        } catch (e: Exception) {
            return ""
        }
        return try {
            suspendCancellableCoroutine { cont ->
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it.text) }
                    .addOnFailureListener { if (cont.isActive) cont.resume("") }
                    .addOnCompleteListener { recognizer.close() }
            }
        } catch (e: Exception) {
            ""
        }
    }
}
