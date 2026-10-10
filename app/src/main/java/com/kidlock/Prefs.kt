package com.kidlock

import android.content.Context
import java.security.MessageDigest
import java.security.SecureRandom

object Prefs {
    private const val MAX_FAILS = 5
    private const val LOCKOUT_MS = 15 * 60 * 1000L

    private fun sp(c: Context) = c.getSharedPreferences("kidlock", Context.MODE_PRIVATE)

    fun allowed(c: Context): Set<String> =
        sp(c).getStringSet("allowed", emptySet())!!.toSet()

    fun setAllowed(c: Context, s: Set<String>) {
        sp(c).edit().putStringSet("allowed", s).apply()
    }

    /** Website list for Chrome: (domain, allowed?). Stored as "domain:1,domain:0". */
    fun siteList(c: Context): List<Pair<String, Boolean>> {
        val raw = sp(c).getString("site_list", null) ?: "kahoot.it:1,kahoot.com:1"
        return raw.split(",")
            .filter { it.isNotBlank() }
            .map { item ->
                val p = item.split(":")
                p[0] to (p.getOrNull(1) != "0")
            }
    }

    fun setSiteList(c: Context, list: List<Pair<String, Boolean>>) {
        sp(c).edit()
            .putString("site_list", list.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" })
            .apply()
    }

    /** Only the websites that are switched ON. */
    fun sites(c: Context): List<String> = siteList(c).filter { it.second }.map { it.first }

    /** Turns "https://www.Kahoot.it/play" into "kahoot.it". Returns null if it is not a domain. */
    fun cleanDomain(raw: String): String? {
        val d = raw.trim().lowercase()
            .removePrefix("https://").removePrefix("http://").removePrefix("www.")
            .substringBefore("/").substringBefore(":")
        val ok = d.contains(".") && !d.startsWith(".") && !d.endsWith(".") &&
            d.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return if (ok) d else null
    }

    /** Parent mode: the install restrictions are lifted until this time (epoch ms). */
    fun parentUntil(c: Context): Long = sp(c).getLong("parent_until", 0)

    fun setParentUntil(c: Context, t: Long) {
        sp(c).edit().putLong("parent_until", t).apply()
    }

    fun parentActive(c: Context) = System.currentTimeMillis() < parentUntil(c)

    fun hasPin(c: Context) = sp(c).contains("pin_hash")

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        sp(c).edit()
            .putString("salt", salt)
            .putString("pin_hash", hash(pin, salt))
            .putInt("fails", 0)
            .putLong("lock_until", 0)
            .apply()
    }

    fun lockedUntil(c: Context) = sp(c).getLong("lock_until", 0)

    /** Checks the PIN. After 5 wrong tries the PIN screen is locked for 15 minutes. */
    fun tryPin(c: Context, pin: String): Boolean {
        val s = sp(c)
        if (System.currentTimeMillis() < lockedUntil(c)) return false
        val salt = s.getString("salt", null) ?: return false
        if (hash(pin, salt) == s.getString("pin_hash", null)) {
            s.edit().putInt("fails", 0).apply()
            return true
        }
        val fails = s.getInt("fails", 0) + 1
        if (fails >= MAX_FAILS) {
            s.edit().putInt("fails", 0)
                .putLong("lock_until", System.currentTimeMillis() + LOCKOUT_MS).apply()
        } else {
            s.edit().putInt("fails", fails).apply()
        }
        return false
    }
}
