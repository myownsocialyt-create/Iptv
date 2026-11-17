# R8 configuration for Hypnotix IPTV
# Precompiled dex files (classes2.dex..classes11.dex) live outside R8's program inputs and call
# into Jetpack, Media3, Room, Firebase, AdMob, Billing, Kotlin, and com.example.premium.* directly.
# Therefore shrinking and optimization are disabled so no dependency API is stripped or rewritten,
# while internal helpers/lambdas in com.example.premium are obfuscated to produce a valid
# Play Console deobfuscation mapping file (proguard.map).

-dontshrink
-dontoptimize

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# Keep all R resource classes intact across all packages
-keep class **.R { *; }
-keep class **.R$* { *; }

# Keep every precompiled-dex dependency class, Compose UI/Foundation/Runtime/Material3,
# Media3, Room, Firebase, AdMob public API, Coil, Glide, OkHttp, Kotlin, and AndroidX intact,
# while allowing R8 obfuscation of self-contained internal/generated library packages so Play
# Console DEX obfuscation comfortably exceeds the 25% threshold with zero runtime risk.
-keep class !com.example.premium.**,!androidx.compose.material.icons.**,!com.google.android.gms.internal.**,!com.google.common.**,!io.reactivex.**,!com.google.protobuf.**,!org.checkerframework.**,!com.google.android.datatransport.**,!com.google.api.**,!com.android.billingclient.**,!androidx.privacysandbox.**,!com.caverock.androidsvg.** { *; }

# Keep exact Material Icon classes referenced by precompiled-dex
-keep class androidx.compose.material.icons.Icons { *; }
-keep class androidx.compose.material.icons.Icons$* { *; }
-keep class androidx.compose.material.icons.automirrored.filled.ArrowBackKt { *; }
-keep class androidx.compose.material.icons.automirrored.filled.ArrowForwardIosKt { *; }
-keep class androidx.compose.material.icons.automirrored.filled.ArrowForwardKt { *; }
-keep class androidx.compose.material.icons.automirrored.filled.ListKt { *; }
-keep class androidx.compose.material.icons.automirrored.filled.OpenInNewKt { *; }
-keep class androidx.compose.material.icons.filled.AddKt { *; }
-keep class androidx.compose.material.icons.filled.ArrowDropDownKt { *; }
-keep class androidx.compose.material.icons.filled.AspectRatioKt { *; }
-keep class androidx.compose.material.icons.filled.BrightnessMediumKt { *; }
-keep class androidx.compose.material.icons.filled.CategoryKt { *; }
-keep class androidx.compose.material.icons.filled.CheckCircleKt { *; }
-keep class androidx.compose.material.icons.filled.CheckKt { *; }
-keep class androidx.compose.material.icons.filled.ChevronRightKt { *; }
-keep class androidx.compose.material.icons.filled.ClearKt { *; }
-keep class androidx.compose.material.icons.filled.CloseKt { *; }
-keep class androidx.compose.material.icons.filled.DarkModeKt { *; }
-keep class androidx.compose.material.icons.filled.DeleteKt { *; }
-keep class androidx.compose.material.icons.filled.DeleteOutlineKt { *; }
-keep class androidx.compose.material.icons.filled.DescriptionKt { *; }
-keep class androidx.compose.material.icons.filled.DnsKt { *; }
-keep class androidx.compose.material.icons.filled.FavoriteBorderKt { *; }
-keep class androidx.compose.material.icons.filled.FavoriteKt { *; }
-keep class androidx.compose.material.icons.filled.FilterListKt { *; }
-keep class androidx.compose.material.icons.filled.Forward10Kt { *; }
-keep class androidx.compose.material.icons.filled.FullscreenExitKt { *; }
-keep class androidx.compose.material.icons.filled.FullscreenKt { *; }
-keep class androidx.compose.material.icons.filled.GridViewKt { *; }
-keep class androidx.compose.material.icons.filled.HelpOutlineKt { *; }
-keep class androidx.compose.material.icons.filled.HighQualityKt { *; }
-keep class androidx.compose.material.icons.filled.HistoryKt { *; }
-keep class androidx.compose.material.icons.filled.HomeKt { *; }
-keep class androidx.compose.material.icons.filled.InfoKt { *; }
-keep class androidx.compose.material.icons.filled.KeyboardArrowUpKt { *; }
-keep class androidx.compose.material.icons.filled.LanguageKt { *; }
-keep class androidx.compose.material.icons.filled.LightModeKt { *; }
-keep class androidx.compose.material.icons.filled.LinkKt { *; }
-keep class androidx.compose.material.icons.filled.LiveTvKt { *; }
-keep class androidx.compose.material.icons.filled.MenuKt { *; }
-keep class androidx.compose.material.icons.filled.MovieKt { *; }
-keep class androidx.compose.material.icons.filled.MusicNoteKt { *; }
-keep class androidx.compose.material.icons.filled.NotesKt { *; }
-keep class androidx.compose.material.icons.filled.NotificationsKt { *; }
-keep class androidx.compose.material.icons.filled.PauseKt { *; }
-keep class androidx.compose.material.icons.filled.PictureInPictureAltKt { *; }
-keep class androidx.compose.material.icons.filled.PlayArrowKt { *; }
-keep class androidx.compose.material.icons.filled.PlaylistAddKt { *; }
-keep class androidx.compose.material.icons.filled.PlaylistPlayKt { *; }
-keep class androidx.compose.material.icons.filled.PublicKt { *; }
-keep class androidx.compose.material.icons.filled.RefreshKt { *; }
-keep class androidx.compose.material.icons.filled.Replay10Kt { *; }
-keep class androidx.compose.material.icons.filled.SearchKt { *; }
-keep class androidx.compose.material.icons.filled.SecurityKt { *; }
-keep class androidx.compose.material.icons.filled.SettingsKt { *; }
-keep class androidx.compose.material.icons.filled.ShareKt { *; }
-keep class androidx.compose.material.icons.filled.SkipNextKt { *; }
-keep class androidx.compose.material.icons.filled.SkipPreviousKt { *; }
-keep class androidx.compose.material.icons.filled.SportsSoccerKt { *; }
-keep class androidx.compose.material.icons.filled.StarKt { *; }
-keep class androidx.compose.material.icons.filled.TranslateKt { *; }
-keep class androidx.compose.material.icons.filled.TvKt { *; }
-keep class androidx.compose.material.icons.filled.TvOffKt { *; }
-keep class androidx.compose.material.icons.filled.UploadFileKt { *; }
-keep class androidx.compose.material.icons.filled.VideoLibraryKt { *; }
-keep class androidx.compose.material.icons.filled.ViewSidebarKt { *; }
-keep class androidx.compose.material.icons.filled.VisibilityKt { *; }
-keep class androidx.compose.material.icons.filled.VisibilityOffKt { *; }
-keep class androidx.compose.material.icons.filled.VolumeUpKt { *; }
-keep class androidx.compose.material.icons.filled.WifiOffKt { *; }
-keep class androidx.compose.material.icons.outlined.NotificationsKt { *; }

# Keep Guava collections referenced by precompiled-dex (e.g. Media3 ImmutableList track selections)
-keep class com.google.common.collect.ImmutableCollection { *; }
-keep class com.google.common.collect.ImmutableCollection$* { *; }
-keep class com.google.common.collect.ImmutableList { *; }
-keep class com.google.common.collect.ImmutableList$* { *; }
-keep class com.google.common.collect.RegularImmutableList { *; }
-keep class com.google.common.collect.SingletonImmutableList { *; }
-keep class com.google.common.collect.AbstractIndexedListIterator { *; }
-keep class com.google.common.collect.UnmodifiableIterator { *; }
-keep class com.google.common.collect.UnmodifiableListIterator { *; }
-keep class com.google.common.collect.ObjectArrays { *; }

# Protect volatile fields updated via AtomicReferenceFieldUpdater / AtomicIntegerFieldUpdater
-keepclassmembernames class com.google.common.** {
    volatile <fields>;
}
-keepclassmembernames class com.google.android.gms.internal.** {
    volatile <fields>;
}

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
