package com.example.premium

internal object TranslationsPlayerAndXtream {
    fun populate(add: (String, Map<String, String>) -> Unit) {
        fun m(
            hi: String,
            es: String,
            fr: String,
            de: String,
            pt: String,
            ar: String,
            ru: String
        ) = mapOf(
            "hi" to hi,
            "es" to es,
            "fr" to fr,
            "de" to de,
            "pt" to pt,
            "ar" to ar,
            "ru" to ru
        )

        // Player Controls & Status
        add("Play", m("चलाएं", "Reproducir", "Lire", "Abspielen", "Reproduzir", "تشغيل", "Воспроизвести"))
        add("Pause", m("रोकें", "Pausar", "Pause", "Pause", "Pausar", "إيقاف مؤقت", "Пауза"))
        add("LIVE", m("लाइव", "EN VIVO", "DIRECT", "LIVE", "AO VIVO", "مباشر", "ЭФИР"))
        add("• LIVE", m("• लाइव", "• EN VIVO", "• DIRECT", "• LIVE", "• AO VIVO", "• مباشر", "• ЭФИР"))
        add("MOVIE", m("फिल्म", "PELÍCULA", "FILM", "FILM", "FILME", "فيلم", "ФИЛЬМ"))
        add("VOD", m("VOD", "VOD", "VOD", "VOD", "VOD", "فيديو حسب الطلب", "VOD"))
        add("Live", m("लाइव", "En vivo", "Direct", "Live", "Ao vivo", "مباشر", "Эфир"))
        add("LIVE TV", m("लाइव टीवी", "TV EN VIVO", "TV EN DIRECT", "LIVE-TV", "TV AO VIVO", "بث مباشر", "ТВ ОНЛАЙН"))
        add("Live TV", m("लाइव टीवी", "TV en vivo", "TV en direct", "Live-TV", "TV ao Vivo", "بث مباشر", "Прямое ТВ"))
        add("Live Channel", m("लाइव चैनल", "Canal en vivo", "Chaîne en direct", "Live-Kanal", "Canal ao Vivo", "قناة مباشرة", "Прямой канал"))
        add("Live Stream", m("लाइव स्ट्रीम", "Transmisión en vivo", "Flux en direct", "Livestream", "Transmissão ao Vivo", "بث مباشر", "Прямой эфир"))
        add("VOD Movie", m("VOD फिल्म", "Película VOD", "Film VOD", "VOD-Film", "Filme VOD", "فيلم حسب الطلب", "Фильм VOD"))
        add("0:00 (LIVE)", m("0:00 (लाइव)", "0:00 (EN VIVO)", "0:00 (DIRECT)", "0:00 (LIVE)", "0:00 (AO VIVO)", "0:00 (مباشر)", "0:00 (ЭФИР)"))
        add("PLAYING", m("चल रहा है", "REPRODUCIENDO", "EN LECTURE", "LÄUFT", "REPRODUZINDO", "قيد التشغيل", "ИГРАЕТ"))
        add("Auto", m("ऑटो", "Auto", "Auto", "Auto", "Auto", "تلقائي", "Авто"))
        add("Quality", m("क्वालिटी", "Calidad", "Qualité", "Qualität", "Qualidade", "الجودة", "Качество"))
        add("Aspect Ratio", m("आस्पेक्ट रेशियो", "Relación de aspecto", "Format d'image", "Bildformat", "Proporção da tela", "نسبة العرض", "Соотношение сторон"))
        add("Auto (Smart Adaptive)", m("ऑटो (स्मार्ट अडैप्टिव)", "Auto (Adaptativo)", "Auto (Adaptatif)", "Auto (Adaptiv)", "Auto (Adaptativo)", "تلقائي (تكيفي ذكي)", "Авто (Адаптивное)"))
        add("360p Saver", m("360p सेवर", "Ahorro 360p", "Éco 360p", "360p-Sparmodus", "Economia 360p", "توفير 360p", "Эко 360p"))
        add("⚡ Slow Internet Saver (≤360p)", m("⚡ धीमा इंटरनेट सेवर (≤360p)", "⚡ Ahorro red lenta (≤360p)", "⚡ Mode bas débit (≤360p)", "⚡ Sparmodus (≤360p)", "⚡ Economia de dados (≤360p)", "⚡ توفير للإنترنت البطيء (≤360p)", "⚡ Экономия трафика (≤360p)"))
        add("⚡ Slow Internet? Tap for 360p Data Saver", m("⚡ इंटरनेट धीमा है? 360p डेटा सेवर के लिए टैप करें", "⚡ ¿Red lenta? Toca para ahorro 360p", "⚡ Connexion lente ? Mode éco 360p", "⚡ Langsames Netz? 360p-Sparmodus", "⚡ Internet lenta? Toque para 360p", "⚡ إنترنت بطيء؟ اضغط للتوفير 360p", "⚡ Медленный интернет? Вкл. 360p"))
        add("Connecting (Fast Low-Bitrate Start)...", m("कनेक्ट हो रहा है (तेज़ शुरुआत)...", "Conectando (inicio rápido)...", "Connexion (démarrage rapide)...", "Verbinde (Schnellstart)...", "Conectando (início rápido)...", "جاري الاتصال (بدء سريع)...", "Подключение (быстрый старт)..."))
        add("Optimizing Stream for Your Speed...", m("आपकी स्पीड के लिए स्ट्रीम ऑप्टिमाइज़ हो रही है...", "Optimizando stream para tu conexión...", "Optimisation du flux selon votre débit...", "Stream wird für Ihre Geschwindigkeit optimiert...", "Otimizando stream para sua conexão...", "جاري تحسين البث لسرعتك...", "Оптимизация потока под скорость..."))
        add("Slow internet • Optimizing quality...", m("धीमा इंटरनेट • क्वालिटी ऑप्टिमाइज़ हो रही है...", "Internet lento • Optimizando calidad...", "Débit faible • Optimisation qualité...", "Langsames Netz • Qualität wird angepasst...", "Internet lenta • Otimizando qualidade...", "إنترنت بطيء • جاري تحسين الجودة...", "Медленная сеть • Подбор качества..."))
        add("16:9 (Standard)", m("16:9 (स्टैंडर्ड)", "16:9 (Estándar)", "16:9 (Standard)", "16:9 (Standard)", "16:9 (Padrão)", "16:9 (قياسي)", "16:9 (Стандарт)"))
        add("4:3 (Classic TV)", m("4:3 (क्लासिक टीवी)", "4:3 (TV Clásica)", "4:3 (TV Classique)", "4:3 (Klassisches TV)", "4:3 (TV Clássica)", "4:3 (تلفاز كلاسيكي)", "4:3 (Классика)"))
        add("Fit", m("फ़िट", "Ajustar", "Adapter", "Anpassen", "Ajustar", "ملاءمة", "Вписать"))
        add("Full", m("फ़ुल", "Lleno", "Plein", "Voll", "Cheio", "ملء", "Заполнить"))
        add("Zoom", m("ज़ूम", "Zoom", "Zoom", "Zoom", "Zoom", "تكبير", "Зум"))
        add("Fit (Original Stream)", m("फ़िट (मूल स्ट्रीम)", "Ajustar (Original)", "Adapter (Original)", "Anpassen (Original)", "Ajustar (Original)", "ملاءمة (الأصلي)", "Вписать (Оригинал)"))
        add("Full (Stretch)", m("फ़ुल (स्ट्रेच)", "Lleno (Estirar)", "Plein (Étirer)", "Voll (Strecken)", "Cheio (Esticar)", "ملء (تمديد)", "Заполнить (Растянуть)"))
        add("Zoom (Fill Screen)", m("ज़ूम (फ़ुल स्क्रीन)", "Zoom (Llenar)", "Zoom (Remplir)", "Zoom (Ausfüllen)", "Zoom (Preencher)", "تكبير (ملء الشاشة)", "Зум (На весь экран)"))
        add("1x (Normal)", m("1x (सामान्य)", "1x (Normal)", "1x (Normal)", "1x (Normal)", "1x (Normal)", "1x (عادي)", "1x (Обычная)"))
        add("Selected speed", m("चयनित गति", "Velocidad seleccionada", "Vitesse sélectionnée", "Ausgewählte Geschwindigkeit", "Velocidade selecionada", "السرعة المحددة", "Выбранная скорость"))
        add("Picture in Picture", m("पिक्चर-इन-पिक्चर", "Imagen en imagen", "Image dans l'image", "Bild-in-Bild", "Picture-in-Picture", "صورة داخل صورة", "Картинка в картинке"))
        add("PiP", m("PiP", "PiP", "PiP", "PiP", "PiP", "PiP", "PiP"))
        add("Landscape Mode", m("लैंडस्केप मोड", "Modo horizontal", "Mode paysage", "Querformat", "Modo paisagem", "الوضع الأفقي", "Альбомный режим"))
        add("Exit Landscape", m("लैंडस्केप से बाहर आएं", "Salir de horizontal", "Quitter paysage", "Querformat verlassen", "Sair da paisagem", "خروج من الأفقي", "Выйти из альбомного"))
        add("Enter Fullscreen", m("फ़ुलस्क्रीन खोलें", "Pantalla completa", "Plein écran", "Vollbild", "Tela cheia", "ملء الشاشة", "На весь экран"))
        add("Exit Fullscreen", m("फ़ुलस्क्रीन से बाहर आएं", "Salir pantalla completa", "Quitter plein écran", "Vollbild beenden", "Sair da tela cheia", "خروج من ملء الشاشة", "Выйти из полноэкранного"))
        add("Landscape / Fullscreen", m("लैंडस्केप / फ़ुलस्क्रीन", "Horizontal / Pantalla completa", "Paysage / Plein écran", "Querformat / Vollbild", "Paisagem / Tela cheia", "أفقي / ملء الشاشة", "Полный экран"))
        add("Fullscreen landscape mode", m("फ़ुलस्क्रीन लैंडस्केप मोड", "Modo horizontal completo", "Mode plein écran paysage", "Vollbild-Querformat", "Modo paisagem completo", "ملء الشاشة بالوضع الأفقي", "Полноэкранный режим"))
        add("Switch Channel", m("चैनल बदलें", "Cambiar canal", "Changer de chaîne", "Kanal wechseln", "Trocar canal", "تبديل القناة", "Сменить канал"))
        add("Next Channel", m("अगला चैनल", "Siguiente canal", "Chaîne suivante", "Nächster Kanal", "Próximo canal", "القناة التالية", "Следующий канал"))
        add("Previous Channel", m("पिछला चैनल", "Canal anterior", "Chaîne précédente", "Vorheriger Kanal", "Canal anterior", "القناة السابقة", "Предыдущий канал"))
        add("Rewind 10 seconds", m("10 सेकंड पीछे", "Retroceder 10 s", "Reculer de 10 s", "10 Sek. zurück", "Voltar 10 s", "ترجيع 10 ثوانٍ", "Назад на 10 сек"))
        add("Forward 10 seconds", m("10 सेकंड आगे", "Avanzar 10 s", "Avancer de 10 s", "10 Sek. vor", "Avançar 10 s", "تقديم 10 ثوانٍ", "Вперёд на 10 сек"))
        add("-10 sec", m("-10 सेकंड", "-10 s", "-10 s", "-10 Sek.", "-10 s", "-10 ث", "-10 сек"))
        add("+10 sec", m("+10 सेकंड", "+10 s", "+10 s", "+10 Sek.", "+10 s", "+10 ث", "+10 сек"))
        add("Brightness", m("चमक", "Brillo", "Luminosité", "Helligkeit", "Brilho", "السطوع", "Яркость"))
        add("Volume", m("आवाज़", "Volumen", "Volume", "Lautstärke", "Volume", "الصوت", "Громкость"))

        // Player Error Messages
        add("Stream Offline", m("स्ट्रीम ऑफ़लाइन", "Stream sin conexión", "Flux hors ligne", "Stream offline", "Stream offline", "البث غير متصل", "Поток оффлайн"))
        add("Channel stream is currently offline or unreachable.", m("चैनल स्ट्रीम अभी ऑफ़लाइन है या उपलब्ध नहीं है।", "El canal está sin conexión o no accesible.", "Le flux est hors ligne ou inaccessible.", "Der Stream ist derzeit offline oder nicht erreichbar.", "O canal está offline ou inacessível no momento.", "بث القناة غير متصل حالياً أو يتعذر الوصول إليه.", "Канал сейчас не в сети или недоступен."))
        add("Network connection timed out. Check your internet or try another channel.", m("नेटवर्क कनेक्शन टाइमआउट हो गया। इंटरनेट जांचें या दूसरा चैनल चलाएं।", "Tiempo de espera agotado. Revisa tu internet o prueba otro canal.", "Délai dépassé. Vérifiez votre connexion ou essayez une autre chaîne.", "Zeitüberschreitung. Prüfen Sie Ihr Internet oder wählen Sie einen anderen Kanal.", "Tempo limite esgotado. Verifique sua internet ou tente outro canal.", "انتهت مهلة الاتصال بالشبكة. تحقق من الإنترنت أو جرب قناة أخرى.", "Превышено время ожидания сети. Проверьте интернет или выберите другой канал."))
        add("Playback error occurred", m("प्लेबैक त्रुटि हुई", "Error de reproducción", "Erreur de lecture", "Wiedergabefehler", "Erro na reprodução", "حدث خطأ أثناء التشغيل", "Произошла ошибка воспроизведения"))
        add("Playback error", m("प्लेबैक त्रुटि", "Error de reproducción", "Erreur de lecture", "Wiedergabefehler", "Erro de reprodução", "خطأ في التشغيل", "Ошибка воспроизведения"))
        add("Error starting channel playback", m("चैनल प्ले करने में त्रुटि", "Error al iniciar la reproducción", "Erreur de lancement de la chaîne", "Fehler beim Starten der Wiedergabe", "Erro ao iniciar o canal", "خطأ في بدء تشغيل القناة", "Ошибка запуска воспроизведения"))
        add("Unable to load stream. Try another server or channel.", m("स्ट्रीम लोड नहीं हो सकी। कोई अन्य सर्वर या चैनल आज़माएं।", "No se pudo cargar el stream. Prueba otro servidor o canal.", "Impossible de charger le flux. Essayez un autre serveur ou chaîne.", "Stream konnte nicht geladen werden. Anderen Server oder Kanal testen.", "Não foi possível carregar o stream. Tente outro servidor ou canal.", "تعذر تحميل البث. جرب خادماً أو قناة أخرى.", "Не удалось загрузить поток. Попробуйте другой сервер или канал."))

        // Xtream Screens & Demo Hook & Header Hook
        add("Who's Watching?", m("कौन देख रहा है?", "¿Quién está viendo?", "Qui regarde ?", "Wer schaut zu?", "Quem está assistindo?", "من يشاهد؟", "Кто смотрит?"))
        add("Select your Xtream profile to load streams", m("स्ट्रीम लोड करने के लिए अपनी Xtream प्रोफ़ाइल चुनें", "Elige tu perfil Xtream para cargar streams", "Choisissez votre profil Xtream pour charger les flux", "Xtream-Profil wählen, um Streams zu laden", "Selecione seu perfil Xtream para carregar streams", "اختر ملف Xtream لتحميل البثوث", "Выберите профиль Xtream для загрузки потоков"))
        add("Active", m("सक्रिय", "Activo", "Actif", "Aktiv", "Ativo", "نشط", "Активный"))
        add("Add Profile", m("प्रोफ़ाइल जोड़ें", "Añadir perfil", "Ajouter profil", "Profil hinzufügen", "Adicionar perfil", "إضافة ملف", "Добавить профиль"))
        add("+ Add", m("+ जोड़ें", "+ Añadir", "+ Ajouter", "+ Hinzufügen", "+ Adicionar", "+ إضافة", "+ Добавить"))
        add("+ Connect", m("+ कनेक्ट", "+ Conectar", "+ Connecter", "+ Verbinden", "+ Conectar", "+ اتصال", "+ Подкл."))
        add("Add Account", m("खाता जोड़ें", "Añadir cuenta", "Ajouter compte", "Konto hinzufügen", "Adicionar conta", "إضافة حساب", "Добавить аккаунт"))
        add("Demo Account", m("डेमो खाता", "Cuenta demo", "Compte démo", "Demo-Konto", "Conta demo", "حساب تجريبي", "Демо-аккаунт"))
        add("Demo active • Add your server", m("डेमो सक्रिय • अपना सर्वर जोड़ें", "Demo activa • Añade tu servidor", "Démo active • Ajoutez votre serveur", "Demo aktiv • Eigenen Server hinzufügen", "Demo ativo • Adicione seu servidor", "العرض التجريبي نشط • أضف خادمك", "Демо активно • Добавьте сервер"))
        add("Need a test server?", m("टेस्ट सर्वर चाहिए?", "¿Necesitas un servidor de prueba?", "Besoin d'un serveur de test ?", "Testserver benötigt?", "Precisa de um servidor de teste?", "هل تحتاج إلى خادم تجريبي؟", "Нужен тестовый сервер?"))
        add("One-tap connect to verified sample Xtream streams", m("सत्यापित सैंपल Xtream स्ट्रीम से एक-टैप में जुड़ें", "Conexión en un toque a streams Xtream de muestra", "Connexion en un clic aux flux Xtream de test", "Ein-Tipp-Verbindung zu Xtream-Test-Streams", "Conexão em um toque a streams Xtream de teste", "اتصال بنقرة واحدة ببثوث Xtream التجريبية", "Подключение в одно касание к демо-потокам Xtream"))
        add("Add", m("जोड़ें", "Añadir", "Ajouter", "Hinzu", "Add", "إضافة", "Добавить"))
        add("Connect", m("कनेक्ट", "Conectar", "Connecter", "Verbinden", "Conectar", "اتصال", "Подкл."))
        add("Add Xtream Account", m("Xtream खाता जोड़ें", "Añadir cuenta Xtream", "Ajouter compte Xtream", "Xtream-Konto hinzufügen", "Adicionar conta Xtream", "إضافة حساب Xtream", "Добавить аккаунт Xtream"))
        add("Connect & Save Profile", m("कनेक्ट करें और प्रोफ़ाइल सहेजें", "Conectar y guardar perfil", "Connecter et enregistrer", "Verbinden & Profil speichern", "Conectar e salvar perfil", "اتصال وحفظ الملف", "Подключить и сохранить"))
        add("⚡ Fill Working Sample Demo Account", m("⚡ सैंपल डेमो खाता भरें", "⚡ Rellenar cuenta demo de muestra", "⚡ Remplir le compte démo", "⚡ Demo-Konto ausfüllen", "⚡ Preencher conta demo", "⚡ تعبئة الحساب التجريبي", "⚡ Заполнить демо-аккаунт"))
        add("Find Public Xtream Account", m("पब्लिक Xtream खाता खोजें", "Buscar cuenta Xtream pública", "Trouver un compte Xtream public", "Öffentliches Xtream-Konto finden", "Buscar conta Xtream pública", "ابحث عن حساب Xtream عام", "Найти публичный Xtream-аккаунт"))
        add("Server URL", m("सर्वर URL", "URL del servidor", "URL du serveur", "Server-URL", "URL do servidor", "رابط الخادم", "URL сервера"))
        add("Username", m("उपयोगकर्ता नाम", "Usuario", "Nom d'utilisateur", "Benutzername", "Usuário", "اسم المستخدم", "Имя пользователя"))
        add("Password", m("पासवर्ड", "Contraseña", "Mot de passe", "Passwort", "Senha", "كلمة المرور", "Пароль"))
        add("Profile Name (Optional)", m("प्रोफ़ाइल नाम (वैकल्पिक)", "Nombre de perfil (opcional)", "Nom du profil (optionnel)", "Profilname (Optional)", "Nome do perfil (Opcional)", "اسم الملف الشخصي (اختياري)", "Имя профиля (необязательно)"))
        add("Xtream Server • Username • Password", m("Xtream सर्वर • उपयोगकर्ता नाम • पासवर्ड", "Servidor Xtream • Usuario • Contraseña", "Serveur Xtream • Utilisateur • Mot de passe", "Xtream-Server • Benutzer • Passwort", "Servidor Xtream • Usuário • Senha", "خادم Xtream • المستخدم • كلمة المرور", "Сервер Xtream • Логин • Пароль"))
        add("Show password", m("पासवर्ड दिखाएं", "Mostrar contraseña", "Afficher mot de passe", "Passwort anzeigen", "Mostrar senha", "إظهار كلمة المرور", "Показать пароль"))
        add("Hide password", m("पासवर्ड छिपाएं", "Ocultar contraseña", "Masquer mot de passe", "Passwort verbergen", "Ocultar senha", "إخفاء كلمة المرور", "Скрыть пароль"))
        add("Please fill Server URL, Username & Password", m("कृपया सर्वर URL, उपयोगकर्ता नाम और पासवर्ड भरें", "Completa URL del servidor, usuario y contraseña", "Veuillez remplir URL, utilisateur et mot de passe", "Bitte Server-URL, Benutzername & Passwort eingeben", "Preencha URL, usuário e senha", "يرجى إدخال رابط الخادم والمستخدم وكلمة المرور", "Заполните URL сервера, логин и пароль"))
        add("Connected to Xtream account!", m("Xtream खाते से कनेक्ट हो गया!", "¡Conectado a la cuenta Xtream!", "Connecté au compte Xtream !", "Mit Xtream-Konto verbunden!", "Conectado à conta Xtream!", "تم الاتصال بحساب Xtream!", "Подключено к аккаунту Xtream!"))
        add("Connection failed. Please check credentials.", m("कनेक्शन विफल। कृपया विवरण जांचें।", "Error de conexión. Revisa los datos.", "Échec de connexion. Vérifiez vos identifiants.", "Verbindung fehlgeschlagen. Zugangsdaten prüfen.", "Falha na conexão. Verifique os dados.", "فشل الاتصال. يرجى التحقق من البيانات.", "Ошибка подключения. Проверьте данные."))
        add("Sample demo credentials filled!", m("सैंपल डेमो विवरण भर दिए गए!", "¡Datos de demo rellenados!", "Identifiants démo remplis !", "Demo-Zugangsdaten ausgefüllt!", "Dados de demo preenchidos!", "تم تعبئة بيانات التجربة!", "Демо-данные заполнены!"))
        add("Delete Profile?", m("प्रोफ़ाइल हटाएं?", "¿Eliminar perfil?", "Supprimer le profil ?", "Profil löschen?", "Excluir perfil?", "حذف الملف الشخصي؟", "Удалить профиль?"))
        add("Delete Profile", m("प्रोफ़ाइल हटाएं", "Eliminar perfil", "Supprimer profil", "Profil löschen", "Excluir perfil", "حذف الملف", "Удалить профиль"))
        add("Profile removed", m("प्रोफ़ाइल हटाई गई", "Perfil eliminado", "Profil supprimé", "Profil entfernt", "Perfil removido", "تمت إزالة الملف الشخصي", "Профиль удалён"))
        add("Movies & VOD", m("फिल्में और VOD", "Películas y VOD", "Films et VOD", "Filme & VOD", "Filmes e VOD", "الأفلام و VOD", "Фильмы и VOD"))
        add("Featured Stream", m("विशेष स्ट्रीम", "Stream destacado", "Flux à la une", "Empfohlener Stream", "Stream em destaque", "بث مميز", "Рекомендуемый поток"))
        add("FEATURED MOVIE", m("विशेष फिल्म", "PELÍCULA DESTACADA", "FILM À LA UNE", "FILM-HIGHLIGHT", "FILME EM DESTAQUE", "فيلم مميز", "РЕКОМЕНДУЕМЫЙ ФИЛЬМ"))
        add("FEATURED STREAM", m("विशेष स्ट्रीम", "STREAM DESTACADO", "FLUX À LA UNE", "STREAM-HIGHLIGHT", "STREAM EM DESTAQUE", "بث مميز", "РЕКОМЕНДУЕМЫЙ ПОТОК"))
        add("Recommend for you", m("आपके लिए अनुशंसित", "Recomendado para ti", "Recommandé pour vous", "Für Sie empfohlen", "Recomendado para você", "موصى به لك", "Рекомендуем вам"))
        add("Search live tv, movie, series...", m("लाइव टीवी, फिल्म, सीरीज़ खोजें...", "Buscar TV en vivo, películas, series...", "Rechercher TV, films, séries...", "Live-TV, Filme, Serien suchen...", "Buscar TV, filmes, séries...", "ابحث عن البث أو الأفلام أو المسلسلات...", "Поиск ТВ, фильмов, сериалов..."))
        add("Tap '+ Add' to verify server credentials or switch profile.", m("सर्वर विवरण सत्यापित करने या प्रोफ़ाइल बदलने के लिए '+ जोड़ें' पर टैप करें।", "Toca '+ Añadir' para verificar credenciales o cambiar de perfil.", "Touchez '+ Ajouter' pour vérifier ou changer de profil.", "Tippen Sie auf '+ Hinzufügen', um Zugangsdaten zu prüfen.", "Toque em '+ Adicionar' para verificar dados ou trocar perfil.", "اضغط على '+ إضافة' للتحقق من البيانات أو تبديل الملف.", "Нажмите «+ Добавить» для проверки данных или смены профиля."))
        add("Xtream Profile", m("Xtream प्रोफ़ाइल", "Perfil Xtream", "Profil Xtream", "Xtream-Profil", "Perfil Xtream", "ملف Xtream", "Профиль Xtream"))
        add("Xtream Account", m("Xtream खाता", "Cuenta Xtream", "Compte Xtream", "Xtream-Konto", "Conta Xtream", "حساب Xtream", "Аккаунт Xtream"))
        add("Xtream Stream Player", m("Xtream स्ट्रीम प्लेयर", "Reproductor Xtream", "Lecteur Xtream", "Xtream-Stream-Player", "Reprodutor Xtream", "مشغل Xtream", "Xtream-плеер"))
        add("Login failed", m("लॉगिन विफल", "Error de inicio de sesión", "Échec de connexion", "Anmeldung fehlgeschlagen", "Falha no login", "فشل تسجيل الدخول", "Ошибка входа"))
        add("Invalid credentials", m("अमान्य विवरण", "Credenciales inválidas", "Identifiants invalides", "Ungültige Zugangsdaten", "Credenciais inválidas", "بيانات الاعتماد غير صالحة", "Неверные учётные данные"))

        // EPG Upcoming Schedule
        add("Loading...", m("लोड हो रहा है...", "Cargando...", "Chargement...", "Lädt...", "Carregando...", "جاري التحميل...", "Загрузка..."))
        add("Not available", m("उपलब्ध नहीं", "No disponible", "Indisponible", "Nicht verfügbar", "Indisponível", "غير متاح", "Недоступно"))
        add("UPCOMING", m("आगामी", "PRÓXIMO", "À SUIVRE", "DEMNÄCHST", "A SEGUIR", "التالي", "ДАЛЕЕ"))
        add("Upcoming Schedule", m("आगामी कार्यक्रम", "Programación", "Programme à venir", "Kommendes Programm", "Programação", "الجدول القادم", "Программа передач"))
        add("Refresh schedule", m("कार्यक्रम रिफ्रेश करें", "Actualizar horario", "Actualiser programme", "Programm aktualisieren", "Atualizar grade", "تحديث الجدول", "Обновить расписание"))
        add("• LIVE NOW", m("• अभी लाइव", "• EN VIVO AHORA", "• EN DIRECT", "• JETZT LIVE", "• AO VIVO AGORA", "• مباشر الآن", "• СЕЙЧАС В ЭФИРЕ"))
        add("No upcoming details available", m("आगामी विवरण उपलब्ध नहीं हैं", "Sin detalles próximos disponibles", "Aucun programme à venir", "Keine weiteren Programmdaten", "Sem detalhes da programação", "لا تتوفر تفاصيل قادمة", "Нет данных о следующих передачах"))
        add("Configure EPG URL", m("EPG URL सेट करें", "Configurar URL EPG", "Configurer URL EPG", "EPG-URL einrichten", "Configurar URL EPG", "إعداد رابط EPG", "Настроить URL EPG"))
        add("Set Custom XMLTV EPG URL", m("कस्टम XMLTV EPG URL सेट करें", "URL EPG XMLTV personalizada", "Définir URL EPG XMLTV", "Eigene XMLTV-EPG-URL setzen", "Definir URL EPG XMLTV", "تعيين رابط XMLTV EPG", "Указать URL XMLTV EPG"))
        add("Playlist EPG URL (XMLTV)", m("प्लेलिस्ट EPG URL (XMLTV)", "URL EPG de la lista (XMLTV)", "URL EPG (XMLTV)", "Playlist-EPG-URL (XMLTV)", "URL EPG da lista (XMLTV)", "رابط EPG للقائمة (XMLTV)", "URL EPG плейлиста (XMLTV)"))
        add("Optional: Paste an XMLTV (.xml or .xml.gz) guide URL for M3U playlists that don't include built-in EPG data.", m("वैकल्पिक: उन M3U प्लेलिस्ट के लिए XMLTV (.xml या .xml.gz) गाइड URL पेस्ट करें जिनमें EPG डेटा नहीं है।", "Opcional: pega una URL XMLTV (.xml o .xml.gz) para listas M3U sin guía integrada.", "Optionnel : collez une URL XMLTV (.xml ou .xml.gz) pour les listes sans guide intégré.", "Optional: XMLTV-URL (.xml/.xml.gz) für M3U-Listen ohne integriertes EPG einfügen.", "Opcional: cole uma URL XMLTV (.xml ou .xml.gz) para listas M3U sem EPG integrado.", "اختياري: الصق رابط دليل XMLTV لقوائم M3U التي لا تحتوي على EPG.", "Необязательно: вставьте URL XMLTV (.xml или .xml.gz) для плейлистов без EPG."))
        add("EPG URL cleared", m("EPG URL हटाया गया", "URL EPG borrada", "URL EPG effacée", "EPG-URL gelöscht", "URL EPG limpa", "تم مسح رابط EPG", "URL EPG очищен"))
        add("EPG URL saved", m("EPG URL सहेजा गया", "URL EPG guardada", "URL EPG enregistrée", "EPG-URL gespeichert", "URL EPG salva", "تم حفظ رابط EPG", "URL EPG сохранён"))
        add("Enter a valid http/https EPG URL", m("एक मान्य http/https EPG URL दर्ज करें", "Ingresa una URL http/https válida", "Entrez une URL http/https valide", "Gültige http/https-EPG-URL eingeben", "Digite uma URL http/https válida", "أدخل رابط http/https صالحاً", "Введите корректный http/https URL EPG"))
        add("starting soon", m("जल्द शुरू होगा", "empieza pronto", "commence bientôt", "beginnt bald", "começa em breve", "يبدأ قريباً", "скоро начнётся"))
    }
}
