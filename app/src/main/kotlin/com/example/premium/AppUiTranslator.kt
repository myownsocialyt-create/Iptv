package com.example.premium

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference
import java.util.Locale

/**
 * Centralized multi-language runtime translator across all 8 app languages
 * (`en`, `hi`, `es`, `fr`, `de`, `pt`, `ar`, `ru`).
 *
 * Used by both custom Kotlin UI screens/hooks/dialogs and precompiled DEX Compose/Toast/Player
 * error call sites so non-English languages translate consistently without modifying dynamic
 * channel names, movie titles, or user-defined playlist names.
 */
object AppUiTranslator {
    private const val PREFS_MAIN = "hypnotix_prefs"
    private const val KEY_MAIN_LANG = "app_language_code"
    private const val PREFS_ONBOARDING = "hypnotix_onboarding_prefs"
    private const val KEY_ONBOARDING_LANG = "selected_app_language"

    private val SUPPORTED_CODES = setOf("en", "hi", "es", "fr", "de", "pt", "ar", "ru")

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var currentCode: String = "en"

    @Volatile
    private var hasSyncedFromPrefs: Boolean = false

    val languageCodeState = mutableStateOf("en")

    @Volatile
    private var prefsListenerRef: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Volatile
    private var listeningContextRef: WeakReference<Context>? = null

    private val exactMap: HashMap<String, Map<String, String>> by lazy {
        HashMap<String, Map<String, String>>(420).apply {
            val adder: (String, Map<String, String>) -> Unit = { key, map ->
                this[key] = map
            }
            TranslationsNavigationAndHome.populate(adder)
            TranslationsPlayerAndXtream.populate(adder)
            TranslationsPremiumCastSupport.populate(adder)
        }
    }

    private fun normalizeCode(raw: String?): String {
        val clean = raw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (clean in SUPPORTED_CODES) return clean
        val prefix = clean.substringBefore('-').substringBefore('_')
        return if (prefix in SUPPORTED_CODES) prefix else "en"
    }

    @JvmStatic
    fun setLanguageCode(code: String?) {
        val normalized = normalizeCode(code)
        hasSyncedFromPrefs = true
        currentCode = normalized
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (languageCodeState.value != normalized) {
                languageCodeState.value = normalized
            }
        } else {
            mainHandler.post {
                if (languageCodeState.value != normalized) {
                    languageCodeState.value = normalized
                }
            }
        }
    }

    @JvmStatic
    fun setLanguageCodeFromEnum(langEnum: Any?) {
        if (langEnum == null) return
        try {
            val code = langEnum.javaClass.getMethod("getCode").invoke(langEnum) as? String
            if (!code.isNullOrBlank()) {
                setLanguageCode(code)
                return
            }
        } catch (_: Throwable) {
        }
        val fallback = when (langEnum.toString().uppercase(Locale.ROOT)) {
            "HINDI" -> "hi"
            "SPANISH" -> "es"
            "FRENCH" -> "fr"
            "GERMAN" -> "de"
            "PORTUGUESE" -> "pt"
            "ARABIC" -> "ar"
            "RUSSIAN" -> "ru"
            else -> "en"
        }
        setLanguageCode(fallback)
    }

    fun syncFromContext(context: Context?) {
        if (context == null) return
        val appCtx = context.applicationContext ?: context
        if (prefsListenerRef == null) {
            try {
                val mainPrefs = appCtx.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    if (key == KEY_MAIN_LANG) {
                        val updated = sp.getString(KEY_MAIN_LANG, null)
                        if (!updated.isNullOrBlank()) {
                            setLanguageCode(updated)
                        }
                    }
                }
                mainPrefs.registerOnSharedPreferenceChangeListener(listener)
                prefsListenerRef = listener
                listeningContextRef = WeakReference(appCtx)
            } catch (_: Throwable) {
            }
        }

        if (hasSyncedFromPrefs) return
        try {
            val mainPrefs = appCtx.getSharedPreferences(PREFS_MAIN, Context.MODE_PRIVATE)
            val savedMain = mainPrefs.getString(KEY_MAIN_LANG, null)
            if (!savedMain.isNullOrBlank()) {
                setLanguageCode(savedMain)
                return
            }
            val onboardingPrefs = appCtx.getSharedPreferences(PREFS_ONBOARDING, Context.MODE_PRIVATE)
            val savedOnboarding = onboardingPrefs.getString(KEY_ONBOARDING_LANG, null)
            if (!savedOnboarding.isNullOrBlank()) {
                setLanguageCode(savedOnboarding)
                return
            }
            hasSyncedFromPrefs = true
        } catch (_: Throwable) {
        }
    }

    @Composable
    fun rememberLanguageCode(): String {
        val ctx = LocalContext.current
        syncFromContext(ctx)
        return languageCodeState.value
    }

    @Composable
    fun t(text: String): String {
        val ctx = LocalContext.current
        syncFromContext(ctx)
        val lang = languageCodeState.value
        return translateInternal(text, lang)
    }

    @JvmStatic
    fun tx(text: String, context: Context? = null): String {
        if (context != null) {
            syncFromContext(context)
        }
        return translateInternal(text, currentCode)
    }

    @JvmStatic
    fun translateDexText(text: String?): String {
        val raw = text ?: return ""
        val lang = languageCodeState.value
        if (lang == "en" || raw.isEmpty()) return raw
        return translateInternal(raw, lang)
    }

    @JvmStatic
    fun translateDexCharSeq(text: CharSequence?): CharSequence? {
        if (text == null) return null
        val lang = languageCodeState.value
        if (lang == "en") return text
        return translateInternal(text.toString(), lang)
    }

    @JvmStatic
    fun translateDexAnnotatedText(text: androidx.compose.ui.text.AnnotatedString?): androidx.compose.ui.text.AnnotatedString? {
        if (text == null) return null
        val lang = languageCodeState.value
        if (lang == "en" || text.text.isEmpty()) return text
        val raw = text.text
        val translated = when {
            raw.startsWith("• User Content Only:") -> when (lang) {
                "hi" -> "• केवल उपयोगकर्ता सामग्री: ऐप में जोड़ी गई किसी भी स्ट्रीम का उपयोग करने के अधिकार की जिम्मेदारी उपयोगकर्ता की है।"
                "es" -> "• Solo contenido del usuario: los usuarios son responsables de los derechos de cada stream añadido."
                "fr" -> "• Contenu utilisateur : l'utilisateur est responsable des droits des flux ajoutés."
                "de" -> "• Nur Nutzerinhalte: Nutzer sind für die Rechte der hinzugefügten Streams verantwortlich."
                "pt" -> "• Conteúdo do usuário: os usuários são responsáveis pelos direitos dos streams adicionados."
                "ar" -> "• محتوى المستخدم فقط: المستخدم مسؤول عن حقوق أي بث يتم إضافته."
                "ru" -> "• Контент пользователя: пользователь отвечает за права на добавленные потоки."
                else -> raw
            }
            raw.startsWith("• No Third-Party Affiliation:") -> when (lang) {
                "hi" -> "• कोई थर्ड-पार्टी संबद्धता नहीं: Hypnotix किसी बाहरी IPTV प्रदाता से जुड़ा नहीं है।"
                "es" -> "• Sin afiliación de terceros: Hypnotix no está vinculado a proveedores IPTV externos."
                "fr" -> "• Aucune affiliation tierce : Hypnotix n'est lié à aucun fournisseur IPTV."
                "de" -> "• Keine Drittanbieter-Bindung: Hypnotix ist mit keinem IPTV-Anbieter verbunden."
                "pt" -> "• Sem vínculo com terceiros: Hypnotix não tem relação com provedores IPTV."
                "ar" -> "• لا ارتباط بطرف ثالث: التطبيق غير مرتبط بأي مزود IPTV خارجي."
                "ru" -> "• Без сторонних связей: Hypnotix не связан со сторонними IPTV-провайдерами."
                else -> raw
            }
            raw.startsWith("• Public/Preloaded Lists:") -> when (lang) {
                "hi" -> "• पब्लिक/प्रीलोडेड सूचियां: पहले से लोड की गई स्ट्रीम केवल ओपन-सोर्स और बिना कॉपीराइट वाली हैं।"
                "es" -> "• Listas públicas/precargadas: solo contienen streams libres de código abierto."
                "fr" -> "• Listes publiques : ne contiennent que des flux libres et open source."
                "de" -> "• Öffentliche Listen: enthalten nur frei verfügbare Open-Source-Streams."
                "pt" -> "• Listas públicas: contêm apenas transmissões abertas e sem direitos autorais."
                "ar" -> "• القوائم العامة: تحتوي فقط على بثوث مجانية ومفتوحة المصدر."
                "ru" -> "• Публичные списки: содержат только свободные открытые потоки."
                else -> raw
            }
            raw.startsWith("• Copyright Compliance:") -> when (lang) {
                "hi" -> "• कॉपीराइट अनुपालन: हम अनधिकृत कॉपीराइट स्ट्रीमिंग का समर्थन नहीं करते।"
                "es" -> "• Cumplimiento de derechos: no apoyamos la retransmisión no autorizada."
                "fr" -> "• Droits d'auteur : nous ne soutenons pas le streaming non autorisé."
                "de" -> "• Urheberrecht: Unbefugtes Streaming wird nicht unterstützt."
                "pt" -> "• Direitos autorais: não apoiamos transmissão não autorizada."
                "ar" -> "• حقوق النشر: لا ندعم البث غير المصرح به."
                "ru" -> "• Авторские права: мы не поддерживаем нелегальные трансляции."
                else -> raw
            }
            raw.contains("I agree to these terms &") -> when (lang) {
                "hi" -> "मैं इन शर्तों और गोपनीयता नीति से सहमत हूँ"
                "es" -> "Acepto estos términos y la Política de privacidad"
                "fr" -> "J'accepte ces conditions et la Confidentialité"
                "de" -> "Ich akzeptiere diese Bedingungen & den Datenschutz"
                "pt" -> "Concordo com estes termos e a Política de Privacidade"
                "ar" -> "أوافق على هذه الشروط وسياسة الخصوصية"
                "ru" -> "Я принимаю эти условия и Политику конфиденциальности"
                else -> raw
            }
            else -> translateInternal(raw, lang)
        }
        return if (translated == raw) text else androidx.compose.ui.text.AnnotatedString(translated)
    }

    private fun lookup(key: String, lang: String): String? {
        return exactMap[key]?.get(lang)
    }

    internal fun translateInternal(text: String, lang: String): String {
        if (lang == "en" || text.isEmpty()) return text

        lookup(text, lang)?.let { return it }

        val trimmed = text.trim()
        if (trimmed != text) {
            lookup(trimmed, lang)?.let { mapped ->
                val lead = text.substring(0, text.indexOf(trimmed))
                val trail = text.substring(text.indexOf(trimmed) + trimmed.length)
                return "$lead$mapped$trail"
            }
        }

        // Dynamic UI pattern matching
        translatePattern(trimmed, lang)?.let { return it }

        return text
    }

    private fun formatCountSuffix(baseEn: String, count: String, lang: String): String {
        val translatedLabel = lookup(baseEn, lang) ?: baseEn
        return "$translatedLabel ($count)"
    }

    private fun translatePattern(text: String, lang: String): String? {
        // Playing: <Channel Name>
        if (text.startsWith("Playing: ")) {
            val rest = text.removePrefix("Playing: ")
            val label = when (lang) {
                "hi" -> "चल रहा है: "
                "es" -> "Reproduciendo: "
                "fr" -> "En lecture : "
                "de" -> "Läuft: "
                "pt" -> "Reproduzindo: "
                "ar" -> "قيد التشغيل: "
                "ru" -> "Играет: "
                else -> "Playing: "
            }
            return "$label$rest"
        }

        // Now streaming: <Channel Name>
        if (text.startsWith("Now streaming: ")) {
            val rest = translateInternal(text.removePrefix("Now streaming: "), lang)
            val label = when (lang) {
                "hi" -> "अभी चल रहा है: "
                "es" -> "Transmitiendo: "
                "fr" -> "Diffusion en cours : "
                "de" -> "Läuft gerade: "
                "pt" -> "Transmitindo agora: "
                "ar" -> "يبث الآن: "
                "ru" -> "Сейчас в эфире: "
                else -> "Now streaming: "
            }
            return "$label$rest"
        }

        // Count headers
        Regex("^Channels \\((\\d+)\\)$").matchEntire(text)?.let {
            return formatCountSuffix("Channels", it.groupValues[1], lang)
        }
        Regex("^ADDED PLAYLISTS \\((\\d+)\\)$").matchEntire(text)?.let {
            val lbl = when (lang) {
                "hi" -> "जोड़ी गई प्लेलिस्ट"
                "es" -> "LISTAS AÑADIDAS"
                "fr" -> "LISTES AJOUTÉES"
                "de" -> "EIGENE PLAYLISTS"
                "pt" -> "LISTAS ADICIONADAS"
                "ar" -> "القوائم المضافة"
                "ru" -> "ДОБАВЛЕННЫЕ ПЛЕЙЛИСТЫ"
                else -> "ADDED PLAYLISTS"
            }
            return "$lbl (${it.groupValues[1]})"
        }
        Regex("^XTREAM ACCOUNTS \\((\\d+)\\)$").matchEntire(text)?.let {
            val lbl = when (lang) {
                "hi" -> "XTREAM खाते"
                "es" -> "CUENTAS XTREAM"
                "fr" -> "COMPTES XTREAM"
                "de" -> "XTREAM-KONTEN"
                "pt" -> "CONTAS XTREAM"
                "ar" -> "حسابات XTREAM"
                "ru" -> "АККАУНТЫ XTREAM"
                else -> "XTREAM ACCOUNTS"
            }
            return "$lbl (${it.groupValues[1]})"
        }
        Regex("^All Channels & Streams \\((\\d+)\\)$").matchEntire(text)?.let {
            val lbl = when (lang) {
                "hi" -> "सभी चैनल और स्ट्रीम"
                "es" -> "Todos los canales y streams"
                "fr" -> "Toutes les chaînes et flux"
                "de" -> "Alle Kanäle & Streams"
                "pt" -> "Todos os canais e streams"
                "ar" -> "جميع القنوات والبثوث"
                "ru" -> "Все каналы и потоки"
                else -> "All Channels & Streams"
            }
            return "$lbl (${it.groupValues[1]})"
        }
        Regex("^(\\d+) Available$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "$n उपलब्ध"
                "es" -> "$n disponibles"
                "fr" -> "$n disponibles"
                "de" -> "$n verfügbar"
                "pt" -> "$n disponíveis"
                "ar" -> "$n متاح"
                "ru" -> "Доступно: $n"
                else -> text
            }
        }
        Regex("^(\\d+) Channels$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            val ch = lookup("Channels", lang) ?: "Channels"
            return "$n $ch"
        }

        // Empty search / playlist quoted templates
        Regex("^No channels found in \"(.*)\"$").matchEntire(text)?.let {
            val q = translateInternal(it.groupValues[1], lang)
            return when (lang) {
                "hi" -> "\"$q\" में कोई चैनल नहीं मिला"
                "es" -> "No hay canales en \"$q\""
                "fr" -> "Aucune chaîne dans \"$q\""
                "de" -> "Keine Kanäle in \"$q\" gefunden"
                "pt" -> "Nenhum canal encontrado em \"$q\""
                "ar" -> "لا توجد قنوات في \"$q\""
                "ru" -> "В «$q» каналы не найдены"
                else -> text
            }
        }
        Regex("^No channels matching \"(.*)\"$").matchEntire(text)?.let {
            val q = it.groupValues[1]
            return when (lang) {
                "hi" -> "\"$q\" से मेल खाने वाला कोई चैनल नहीं"
                "es" -> "Sin canales que coincidan con \"$q\""
                "fr" -> "Aucune chaîne pour \"$q\""
                "de" -> "Keine Kanäle passend zu \"$q\""
                "pt" -> "Nenhum canal para \"$q\""
                "ar" -> "لا توجد قنوات تطابق \"$q\""
                "ru" -> "Нет каналов по запросу «$q»"
                else -> text
            }
        }
        Regex("^No channels found for \"(.*)\"$").matchEntire(text)?.let {
            val q = it.groupValues[1]
            return when (lang) {
                "hi" -> "\"$q\" के लिए कोई चैनल नहीं मिला"
                "es" -> "No se encontraron canales para \"$q\""
                "fr" -> "Aucune chaîne trouvée pour \"$q\""
                "de" -> "Keine Kanäle für \"$q\" gefunden"
                "pt" -> "Nenhum canal encontrado para \"$q\""
                "ar" -> "لم يتم العثور على قنوات لـ \"$q\""
                "ru" -> "По запросу «$q» каналы не найдены"
                else -> text
            }
        }
        Regex("^No channels loaded for \"(.*)\"$").matchEntire(text)?.let {
            val q = it.groupValues[1]
            return when (lang) {
                "hi" -> "\"$q\" के लिए कोई चैनल लोड नहीं हुआ"
                "es" -> "No se cargaron canales para \"$q\""
                "fr" -> "Aucune chaîne chargée pour \"$q\""
                "de" -> "Keine Kanäle für \"$q\" geladen"
                "pt" -> "Nenhum canal carregado para \"$q\""
                "ar" -> "لم يتم تحميل قنوات لـ \"$q\""
                "ru" -> "Нет загруженных каналов для «$q»"
                else -> text
            }
        }
        Regex("^No custom playlists matching \"(.*)\"$").matchEntire(text)?.let {
            val q = it.groupValues[1]
            return when (lang) {
                "hi" -> "\"$q\" से मेल खाती कोई कस्टम प्लेलिस्ट नहीं"
                "es" -> "Sin listas propias para \"$q\""
                "fr" -> "Aucune liste personnalisée pour \"$q\""
                "de" -> "Keine eigenen Playlists für \"$q\""
                "pt" -> "Nenhuma lista personalizada para \"$q\""
                "ar" -> "لا توجد قوائم مخصصة تطابق \"$q\""
                "ru" -> "Нет своих плейлистов по «$q»"
                else -> text
            }
        }
        Regex("^No preset playlists matching \"(.*)\"$").matchEntire(text)?.let {
            val q = it.groupValues[1]
            return when (lang) {
                "hi" -> "\"$q\" से मेल खाती कोई प्रीसेट प्लेलिस्ट नहीं"
                "es" -> "Sin listas predefinidas para \"$q\""
                "fr" -> "Aucune liste prédéfinie pour \"$q\""
                "de" -> "Keine vordefinierten Playlists für \"$q\""
                "pt" -> "Nenhuma lista predefinida para \"$q\""
                "ar" -> "لا توجد قوائم مسبقة تطابق \"$q\""
                "ru" -> "Нет готовых плейлистов по «$q»"
                else -> text
            }
        }
        Regex("^Are you sure you want to remove profile \"(.*)\"\\??$").matchEntire(text)?.let {
            val name = it.groupValues[1]
            return when (lang) {
                "hi" -> "क्या आप वाकई प्रोफ़ाइल \"$name\" को हटाना चाहते हैं?"
                "es" -> "¿Seguro que deseas eliminar el perfil \"$name\"?"
                "fr" -> "Voulez-vous vraiment supprimer le profil \"$name\" ?"
                "de" -> "Profil \"$name\" wirklich entfernen?"
                "pt" -> "Tem certeza que deseja remover o perfil \"$name\"?"
                "ar" -> "هل أنت متأكد من إزالة الملف \"$name\"؟"
                "ru" -> "Удалить профиль «$name»?"
                else -> text
            }
        }

        // Player & Network errors
        Regex("^Channel server error \\(HTTP (\\d+)\\)(.*)$").matchEntire(text)?.let {
            val code = it.groupValues[1]
            val retryHint = when (lang) {
                "hi" -> "चैनल सर्वर त्रुटि (HTTP $code)। कृपया बाद में पुनः प्रयास करें।"
                "es" -> "Error del servidor (HTTP $code). Inténtalo más tarde."
                "fr" -> "Erreur serveur de chaîne (HTTP $code). Réessayez plus tard."
                "de" -> "Serverfehler (HTTP $code). Bitte später erneut versuchen."
                "pt" -> "Erro no servidor do canal (HTTP $code). Tente mais tarde."
                "ar" -> "خطأ في خادم القناة (HTTP $code). يرجى المحاولة لاحقاً."
                "ru" -> "Ошибка сервера канала (HTTP $code). Попробуйте позже."
                else -> text
            }
            return retryHint
        }
        Regex("^Channel stream offline or not found \\(HTTP (\\d+)\\)(.*)$").matchEntire(text)?.let {
            val code = it.groupValues[1]
            return when (lang) {
                "hi" -> "चैनल स्ट्रीम ऑफ़लाइन है या नहीं मिली (HTTP $code)। कोई अन्य सर्वर या चैनल आज़माएं।"
                "es" -> "Canal sin conexión o no encontrado (HTTP $code). Prueba otro servidor o canal."
                "fr" -> "Flux hors ligne ou introuvable (HTTP $code). Essayez une autre chaîne."
                "de" -> "Stream offline oder nicht gefunden (HTTP $code). Anderen Kanal testen."
                "pt" -> "Stream offline ou não encontrado (HTTP $code). Tente outro canal."
                "ar" -> "البث غير متصل أو غير موجود (HTTP $code). جرب قناة أخرى."
                "ru" -> "Поток не в сети или не найден (HTTP $code). Попробуйте другой канал."
                else -> text
            }
        }
        Regex("^Stream access denied \\(HTTP (\\d+)\\)(.*)$").matchEntire(text)?.let {
            val code = it.groupValues[1]
            return when (lang) {
                "hi" -> "स्ट्रीम एक्सेस अस्वीकृत (HTTP $code)। कोई अन्य सर्वर या चैनल आज़माएं।"
                "es" -> "Acceso al stream denegado (HTTP $code). Prueba otro servidor o canal."
                "fr" -> "Accès au flux refusé (HTTP $code). Essayez une autre chaîne."
                "de" -> "Stream-Zugriff verweigert (HTTP $code). Anderen Server testen."
                "pt" -> "Acesso ao stream negado (HTTP $code). Tente outro servidor ou canal."
                "ar" -> "تم رفض الوصول للبث (HTTP $code). جرب خادماً أو قناة أخرى."
                "ru" -> "Доступ к потоку запрещён (HTTP $code). Попробуйте другой канал."
                else -> text
            }
        }
        if (text.startsWith("Unable to open stream:")) {
            val suffix = text.removePrefix("Unable to open stream:").trim()
            val prefix = when (lang) {
                "hi" -> "स्ट्रीम खोलने में असमर्थ:"
                "es" -> "No se pudo abrir el stream:"
                "fr" -> "Impossible d'ouvrir le flux :"
                "de" -> "Stream kann nicht geöffnet werden:"
                "pt" -> "Não foi possível abrir o stream:"
                "ar" -> "تعذر فتح البث:"
                "ru" -> "Не удалось открыть поток:"
                else -> "Unable to open stream:"
            }
            return if (suffix.isEmpty()) prefix else "$prefix $suffix"
        }
        if (text.startsWith("Xtream stream error")) {
            var rest = text.removePrefix("Xtream stream error")
            if (rest.contains("[Timeout after 40s]")) {
                val timeoutLabel = when (lang) {
                    "hi" -> "[40 सेकंड के बाद टाइमआउट]"
                    "es" -> "[Tiempo agotado tras 40s]"
                    "fr" -> "[Délai expiré après 40s]"
                    "de" -> "[Zeitüberschreitung nach 40s]"
                    "pt" -> "[Tempo esgotado após 40s]"
                    "ar" -> "[انتهت المهلة بعد 40 ثانية]"
                    "ru" -> "[Тайм-аут после 40с]"
                    else -> "[Timeout after 40s]"
                }
                rest = rest.replace("[Timeout after 40s]", timeoutLabel)
            }
            val head = when (lang) {
                "hi" -> "Xtream स्ट्रीम त्रुटि"
                "es" -> "Error de stream Xtream"
                "fr" -> "Erreur de flux Xtream"
                "de" -> "Xtream-Stream-Fehler"
                "pt" -> "Erro no stream Xtream"
                "ar" -> "خطأ في بث Xtream"
                "ru" -> "Ошибка потока Xtream"
                else -> "Xtream stream error"
            }
            return "$head$rest"
        }
        if (text.startsWith("Failed to load playlist: HTTP ")) {
            val code = text.removePrefix("Failed to load playlist: HTTP ")
            return when (lang) {
                "hi" -> "प्लेलिस्ट लोड करने में विफल: HTTP $code"
                "es" -> "Error al cargar la lista: HTTP $code"
                "fr" -> "Échec du chargement : HTTP $code"
                "de" -> "Playlist konnte nicht geladen werden: HTTP $code"
                "pt" -> "Falha ao carregar lista: HTTP $code"
                "ar" -> "فشل تحميل القائمة: HTTP $code"
                "ru" -> "Ошибка загрузки плейлиста: HTTP $code"
                else -> text
            }
        }
        if (text.startsWith("Playlist file ready:")) {
            val name = text.removePrefix("Playlist file ready:").trim()
            val lbl = when (lang) {
                "hi" -> "प्लेलिस्ट फ़ाइल तैयार:"
                "es" -> "Archivo de lista listo:"
                "fr" -> "Fichier prêt :"
                "de" -> "Playlist-Datei bereit:"
                "pt" -> "Arquivo pronto:"
                "ar" -> "ملف القائمة جاهز:"
                "ru" -> "Файл плейлиста готов:"
                else -> "Playlist file ready:"
            }
            return "$lbl $name"
        }
        if (text.startsWith("Failed to load .m3u / .m3u8 file:")) {
            val rest = text.removePrefix("Failed to load .m3u / .m3u8 file:").trim()
            val lbl = when (lang) {
                "hi" -> ".m3u / .m3u8 फ़ाइल लोड करने में विफल:"
                "es" -> "Error al cargar archivo .m3u / .m3u8:"
                "fr" -> "Échec de chargement .m3u / .m3u8 :"
                "de" -> ".m3u/.m3u8-Datei konnte nicht geladen werden:"
                "pt" -> "Falha ao carregar arquivo .m3u / .m3u8:"
                "ar" -> "فشل تحميل ملف .m3u / .m3u8:"
                "ru" -> "Не удалось загрузить .m3u / .m3u8:"
                else -> "Failed to load .m3u / .m3u8 file:"
            }
            return "$lbl $rest"
        }

        // Player indicators / speed / billing suffixes / theme description
        if (text.startsWith("Playback speed ")) {
            val sp = text.removePrefix("Playback speed ")
            return when (lang) {
                "hi" -> "प्लेबैक गति $sp"
                "es" -> "Velocidad $sp"
                "fr" -> "Vitesse $sp"
                "de" -> "Geschwindigkeit $sp"
                "pt" -> "Velocidade $sp"
                "ar" -> "سرعة التشغيل $sp"
                "ru" -> "Скорость $sp"
                else -> text
            }
        }
        if (text.startsWith("Brightness ")) {
            val v = text.removePrefix("Brightness ")
            return "${lookup("Brightness", lang) ?: "Brightness"} $v"
        }
        if (text.startsWith("Volume ")) {
            val v = text.removePrefix("Volume ")
            return "${lookup("Volume", lang) ?: "Volume"} $v"
        }
        if (text.endsWith("/Month")) {
            val p = text.removeSuffix("/Month")
            val s = when (lang) {
                "hi" -> "/माह"
                "es" -> "/Mes"
                "fr" -> "/Mois"
                "de" -> "/Monat"
                "pt" -> "/Mês"
                "ar" -> "/شهرياً"
                "ru" -> "/Мес"
                else -> "/Month"
            }
            return "$p$s"
        }
        if (text.endsWith("/Year")) {
            val p = text.removeSuffix("/Year")
            val s = when (lang) {
                "hi" -> "/वर्ष"
                "es" -> "/Año"
                "fr" -> "/An"
                "de" -> "/Jahr"
                "pt" -> "/Ano"
                "ar" -> "/سنوياً"
                "ru" -> "/Год"
                else -> "/Year"
            }
            return "$p$s"
        }
        if (text == "of 4" || text.endsWith(" of 4")) {
            val n = text.removeSuffix("of 4").trim()
            val tail = when (lang) {
                "hi" -> "4 में से"
                "es" -> "de 4"
                "fr" -> "sur 4"
                "de" -> "von 4"
                "pt" -> "de 4"
                "ar" -> "من 4"
                "ru" -> "из 4"
                else -> "of 4"
            }
            return if (n.isEmpty()) tail else "$n $tail"
        }
        if (text == "Following device theme (Currently Dark)." || text == "Following device theme (Currently Light).") {
            val isDarkNow = text.contains("Dark")
            val modeLbl = if (isDarkNow) (lookup("Dark", lang) ?: "Dark") else (lookup("Light", lang) ?: "Light")
            return when (lang) {
                "hi" -> "डिवाइस थीम का पालन (वर्तमान: $modeLbl)।"
                "es" -> "Siguiendo tema del dispositivo (Actual: $modeLbl)."
                "fr" -> "Thème de l'appareil suivi (Actuel : $modeLbl)."
                "de" -> "Folgt Geräte-Design (Aktuell: $modeLbl)."
                "pt" -> "Seguindo tema do dispositivo (Atual: $modeLbl)."
                "ar" -> "يتبع مظهر الجهاز (الحالي: $modeLbl)."
                "ru" -> "По теме устройства (сейчас: $modeLbl)."
                else -> text
            }
        }

        // EPG & Notification relative times
        Regex("^in (\\d+)m$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "${n} मिनट में"
                "es" -> "en ${n}m"
                "fr" -> "dans ${n} min"
                "de" -> "in ${n} Min."
                "pt" -> "em ${n}m"
                "ar" -> "خلال ${n} د"
                "ru" -> "через ${n} мин"
                else -> text
            }
        }
        Regex("^in (\\d+)h$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "${n} घंटे में"
                "es" -> "en ${n}h"
                "fr" -> "dans ${n}h"
                "de" -> "in ${n} Std."
                "pt" -> "em ${n}h"
                "ar" -> "خلال ${n} س"
                "ru" -> "через ${n} ч"
                else -> text
            }
        }
        Regex("^in (\\d+)h (\\d+)m$").matchEntire(text)?.let {
            val h = it.groupValues[1]
            val mins = it.groupValues[2]
            return when (lang) {
                "hi" -> "${h} घं ${mins} मि में"
                "es" -> "en ${h}h ${mins}m"
                "fr" -> "dans ${h}h ${mins}min"
                "de" -> "in ${h}h ${mins}m"
                "pt" -> "em ${h}h ${mins}m"
                "ar" -> "خلال ${h}س ${mins}د"
                "ru" -> "через ${h}ч ${mins}м"
                else -> text
            }
        }
        Regex("^(\\d+)d ago$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "${n} दिन पहले"
                "es" -> "hace ${n} d"
                "fr" -> "il y a ${n} j"
                "de" -> "vor ${n} T"
                "pt" -> "há ${n} d"
                "ar" -> "منذ ${n} ي"
                "ru" -> "${n} дн. назад"
                else -> text
            }
        }
        Regex("^(\\d+)h ago$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "${n} घं पहले"
                "es" -> "hace ${n} h"
                "fr" -> "il y a ${n} h"
                "de" -> "vor ${n} Std."
                "pt" -> "há ${n} h"
                "ar" -> "منذ ${n} س"
                "ru" -> "${n} ч. назад"
                else -> text
            }
        }
        Regex("^(\\d+)m ago$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "${n} मि पहले"
                "es" -> "hace ${n} min"
                "fr" -> "il y a ${n} min"
                "de" -> "vor ${n} Min."
                "pt" -> "há ${n} min"
                "ar" -> "منذ ${n} د"
                "ru" -> "${n} мин назад"
                else -> text
            }
        }

        // Cast dynamic templates
        Regex("^Same Wi-Fi TVs \\(IP: (.+)\\)$").matchEntire(text)?.let {
            val ip = it.groupValues[1]
            return when (lang) {
                "hi" -> "एक ही Wi-Fi पर टीवी (IP: $ip)"
                "es" -> "TVs en el mismo Wi-Fi (IP: $ip)"
                "fr" -> "TV sur le même Wi-Fi (IP : $ip)"
                "de" -> "TVs im selben WLAN (IP: $ip)"
                "pt" -> "TVs no mesmo Wi-Fi (IP: $ip)"
                "ar" -> "تلفزيونات على نفس Wi-Fi (IP: $ip)"
                "ru" -> "ТВ в том же Wi-Fi (IP: $ip)"
                else -> text
            }
        }
        Regex("^Active • (\\d+) TV browser\\(s\\) connected$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "सक्रिय • $n टीवी ब्राउज़र जुड़ा"
                "es" -> "Activo • $n navegador(es) de TV conectado(s)"
                "fr" -> "Actif • $n navigateur(s) TV connecté(s)"
                "de" -> "Aktiv • $n TV-Browser verbunden"
                "pt" -> "Ativo • $n navegador(es) de TV conectado(s)"
                "ar" -> "نشط • $n متصفح تلفاز متصل"
                "ru" -> "Активен • подключено ТВ: $n"
                else -> text
            }
        }
        Regex("^Found (\\d+) device\\(s\\) on network$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "नेटवर्क पर $n डिवाइस मिले"
                "es" -> "$n dispositivo(s) encontrado(s) en la red"
                "fr" -> "$n appareil(s) trouvé(s) sur le réseau"
                "de" -> "$n Gerät(e) im Netzwerk gefunden"
                "pt" -> "$n dispositivo(s) encontrado(s) na rede"
                "ar" -> "تم العثور على $n جهاز على الشبكة"
                "ru" -> "Найдено устройств в сети: $n"
                else -> text
            }
        }
        if (text.startsWith("Web TV Active at ")) {
            val u = text.removePrefix("Web TV Active at ")
            return when (lang) {
                "hi" -> "Web TV सक्रिय: $u"
                "es" -> "Web TV activo en $u"
                "fr" -> "Web TV actif sur $u"
                "de" -> "Web-TV aktiv unter $u"
                "pt" -> "Web TV ativo em $u"
                "ar" -> "Web TV نشط على $u"
                "ru" -> "Web TV активен: $u"
                else -> text
            }
        }
        if (text.startsWith("Web TV synced: ")) {
            val ch = text.removePrefix("Web TV synced: ")
            return when (lang) {
                "hi" -> "Web TV सिंक हुआ: $ch"
                "es" -> "Web TV sincronizado: $ch"
                "fr" -> "Web TV synchronisé : $ch"
                "de" -> "Web-TV synchronisiert: $ch"
                "pt" -> "Web TV sincronizado: $ch"
                "ar" -> "تمت مزامنة Web TV: $ch"
                "ru" -> "Web TV синхронизирован: $ch"
                else -> text
            }
        }
        if (text.startsWith("Web TV Ready: Open ") && text.endsWith(" on Smart TV Browser")) {
            val u = text.removePrefix("Web TV Ready: Open ").removeSuffix(" on Smart TV Browser")
            return when (lang) {
                "hi" -> "Web TV तैयार: स्मार्ट टीवी ब्राउज़र पर $u खोलें"
                "es" -> "Web TV listo: abre $u en tu Smart TV"
                "fr" -> "Web TV prêt : ouvrez $u sur le navigateur TV"
                "de" -> "Web-TV bereit: $u im Smart-TV-Browser öffnen"
                "pt" -> "Web TV pronto: abra $u no navegador da TV"
                "ar" -> "Web TV جاهز: افتح $u في متصفح التلفاز"
                "ru" -> "Web TV готов: откройте $u в браузере ТВ"
                else -> text
            }
        }
        if (text.startsWith("Probing Smart TV at ")) {
            val ip = text.removePrefix("Probing Smart TV at ").removeSuffix("...")
            return when (lang) {
                "hi" -> "$ip पर स्मार्ट टीवी खोजा जा रहा है..."
                "es" -> "Probando Smart TV en $ip..."
                "fr" -> "Test de la Smart TV sur $ip..."
                "de" -> "Suche Smart-TV unter $ip..."
                "pt" -> "Verificando Smart TV em $ip..."
                "ar" -> "جاري فحص التلفاز على $ip..."
                "ru" -> "Проверка Smart TV по адресу $ip..."
                else -> text
            }
        }
        if (text.startsWith("Found Smart TV at ")) {
            val ip = text.removePrefix("Found Smart TV at ").removeSuffix("!")
            return when (lang) {
                "hi" -> "$ip पर स्मार्ट टीवी मिल गया!"
                "es" -> "¡Smart TV encontrado en $ip!"
                "fr" -> "Smart TV trouvée sur $ip !"
                "de" -> "Smart-TV unter $ip gefunden!"
                "pt" -> "Smart TV encontrada em $ip!"
                "ar" -> "تم العثور على التلفاز في $ip!"
                "ru" -> "Smart TV найден: $ip!"
                else -> text
            }
        }
        if (text.startsWith("Smart TV connected from ")) {
            val ip = text.removePrefix("Smart TV connected from ")
            return when (lang) {
                "hi" -> "$ip से स्मार्ट टीवी कनेक्ट हुआ"
                "es" -> "Smart TV conectado desde $ip"
                "fr" -> "Smart TV connectée depuis $ip"
                "de" -> "Smart-TV verbunden von $ip"
                "pt" -> "Smart TV conectada de $ip"
                "ar" -> "تم اتصال التلفاز من $ip"
                "ru" -> "Smart TV подключён с $ip"
                else -> text
            }
        }
        if (text.startsWith("Copied Web TV URL: ")) {
            val u = text.removePrefix("Copied Web TV URL: ")
            return when (lang) {
                "hi" -> "Web TV URL कॉपी किया गया: $u"
                "es" -> "URL Web TV copiada: $u"
                "fr" -> "URL Web TV copiée : $u"
                "de" -> "Web-TV-URL kopiert: $u"
                "pt" -> "URL Web TV copiada: $u"
                "ar" -> "تم نسخ رابط Web TV: $u"
                "ru" -> "URL Web TV скопирован: $u"
                else -> text
            }
        }
        Regex("^Purchase failed \\(code (-?\\d+)\\)\\.$").matchEntire(text)?.let {
            val c = it.groupValues[1]
            return when (lang) {
                "hi" -> "खरीदारी विफल (कोड $c)।"
                "es" -> "Compra fallida (código $c)."
                "fr" -> "Échec de l'achat (code $c)."
                "de" -> "Kauf fehlgeschlagen (Code $c)."
                "pt" -> "Falha na compra (código $c)."
                "ar" -> "فشل الشراء (الرمز $c)."
                "ru" -> "Ошибка покупки (код $c)."
                else -> text
            }
        }
        Regex("^Product \\((.+)\\) not found on Google Play yet\\. Please check Play Console status\\.$").matchEntire(text)?.let {
            val id = it.groupValues[1]
            return when (lang) {
                "hi" -> "उत्पाद ($id) अभी Google Play पर नहीं मिला। कृपया Play Console स्थिति जांचें।"
                "es" -> "Producto ($id) no encontrado en Google Play aún. Verifica Play Console."
                "fr" -> "Produit ($id) introuvable sur Google Play. Vérifiez Play Console."
                "de" -> "Produkt ($id) noch nicht bei Google Play gefunden. Play Console prüfen."
                "pt" -> "Produto ($id) ainda não encontrado no Google Play. Verifique o Play Console."
                "ar" -> "المنتج ($id) غير موجود على Google Play بعد. يرجى مراجعة Play Console."
                "ru" -> "Товар ($id) ещё не найден в Google Play. Проверьте статус Play Console."
                else -> text
            }
        }
        Regex("^Subscription offer not available for (.+)\\.$").matchEntire(text)?.let {
            val id = it.groupValues[1]
            return when (lang) {
                "hi" -> "$id के लिए सदस्यता ऑफ़र उपलब्ध नहीं है।"
                "es" -> "Oferta de suscripción no disponible para $id."
                "fr" -> "Offre d'abonnement indisponible pour $id."
                "de" -> "Abo-Angebot für $id nicht verfügbar."
                "pt" -> "Oferta de assinatura indisponível para $id."
                "ar" -> "عرض الاشتراك غير متاح لـ $id."
                "ru" -> "Предложение подписки недоступно для $id."
                else -> text
            }
        }
        Regex("^No DLNA descriptor on (.+) — use Web TV Cast link on TV browser!$").matchEntire(text)?.let {
            val ip = it.groupValues[1]
            return when (lang) {
                "hi" -> "$ip पर कोई DLNA डिस्क्रिप्टर नहीं मिला — टीवी ब्राउज़र में Web TV लिंक खोलें!"
                "es" -> "Sin descriptor DLNA en $ip — usa el enlace Web TV en tu TV."
                "fr" -> "Aucun descripteur DLNA sur $ip — utilisez le lien Web TV sur la TV."
                "de" -> "Kein DLNA-Deskriptor auf $ip — Web-TV-Link im TV-Browser öffnen!"
                "pt" -> "Sem descritor DLNA em $ip — use o link Web TV no navegador da TV!"
                "ar" -> "لا يوجد وصف DLNA على $ip — استخدم رابط Web TV في متصفح التلفاز!"
                "ru" -> "Нет DLNA на $ip — откройте ссылку Web TV в браузере ТВ!"
                else -> text
            }
        }
        Regex("^DLNA port not open on (.+)\\. Turn on Web TV Server below & open link in TV browser!$").matchEntire(text)?.let {
            val ip = it.groupValues[1]
            return when (lang) {
                "hi" -> "$ip पर DLNA पोर्ट खुला नहीं है। नीचे Web TV सर्वर चालू करें और टीवी ब्राउज़र में लिंक खोलें!"
                "es" -> "Puerto DLNA cerrado en $ip. Activa el servidor Web TV abajo y abre el enlace en la TV."
                "fr" -> "Port DLNA fermé sur $ip. Activez le serveur Web TV ci-dessous."
                "de" -> "DLNA-Port auf $ip nicht offen. Web-TV-Server aktivieren & Link im TV öffnen!"
                "pt" -> "Porta DLNA fechada em $ip. Ative o servidor Web TV abaixo e abra na TV!"
                "ar" -> "منفذ DLNA غير مفتوح على $ip. شغّل خادم Web TV أدناه وافتح الرابط في التلفاز!"
                "ru" -> "Порт DLNA закрыт на $ip. Включите сервер Web TV ниже и откройте в браузере ТВ!"
                else -> text
            }
        }
        Regex("^Web TV Ready for (.+): (.+)$").matchEntire(text)?.let {
            val dev = it.groupValues[1]
            val u = it.groupValues[2]
            return when (lang) {
                "hi" -> "$dev के लिए Web TV तैयार: $u"
                "es" -> "Web TV listo para $dev: $u"
                "fr" -> "Web TV prêt pour $dev : $u"
                "de" -> "Web-TV bereit für $dev: $u"
                "pt" -> "Web TV pronto para $dev: $u"
                "ar" -> "Web TV جاهز لـ $dev: $u"
                "ru" -> "Web TV готов для $dev: $u"
                else -> text
            }
        }
        Regex("^Sending '(.+)' to (.+)\\.\\.\\.$").matchEntire(text)?.let {
            val ch = it.groupValues[1]
            val dev = it.groupValues[2]
            return when (lang) {
                "hi" -> "'$ch' को $dev पर भेजा जा रहा है..."
                "es" -> "Enviando '$ch' a $dev..."
                "fr" -> "Envoi de '$ch' vers $dev..."
                "de" -> "Sende '$ch' an $dev..."
                "pt" -> "Enviando '$ch' para $dev..."
                "ar" -> "جاري إرسال '$ch' إلى $dev..."
                "ru" -> "Отправка «$ch» на $dev..."
                else -> text
            }
        }
        Regex("^Casting '(.+)' on (.+)$").matchEntire(text)?.let {
            val ch = it.groupValues[1]
            val dev = it.groupValues[2]
            return when (lang) {
                "hi" -> "$dev पर '$ch' कास्ट हो रहा है"
                "es" -> "Transmitiendo '$ch' en $dev"
                "fr" -> "Diffusion de '$ch' sur $dev"
                "de" -> "Übertrage '$ch' auf $dev"
                "pt" -> "Transmitindo '$ch' em $dev"
                "ar" -> "جاري بث '$ch' على $dev"
                "ru" -> "Трансляция «$ch» на $dev"
                else -> text
            }
        }
        Regex("^Casting '(.+)' to (.+)$").matchEntire(text)?.let {
            val ch = it.groupValues[1]
            val dev = it.groupValues[2]
            return when (lang) {
                "hi" -> "$dev पर '$ch' कास्ट हो रहा है"
                "es" -> "Transmitiendo '$ch' a $dev"
                "fr" -> "Diffusion de '$ch' vers $dev"
                "de" -> "Übertrage '$ch' auf $dev"
                "pt" -> "Transmitindo '$ch' para $dev"
                "ar" -> "جاري بث '$ch' إلى $dev"
                "ru" -> "Трансляция «$ch» на $dev"
                else -> text
            }
        }
        Regex("^DLNA rejected stream format — Web TV Server enabled \\((.+)\\)$").matchEntire(text)?.let {
            val u = it.groupValues[1]
            return when (lang) {
                "hi" -> "DLNA ने स्ट्रीम फ़ॉर्मेट अस्वीकार किया — Web TV सर्वर चालू ($u)"
                "es" -> "DLNA rechazó el formato — Servidor Web TV activado ($u)"
                "fr" -> "Format refusé par DLNA — Serveur Web TV activé ($u)"
                "de" -> "DLNA lehnte Format ab — Web-TV-Server aktiv ($u)"
                "pt" -> "DLNA recusou o formato — Servidor Web TV ativado ($u)"
                "ar" -> "رفض DLNA صيغة البث — تم تفعيل خادم Web TV ($u)"
                "ru" -> "DLNA отклонил формат — включён сервер Web TV ($u)"
                else -> text
            }
        }
        Regex("^• Type (.+) once in your TV's web browser\\.\\n• Whenever you switch channels in Hypnotix on your phone, the TV automatically switches channels live!$").matchEntire(text)?.let {
            val u = it.groupValues[1]
            return when (lang) {
                "hi" -> "• अपने टीवी ब्राउज़र में एक बार $u टाइप करें।\n• जब भी आप फ़ोन पर चैनल बदलेंगे, टीवी अपने आप लाइव चैनल बदल लेगा!"
                "es" -> "• Escribe $u una vez en el navegador de tu TV.\n• Cada vez que cambies de canal en el móvil, la TV cambiará automáticamente en vivo."
                "fr" -> "• Saisissez $u une fois dans le navigateur de votre TV.\n• À chaque changement de chaîne sur le téléphone, la TV bascule automatiquement !"
                "de" -> "• Geben Sie $u einmal im TV-Browser ein.\n• Beim Senderwechsel am Handy wechselt der TV automatisch live mit!"
                "pt" -> "• Digite $u uma vez no navegador da TV.\n• Sempre que trocar de canal no celular, a TV troca automaticamente ao vivo!"
                "ar" -> "• اكتب $u مرة واحدة في متصفح التلفاز.\n• عندما تغيّر القناة على هاتفك، يتغيّر التلفاز تلقائياً مباشرة!"
                "ru" -> "• Введите $u один раз в браузере ТВ.\n• При переключении канала на телефоне ТВ переключится автоматически!"
                else -> text
            }
        }
        Regex("^Auto-syncing: (.+)$").matchEntire(text)?.let {
            val ch = it.groupValues[1]
            return when (lang) {
                "hi" -> "ऑटो-सिंक: $ch"
                "es" -> "Sincronizando: $ch"
                "fr" -> "Synchro auto : $ch"
                "de" -> "Auto-Sync: $ch"
                "pt" -> "Sincronizando: $ch"
                "ar" -> "مزامنة تلقائية: $ch"
                "ru" -> "Автосинхронизация: $ch"
                else -> text
            }
        }
        Regex("^Sent (.+) to Cast receiver$").matchEntire(text)?.let {
            val cmd = it.groupValues[1]
            return when (lang) {
                "hi" -> "कास्ट रिसीवर को $cmd भेजा गया"
                "es" -> "Enviado $cmd al receptor Cast"
                "fr" -> "$cmd envoyé au récepteur Cast"
                "de" -> "$cmd an Cast-Empfänger gesendet"
                "pt" -> "$cmd enviado ao receptor Cast"
                "ar" -> "تم إرسال $cmd إلى مستقبل البث"
                "ru" -> "Команда $cmd отправлена на Cast"
                else -> text
            }
        }
        Regex("^DLNA stopped • Web TV active at (.+)$").matchEntire(text)?.let {
            val u = it.groupValues[1]
            return when (lang) {
                "hi" -> "DLNA बंद • Web TV सक्रिय: $u"
                "es" -> "DLNA detenido • Web TV activo en $u"
                "fr" -> "DLNA arrêté • Web TV actif sur $u"
                "de" -> "DLNA gestoppt • Web-TV aktiv: $u"
                "pt" -> "DLNA parado • Web TV ativo em $u"
                "ar" -> "توقف DLNA • Web TV نشط على $u"
                "ru" -> "DLNA остановлен • Web TV активен: $u"
                else -> text
            }
        }
        Regex("^Web TV \\((\\d+) TV Connected\\)$").matchEntire(text)?.let {
            val n = it.groupValues[1]
            return when (lang) {
                "hi" -> "Web TV ($n टीवी कनेक्टेड)"
                "es" -> "Web TV ($n TV conectada)"
                "fr" -> "Web TV ($n TV connectée)"
                "de" -> "Web-TV ($n TV verbunden)"
                "pt" -> "Web TV ($n TV conectada)"
                "ar" -> "Web TV ($n تلفاز متصل)"
                "ru" -> "Web TV ($n ТВ подключено)"
                else -> text
            }
        }
        Regex("^(.+) uses Cast/Web receiver\\. Open (.+) on TV or use System Cast!$").matchEntire(text)?.let {
            val dev = it.groupValues[1]
            val u = it.groupValues[2]
            return when (lang) {
                "hi" -> "$dev Cast/Web रिसीवर उपयोग करता है। टीवी पर $u खोलें या System Cast चुनें!"
                "es" -> "$dev usa receptor Cast/Web. ¡Abre $u en la TV o usa Cast del sistema!"
                "fr" -> "$dev utilise le récepteur Web/Cast. Ouvrez $u sur la TV !"
                "de" -> "$dev nutzt Cast/Web-Empfänger. $u auf dem TV öffnen oder System-Cast nutzen!"
                "pt" -> "$dev usa receptor Cast/Web. Abra $u na TV ou use o Cast do sistema!"
                "ar" -> "$dev يستخدم مستقبل Cast/Web. افتح $u على التلفاز!"
                "ru" -> "$dev использует Web/Cast. Откройте $u на ТВ или выберите системный Cast!"
                else -> text
            }
        }
        Regex("^TV rejected direct DLNA stream\\. Try toggling 'Proxy Mode' or open (.+) on TV!$").matchEntire(text)?.let {
            val u = it.groupValues[1]
            return when (lang) {
                "hi" -> "टीवी ने डायरेक्ट DLNA स्ट्रीम अस्वीकार की। 'प्रॉक्सी मोड' चालू करें या टीवी पर $u खोलें!"
                "es" -> "La TV rechazó el stream DLNA directo. ¡Prueba 'Modo Proxy' o abre $u en la TV!"
                "fr" -> "La TV a refusé le flux DLNA. Activez 'Mode Proxy' ou ouvrez $u sur la TV !"
                "de" -> "TV lehnte direkten DLNA-Stream ab. 'Proxy-Modus' testen oder $u auf dem TV öffnen!"
                "pt" -> "A TV recusou o stream DLNA direto. Tente o 'Modo Proxy' ou abra $u na TV!"
                "ar" -> "رفض التلفاز بث DLNA المباشر. جرّب تفعيل وضع البروكسي أو افتح $u على التلفاز!"
                "ru" -> "ТВ отклонил прямой DLNA-поток. Включите прокси-режим или откройте $u на ТВ!"
                else -> text
            }
        }
        Regex("^Cast '(.+)' via App$").matchEntire(text)?.let {
            val ch = it.groupValues[1]
            return when (lang) {
                "hi" -> "ऐप से '$ch' कास्ट करें"
                "es" -> "Enviar '$ch' mediante app"
                "fr" -> "Caster '$ch' via une app"
                "de" -> "'$ch' über App übertragen"
                "pt" -> "Transmitir '$ch' via app"
                "ar" -> "بث '$ch' عبر تطبيق"
                "ru" -> "Транслировать «$ch» через приложение"
                else -> text
            }
        }
        Regex("^Skip in (\\d+)s$").matchEntire(text)?.let {
            val s = it.groupValues[1]
            return when (lang) {
                "hi" -> "${s}s में छोड़ें"
                "es" -> "Saltar en ${s}s"
                "fr" -> "Ignorer dans ${s}s"
                "de" -> "Überspringen in ${s}s"
                "pt" -> "Pular em ${s}s"
                "ar" -> "تخطي خلال ${s}ث"
                "ru" -> "Пропуск через ${s}с"
                else -> text
            }
        }
        if (text.startsWith("Sponsored • ")) {
            val rest = text.removePrefix("Sponsored • ")
            val sp = lookup("Sponsored", lang) ?: "Sponsored"
            return "$sp • $rest"
        }
        Regex("^(\\d+) hr (\\d+) min$").matchEntire(text)?.let {
            val h = it.groupValues[1]
            val mins = it.groupValues[2]
            return when (lang) {
                "hi" -> "$h घं $mins मि"
                "es" -> "$h h $mins min"
                "fr" -> "$h h $mins min"
                "de" -> "$h Std. $mins Min."
                "pt" -> "$h h $mins min"
                "ar" -> "$h س $mins د"
                "ru" -> "$h ч $mins мин"
                else -> text
            }
        }
        Regex("^(\\d+) min$").matchEntire(text)?.let {
            val mins = it.groupValues[1]
            return when (lang) {
                "hi" -> "$mins मि"
                "es" -> "$mins min"
                "fr" -> "$mins min"
                "de" -> "$mins Min."
                "pt" -> "$mins min"
                "ar" -> "$mins د"
                "ru" -> "$mins мин"
                else -> text
            }
        }

        return null
    }
}
