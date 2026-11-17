# ProGuard rules for Teller App
# Enhanced anti-tampering and obfuscation rules

# Maximum obfuscation
-repackageclasses 'a'
-allowaccessmodification
# -useuniqueclassmembernames removed: R8 ignores it and prints a warning every build.
-keeppackagenames doNotKeepAPackage

# Anti-tampering: Prevent stack trace de-obfuscation
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Remove debug/verbose logging but KEEP error and warning logs for production diagnostics
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Apache POI & dependencies
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn javax.xml.stream.**
-dontwarn java.awt.**
-dontwarn org.apache.commons.collections4.**
-dontwarn org.apache.commons.io.**
-dontwarn org.apache.commons.compress.**
-dontwarn org.apache.logging.log4j.**
-dontwarn com.fasterxml.aalto.**
-dontwarn com.github.virtuald.**

# iText
-dontwarn com.lowagie.**

# Saxon (XSLT)
-dontwarn net.sf.saxon.**

# Batik (SVG)
-dontwarn org.apache.batik.**

# OSGi
-dontwarn org.osgi.framework.**

# BND Annotations
-dontwarn aQute.bnd.annotation.**

# FindBugs
-dontwarn edu.umd.cs.findbugs.annotations.**

# Kotlin compiler plugins
-dontwarn org.jetbrains.kotlin.compiler.plugin.**
-dontwarn org.jetbrains.kotlin.diagnostics.rendering.**
-dontwarn org.jetbrains.kotlin.fir.extensions.**

# Keep Compose rules (usually handled by the plugin, but just in case)
-keepclassmembers class * extends androidx.compose.runtime.ComposeNode { *; }

# Keep Room (entities/DAOs use annotations, not inheritance)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Keep kotlinx-serialization (used by mlc4j module)
-keepattributes RuntimeVisibleAnnotations
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
    *** serializer(...);
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Apache POI and its runtime reflection dependencies
-keep class org.apache.poi.** { *; }
-keep interface org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.xmlbeans.** { *; }

# Keep Log4j 2 classes (required by Apache POI 5.x)
-keep class org.apache.logging.log4j.** { *; }
-keep interface org.apache.logging.log4j.** { *; }

# Keep Aalto XML & StAX parser classes (required by Apache POI on Android)
-keep class com.fasterxml.aalto.** { *; }
-keep interface com.fasterxml.aalto.** { *; }
-keep class javax.xml.stream.** { *; }
-keep interface javax.xml.stream.** { *; }

# Keep Apache Commons & Curvesapi used by POI
-keep class org.apache.commons.collections4.** { *; }
-keep class org.apache.commons.io.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class com.github.virtuald.** { *; }

# Keep iText classes that use reflection for fonts/encoding
-keep class com.lowagie.** { *; }

# Google Play Services - CRITICAL (protects com.google.android.gms.common.R$string)
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.common.** { *; }
-keep interface com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Firebase
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Play Billing
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

# Play Integrity
-keep class com.google.android.play.integrity.** { *; }
-dontwarn com.google.android.play.integrity.**

# MediaPipe Tasks GenAI (LLM Inference)
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.mediapipe.tasks.genai.** { *; }
-keep class com.google.mediapipe.framework.** { *; }

# sherpa-onnx (on-device neural TTS — Piper)
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# Hardened Obfuscation for Licensing & Security Utilities
-keepclassmembers class com.minhas.teller.utils.LicenseUtils {
    public static *** getStoredLicenseKey(...);
    public static *** getAppStatus(...);
    public static *** checkLicenseStatus(...);
    public static *** activateLicense(...);
    public static *** surrenderLicense(...);
}

-keepclassmembers class com.minhas.teller.utils.SecurityUtils {
    public static *** performSecurityChecks(...);
    public static *** isAppTampered(...);
}

