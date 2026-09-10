package com.bits.facultyai.domain

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR via ML Kit (bundled Latin model — no network calls, works
 * offline, no user data leaves the device).
 *
 * Produces text lines in logical reading order: ML Kit returns line
 * bounding boxes, so rows are grouped by vertical position and sorted
 * left-to-right within each row. This repairs the reading order for
 * timetable images whose text blocks aren't returned top-down.
 */
object TimetableOcr {

    suspend fun recognizeLines(context: Context, uri: Uri): List<String> {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer: TextRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        return try {
            suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        cont.resume(orderLines(visionText))
                    }
                    .addOnFailureListener { e ->
                        cont.resumeWithException(e)
                    }
            }
        } finally {
            recognizer.close()
        }
    }

    /**
     * Groups OCR lines into visual rows (same baseline ≈ same row), sorts
     * each row left→right, then returns rows top→bottom as text lines.
     */
    internal fun orderLines(visionText: com.google.mlkit.vision.text.Text): List<String> {
        data class Boxed(val text: String, val top: Float, val left: Float, val height: Float)

        val boxes = visionText.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val b = line.boundingBox
                Boxed(
                    text = line.text.trim(),
                    top = b?.top?.toFloat() ?: 0f,
                    left = b?.left?.toFloat() ?: 0f,
                    height = (b?.height()?.toFloat() ?: 20f).coerceAtLeast(1f),
                )
            }
        }.filter { it.text.isNotBlank() }
        if (boxes.isEmpty()) return emptyList()

        val medianHeight = boxes.map { it.height }.sorted()[boxes.size / 2]
        val rowTolerance = medianHeight * 0.6f

        val sorted = boxes.sortedBy { it.top }
        val rows = mutableListOf<MutableList<Boxed>>()
        for (box in sorted) {
            val current = rows.lastOrNull()
            if (current != null && kotlin.math.abs(box.top - current.first().top) <= rowTolerance) {
                current.add(box)
            } else {
                rows.add(mutableListOf(box))
            }
        }

        return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
            .filter { it.isNotBlank() }
    }
}
