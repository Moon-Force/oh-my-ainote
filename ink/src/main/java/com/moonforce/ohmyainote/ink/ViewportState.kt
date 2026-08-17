package com.moonforce.ohmyainote.ink

import android.graphics.Matrix as AndroidMatrix
import androidx.compose.ui.graphics.Matrix as ComposeMatrix
import com.moonforce.ohmyainote.document.format.Affine2

data class ViewportState(
    val scale: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val pageOriginX: Float = 0f,
    val pageOriginY: Float = 0f,
    val densityDpi: Float = 160f,
) {
    val pageToView: Affine2
        get() = Affine2.pageToView(
            densityDpi = densityDpi.toDouble(),
            scale = scale.toDouble(),
            pageOriginX = pageOriginX.toDouble(),
            pageOriginY = pageOriginY.toDouble(),
            panX = panX.toDouble(),
            panY = panY.toDouble(),
        )

    fun pageToViewAndroid(): AndroidMatrix = pageToView.toAndroidMatrix()
    fun viewToPageCompose(): ComposeMatrix = pageToView.invert().toComposeMatrix()
}

fun Affine2.toAndroidMatrix(): AndroidMatrix = AndroidMatrix().apply {
    setValues(
        floatArrayOf(
            a.toFloat(), c.toFloat(), tx.toFloat(),
            b.toFloat(), d.toFloat(), ty.toFloat(),
            0f, 0f, 1f,
        )
    )
}

fun Affine2.toComposeMatrix(): ComposeMatrix = ComposeMatrix(
    floatArrayOf(
        a.toFloat(), b.toFloat(), 0f, 0f,
        c.toFloat(), d.toFloat(), 0f, 0f,
        0f, 0f, 1f, 0f,
        tx.toFloat(), ty.toFloat(), 0f, 1f,
    )
)
