package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.PageRect
import com.moonforce.ohmyainote.document.model.StrokePoint
import com.moonforce.ohmyainote.document.model.StrokeRecord
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object OmaInputsV1 {
    private val magic = byteArrayOf('O'.code.toByte(), 'M'.code.toByte(), 'A'.code.toByte(), '1'.code.toByte())
    private const val headerBytes = 12
    private const val pointBaseBytes = 16
    private const val flagPressure = 1
    private const val flagTilt = 2
    private const val flagOrientation = 4

    data class Encoded(val bytes: ByteArray, val records: List<StrokeRecord>)

    fun encode(strokes: List<StrokeRecord>): Encoded {
        val capacity = headerBytes + strokes.sumOf { stroke ->
            4 + stroke.points.sumOf { point ->
                pointBaseBytes +
                    (if (point.pressure != null) 4 else 0) +
                    (if (point.tiltRadians != null) 4 else 0) +
                    (if (point.orientationRadians != null) 4 else 0)
            }
        }
        val buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(magic)
        buffer.putShort(1)
        buffer.putShort(0)
        buffer.putInt(strokes.size)

        val persisted = strokes.map { stroke ->
            require(stroke.points.isNotEmpty()) { "Stroke ${stroke.id} has no points" }
            val start = buffer.position()
            buffer.putInt(stroke.points.size)
            stroke.points.forEach(::validatePoint)
            stroke.points.forEach { point ->
                buffer.putFloat(point.x)
                buffer.putFloat(point.y)
                buffer.putInt(point.tMs.toUInt().toInt())
                var flags = 0
                if (point.pressure != null) flags = flags or flagPressure
                if (point.tiltRadians != null) flags = flags or flagTilt
                if (point.orientationRadians != null) flags = flags or flagOrientation
                buffer.put(flags.toByte())
                buffer.put(byteArrayOf(0, 0, 0))
                point.pressure?.let(buffer::putFloat)
                point.tiltRadians?.let(buffer::putFloat)
                point.orientationRadians?.let(buffer::putFloat)
            }
            val end = buffer.position()
            stroke.copy(
                byteOffset = start.toLong(),
                byteLength = end - start,
                pointCount = stroke.points.size,
                aabb = boundsOf(stroke.points),
            )
        }
        check(buffer.position() == capacity)
        return Encoded(buffer.array(), persisted)
    }

    fun decode(bytes: ByteArray, records: List<StrokeRecord>): List<StrokeRecord> {
        require(bytes.size >= headerBytes) { "Truncated OmaInputsV1 header" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val actualMagic = ByteArray(4).also(buffer::get)
        require(actualMagic.contentEquals(magic)) { "Invalid OmaInputsV1 magic" }
        require(buffer.short.toInt() == 1) { "Unsupported OmaInputsV1 version" }
        require(buffer.short.toInt() == 0) { "Invalid OmaInputsV1 reserved field" }
        val strokeCount = buffer.int
        require(strokeCount == records.size) { "strokeCount/page.json mismatch" }

        return records.map { record ->
            val start = buffer.position()
            if (record.byteOffset != 0L) {
                require(record.byteOffset == start.toLong()) { "Stroke offset mismatch" }
            }
            require(buffer.remaining() >= 4) { "Truncated stroke" }
            val pointCount = buffer.int
            require(pointCount > 0 && pointCount == record.pointCount) { "Stroke pointCount mismatch" }
            val points = buildList(pointCount) {
                repeat(pointCount) {
                    require(buffer.remaining() >= pointBaseBytes) { "Truncated stroke point" }
                    val x = buffer.float
                    val y = buffer.float
                    val tMs = buffer.int.toUInt().toLong()
                    val flags = buffer.get().toInt() and 0xff
                    require(flags and 0xf8 == 0) { "Unknown stroke point flags" }
                    repeat(3) { require(buffer.get().toInt() == 0) { "Non-zero point padding" } }
                    val optionalBytes = Integer.bitCount(flags) * 4
                    require(buffer.remaining() >= optionalBytes) { "Truncated optional stroke values" }
                    val point = StrokePoint(
                        x = x,
                        y = y,
                        tMs = tMs,
                        pressure = if (flags and flagPressure != 0) buffer.float else null,
                        tiltRadians = if (flags and flagTilt != 0) buffer.float else null,
                        orientationRadians = if (flags and flagOrientation != 0) buffer.float else null,
                    )
                    validatePoint(point)
                    add(point)
                }
            }
            val length = buffer.position() - start
            if (record.byteLength != 0) require(record.byteLength == length) { "Stroke byteLength mismatch" }
            record.copy(
                byteOffset = start.toLong(),
                byteLength = length,
                pointCount = pointCount,
                aabb = boundsOf(points),
                points = points,
            )
        }.also {
            require(!buffer.hasRemaining()) { "Trailing bytes in strokes.bin" }
        }
    }

    private fun validatePoint(point: StrokePoint) {
        require(point.x.isFinite() && point.y.isFinite()) { "Non-finite point" }
        require(point.tMs in 0..UInt.MAX_VALUE.toLong()) { "Point time out of range" }
        point.pressure?.let { require(it.isFinite() && it in 0f..1f) { "Pressure out of range" } }
        point.tiltRadians?.let { require(it.isFinite()) { "Non-finite tilt" } }
        point.orientationRadians?.let { require(it.isFinite()) { "Non-finite orientation" } }
    }

    private fun boundsOf(points: List<StrokePoint>): PageRect {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        points.forEach {
            left = min(left, it.x)
            top = min(top, it.y)
            right = max(right, it.x)
            bottom = max(bottom, it.y)
        }
        return PageRect(left, top, right, bottom)
    }
}
