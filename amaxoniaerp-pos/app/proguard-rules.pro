# Required for libraries that inspect annotations at runtime (Ktor and serialization metadata).
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod

# WorkManager instantiates workers by class name. Keep names, while allowing member optimization.
-keepnames class * extends androidx.work.ListenableWorker

# Closed-source fiscal/printer SDKs are invoked through vendor APIs and may use reflection/JNI.
-keep class com.thefactoryhka.hkacryptolib.** { *; }
-keep class com.sunmi.peripheral.printer.** { *; }

# SLF4J supports an optional runtime binding; the app intentionally ships without one.
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Keep source/line metadata for actionable obfuscated production traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
