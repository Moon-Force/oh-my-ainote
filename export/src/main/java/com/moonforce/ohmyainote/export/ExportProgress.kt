package com.moonforce.ohmyainote.export

fun interface ExportProgress {
    fun onPage(completed: Int, total: Int)
}

data class ExportOptions(
    val pressureVarying: Boolean = true,
    val producer: String = "oh-my-ainote",
)
