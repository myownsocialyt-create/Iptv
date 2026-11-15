# R8 configuration for Hypnotix IPTV
# Precompiled dex files (classes2.dex..classes11.dex) live outside R8's program inputs and call
# into Jetpack, Media3, Room, Firebase, AdMob, Billing, Kotlin, and com.example.premium.* directly.
# Therefore shrinking and optimization are disabled so no dependency API is stripped or rewritten,
# while internal helpers/lambdas in com.example.premium are obfuscated to produce a valid
# Play Console deobfuscation mapping file (proguard.map).

-dontshrink
-dontoptimize

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep all Android framework, Jetpack, third-party libraries, R classes, and BuildConfig intact
# because precompiled-dex classes reference them outside R8's visibility.
-keep class !com.example.premium.** { *; }

# Keep all entrypoints and public APIs in com.example.premium called by precompiled-dex or AndroidManifest.
-keep class com.example.premium.PremiumManager { *; }
-keep class com.example.premium.PremiumUiHooks { *; }
-keep class com.example.premium.SubscriptionActivity { *; }
-keep class com.example.premium.BillingHelper { *; }
-keep class com.example.premium.BillingHelper$Companion { *; }
-keep class com.example.premium.SubscriptionScreenKt { *; }
-keep class com.example.premium.SplitPortraitHooks { *; }
-keep class com.example.premium.HybridCastManager { *; }
-keep class com.example.premium.NotificationSyncBridge { *; }

-dontwarn **
