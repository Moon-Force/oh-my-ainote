# AndroidX Ink loads native symbols through JNI.
-keep class androidx.ink.** { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# PdfBox-Android references an optional JPEG2000 codec. v1 rejects JPX PDFs at import.
-dontwarn com.gemalto.jp2.**
