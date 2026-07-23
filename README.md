# FileViewer Android

A local-first Android port of the macOS FileViewer: Markdown notes editor + PDF reader. Personal-use app, sideloaded via APK. No accounts, no cloud sync, no AI features.

## Features

- **Markdown** (`.md`, `.markdown`): preview / source / split modes, live preview with GFM tables + task lists, formatting toolbar, search with highlights, heading navigation drawer, syntax guide, Save / Save As.
- **PDF** (`.pdf`): continuous scroll, pinch + double-tap zoom, fit width/page, page navigation + go-to-page, text search with highlight overlay, outline + page thumbnails in the drawer, reading mode (text reflow), print.
- **App shell**: tabs with unsaved-changes protection, session restore, per-file scroll/page/zoom restore, recent documents, system light/dark theme.

## Build

Requirements: Android Studio (uses its bundled JDK), Android SDK at `~/Library/Android/sdk`.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug              # debug APK
./gradlew testDebugUnitTest          # unit tests
./gradlew lint                       # lint
```

## Release APK (sideload)

Signing needs two gitignored files in the repo root (already present on Patrick's machine):

- `fileviewer-release.keystore`
- `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword)

```bash
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Install on a connected device/emulator:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it (allow "install unknown apps" when prompted).

> Keep `fileviewer-release.keystore` and its password safe — losing them means future APK updates can't be installed over the existing app (Android requires the same signing certificate). The keystore is deliberately not committed to git.

## Project docs

- `HANDOFF.md` — current status, environment notes, code map.
- `docs/android-requirements-and-plan.md` — full requirements and phase plan.
- `docs/pdf-annotation-spike.md` — PDF annotation feasibility results (Phase 5/6 is optional, gated on this).
