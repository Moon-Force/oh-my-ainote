package com.moonforce.ohmyainote.hwr

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import kotlinx.coroutines.withTimeout

class DigitalInkModelStore() {
    /** Test-only injection point; `internal` keeps [RemoteModelManager] off the public API. */
    internal constructor(
        modelManager: RemoteModelManager,
        timeoutMs: Long,
    ) : this() {
        this.modelManager = modelManager
        this.timeoutMs = timeoutMs
    }

    private var modelManager: RemoteModelManager? = null
    private var timeoutMs: Long = DOWNLOAD_TIMEOUT_MS

    private fun manager(): RemoteModelManager =
        modelManager ?: RemoteModelManager.getInstance().also { modelManager = it }

    internal fun model(): DigitalInkRecognitionModel {
        val identifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
            ?: error("Chinese digital-ink model is unavailable")
        return DigitalInkRecognitionModel.builder(identifier).build()
    }

    suspend fun isDownloaded(): Boolean = manager().isModelDownloaded(model()).await() == true

    suspend fun download() {
        runCatching {
            withTimeout(timeoutMs) {
                manager().download(model(), DownloadConditions.Builder().build()).awaitDone()
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                "手写识别模型下载失败（需要可用的 Google 服务）。${error.message.orEmpty()}",
                error,
            )
        }
    }

    companion object {
        const val LANGUAGE_TAG = "zh-CN"
        const val DOWNLOAD_TIMEOUT_MS = 5L * 60L * 1000L
    }
}
