package com.moonforce.ohmyainote.pdf

import com.moonforce.ohmyainote.document.model.PdfPageDescriptor
import com.moonforce.ohmyainote.document.model.PdfRect
import com.moonforce.ohmyainote.document.model.SourceFile
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfImportException(message: String, cause: Throwable? = null) : IOException(message, cause)

class PdfImportInspector {
    suspend fun inspect(path: Path): SourceFile = withContext(Dispatchers.IO) {
        require(Files.isRegularFile(path)) { "PDF source is missing" }
        if (containsSequence(path, "/JPXDecode".toByteArray(Charsets.US_ASCII))) {
            throw PdfImportException("v1 不支持 JPEG2000 页")
        }
        try {
            PDDocument.load(path.toFile()).use { document ->
                if (document.isEncrypted) throw PdfImportException("v1 不支持加密 PDF")
                if (document.numberOfPages <= 0) throw PdfImportException("PDF 没有页面")
                val pages = document.pages.mapIndexed { index, page ->
                    if (page.cosObject.containsKey(COSName.BLEED_BOX) || page.cosObject.containsKey(COSName.TRIM_BOX)) {
                        throw PdfImportException("第 ${index + 1} 页包含 v1 不支持的 BleedBox/TrimBox")
                    }
                    val rawRotation = page.rotation
                    if (rawRotation % 90 != 0) throw PdfImportException("第 ${index + 1} 页旋转角度不是 90° 的倍数")
                    val rotate = ((rawRotation % 360) + 360) % 360
                    val media = page.mediaBox.toModel()
                    val crop = page.cropBox.toModel()
                    if (crop.l < 0f || crop.b < 0f) throw PdfImportException("第 ${index + 1} 页使用负 CropBox")
                    val width = if (rotate == 0 || rotate == 180) crop.width else crop.height
                    val height = if (rotate == 0 || rotate == 180) crop.height else crop.width
                    PdfPageDescriptor(width, height, rotate, media, crop)
                }
                SourceFile(path = path, pdfPages = pages)
            }
        } catch (failure: InvalidPasswordException) {
            throw PdfImportException("v1 不支持加密 PDF", failure)
        } catch (failure: PdfImportException) {
            throw failure
        } catch (failure: Exception) {
            throw PdfImportException("PDF 已损坏或无法读取", failure)
        }
    }

    private fun PDRectangle.toModel() = PdfRect(lowerLeftX, lowerLeftY, upperRightX, upperRightY).also {
        if (it.width <= 0 || it.height <= 0) throw PdfImportException("PDF page box is empty")
    }

    private fun containsSequence(path: Path, needle: ByteArray): Boolean {
        Files.newInputStream(path).use { input ->
            var matched = 0
            while (true) {
                val value = input.read()
                if (value < 0) return false
                if (value.toByte() == needle[matched]) {
                    matched += 1
                    if (matched == needle.size) return true
                } else {
                    matched = if (value.toByte() == needle[0]) 1 else 0
                }
            }
        }
    }
}
