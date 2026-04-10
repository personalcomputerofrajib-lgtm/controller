package com.wifimonitor.analyzer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Maps known domains to their display name, emoji icon, and brand color.
 * Used to show a visual favicon strip in the UI without network calls.
 */
@Singleton
class FaviconResolver @Inject constructor() {

    data class DomainInfo(
        val name: String,
        val emoji: String,
        val colorHex: String,
        val faviconUrl: String? = null
    )

    private val knownDomains = mapOf(
        // Streaming
        "youtube" to DomainInfo("YouTube", "▶️", "#FF0000", "https://www.google.com/s2/favicons?domain=youtube.com&sz=128"),
        "youtu.be" to DomainInfo("YouTube", "▶️", "#FF0000", "https://www.google.com/s2/favicons?domain=youtube.com&sz=128"),
        "netflix" to DomainInfo("Netflix", "🎬", "#E50914", "https://www.google.com/s2/favicons?domain=netflix.com&sz=128"),
        "spotify" to DomainInfo("Spotify", "🎵", "#1DB954", "https://www.google.com/s2/favicons?domain=spotify.com&sz=128"),
        "twitch" to DomainInfo("Twitch", "🎮", "#9146FF", "https://www.google.com/s2/favicons?domain=twitch.tv&sz=128"),
        "primevideo" to DomainInfo("Prime Video", "📺", "#00A8E1", "https://www.google.com/s2/favicons?domain=primevideo.com&sz=128"),
        "disneyplus" to DomainInfo("Disney+", "✨", "#113CCF", "https://www.google.com/s2/favicons?domain=disneyplus.com&sz=128"),
        "hulu" to DomainInfo("Hulu", "📺", "#1CE783", "https://www.google.com/s2/favicons?domain=hulu.com&sz=128"),
        "tiktok" to DomainInfo("TikTok", "🎵", "#000000", "https://www.google.com/s2/favicons?domain=tiktok.com&sz=128"),
        "soundcloud" to DomainInfo("SoundCloud", "🎵", "#FF5500", "https://www.google.com/s2/favicons?domain=soundcloud.com&sz=128"),
        "apple.music" to DomainInfo("Apple Music", "🎵", "#FC3C44", "https://www.google.com/s2/favicons?domain=music.apple.com&sz=128"),
        "deezer" to DomainInfo("Deezer", "🎵", "#EF5466", "https://www.google.com/s2/favicons?domain=deezer.com&sz=128"),

        // Social
        "instagram" to DomainInfo("Instagram", "📸", "#E1306C", "https://www.google.com/s2/favicons?domain=instagram.com&sz=128"),
        "facebook" to DomainInfo("Facebook", "👥", "#1877F2", "https://www.google.com/s2/favicons?domain=facebook.com&sz=128"),
        "twitter" to DomainInfo("Twitter/X", "🐦", "#1DA1F2", "https://www.google.com/s2/favicons?domain=x.com&sz=128"),
        "x.com" to DomainInfo("X", "🐦", "#000000", "https://www.google.com/s2/favicons?domain=x.com&sz=128"),
        "snapchat" to DomainInfo("Snapchat", "👻", "#FFFC00", "https://www.google.com/s2/favicons?domain=snapchat.com&sz=128"),
        "reddit" to DomainInfo("Reddit", "🤖", "#FF4500", "https://www.google.com/s2/favicons?domain=reddit.com&sz=128"),
        "pinterest" to DomainInfo("Pinterest", "📌", "#E60023", "https://www.google.com/s2/favicons?domain=pinterest.com&sz=128"),
        "linkedin" to DomainInfo("LinkedIn", "💼", "#0A66C2", "https://www.google.com/s2/favicons?domain=linkedin.com&sz=128"),
        "discord" to DomainInfo("Discord", "💬", "#5865F2", "https://www.google.com/s2/favicons?domain=discord.com&sz=128"),
        "telegram" to DomainInfo("Telegram", "✈️", "#0088CC", "https://www.google.com/s2/favicons?domain=telegram.org&sz=128"),
        "whatsapp" to DomainInfo("WhatsApp", "💬", "#25D366", "https://www.google.com/s2/favicons?domain=whatsapp.com&sz=128"),
        "signal" to DomainInfo("Signal", "🔒", "#3A76F0", "https://www.google.com/s2/favicons?domain=signal.org&sz=128"),

        // Browsing
        "google" to DomainInfo("Google", "🔍", "#4285F4", "https://www.google.com/s2/favicons?domain=google.com&sz=128"),
        "bing" to DomainInfo("Bing", "🔍", "#008373", "https://www.google.com/s2/favicons?domain=bing.com&sz=128"),
        "duckduckgo" to DomainInfo("DuckDuckGo", "🦆", "#DE5833", "https://www.google.com/s2/favicons?domain=duckduckgo.com&sz=128"),
        "wikipedia" to DomainInfo("Wikipedia", "📖", "#000000", "https://www.google.com/s2/favicons?domain=wikipedia.org&sz=128"),
        "stackoverflow" to DomainInfo("StackOverflow", "💻", "#F48024", "https://www.google.com/s2/favicons?domain=stackoverflow.com&sz=128"),
        "github" to DomainInfo("GitHub", "🐙", "#181717", "https://www.google.com/s2/favicons?domain=github.com&sz=128"),
        "amazon" to DomainInfo("Amazon", "📦", "#FF9900", "https://www.google.com/s2/favicons?domain=amazon.com&sz=128"),
        "ebay" to DomainInfo("eBay", "🛒", "#E53238", "https://www.google.com/s2/favicons?domain=ebay.com&sz=128"),

        // Cloud / Productivity
        "icloud" to DomainInfo("iCloud", "☁️", "#157EFB", "https://www.google.com/s2/favicons?domain=icloud.com&sz=128"),
        "dropbox" to DomainInfo("Dropbox", "📦", "#0061FF", "https://www.google.com/s2/favicons?domain=dropbox.com&sz=128"),
        "drive.google" to DomainInfo("Google Drive", "📁", "#4285F4", "https://www.google.com/s2/favicons?domain=drive.google.com&sz=128"),
        "docs.google" to DomainInfo("Google Docs", "📄", "#4285F4", "https://www.google.com/s2/favicons?domain=docs.google.com&sz=128"),
        "onedrive" to DomainInfo("OneDrive", "☁️", "#0078D4", "https://www.google.com/s2/favicons?domain=onedrive.live.com&sz=128"),
        "office" to DomainInfo("Microsoft Office", "💼", "#D83B01", "https://www.google.com/s2/favicons?domain=office.com&sz=128"),
        "notion" to DomainInfo("Notion", "📝", "#000000", "https://www.google.com/s2/favicons?domain=notion.so&sz=128"),
        "slack" to DomainInfo("Slack", "💬", "#4A154B", "https://www.google.com/s2/favicons?domain=slack.com&sz=128"),
        "zoom" to DomainInfo("Zoom", "📹", "#2D8CFF", "https://www.google.com/s2/favicons?domain=zoom.us&sz=128"),
        "meet.google" to DomainInfo("Google Meet", "📹", "#00897B", "https://www.google.com/s2/favicons?domain=meet.google.com&sz=128"),
        "teams.microsoft" to DomainInfo("MS Teams", "📹", "#6264A7", "https://www.google.com/s2/favicons?domain=teams.microsoft.com&sz=128"),

        // System / CDN
        "amazonaws" to DomainInfo("AWS", "☁️", "#FF9900"),
        "cloudfront" to DomainInfo("CloudFront", "☁️", "#FF9900"),
        "googleapis" to DomainInfo("Google API", "🔧", "#4285F4"),
        "gstatic" to DomainInfo("Google Static", "🔧", "#4285F4"),
        "akamai" to DomainInfo("Akamai CDN", "🔧", "#009BDE"),
        "fastly" to DomainInfo("Fastly CDN", "🔧", "#FF282D"),
        "cloudflare" to DomainInfo("Cloudflare", "🔧", "#F38020"),
        "apple" to DomainInfo("Apple", "🍎", "#555555"),
        "doubleclick" to DomainInfo("Google Ads", "📊", "#4285F4"),
    )

    fun resolve(domain: String): DomainInfo? {
        val lower = domain.lowercase()
        // Try known first
        val known = knownDomains.entries.firstOrNull { (key, _) -> lower.contains(key) }?.value
        if (known != null) return known
        
        // Dynamic fallback for any domain
        // Audit 20: Robust Root-Domain Extraction (Local-Only)
        if (domain.contains(".")) {
            val parts = lower.split(".")
            val root = if (parts.size >= 2) {
                val tldIndex = when {
                    parts.last() in listOf("co","com","net","org","edu") && parts.size >= 3 -> parts.size - 3
                    else -> parts.size - 2
                }
                parts.getOrNull(tldIndex) ?: parts.first()
            } else parts.first()
            
            return DomainInfo(
                name = root.replaceFirstChar { it.uppercase() },
                emoji = "🌐",
                colorHex = "#555555"
            )
        }
        return null
    }

    fun resolveAll(domains: List<String>): List<Pair<String, DomainInfo?>> {
        return domains.map { it to resolve(it) }
    }

    fun isKnownService(domain: String): Boolean = resolve(domain) != null
}
