package com.moonforce.ohmyainote.document.format

import com.moonforce.ohmyainote.document.model.LibraryIndex
import com.moonforce.ohmyainote.document.model.NotebookManifest
import com.moonforce.ohmyainote.document.model.PageModel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object OmaJson {
    val instance = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
}

object ManifestCodec {
    fun encode(value: NotebookManifest): String = OmaJson.instance.encodeToString(value)
    fun decode(value: String): NotebookManifest = OmaJson.instance.decodeFromString(value)
}

object PageCodec {
    fun encode(value: PageModel): String = OmaJson.instance.encodeToString(value)
    fun decode(value: String): PageModel = OmaJson.instance.decodeFromString(value)
}

object LibraryCodec {
    fun encode(value: LibraryIndex): String = OmaJson.instance.encodeToString(value)
    fun decode(value: String): LibraryIndex = OmaJson.instance.decodeFromString(value)
}
