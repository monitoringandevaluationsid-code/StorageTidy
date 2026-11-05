# Installing StorageTidy on your phone with NO computer

You don't need Android Studio or a PC. A free GitHub account builds the app for
you in the cloud, and you download the finished APK straight to your A56 and
tap to install. All of this can be done in the phone's browser.

Total time: ~15 minutes, most of it waiting for the build.

---

## Step 1 — Make a free GitHub account
Go to **github.com** in your phone browser and sign up (free). Verify your
email.

## Step 2 — Create an empty repository
1. Tap **+** (top right) → **New repository**.
2. Name it `StorageTidy`. Leave it **Public** (private also works). Tap
   **Create repository**.

## Step 3 — Upload the project
GitHub's phone web page lets you upload files, but it won't take a whole folder
tree in one go, so the easiest path is:

**Easiest — upload the ZIP's contents using the GitHub mobile app or a PC-free uploader:**
1. Install the **GitHub** app from the Play Store, sign in.
2. Or, on the website: open your new repo → **Add file → Upload files** →
   select the files. You may need to unzip `StorageTidy.zip` first using a file
   manager (Samsung My Files can extract a ZIP: long-press it → Extract).
3. Drag every item from inside the extracted `StorageTidy` folder into the
   upload box — including the hidden `.github` folder (it holds the build
   recipe). In My Files, enable **Show hidden files** in the menu so `.github`
   is visible, then upload it too.
4. Commit the upload.

> If uploading the `.github` folder from a phone is awkward, create it by hand:
> on the repo page tap **Add file → Create new file**, type
> `.github/workflows/build.yml` as the name (the slashes make the folders),
> paste in the contents of that file from the ZIP, and commit.

## Step 4 — Let the cloud build it
1. Open the **Actions** tab of your repo.
2. You'll see a run called **Build APK** start on its own (because you pushed
   files). If not, tap **Build APK → Run workflow**.
3. Wait for the green tick (about 3–6 minutes the first time).

## Step 5 — Download and install the APK
1. Open the finished run → scroll to **Artifacts** → tap **StorageTidy-apk**.
   It downloads a small ZIP containing `app-debug.apk`.
2. In **My Files → Downloads**, extract that ZIP, then tap `app-debug.apk`.
3. Android will ask to allow installing from this source — allow it once.
4. StorageTidy installs. Open it, grant photo/video access, and scan.

---

## Why this route
- No PC, no Android Studio, no on-phone compiler.
- The build happens on GitHub's free Linux machines, which already have the
  Android SDK — the same SDK a PC build would download.
- You only ever handle the finished APK, which installs like any sideloaded app.

If a build fails, open the failed run in the Actions tab, copy the red error
text, and send it to me — I'll fix the project and you re-run.
