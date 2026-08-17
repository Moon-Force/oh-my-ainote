package com.moonforce.ohmyainote.pdf

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PrefetchController(
    private val scope: CoroutineScope,
    private val renderer: PdfPageRenderer,
) {
    private val jobs = mutableMapOf<Int, Job>()

    fun onCurrentPageChanged(page: Int, widthPx: Int, heightPx: Int, onReady: (Int, Bitmap) -> Unit) {
        val desired = setOf(page - 1, page, page + 1).filter { it in 0 until renderer.pageCount }.toSet()
        jobs.filterKeys { it !in desired }.values.forEach(Job::cancel)
        jobs.keys.retainAll(desired)
        desired.forEach { index ->
            if (jobs[index]?.isActive == true) return@forEach
            jobs[index] = scope.launch {
                val bitmap = renderer.render(PageBitmapRequest(index, widthPx, heightPx))
                onReady(index, bitmap)
            }
        }
    }

    fun cancel() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
