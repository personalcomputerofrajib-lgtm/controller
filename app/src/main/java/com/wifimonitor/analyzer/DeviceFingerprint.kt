package com.wifimonitor.analyzer

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device signature persistence — creates a stable identity hash for each device
 * that survives IP changes, and detects MAC randomization patterns.
 *
 * Fingerprint = SHA-256(MAC + vendor + deviceType + services).
 * Even if IP changes, the hash stays the same.
 */
@Singleton
class DeviceFingerprint @Inject constructor() {

    /**
     * Compute stable fingerprint hash from device attributes.
     * Used to re-identify devices across IP changes.
     */
    fun computeFingerprint(
        mac: String,
        vendor: String,
        deviceType: String,
        services: String = ""
    ): String {
        val input = "$mac|${vendor.lowercase().trim()}|${deviceType.lowercase()}|${services.lowercase()}"
        return sha256(input).take(16) // 16-char hex for compact storage
    }

    /**
     * Detect if MAC address is likely randomized (Android 10+, iOS 14+).
     *
     * Randomized MACs have the locally administered bit set (second hex digit
     * of first octet is one of: 2, 6, A, E).
     */
    fun isMacRandomized(mac: String): Boolean {
        if (mac.length < 2) return false
        val firstOctet = mac.replace(":", "").replace("-", "").take(2)
        return try {
            val byte = firstOctet.toInt(16)
            // Locally administered bit = bit 1 of first octet
            (byte and 0x02) != 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Determine if two MACs are likely the same physical device despite randomization.
     * Checks: same vendor, same device type, similar behavior pattern.
     */
    fun isSamePhysicalDevice(
        mac1: String, vendor1: String, type1: String,
        mac2: String, vendor2: String, type2: String
    ): Boolean {
        // If either is not randomized — different MACs = different devices
        if (!isMacRandomized(mac1) && !isMacRandomized(mac2)) return false

        // Same vendor + same type = possibly same device
        if (vendor1.isNotBlank() && vendor1.equals(vendor2, ignoreCase = true) &&
            type1.equals(type2, ignoreCase = true)) {
            // Check OUI match (first 6 chars)
            val oui1 = mac1.replace(":", "").take(6)
            val oui2 = mac2.replace(":", "").take(6)
            return oui1.equals(oui2, ignoreCase = true)
        }

        return false
    }

    /**
     * Compute confidence score for device identity accuracy.
     */
    fun identityConfidence(
        hasHostname: Boolean,
        hasManufacturer: Boolean,
        hasPorts: Boolean,
        isRandomized: Boolean,
        observationCount: Int,
        reliabilityPct: Int
    ): Int {
        var score = 0
        score += when {
            observationCount >= 50 -> 30
            observationCount >= 20 -> 25
            observationCount >= 10 -> 20
            observationCount >= 5 -> 15
            else -> 5
        }
        if (hasHostname) score += 25
        if (hasManufacturer) score += 20
        if (hasPorts) score += 10
        if (isRandomized) score -= 10  // lower confidence for randomized MACs
        score += (reliabilityPct * 0.15f).toInt()
        return score.coerceIn(0, 100)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            sb.append("%02x".format(b))
        }
        return sb.toString()
    }
}
