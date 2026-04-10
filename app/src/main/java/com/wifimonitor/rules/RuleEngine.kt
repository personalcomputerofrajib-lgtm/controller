package com.wifimonitor.rules

import javax.inject.Inject
import javax.inject.Singleton
import java.util.Calendar

/**
 * Real infrastructure-level rule engine.
 * Handles schedules, usage quotas, and domain-level filters.
 */
@Singleton
class RuleEngine @Inject constructor() {

    enum class Action { ALLOW, BLOCK, THROTTLE }
    enum class RuleType { DOMAIN, SCHEDULE, GLOBAL_BLOCK, CATEGORY }
    enum class Category { SOCIAL, VIDEO, GAMING, ADULT, ADVERTISING, OTHER }

    data class Rule(
        val id: String,
        val type: RuleType,
        val deviceMac: String? = null, // null means global rule
        val target: String, // Domain, Quota in MB, or "HH:mm-HH:mm"
        val action: Action,
        val daysOfWeek: Set<Int>? = null, // Calendar.MONDAY, etc. null means every day
        val isEnabled: Boolean = true,
        val expiresAt: Long? = null
    )

    private val rules = mutableListOf<Rule>()

    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    fun removeRule(id: String) {
        rules.removeIf { it.id == id }
    }

    fun getRules() = rules.toList()

    /**
     * Evaluates if a connection attempt for a domain from a specific device should be allowed.
     */
    fun checkDomainAccess(mac: String, domain: String): Action {
        val activeRules = rules.filter { r ->
            r.isEnabled &&
            (r.deviceMac == null || r.deviceMac == mac) &&
            (r.expiresAt == null || r.expiresAt > System.currentTimeMillis())
        }
        val categoryRules = activeRules.filter { it.type == RuleType.CATEGORY }

        // 1. Check Global Kill-switch
        activeRules.filter { it.type == RuleType.GLOBAL_BLOCK }.forEach { return it.action }

        // 2. Check Domain rules
        activeRules.filter { it.type == RuleType.DOMAIN }.forEach { rule ->
            if (domain.contains(rule.target, ignoreCase = true)) {
                return rule.action
            }
        }

        // 3. Check Schedule rules
        activeRules.filter { it.type == RuleType.SCHEDULE }.forEach { rule ->
            val now = Calendar.getInstance()
            val today = now.get(Calendar.DAY_OF_WEEK)
            
            if ((rule.daysOfWeek == null || rule.daysOfWeek.contains(today)) && isInsideSchedule(rule.target)) {
                return rule.action
            }
        }

        // 4. Category Check (Point #12)
        val category = resolveCategory(domain)
        if (categoryRules.any { it.target == category.name && it.isEnabled }) return Action.BLOCK

        return Action.ALLOW
    }

    private fun resolveCategory(domain: String): Category {
        val d = domain.lowercase()
        return when {
            d.contains("facebook") || d.contains("instagram") || d.contains("twitter") || d.contains("tiktok") -> Category.SOCIAL
            d.contains("youtube") || d.contains("netflix") || d.contains("twitch") || d.contains("vimeo") -> Category.VIDEO
            d.contains("roblox") || d.contains("steam") || d.contains("epicgames") -> Category.GAMING
            d.contains("doubleclick") || d.contains("adservice") || d.contains("telemetry") -> Category.ADVERTISING
            else -> Category.OTHER
        }
    }

    /**
     * Parses "22:00-06:00" format and checks against current time.
     */
    private fun isInsideSchedule(schedule: String): Boolean {
        try {
            val parts = schedule.split("-")
            if (parts.size != 2) return false
            
            val now = Calendar.getInstance()
            val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            
            val startMinutes = startParts[0].toInt() * 60 + startParts[1].toInt()
            val endMinutes = endParts[0].toInt() * 60 + endParts[1].toInt()
            
            return if (startMinutes < endMinutes) {
                currentMinutes in startMinutes..endMinutes
            } else {
                // Overnight schedule (e.g., 22:00-06:00)
                currentMinutes >= startMinutes || currentMinutes <= endMinutes
            }
        } catch (e: Exception) {
            return false
        }
    }
}
