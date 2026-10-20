package com.example.ui.components

import androidx.compose.ui.graphics.Color
import com.example.data.model.Channel

/**
 * Resolves, repairs, and optimizes IPTV channel logo URLs and provides
 * deterministic brand colors and monograms so no channel ever displays
 * a blank or missing logo.
 */
object ChannelLogoResolver {

    // Fast, verified logo URLs for well-known Indian & International TV channels
    // when M3U playlists omit tvg-logo or provide broken links.
    private val KNOWN_CHANNEL_LOGOS = listOf(
        "aaj tak" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/240px-Aaj_tak_logo.png",
        "india today" to "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9c/India_Today_Logo.png/240px-India_Today_Logo.png",
        "ndtv" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/NDTV_logo.svg/240px-NDTV_logo.svg.png",
        "abp" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/ABP_News_logo.svg/240px-ABP_News_logo.svg.png",
        "zee news" to "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/Zee_News.svg/240px-Zee_News.svg.png",
        "zee" to "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53/Zee_TV_2017.svg/240px-Zee_TV_2017.svg.png",
        "republic" to "https://upload.wikimedia.org/wikipedia/commons/thumb/7/7b/Republic_TV.svg/240px-Republic_TV.svg.png",
        "india tv" to "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/India_TV.svg/240px-India_TV.svg.png",
        "news18" to "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/News18_India.svg/240px-News18_India.svg.png",
        "dd national" to "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Doordarshan_Logo.svg/240px-Doordarshan_Logo.svg.png",
        "dd news" to "https://upload.wikimedia.org/wikipedia/commons/thumb/8/87/DD_News.svg/240px-DD_News.svg.png",
        "dd sports" to "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Doordarshan_Logo.svg/240px-Doordarshan_Logo.svg.png",
        "dd india" to "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Doordarshan_Logo.svg/240px-Doordarshan_Logo.svg.png",
        "doordarshan" to "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Doordarshan_Logo.svg/240px-Doordarshan_Logo.svg.png",
        "sansad" to "https://upload.wikimedia.org/wikipedia/commons/thumb/1/18/Doordarshan_Logo.svg/240px-Doordarshan_Logo.svg.png",
        "9xm" to "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png",
        "red bull" to "https://resources.redbull.com/logos/redbull-tv-logo.png",
        "bbc" to "https://upload.wikimedia.org/wikipedia/commons/thumb/e/eb/BBC.svg/240px-BBC.svg.png",
        "cnn" to "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b1/CNN.svg/240px-CNN.svg.png",
        "al jazeera" to "https://upload.wikimedia.org/wikipedia/en/thumb/f/f2/Aljazeera_eng.svg/240px-Aljazeera_eng.svg.png",
        "france 24" to "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8a/France24.png/240px-France24.png",
        "dw" to "https://upload.wikimedia.org/wikipedia/commons/thumb/7/75/Deutsche_Welle_symbol_2012.svg/240px-Deutsche_Welle_symbol_2012.svg.png",
        "sky news" to "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c7/Sky_News_logo_2023.svg/240px-Sky_News_logo_2023.svg.png",
        "bloomberg" to "https://upload.wikimedia.org/wikipedia/commons/thumb/5/56/Bloomberg_logo.svg/240px-Bloomberg_logo.svg.png",
        "cnbc" to "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/CNBC_logo.svg/240px-CNBC_logo.svg.png",
        "nasa" to "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e5/NASA_logo.svg/240px-NASA_logo.svg.png",
        "sony" to "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Sony_Entertainment_Television_India.svg/240px-Sony_Entertainment_Television_India.svg.png",
        "star" to "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Star_Plus_logo_2018.svg/240px-Star_Plus_logo_2018.svg.png",
        "colors" to "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/Colors_TV_logo.svg/240px-Colors_TV_logo.svg.png",
        "sun tv" to "https://upload.wikimedia.org/wikipedia/en/thumb/5/5e/Sun_TV_logo.svg/240px-Sun_TV_logo.svg.png",
        "cartoon" to "https://upload.wikimedia.org/wikipedia/commons/thumb/8/80/Cartoon_Network_2010_logo.svg/240px-Cartoon_Network_2010_logo.svg.png",
        "discovery" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/2019_Discovery_logo.svg/240px-2019_Discovery_logo.svg.png",
        "nat geo" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Natgeologo.svg/240px-Natgeologo.svg.png",
        "national geographic" to "https://upload.wikimedia.org/wikipedia/commons/thumb/f/fc/Natgeologo.svg/240px-Natgeologo.svg.png"
    )

    /**
     * Normalizes, repairs, and optimizes a raw logo URL for fast loading on slow networks.
     * If the raw logo URL is missing or known-broken, attempts to resolve a known channel logo.
     */
    fun resolveOptimalLogoUrl(channelName: String, rawLogoUrl: String?, category: String = ""): String? {
        val cleaned = sanitizeUrl(rawLogoUrl)
        if (cleaned != null) {
            return cleaned
        }
        return getFallbackLogoUrl(channelName, category)
    }

    fun resolvePrimaryLogo(rawLogoUrl: String?, channelName: String, category: String = ""): String? {
        return resolveOptimalLogoUrl(channelName, rawLogoUrl, category)
    }

    /**
     * Returns a secondary fallback logo URL when the primary M3U logo URL fails or is missing.
     */
    fun getFallbackLogoUrl(channelName: String, category: String = ""): String? {
        val lowerName = channelName.lowercase()
        for ((keyword, url) in KNOWN_CHANNEL_LOGOS) {
            if (lowerName.contains(keyword)) {
                return url
            }
        }
        return null
    }

    /**
     * Cleans and optimizes an image URL for fast low-bandwidth loading:
     * - Strips quotes and accidental pipe parameters
     * - Converts raw Wikimedia SVG URLs into lightweight 200px PNG thumbnails
     * - Compresses Unsplash URLs to small thumbnail dimensions
     * - Encodes spaces
     */
    fun sanitizeUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank()) return null
        var url = rawUrl.trim().trim('"', '\'')
        if (url.isBlank() || url.equals("null", ignoreCase = true) || url.equals("none", ignoreCase = true)) {
            return null
        }

        // Strip any trailing pipe options
        val pipeIdx = url.indexOf('|')
        if (pipeIdx > 0) {
            url = url.substring(0, pipeIdx).trim()
        }

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }

        // Reject known dead placeholder host
        if (url.contains("135.125.132.93")) {
            return null
        }

        // Convert raw Wikimedia .svg URLs to 200px PNG thumbnails so Coil BitmapFactory decodes them fast
        if (url.contains("upload.wikimedia.org/wikipedia/") &&
            url.endsWith(".svg", ignoreCase = true) &&
            !url.contains("/thumb/")
        ) {
            val parts = url.split("/wikipedia/")
            if (parts.size == 2) {
                val subParts = parts[1].split("/", limit = 2)
                if (subParts.size == 2) {
                    val wikiSection = subParts[0] // e.g., commons or en
                    val filePath = subParts[1]    // e.g., c/c5/NDTV_logo.svg
                    val fileName = filePath.substringAfterLast('/')
                    url = "${parts[0]}/wikipedia/$wikiSection/thumb/$filePath/200px-$fileName.png"
                }
            }
        }

        // Optimize Unsplash image sizes for fast loading on slow connections
        if (url.contains("images.unsplash.com/")) {
            url = url
                .replace("w=600", "w=200&q=70&auto=format")
                .replace("w=300", "w=180&q=70&auto=format")
                .replace("w=200", "w=160&q=70&auto=format")
        }

        return url.replace(" ", "%20")
    }

    /**
     * Generates a clean 2-3 character monogram for a channel name.
     * Example: "Aaj Tak Live HD" -> "AT", "9XM Music" -> "9XM", "DD National" -> "DDN"
     */
    fun getChannelMonogram(channelName: String): String {
        val cleaned = channelName
            .replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ")
            .replace(Regex("(?i)\\b(hd|sd|fhd|uhd|4k|live|tv|channel|india|in|uk|us|global)\\b"), " ")
            .trim()

        val words = cleaned.split(Regex("[\\s\\-_.:|/]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) {
            val raw = channelName.trim().filter { it.isLetterOrDigit() }
            return raw.take(3).uppercase().ifBlank { "TV" }
        }

        if (words.size == 1) {
            val w = words[0].filter { it.isLetterOrDigit() }
            return w.take(3).uppercase().ifBlank { "TV" }
        }

        val initials = words.take(3).mapNotNull { it.firstOrNull { ch -> ch.isLetterOrDigit() } }.joinToString("")
        return initials.uppercase().ifBlank { "TV" }
    }

    /**
     * Returns a pair of gradient colors tailored to the channel name & category.
     */
    fun getBadgeGradientColors(channel: Channel): List<Color> {
        val cat = channel.category.lowercase()
        val hash = kotlin.math.abs(channel.name.lowercase().hashCode())
        return when {
            cat.contains("sport") -> listOf(Color(0xFF059669), Color(0xFF047857))
            cat.contains("news") -> listOf(Color(0xFFDC2626), Color(0xFF991B1B))
            cat.contains("movie") || cat.contains("cinema") || cat.contains("vod") -> listOf(Color(0xFF7C3AED), Color(0xFF4C1D95))
            cat.contains("music") -> listOf(Color(0xFFDB2777), Color(0xFF9D174D))
            cat.contains("kid") || cat.contains("anim") || cat.contains("cartoon") -> listOf(Color(0xFFD97706), Color(0xFFB45309))
            cat.contains("doc") || cat.contains("science") -> listOf(Color(0xFF0284C7), Color(0xFF0369A1))
            else -> {
                val palettes = listOf(
                    listOf(Color(0xFFE11D48), Color(0xFF9F1239)),
                    listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                    listOf(Color(0xFF059669), Color(0xFF065F46)),
                    listOf(Color(0xFF7C3AED), Color(0xFF5B21B6)),
                    listOf(Color(0xFFEA580C), Color(0xFFC2410C)),
                    listOf(Color(0xFF0891B2), Color(0xFF155E75))
                )
                palettes[hash % palettes.size]
            }
        }
    }
}
