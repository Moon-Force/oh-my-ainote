package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PagePoint
import com.moonforce.ohmyainote.document.model.PdfRect
import kotlin.math.abs

/** 2D affine matrix: x'=a*x+c*y+tx, y'=b*x+d*y+ty. */
data class Affine2(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val tx: Double = 0.0,
    val ty: Double = 0.0,
) {
    fun transform(point: PagePoint): PagePoint = PagePoint(
        (a * point.x + c * point.y + tx).toFloat(),
        (b * point.x + d * point.y + ty).toFloat(),
    )

    fun invert(): Affine2 {
        val determinant = a * d - b * c
        require(abs(determinant) > 1e-12) { "Non-invertible affine transform" }
        return Affine2(
            a = d / determinant,
            b = -b / determinant,
            c = -c / determinant,
            d = a / determinant,
            tx = (c * ty - d * tx) / determinant,
            ty = (b * tx - a * ty) / determinant,
        )
    }

    operator fun times(other: Affine2): Affine2 = Affine2(
        a = a * other.a + c * other.b,
        b = b * other.a + d * other.b,
        c = a * other.c + c * other.d,
        d = b * other.c + d * other.d,
        tx = a * other.tx + c * other.ty + tx,
        ty = b * other.tx + d * other.ty + ty,
    )

    companion object {
        fun pageToView(
            densityDpi: Double,
            scale: Double,
            pageOriginX: Double,
            pageOriginY: Double,
            panX: Double,
            panY: Double,
        ): Affine2 {
            require(densityDpi > 0 && scale > 0)
            val pixelsPerPoint = densityDpi / 72.0
            val totalScale = pixelsPerPoint * scale
            return Affine2(
                a = totalScale,
                d = totalScale,
                tx = pageOriginX + panX,
                ty = pageOriginY + panY,
            )
        }
    }
}

fun pageToPdfUserSpace(
    point: PagePoint,
    cropBox: PdfRect,
    rotate: Int,
): PagePoint {
    require(rotate in setOf(0, 90, 180, 270))
    require(cropBox.width > 0f && cropBox.height > 0f)
    val displayWidth = if (rotate == 0 || rotate == 180) cropBox.width else cropBox.height
    val displayHeight = if (rotate == 0 || rotate == 180) cropBox.height else cropBox.width
    val x = point.x
    val y = point.y
    return when (rotate) {
        0 -> PagePoint(cropBox.l + x, cropBox.b + displayHeight - y)
        90 -> PagePoint(cropBox.l + y, cropBox.b + x)
        180 -> PagePoint(cropBox.l + displayWidth - x, cropBox.b + y)
        270 -> PagePoint(cropBox.l + displayHeight - y, cropBox.b + displayWidth - x)
        else -> error("unreachable")
    }
}
