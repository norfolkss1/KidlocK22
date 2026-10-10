# Get the APK without Android Studio (GitHub builds it for free)

1. Sign in (or sign up, free) at github.com.
2. Click **+ > New repository**. Name it `kidlock`. Choose **Private**. Click Create.
3. On the new repo page click **uploading an existing file**.
4. Unzip this project on your computer. Open the unzipped `kidlock` folder, select EVERYTHING inside it
   (including the hidden `.github` folder) and drag it into the browser page. Click **Commit changes**.
   Tip: if `.github` does not show on your computer, enable "Show hidden files".
   The file `.github/workflows/build.yml` must end up in the repo.
5. Click the **Actions** tab. A run called "Build APK" starts by itself (about 3-5 minutes).
6. When it shows a green tick, open the run and download **KidLock-apk** at the bottom (Artifacts). Unzip it: `app-debug.apk`.
7. If the run shows a red cross, open it, copy the red error text and send it to Claude to fix.

Then continue with the setup steps (install Chrome, remove accounts, adb install, set-device-owner).
Note: the final "dpm set-device-owner" command always needs a computer with adb and a USB cable.
It cannot be done by the APK alone.
