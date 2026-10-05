# ProGuard rules for amaxoniaerp-kiosk
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* *;
}
-keep class io.ktor.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class com.sunmi.peripheral.printer.** { *; }
