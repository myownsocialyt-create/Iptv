package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.data.model.AppLanguage

data class AppStrings(
    val appName: String = "Hypnotix IPTV",
    val searchPlaceholder: String = "Search live channels, movies, sports...",
    val featuredChannels: String = "Live TV Channels",
    val continueWatching: String = "Continue Watching",
    val noFavoritesYet: String = "No favorite channels saved yet.",
    val addPlaylist: String = "Add Playlist"
)

val LocalAppStrings = staticCompositionLocalOf { AppStrings() }

fun stringsForLanguage(language: AppLanguage): AppStrings {
    return when (language) {
        AppLanguage.ENGLISH -> AppStrings()
        AppLanguage.HINDI -> AppStrings(
            appName = "Hypnotix IPTV",
            searchPlaceholder = "लाइव चैनल, फ़िल्में या स्पोर्ट्स खोजें...",
            featuredChannels = "लाइव टीवी चैनल",
            continueWatching = "देखना जारी रखें",
            noFavoritesYet = "अभी तक कोई पसंदीदा चैनल नहीं जोड़ा गया है।",
            addPlaylist = "प्लेलिस्ट जोड़ें"
        )
        AppLanguage.SPANISH -> AppStrings(
            appName = "Hypnotix IPTV",
            searchPlaceholder = "Buscar canales en vivo, películas, deportes...",
            featuredChannels = "Canales en Vivo",
            continueWatching = "Continuar Viendo",
            noFavoritesYet = "Aún no tienes canales favoritos guardados.",
            addPlaylist = "Añadir Lista"
        )
        AppLanguage.ARABIC -> AppStrings(
            appName = "Hypnotix IPTV",
            searchPlaceholder = "ابحث عن القنوات المباشرة والأفلام...",
            featuredChannels = "القنوات المباشرة",
            continueWatching = "متابعة المشاهدة",
            noFavoritesYet = "لا توجد قنوات مفضلة حتى الآن.",
            addPlaylist = "إضافة قائمة"
        )
    }
}
