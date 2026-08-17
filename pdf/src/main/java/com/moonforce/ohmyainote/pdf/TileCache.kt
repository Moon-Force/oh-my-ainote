package com.moonforce.ohmyainote.pdf

import android.graphics.Bitmap
import android.util.LruCache

data class TileKey(
    val notebookId: String,
    val pageIndex: Int,
    val scaleBucket: Float,
    val tileX: Int,
    val tileY: Int,
)

class TileCache(maxBytes: Int = 96 * 1024 * 1024) {
    private val cache = object : LruCache<TileKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: TileKey, value: Bitmap): Int = value.allocationByteCount
    }

    @Synchronized fun get(key: TileKey): Bitmap? = cache.get(key)

    @Synchronized fun put(key: TileKey, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    @Synchronized fun clear() = cache.evictAll()

    val sizeBytes: Int get() = cache.size()
    val tileCount: Int get() = cache.snapshot().size
}
