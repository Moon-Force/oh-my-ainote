# AndroidX Ink loads native symbols through JNI.
-keep class androidx.ink.** { *; }
-keepclasseswithmembers,includedescriptorclasses class * {
    native <methods>;
}

# PdfBox-Android references an optional JPEG2000 codec. v1 rejects JPX PDFs at import.
-dontwarn com.gemalto.jp2.**

# ML Kit digital ink recognition (on-demand language models).
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.android.gms.internal.mlkit_vision_digital_ink.** { *; }
