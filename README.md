# KidLock: allowlist-only kiosk for an Android tablet

The tablet runs only the apps you tick. Everything else cannot open (games, browser, Play Store, Settings).
It works by making KidLock the **device owner**, which is much stronger than Family Link.

## 1. Build
1. Install Android Studio and open the `kidlock` folder (it downloads Gradle and the SDK on first sync).
2. Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`.

## 2. Prepare the Xiaomi Pad (important, Xiaomi is picky)
Device-owner setup only works when the tablet has **no accounts and no extra users**.
0. For Kahoot you need **Google Chrome** on the tablet. If it is not installed, install it from Play Store now. After step 3 the accounts are gone and Play Store can no longer install anything.
1. Settings > About > tap the MIUI/HyperOS version 7 times to enable Developer options.
2. In Developer options turn on **USB debugging** and **USB debugging (Security settings)**.
   Xiaomi may ask you to sign in to a Mi account for this toggle. Do that first, then continue.
3. Remove ALL accounts (Google, Mi, others) in Settings > Accounts.
4. Delete Second space / extra users / Dual apps if present.
5. Remove any screen lock PIN/pattern for now (you can set one again after setup).

## 3. Install and make it device owner
On your computer, with the tablet connected by USB (allow the debugging prompt):

```
adb install app-debug.apk
adb shell dpm set-device-owner com.kidlock/.AdminReceiver
```
You should see "Success". If you see "already several users" or "accounts" errors, repeat step 2.4 / 2.3.

## 4. First run
1. Press Home and choose KidLock if asked.
2. Set a parent PIN (6+ digits). Keep it secret and do not use a birthday.
3. **Long-press the "My apps" title**, enter the PIN, tick the allowed apps, press Save.

## What it enforces
- Pinned kiosk (lock task): only ticked apps can start; no status bar, no recents, Home returns to KidLock.
- Non-ticked user apps are suspended as a second layer.
- No installing apps or sideloading, no uninstalling, no app-info changes.
- No accounts changes, no new users, no safe mode, no factory reset from Settings.
- USB file transfer and USB debugging blocked (so adb cannot be used to remove it).
- Date/time locked to automatic (stops time tricks).
- 5 wrong PINs = PIN screen locked for 15 minutes.

## Browser: Kahoot only
Tick **Chrome** in the parent screen. KidLock then sets Chrome's managed policies:
- every website is blocked except the list in "Websites Chrome may open" (default `kahoot.it, kahoot.com`)
- incognito is off, internal chrome:// pages are blocked, the home page and new tab page open Kahoot
- Xiaomi's own browser and any other browser stay blocked, because they are not ticked

If his school uses a login (Google, Microsoft, Clever) add that site to the list too, or Kahoot sign-in will fail.
Joining a game with a PIN at kahoot.it needs no login.
Every site you add is a site he can open, so keep the list short.

## Parent mode (installing apps)
Normally installing and accounts are blocked. To install apps:
1. Long-press the title, enter PIN, tap **Turn on Parent mode (30 minutes)**.
2. Tap **Open Play Store**, sign in with YOUR Google account, install the app.
3. Sign OUT of your Google account in Play Store, come back to KidLock, long-press the title, enter PIN.
4. Tick the new app in ALLOWED APPS (new apps stay blocked until you tick them) and tap **SAVE**.
5. Tap **Lock again now** (it also locks by itself after 30 minutes).
While Parent mode is on the tablet is open. Do it with the tablet in your hands.

## Website list (Chrome)
In the parent screen, CHROME WEBSITES shows your list. Tick = allowed, untick = restricted,
X = delete, "Add website" to add one. Tap **SAVE** after changing. Adding `kahoot.it` also covers its subdomains.

## Honest limits
- The Chrome website limit relies on Chrome obeying its managed policies. Test it on the tablet: try google.com, youtube.com and a search, they should all be blocked.
- A hardware recovery-mode wipe (power + volume buttons) is outside what any app can block. It erases the tablet; it does not give him back his data.
- If you forget the PIN you will need a recovery wipe too. Write the PIN down somewhere safe.
- Anything you allow can be a way around it. A browser, YouTube, or an app with a built-in store or mini-games defeats the purpose. Allow only what you checked.
- This is a first version: written for Android 9+ and **not yet tested on a real Xiaomi Pad**. Test with the tablet in your hands before handing it over.

## To remove everything
Long-press the title, enter PIN, "Remove all controls and exit". Then uninstall KidLock.
