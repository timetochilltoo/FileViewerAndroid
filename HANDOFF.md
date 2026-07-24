# FileViewer Android — Handoff

Last updated: 2026-07-23 (Phase 7 done — signed release APK ready)
Active repo (local): `/Users/patrickshi/KimiCoding/FileViewer`
GitHub remote: `https://github.com/timetochilltoo/FileViewerAndroid.git`
Git identity (already in Patrick's global git config): user `timetochilltoo`, email `152804118+timetochilltoo@users.noreply.github.com`
Current branch: `main`
Current committed baseline: see `git log -1` (Phase 4 commit)

> This document is the single source of truth for taking over the project. The **scope/plan** source of truth is `docs/android-requirements-and-plan.md` (v2). Where they conflict, the plan wins on scope and this file wins on environment/status.

---

## 1. Project purpose

FileViewer Android is Patrick's local-first native Android port of his macOS FileViewer (see that repo's `HANDOFF.md` for the original). It covers:

- Markdown notes (`.md`, `.markdown`) — view, edit, search, formatting toolbar
- PDFs (`.pdf`) — view, navigate, zoom, search, thumbnails, outline, text reflow reading mode
- Multiple open documents via tabs
- Unsaved-changes protection, session restore, per-file state restore
- Print / export
- PDF annotations + fillable forms are **P2/P3 nice-to-have**, gated on the Phase 0 spike (which **passed**)

Explicitly **out of scope**: AI features, cloud sync/accounts, multi-window, phone split-mode, preview-selection formatting, OCR, EPUB/DOCX, Play Store release (personal-use app, sideloaded APK).

## 2. Environment (macOS host specifics — read first)

Patrick's machine has quirks every command must respect:

- **No system Java.** `java` is not on PATH. Every Gradle/sdkmanager command needs:
  ```bash
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  ```
  (Android Studio's bundled JetBrains Runtime, JDK 21.) `gradle.properties` already sets `org.gradle.java.home` for the daemon, but the `gradlew` launcher script itself also needs `JAVA_HOME`.
- **Android SDK**: `~/Library/Android/sdk` (platforms: android-34, android-36.1; build-tools 34/36.1/37). `local.properties` (gitignored) points `sdk.dir` there.
- **cmdline-tools**: installed by the previous agent at `~/Library/Android/sdk/cmdline-tools/latest` (was missing; downloaded from `dl.google.com`). Provides `sdkmanager`/`avdmanager`.
- **Emulator**: AVD `fileviewer_test` exists (pixel_6, API 34, google_apis, arm64). Boot headless:
  ```bash
  nohup ~/Library/Android/sdk/emulator/emulator -avd fileviewer_test -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-save > /tmp/emulator.log 2>&1 & disown
  ```
  Boot takes ~60–90s. Poll `adb shell getprop sys.boot_completed` for `1`.
  **Gotcha:** the agent shell kills the process group when a command times out — always launch the emulator with `nohup ... & disown` and poll in *separate, short* commands, or the emulator dies with the polling command.
- **No `timeout` command on macOS** — use shell loops with counters instead.
- **adb** at `~/Library/Android/sdk/platform-tools/adb`, works.

## 3. Build / test commands

```bash
cd /Users/patrickshi/KimiCoding/FileViewer
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

./gradlew assembleDebug               # debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest           # JVM unit tests (Robolectric available)
./gradlew connectedDebugAndroidTest   # instrumented tests — needs booted emulator/device
./gradlew lint                        # lint
```

## 4. Version pins (do not bump casually)

| Component | Version | Note |
|---|---|---|
| Gradle wrapper | 8.9 | generated with Homebrew gradle 9.6.1 (only used once to generate) |
| AGP | 8.7.3 | |
| Kotlin | 2.0.21 | with `org.jetbrains.kotlin.plugin.compose` |
| compileSdk / targetSdk | 34 / 34 | `androidx.core:core-ktx:1.15.0` requires compileSdk 35 → **core-ktx is pinned to 1.13.1**; bump both together later if needed (android-36.1 platform is installed but AGP 8.7.3 doesn't know API 36) |
| Compose BOM | 2024.12.01 | |
| activity-compose / lifecycle / datastore | 1.9.3 / 2.8.7 / 1.1.1 | |
| Markwon (core, ext-tables, ext-tasklist, ext-strikethrough) | 4.6.2 | Markdown rendering |
| `io.legere:pdfiumandroid` | 1.0.24 | PDF render/search/text only — **no annotation APIs** |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | PDF annotation/form **write-back** |
| junit / robolectric / androidx-test / espresso | 4.13.2 / 4.14.1 / 1.2.1 / 3.6.1 | |

All pins live in `gradle/libs.versions.toml`.

## 5. Key architectural decisions (and why)

1. **Two PDF libraries, separate jobs.** Pdfium (`io.legere`) is fast but has zero annotation APIs (verified by inspecting the AAR). PDFBox-Android writes annotations/forms but is too slow for interactive rendering. → **Pdfium renders/searches/extracts text; PDFBox-Android loads the file fresh, applies annotation/form changes, saves via SAF.** The two never share a live document object.
2. **MVVM + Compose, per-tab state.** `AppViewModel` holds `tabs: StateFlow<List<DocumentTab>>`; everything document-specific (search text/matches, pdfPage/scale, scroll offsets, dirty flags) lives inside the immutable `DocumentTab`, never at root. This mirrors the macOS `AppModel`/`DocumentTab` split and its hard-won lessons.
3. **State-boundary rules** (from macOS bugs; enforce in review):
   - Sync page/scale/search *into* wrapped views (Pdfium view, Markwon preview) on explicit events (request-ID pattern), never on every recomposition — prevents the "scrolls back to search hit" bug.
   - Guard every PDF callback against page index `-1` and stale document identity.
   - One writable instance per file: opening an already-open URI selects its tab.
4. **Single-window tab model** — no multi-window/multi-instance (cut in plan v2).
5. **SAF everywhere** — no direct file paths; persistable URI permissions taken on open; write via `ContentResolver.openOutputStream(uri, "wt")` with Save-Copy-As fallback.
6. **No `INTERNET` permission** — local-first.

## 6. Phase 0 spike verdict (detailed in `docs/pdf-annotation-spike.md`)

**PASSED — full annotation scope per plan is feasible.** 3/3 instrumented tests green on emulator (`app/src/androidTest/.../PdfAnnotationSpikeTest.kt`):

- Highlight (`PDAnnotationTextMarkup` + quad points + color + 55% opacity) → save → reload ✓
- FreeText → save → reload ✓ — **`PDAnnotationFreeText` is NOT shipped in pdfbox-android**; construct at COS level (`COSDictionary` `/Subtype /FreeText` wrapped in `PDAnnotationMarkup`). Same trick needed for Ink (P3).
- AcroForm text-field fill → save → reload ✓ — requires `acroForm.defaultResources` with a `Helv` font or appearance generation fails.
- Port also ships `PDAnnotationText` (sticky note), `PDAnnotationSquareCircle`, `PDAnnotationLine` → covers PDF-11/12 and most of PDF-16.
- **Test-assets gotcha:** read fixtures via `InstrumentationRegistry.getInstrumentation().context` (test APK), NOT `targetContext` (app APK) — cost one failing run.

## 7. Current code map

```text
app/src/main/java/com/timetochilltoo/fileviewer/
├── app/
│   ├── MainActivity.kt          # single activity; routes VIEW/SEND/SEND_MULTIPLE via IntentRouter
│   └── theme/Theme.kt           # FileViewerTheme: system light/dark M3
├── core/
│   ├── model/
│   │   ├── DocumentKind.kt      # MARKDOWN | PDF
│   │   ├── MarkdownMode.kt      # PREVIEW | SOURCE | SPLIT
│   │   ├── ViewerDocument.kt    # sealed: Markdown(uri?, text, savedText) | Pdf(uri, pageCount, handle)
│   │   ├── DocumentTab.kt       # immutable per-tab state; hasUnsavedChanges computed (md: text != savedText)
│   │   ├── TabManager.kt        # PURE tab-list logic (add/select/update/remove/findByUri) — unit-tested
│   │   ├── MarkdownFormatter.kt # Phase 3: toggle/wrap/format commands (UTF-16 safe), unit-tested
│   │   ├── MarkdownOutline.kt   # heading parser + EditorSelection/HeadingJump data
│   │   ├── PdfModel.kt          # PdfPageMetrics, PdfSearchResult, PdfOutlineItem
│   │   └── PdfScaleMode.kt      # FIT_WIDTH | FIT_PAGE | FREE
│   └── files/
│       ├── DocumentRepository.kt # SAF: load(uri)->ViewerDocument, displayName, kindFor, writeMarkdown
│       ├── PdfDocumentHandle.kt # interface: render/search/text/outline/thumbnail + page cache contract
│       ├── PdfHandle.kt         # PdfiumCore + ParcelFileDescriptor + PdfDocument; close() on tab close
│       ├── RecentsStore.kt      # DataStore JSON list, dedupe by uri, cap 20
│       ├── SessionStore.kt      # DataStore session + scroll + PDF state; SessionCodec pure/tested
│       ├── RecentDocument.kt
│       └── IntentRouter.kt      # Intent -> Ingress (OpenUris | SharedText | None), Robolectric-tested
└── feature/
    ├── shell/
    │   ├── AppViewModel.kt      # AndroidViewModel; ONE ShellUiState flow (tabs+selectedTabId)
    │   │                        # close-request, save/save-as, recents, markdownMode, session/PDF-state save/restore,
    │   │                        # scroll positions, persistState() (called from MainActivity.onPause)
    │   └── ShellScreen.kt       # TopAppBar+overflow menu, custom TabStrip, StatusStrip, EmptyState w/ recents,
    │                            # UnsavedCloseDialog, back-press chain, drawer (MD headings / PDF outline+thumbnails)
    ├── markdown/
    │   ├── MarkdownWorkspace.kt      # mode selector + SOURCE/PREVIEW/SPLIT layout (Split only ≥840dp); key(tab.id)
    │   ├── MarkdownSourceEditor.kt   # BasicTextField(TextFieldValue), monospace, no autocorrect
    │   ├── MarkdownPreview.kt        # Markwon (tables/tasklist/strikethrough) in ScrollView AndroidView; 150ms debounce
    │   ├── FormattingToolbar.kt      # Phase 3: Format dropdown + icon buttons
    │   └── MarkdownGuideScreen.kt    # Phase 3: static syntax guide
    └── pdf/
        ├── PdfWorkspace.kt           # continuous scroll LazyColumn, page nav, zoom +/-, fit width/page, reading mode
        ├── PdfPageView.kt            # single page bitmap + search-highlight canvas overlay
        └── PdfPrintAdapter.kt        # system PrintDocumentAdapter that streams the PDF bytes
app/src/androidTest/
├── assets/fixture_hello.pdf     # generated 1-page PDF
└── java/.../feature/pdf/PdfAnnotationSpikeTest.kt   # 3 passing spike tests (Phase 0)
app/src/test/
├── core/model/TabManagerTest.kt        # 9 tests: duplicate guard, selection fixup, dirty logic
├── core/model/MarkdownFormatterTest.kt # Phase 3 formatter toggles, tables, task lists, CJK
├── core/model/MarkdownOutlineTest.kt   # heading parsing
└── core/files/SessionCodecTest.kt      # session/scroll/PDF-state codecs (Robolectric)
```

- Manifest: single `MainActivity` (`singleTask`, configChanges handled), intent filters for PDF (`application/pdf`), Markdown (`text/markdown`, `text/plain`), extension-based `*/*` filters with `pathPattern` for `.md`/`.markdown`/`.pdf`, VIEW+SEND.
- URIs are stored as **String** in model classes (never `android.net.Uri`) so `core.model` stays pure-JVM-testable.
- Adaptive icon: indigo `#3949AB` + white document vector (placeholder quality).
- The fixture PDF generator script is **not** committed; regenerate via Python if needed (see spike doc / git history).

### 7.1 State-hoisting lesson (Phase 1 bug 1)

Two separate `StateFlow`s for `tabs` and `selectedTabId` caused state tearing: Compose recomposed between the two writes, and `ScrollableTabRow` crashed with `IndexOutOfBoundsException` (its subcomposed tabPositions lag one frame behind `selectedTabIndex`). Fixes applied:

1. **Single `ShellUiState(tabs, selectedTabId)` flow** — one atomic write per mutation. Keep this pattern: never expose two flows that must change together.
2. **Custom `TabStrip`** (horizontal-scroll Row of Surface chips) replaced `ScrollableTabRow` entirely — the M3 row can crash even with consistent state because of the subcomposition lag when tabs are added. Do not reintroduce `ScrollableTabRow` for dynamic tab lists.

### 7.2 `file://` URIs are dead on modern Android (Phase 1 bug 2)

Scoped storage (API 30+) blocks raw-path reads via `file://` even with correct permissions (`EACCES`). This is expected OS behavior, not an app bug:

- Real-world ingress is `content://` (SAF picker, file managers, share sheets) which carries a read grant. `DocumentRepository.takePersistablePermission` then makes it durable.
- `text/plain` and `*/*`-with-pathPattern filters exist for old file managers; files that still arrive as `file://` outside our sandbox will fail — acceptable, the app shows "Could not open document" (visible on the empty state too).
- **Emulator smoke-test procedure** (SAF grants can't be scripted via adb):
  ```bash
  adb shell chmod 777 /data/local/tmp
  adb push fixture.pdf /data/local/tmp/ && adb shell chmod 644 /data/local/tmp/fixture.pdf
  adb shell "run-as com.timetochilltoo.fileviewer cp /data/local/tmp/fixture.pdf \
    /data/data/com.timetochilltoo.fileviewer/files/test.pdf"   # no nested sh -c — quoting breaks it
  adb shell am start -a android.intent.action.VIEW \
    -d "file:///data/data/com.timetochilltoo.fileviewer/files/test.pdf" \
    -t application/pdf com.timetochilltoo.fileviewer
  adb exec-out screencap -p > shot.png
  ```
  Works because an app can always read its own internal files dir via raw path.

### 7.3 Phase 2 lessons

- Markwon plugin class is `TablePlugin` (not `TablesPlugin`), and its factory needs a `Context`: `TablePlugin.create(context)`.
- Session restore is a suspend `restoreSession()` in `AppViewModel.init`; `skipSessionRestore()` is set synchronously from `MainActivity` before the coroutine runs (external-intent suppression, mirrors macOS `suppressSessionRestore`).
- `persistState()` (session + scroll) runs from `MainActivity.onPause`; `am force-stop` does NOT fire it — smoke tests must background the app first (HOME key) then force-stop.
- Only **preview** scroll is tracked/restored; `BasicTextField` internal scroll isn't exposed. Editor scroll restore is a known limitation (consider a custom scroll connection if Patrick wants it).
- Untitled tabs are never written to the session (by design).
- Untitled-save bug (0-byte file from overflow menu): fixed by `PendingSaveAs(tabId, closeAfter)` covering both menu-Save and close-dialog Save paths through the one CreateDocument result callback.

### 7.4 Phase 4 lessons

- **Recycled-bitmap crash (PDF opened → instant quit).** `PdfPageView` called `bitmap?.recycle()` right before swapping in a newly rendered bitmap; the frame still being drawn referenced the old bitmap → `RuntimeException: Canvas: trying to use a recycled bitmap`. It fired on every open because scale starts at a placeholder (1.0) then snaps to real fit-width, forcing an immediate re-render. Fix: never eagerly recycle bitmaps that Compose may still be drawing — let GC reclaim them (page bitmaps are only held while the page is composed in the LazyColumn). Same fix applied to drawer thumbnails.
- **Release vs debug on the same device/emulator** have different signatures: `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Uninstall first (`adb uninstall com.timetochilltoo.fileviewer`) before switching builds. Also `run-as` doesn't work on the release APK (not debuggable).
- **Zoom UX:** pinch zoom is a custom `awaitEachGesture` handler that ignores single-finger gestures (so LazyColumn scroll still works) and commits `onScaleChange` only when the pinch ends (avoids per-frame ViewModel churn + bitmap re-renders). Pages wider than the viewport (scale > fit-width) are wrapped in a `horizontalScroll` Row so single-finger horizontal drag pans the page; vertical drag still scrolls pages. Pinch does NOT zoom around the centroid and double-tap does NOT center on the tapped point — acceptable for personal use, revisit if annoying.

## 8. Status & what's next

**Done:**
- **Phase 0** — skeleton, build green, spike passed, emulator created.
- **Phase 1** — document model, tabs UI, SAF open, share/VIEW ingress, unsaved-close dialog, save/save-as, recents, status strip, back-press close chain. Smoke-verified on emulator.
- **Save-flow fix** — 0-byte file when saving untitled doc from overflow menu (`PendingSaveAs`), plus Save As menu item (`c6086d4`).
- **Phase 2** — Markwon preview (tables/tasklist/strikethrough, 150ms debounce), TextFieldValue source editor (no autocorrect), Preview/Source/Split modes (Split ≥840dp), session restore with external-intent suppression, per-file preview-scroll restore. 7 SessionCodec tests added (21 unit tests total). Smoke-verified: source edit, preview render, force-stop→relaunch restores both tabs with selection.

**Phase 3** — `MarkdownFormatter` (bold/italic/underline-HTML/heading/bullet/numbered/quote/link/code/table/task-list), formatting toolbar (Format dropdown + icon buttons, underline labeled “HTML underline”), search with preview span highlight (yellow/orange), counter, prev/next, Navigate panel with Markdown heading list, and a Markdown Syntax Guide screen. 21 unit tests added (formatter toggles + outline). Smoke-verified: heading underline in preview removed via `MarkwonTheme.Builder.headingBreakHeight(0)`; toolbar, search, drawer, and guide all work on device.

**Phase 4** — PDF viewer core (complete): Pdfium behind `PdfDocumentHandle` (render/search/text/outline/thumbnail, 4-page LRU cache), Compose `LazyColumn` continuous-scroll `PdfWorkspace`, pinch-to-zoom + double-tap zoom (toggle fit-width ↔ 2×), page nav + go-to-page field, zoom +/- and fit-width/fit-page modes, text-reflow reading mode (PDF-4), PDF search with highlight overlay and counter (PDF-7), drawer with outline + lazy 96dp page thumbnails (PDF-5/6), print via `PdfPrintAdapter` (PDF-9), and per-file page/zoom persistence (PDF state merged on tab close and `persistState()`). Fixed a print crash caused by using the Application context instead of the Activity for `PrintManager`.

**Phase 7** — release polish: dark/light system theme confirmed (M3 default schemes), release signing configured (`signingConfigs.release` in `app/build.gradle.kts`, reads gitignored `keystore.properties` + `fileviewer-release.keystore` in repo root), signed release APK verified with `apksigner` (33M, cert `CN=FileViewer`), `README.md` with build/sideload steps, version bumped to `0.2.0` (versionCode 2). **Keystore + passwords are NOT committed; back them up — losing them blocks future in-place updates.** Remaining manual item: performance pass on a 500-page PDF / 1MB Markdown on real hardware.

**Next:** optional Phases 5–6 (PDF annotations) — spike green-lit; core app ships without them.

Then Phase 7 (polish). Phases 5–6 (annotations) remain optional — spike green-lit.

## 9. Working agreements

- Commit + push to `main` when a meaningful unit is done (Patrick prefers this).
- Keep `docs/android-requirements-and-plan.md` and this handoff updated when scope/status changes.
- Verification before committing: `assembleDebug` + relevant tests green; manual run on `fileviewer_test` emulator for UI changes.
- Explain changes to Patrick in practical, non-technical terms unless debugging detail is needed.
