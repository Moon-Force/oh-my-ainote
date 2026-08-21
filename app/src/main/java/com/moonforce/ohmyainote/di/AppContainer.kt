package com.moonforce.ohmyainote.di

import android.content.Context
import com.moonforce.ohmyainote.ai.AiSettingsStore
import com.moonforce.ohmyainote.ai.RegionRasterizer
import com.moonforce.ohmyainote.ai.api.OkHttpOpenAiCompatibleClient
import com.moonforce.ohmyainote.document.store.LocalNotebookStore
import com.moonforce.ohmyainote.export.FlattenedPdfExporter
import com.moonforce.ohmyainote.importing.ImportProcessors
import com.moonforce.ohmyainote.pdf.PdfImportInspector
import com.moonforce.ohmyainote.hwr.DigitalInkModelStore
import com.moonforce.ohmyainote.hwr.HandwritingRecognizer
import com.moonforce.ohmyainote.hwr.HwrSettingsStore
import com.moonforce.ohmyainote.ui.editor.PageRasterComposer

class AppContainer(context: Context) {
    val context: Context = context.applicationContext
    val notebookStore = LocalNotebookStore(this.context.filesDir.toPath())
    val aiSettingsStore = AiSettingsStore(this.context)
    val aiClient = OkHttpOpenAiCompatibleClient()
    val regionRasterizer = RegionRasterizer()
    val pageRasterComposer = PageRasterComposer(regionRasterizer)
    val pdfImportInspector = PdfImportInspector()
    val exporter = FlattenedPdfExporter()
    val importProcessors = ImportProcessors(this.context)
    val hwrSettingsStore = HwrSettingsStore(this.context)
    val digitalInkModelStore = DigitalInkModelStore()
    val handwritingRecognizer = HandwritingRecognizer(digitalInkModelStore)
}
