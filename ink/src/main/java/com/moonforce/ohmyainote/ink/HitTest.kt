package com.moonforce.ohmyainote.ink

import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.geometry.MutableParallelogram
import androidx.ink.geometry.MutableSegment
import androidx.ink.geometry.MutableVec
import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.TextRecord

fun eraseIntersectingStrokes(
    previousX: Float,
    previousY: Float,
    currentX: Float,
    currentY: Float,
    strokes: List<FinishedStroke>,
    eraserPaddingPt: Float = 4f,
): Set<String> {
    val sweepBounds = PageRect(
        l = minOf(previousX, currentX) - eraserPaddingPt,
        t = minOf(previousY, currentY) - eraserPaddingPt,
        r = maxOf(previousX, currentX) + eraserPaddingPt,
        b = maxOf(previousY, currentY) + eraserPaddingPt,
    )
    val shape = MutableParallelogram().populateFromSegmentAndPadding(
        MutableSegment(MutableVec(previousX, previousY), MutableVec(currentX, currentY)),
        eraserPaddingPt,
    )
    return strokes.asSequence()
        .filter { it.record.aabb.overlaps(sweepBounds) }
        .filter { it.ink.shape.intersects(shape, AffineTransform.IDENTITY) }
        .map { it.record.id }
        .toSet()
}

fun eraseIntersectingTexts(
    previousX: Float,
    previousY: Float,
    currentX: Float,
    currentY: Float,
    texts: List<TextRecord>,
    eraserPaddingPt: Float = 4f,
): Set<String> {
    val sweepBounds = PageRect(
        l = minOf(previousX, currentX) - eraserPaddingPt,
        t = minOf(previousY, currentY) - eraserPaddingPt,
        r = maxOf(previousX, currentX) + eraserPaddingPt,
        b = maxOf(previousY, currentY) + eraserPaddingPt,
    )
    return texts.filter { it.aabb.overlaps(sweepBounds) }.map { it.id }.toSet()
}
