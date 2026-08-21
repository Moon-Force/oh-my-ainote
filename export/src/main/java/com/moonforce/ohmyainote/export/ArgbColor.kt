package com.moonforce.ohmyainote.export

import android.graphics.Color

/**
 * Decodes `#AARRGGBB` color strings into an opaque ARGB int and exposes its channels.
 *
 * The production implementation backs onto [android.graphics.Color]; a pure-Kotlin
 * implementation ([PureJvmArgbColor]) keeps the template/stroke export path runnable in
 * plain JVM unit tests where the Android framework is a stubbed "not mocked" no-op.
 */
interface ArgbColor {
    fun parse(hex: String): Int
    fun red(argb: Int): Float
    fun green(argb: Int): Float
    fun blue(argb: Int): Float
    fun alpha(argb: Int): Int
}

/** Default backed by the real Android [Color] API. */
object AndroidArgbColor : ArgbColor {
    override fun parse(hex: String): Int = Color.parseColor(hex)
    override fun red(argb: Int): Float = Color.red(argb) / 255f
    override fun green(argb: Int): Float = Color.green(argb) / 255f
    override fun blue(argb: Int): Float = Color.blue(argb) / 255f
    override fun alpha(argb: Int): Int = Color.alpha(argb)
}

/** Pure-JVM [ArgbColor] usable in unit tests without the Android runtime. */
object PureJvmArgbColor : ArgbColor {
    override fun parse(hex: String): Int {
        val cleaned = hex.removePrefix("#")
        require(cleaned.length == 6 || cleaned.length == 8) { "Unsupported color '$hex'" }
        val value = cleaned.toLong(16)
        return if (cleaned.length == 8) {
            value.toInt()
        } else {
            (0xFF000000.toInt() or value.toInt())
        }
    }

    override fun red(argb: Int): Float = ((argb shr 16) and 0xFF) / 255f
    override fun green(argb: Int): Float = ((argb shr 8) and 0xFF) / 255f
    override fun blue(argb: Int): Float = (argb and 0xFF) / 255f
    override fun alpha(argb: Int): Int = (argb ushr 24) and 0xFF
}
