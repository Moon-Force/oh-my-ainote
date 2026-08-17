package com.moonforce.ohmyainote.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Two independent PdfRenderer instances; each instance serializes openPage/render. */
class PdfPageRenderer(file: File) : Closeable {
    private val slots: List<Slot>
    val pageCount: Int

    init {
        val original = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            slots = listOf(Slot(PdfRenderer(original.dup())), Slot(PdfRenderer(original.dup())))
            pageCount = slots.first().renderer.pageCount
        } finally {
            original.close()
        }
    }

    suspend fun pageInfo(index: Int): PdfPageInfo = withContext(Dispatchers.Default) {
        require(index in 0 until pageCount)
        slots[index and 1].mutex.withLock {
            slots[index and 1].renderer.openPage(index).use { page ->
                PdfPageInfo(index, page.width, page.height)
            }
        }
    }

    suspend fun render(request: PageBitmapRequest): Bitmap = withContext(Dispatchers.Default) {
        require(request.pageIndex in 0 until pageCount)
        require(request.widthPx > 0 && request.heightPx > 0)
        val slot = slots[request.pageIndex and 1]
        slot.mutex.withLock {
            slot.renderer.openPage(request.pageIndex).use { page ->
                val bitmap = Bitmap.createBitmap(request.widthPx, request.heightPx, Bitmap.Config.ARGB_8888)
                val transform = request.pageToBitmap ?: Matrix().apply {
                    setScale(request.widthPx / page.width.toFloat(), request.heightPx / page.height.toFloat())
                }
                try {
                    page.render(
                        bitmap,
                        request.destinationClip,
                        transform,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    bitmap
                } catch (failure: Throwable) {
                    bitmap.recycle()
                    throw failure
                }
            }
        }
    }

    override fun close() {
        slots.forEach { it.renderer.close() }
    }

    private class Slot(val renderer: PdfRenderer, val mutex: Mutex = Mutex())
}
