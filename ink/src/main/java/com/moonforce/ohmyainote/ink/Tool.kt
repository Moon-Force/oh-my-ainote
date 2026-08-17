package com.moonforce.ohmyainote.ink

enum class Tool { PEN, HIGHLIGHTER, ERASER, BOX_ASK, PAN }

val Tool.isWriting: Boolean get() = this == Tool.PEN || this == Tool.HIGHLIGHTER
