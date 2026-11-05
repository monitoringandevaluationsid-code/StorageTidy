# StorageTidy

A small Android app that scans your **Gallery** (photos + videos) and
**Downloads**, finds **exact duplicate files**, and flags **large** and **old**
files so you can clear them. It is built for the **Samsung Galaxy A56**
(One UI 7 / Android 15) but runs on any phone from Android 10 upward.

Nothing is ever deleted silently: every removal goes through Android's own
system confirmation screen, so you tap to approve. The app needs only
read-media permission — not the scary "All files access".

---

## What it does

| Tab | What it shows |
|-----|---------------|
| **Duplicates** | Groups of byte-for-byte identical files. The oldest copy is marked *keep*; the rest are tickable. "Select all extras" ticks every reclaimable copy at once. |
| **Screenshots** | Every screenshot, newest first — usually the biggest easy win in a cluttered gallery. |
| **Large** | Any file 50 MB or larger, biggest first. |
| **Old** | Files in Downloads older than ~6 months. |

Each row shows a thumbnail preview. The broom icon (top right) opens Android's
own storage-cleanup screen for clearing app caches, which no third-party app is
allowed to do itself.

Tick what you want gone, press **Delete selected**, confirm on Android's
dialog. The list updates immediately — no re-scan needed.

### How duplicate detection works
1. Files are grouped by size. A size that only appears once can't have a
   duplicate, so it is never even read.
2. Within each size group, file contents are hashed with SHA-256 and grouped by
   hash. Identical hash = identical file. This finds true duplicates (e.g. the
   same photo saved twice, a re-downloaded PDF), not merely similar-looking
   images.

---

## No computer? Build it in the cloud

See **INSTALL_NO_PC.md** — a free GitHub account compiles the APK for you online
and you download it straight to the phone. No PC, no Android Studio, no on-phone
compiler. This is the recommended route if you don't have a computer.

## Building it (one-time, on a PC)

You need **Android Studio** (free). You do **not** need to install Gradle or
the command-line SDK separately — Android Studio brings them, and the included
`gradlew` wrapper handles the rest.

### Option A — Android Studio (easiest)
1. Install **Android Studio** (Ladybug 2024.2 or newer) from
   `developer.android.com/studio`.
2. **File → Open** → select this `StorageTidy` folder → **OK**.
3. Wait for the first Gradle sync (it downloads the Android SDK bits and
   dependencies — needs internet, a few minutes the first time).
4. Plug in your A56 with a USB cable. On the phone, enable **Developer
   options** (Settings → About phone → tap *Build number* 7 times), then turn on
   **USB debugging** (Settings → Developer options).
5. Pick your phone in the device dropdown at the top, press the green **Run ▶**.
   The app installs and launches.

### Option B — command line
From this folder, with a JDK 17 and the Android SDK installed and
`ANDROID_HOME` set:
```
./gradlew assembleDebug        # macOS / Linux
gradlew.bat assembleDebug      # Windows
```
The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to the
phone and open it to install (you'll need to allow "install from unknown
source" once).

---

## Installing the APK directly

Because this is your own build (not from the Play Store), the phone will ask you
to allow installation from the file manager or browser the first time. That's
expected for a sideloaded app.

---

## Making it "automatic"

This app is **on-demand** by design: it shows you what it found and you approve
deletes. A truly unattended auto-delete of personal photos is risky — it can't
know which files matter to you. If you still want scheduled, hands-off cleanup
of the **Downloads** folder specifically (where auto-deleting old files is
safe), pair this app with **MacroDroid** and a weekly "delete files older than
30 days in /Download" macro. Use StorageTidy for the Gallery, where a human eye
on each delete is worth it.

---

## Project layout
```
app/src/main/java/com/vsandoram/storagetidy/
├── MainActivity.kt            entry point, permissions, system delete dialog
├── data/
│   ├── MediaItem.kt           the file model + duplicate group
│   ├── MediaScanner.kt        reads Gallery + Downloads via MediaStore
│   ├── DuplicateFinder.kt     size-bucket then SHA-256 hashing
│   └── Analyzer.kt            builds the large / old / duplicate views
├── viewmodel/TidyViewModel.kt scan state + selection
├── ui/                        Compose screens
└── util/Format.kt             byte + date formatting
```

## Privacy
The app reads only the media index on your phone and keeps everything local. It
makes no network calls, has no analytics, and stores nothing off-device.

Author: Virendrasingh Sandoram
