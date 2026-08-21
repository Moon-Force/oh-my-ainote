package com.moonforce.ohmyainote.hwr

import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import com.moonforce.ohmyainote.document.format.DigitalInkGeometry
import com.moonforce.ohmyainote.document.format.InkSample
import com.moonforce.ohmyainote.document.model.StrokeRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HandwritingRecognizer(
    private val modelStore: DigitalInkModelStore = DigitalInkModelStore(),
) {
    @Volatile
    private var client: DigitalInkRecognizer? = null

    suspend fun recognize(records: List<StrokeRecord>): String = withContext(Dispatchers.Default) {
        val sample = DigitalInkGeometry.samples(records)
        recognizer().recognize(buildInk(sample), recognitionContext(sample.area)).await()
            .candidates
            .firstOrNull()
            ?.text
            .orEmpty()
            .trim()
    }

    fun close() {
        client?.close()
        client = null
    }

    internal fun buildInk(sample: InkSample): Ink {
        val inkBuilder = Ink.builder()
        sample.strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.points.forEach { point ->
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.tMs))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        return inkBuilder.build()
    }

    internal fun recognitionContext(area: com.moonforce.ohmyainote.document.model.PageRect): RecognitionContext =
        RecognitionContext.builder()
            .setPreContext("")
            .setWritingArea(
                WritingArea(
                    area.width.coerceAtLeast(1f),
                    area.height.coerceAtLeast(1f),
                ),
            )
            .build()

    private fun recognizer(): DigitalInkRecognizer {
        client?.let { return it }
        return DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(modelStore.model()).build(),
        ).also { client = it }
    }
}
