package com.moonforce.ohmyainote.pdf

import android.graphics.Matrix
import android.graphics.Rect

data class PageBitmapRequest(
    val pageIndex: Int,
    val widthPx: Int,
    val heightPx: Int,
    val destinationClip: Rect? = null,
    val pageToBitmap: Matrix? = null,
)

data class PdfPageInfo(val index: Int, val widthPt: Int, val heightPt: Int)
