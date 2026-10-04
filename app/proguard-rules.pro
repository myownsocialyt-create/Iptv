# Proguard rules for Hypnotix
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
