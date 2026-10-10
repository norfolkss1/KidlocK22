package com.kidlock

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings

object Policy {

    /** Always on, even in Parent mode. */
    private val ALWAYS = listOf(
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_APPS_CONTROL,
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_USER_SWITCH,
        UserManager.DISALLOW_CONFIG_DATE_TIME,
        UserManager.DISALLOW_USB_FILE_TRANSFER,
        UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        UserManager.DISALLOW_DEBUGGING_FEATURES
    )

    /** Lifted only while Parent mode is on (so you can sign in to Play Store and install). */
    private val LOCKED_ONLY = listOf(
        UserManager.DISALLOW_INSTALL_APPS,
        UserManager.DISALLOW_UNINSTALL_APPS,
        UserManager.DISALLOW_MODIFY_ACCOUNTS
    )

    private fun dpm(c: Context) = c.getSystemService(DevicePolicyManager::class.java)
    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    private fun userApps(c: Context): List<String> =
        c.packageManager.getInstalledApplications(0)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != c.packageName }
            .map { it.packageName }

    /** Applies the lockdown (or the Parent-mode version of it). Safe to call repeatedly. */
    fun apply(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c)
        val a = admin(c)
        val allowed = Prefs.allowed(c) + c.packageName
        val open = Prefs.parentActive(c)

        // Only these packages may run while the tablet is pinned (kiosk).
        d.setLockTaskPackages(a, allowed.toTypedArray())
        d.setLockTaskFeatures(a, DevicePolicyManager.LOCK_TASK_FEATURE_HOME)

        ALWAYS.forEach { d.addUserRestriction(a, it) }
        LOCKED_ONLY.forEach { if (open) d.clearUserRestriction(a, it) else d.addUserRestriction(a, it) }

        try {
            d.setGlobalSetting(a, Settings.Global.AUTO_TIME, "1")
            d.setGlobalSetting(a, Settings.Global.AUTO_TIME_ZONE, "1")
        } catch (_: Exception) {
        }

        // Make this app the permanent home screen.
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        d.addPersistentPreferredActivity(a, home, ComponentName(c, MainActivity::class.java))

        // Suspend every installed non-system app that is not ticked.
        // Skipped during Parent mode so a freshly installed app can finish installing.
        if (!open) {
            val (ok, blocked) = userApps(c).partition { it in allowed }
            if (ok.isNotEmpty()) d.setPackagesSuspended(a, ok.toTypedArray(), false)
            if (blocked.isNotEmpty()) d.setPackagesSuspended(a, blocked.toTypedArray(), true)
        }

        applyBrowser(c, d, a, allowed)
    }

    // ---- Parent mode ---------------------------------------------------------------------

    fun openParentMode(c: Context, minutes: Int) {
        Prefs.setParentUntil(c, System.currentTimeMillis() + minutes * 60_000L)
        apply(c)
        scheduleRelock(c)
    }

    fun lockNow(c: Context) {
        Prefs.setParentUntil(c, 0)
        c.getSystemService(AlarmManager::class.java).cancel(relockIntent(c))
        apply(c)
    }

    private fun relockIntent(c: Context): PendingIntent =
        PendingIntent.getBroadcast(
            c, 1, Intent(c, RelockReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun scheduleRelock(c: Context) {
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, Prefs.parentUntil(c) + 1000, relockIntent(c)
        )
    }

    // ---- Chrome website list -------------------------------------------------------------

    private const val CHROME = "com.android.chrome"

    /**
     * If Chrome is allowed, lock it to the website list using Chrome's managed-browser
     * policies: block every URL, then allow only the sites switched ON.
     */
    fun applyBrowser(c: Context) {
        if (!isOwner(c)) return
        applyBrowser(c, dpm(c), admin(c), Prefs.allowed(c) + c.packageName)
    }

    private fun applyBrowser(c: Context, d: DevicePolicyManager, a: ComponentName, allowed: Set<String>) {
        if (CHROME !in allowed) return
        val sites = Prefs.sites(c)
        val home = "https://" + (sites.firstOrNull() ?: "kahoot.it")
        val b = Bundle().apply {
            putStringArray(
                "URLBlocklist",
                arrayOf("*", "chrome://*", "chrome-untrusted://*", "view-source:*", "javascript:*")
            )
            putStringArray("URLAllowlist", sites.toTypedArray())
            putInt("IncognitoModeAvailability", 1) // incognito disabled
            putString("HomepageLocation", home)
            putBoolean("HomepageIsNewTabPage", false)
            putString("NewTabPageLocation", home)
            putBoolean("ShowHomeButton", true)
            putBoolean("SearchSuggestEnabled", false)
            putBoolean("TranslateEnabled", false)
        }
        try {
            d.setApplicationRestrictions(a, CHROME, b)
        } catch (_: Exception) {
        }
    }

    // ---- Remove everything ---------------------------------------------------------------

    /** Removes all restrictions and gives up device-owner status. */
    fun release(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c)
        val a = admin(c)
        (ALWAYS + LOCKED_ONLY).forEach { d.clearUserRestriction(a, it) }
        d.setLockTaskPackages(a, emptyArray())
        d.clearPackagePersistentPreferredActivities(a, c.packageName)
        val all = userApps(c)
        if (all.isNotEmpty()) d.setPackagesSuspended(a, all.toTypedArray(), false)
        try {
            d.setApplicationRestrictions(a, CHROME, Bundle())
        } catch (_: Exception) {
        }
        d.clearDeviceOwnerApp(c.packageName)
    }
}
