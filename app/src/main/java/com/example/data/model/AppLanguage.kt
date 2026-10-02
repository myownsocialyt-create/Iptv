package com.example.data.model

data class AppLanguage(
    val code: String,
    val name: String,
    val nativeName: String
)

object SupportedLanguages {
    val list = listOf(
        AppLanguage("en", "English", "English"),
        AppLanguage("hi", "Hindi", "हिन्दी"),
        AppLanguage("es", "Spanish", "Español"),
        AppLanguage("fr", "French", "Français"),
        AppLanguage("de", "German", "Deutsch"),
        AppLanguage("ar", "Arabic", "العربية"),
        AppLanguage("pt", "Portuguese", "Português"),
        AppLanguage("ru", "Russian", "Русский"),
        AppLanguage("bn", "Bengali", "বাংলা"),
        AppLanguage("ta", "Tamil", "தமிழ்"),
        AppLanguage("te", "Telugu", "తెలుగు"),
        AppLanguage("mr", "Marathi", "मराठी"),
        AppLanguage("ur", "Urdu", "اردو")
    )
}
