package com.example.data.model

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String, val flagEmoji: String) {
    ENGLISH("en", "English", "English", "🇺🇸"),
    HINDI("hi", "Hindi", "हिन्दी", "🇮🇳"),
    SPANISH("es", "Spanish", "Español", "🇪🇸"),
    FRENCH("fr", "French", "Français", "🇫🇷"),
    GERMAN("de", "German", "Deutsch", "🇩🇪"),
    ARABIC("ar", "Arabic", "العربية", "🇸🇦"),
    RUSSIAN("ru", "Russian", "Русский", "🇷🇺"),
    PORTUGUESE("pt", "Portuguese", "Português", "🇧🇷");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

enum class AppThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System Default"),
    LIGHT("light", "Light Mode"),
    DARK("dark", "Dark Mode");

    companion object {
        fun fromKey(key: String): AppThemeMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SYSTEM
        }
    }
}
