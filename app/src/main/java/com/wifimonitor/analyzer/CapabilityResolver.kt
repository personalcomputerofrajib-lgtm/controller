package com.wifimonitor.analyzer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Level 10 Precision Component.
 * Translates raw technical artifacts (Ports, mDNS tags, OUI) into
 * Human-readable "Identified Capabilities."
 */
@Singleton
class CapabilityResolver @Inject constructor() {

    data class Capability(
        val name: String,
        val icon: String,
        val description: String
    )

    /**
     * Resolves capabilities based on open ports and mDNS names.
     */
    fun resolve(ports: List<Int>, mdnsNames: List<String>, vendor: String): List<Capability> {
        val result = mutableListOf<Capability>()
        val p = ports.toSet()
        val m = mdnsNames.map { it.lowercase() }
        val v = vendor.lowercase()

        // ── Entertainment & Media ──
        if (p.contains(8008) || m.any { it.contains("chromecast") }) {
            result.add(Capability("Video Casting", "📺", "Ready for Google Cast / YouTube"))
        }
        if (p.contains(7000) || m.any { it.contains("airplay") }) {
            result.add(Capability("AirPlay", "📱", "Apple screen mirroring & audio"))
        }
        if (p.contains(32400) || m.any { it.contains("plex") }) {
            result.add(Capability("Plex Media Server", "🎬", "Hosting a private movie library"))
        }
        if (p.contains(1900) || m.any { it.contains("_dlna") }) {
            result.add(Capability("DLNA Streamer", "📻", "Universal media streaming sink"))
        }

        // ── Storage & Networking ──
        if (p.contains(445) || p.contains(139) || m.any { it.contains("_smb") }) {
            result.add(Capability("File Sharing (SMB)", "📂", "Windows/Samba network share available"))
        }
        if (p.contains(548) || m.any { it.contains("_afp") }) {
            result.add(Capability("File Sharing (AFP)", "🍎", "Apple Filing Protocol share active"))
        }
        if (p.contains(22) || m.any { it.contains("_ssh") }) {
            result.add(Capability("Secure Remote Shell", "💻", "Command-line access via SSH"))
        }
        if (p.contains(80) || p.contains(443) || p.contains(8080)) {
            result.add(Capability("Web Admin Dashboard", "🌐", "Browser-based configuration site"))
        }

        // ── Smart Home & IoT ──
        if (m.any { it.contains("mqtt") } || p.contains(1883)) {
            result.add(Capability("MQTT Broker", "💡", "Smart home automation gateway"))
        }
        if (m.any { it.contains("homekit") } || m.any { it.contains("_hap") }) {
            result.add(Capability("Apple HomeKit", "🏠", "Home automation accessory"))
        }
        if (v.contains("sonos") || m.any { it.contains("sonos") }) {
            result.add(Capability("Hi-Fi Audio Station", "🔊", "Networked speaker system"))
        }

        // ── General ──
        if (p.contains(9100) || m.any { it.contains("_printer") } || m.any { it.contains("_pjl") }) {
            result.add(Capability("Network Printing", "🖨️", "Wireless document printing support"))
        }

        return result
    }
}
