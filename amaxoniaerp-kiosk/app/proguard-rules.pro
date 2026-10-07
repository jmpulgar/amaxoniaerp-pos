# ProGuard / R8 rules for amaxoniaerp-kiosk (release builds are minified and resource-shrunk).
# Stack traces from the field stay readable with the mapping file.
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization: backend DTOs (core/network) are decoded reflectively by generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class com.amaxonia.kiosk.** {
    static ** Companion;
    *** Companion;
    static **$* *;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.amaxonia.kiosk.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.amaxonia.kiosk.**$$serializer { *; }
-keepclassmembers class com.amaxonia.kiosk.** {
    *** Companion;
}
-keep class com.amaxonia.kiosk.core.network.** { *; }

# --- Ktor client (OkHttp engine is discovered via ServiceLoader).
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Room: entities and DAOs are generated; keep the outbox model stable.
-keep class com.amaxonia.kiosk.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

# --- Sunmi printer AIDL service (binder interfaces must keep their names).
-keep class com.sunmi.peripheral.printer.** { *; }
-keep class woyou.aidlservice.jiuiv5.** { *; }
-dontwarn com.sunmi.**

# --- Device admin / boot receivers are referenced from the manifest (kept by AAPT), keep their names for dpm.
-keep class com.amaxonia.kiosk.receiver.** { *; }

# --- security-crypto / Tink (EncryptedSharedPreferences).
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# --- ZXing (Yappy QR rendering).
-dontwarn com.google.zxing.**
