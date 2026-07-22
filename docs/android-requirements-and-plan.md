# FileViewer Android — Requirements & Development Plan

Last updated: 2026-07-22 (v3 — Phase 3 done)
Reference: macOS FileViewer `HANDOFF.md` (baseline `834c986`)
Scope: Android port of the macOS FileViewer for Markdown + PDF. **AI assistant is explicitly out of scope.** Personal-use app (not Play Store); distributed by sideloaded APK.

---

## 1. Project purpose

FileViewer Android is a local-first native Android document viewer/editor, mirroring the macOS app:

- Markdown notes (`.md`, `.markdown`) — view, edit, search, format
- PDFs (`.pdf`) — view, navigate, zoom, search, thumbnails, outline, text reflow
- Multiple open documents via tabs
- Unsaved-changes protection and session restore
- Print / export
- PDF annotations are a **nice-to-have (P2/P3)**, gated on an early technical spike

Everything is on-device. No accounts, no cloud sync, no AI features.

## 2. Platform & tech stack

| Decision | Choice | Rationale |
|---|---|---|
| Language | Kotlin | Standard for modern Android |
| UI | Jetpack Compose + Material 3 | Closest analogue to SwiftUI; declarative per-tab state |
| Min SDK | 26 (Android 8.0) | Covers ~95%+ devices; keeps PDF/font APIs simple |
| Target SDK | 35 | Current |
| Architecture | MVVM: `ViewModel` + `StateFlow`, per-tab state objects | Maps directly onto macOS `AppModel`/`DocumentTab` split |
| PDF rendering | Pdfium (community fork of `barteksc/PdfiumAndroid`) | Free, native-speed, text extraction, links; annotation write support verified by Phase 0 spike |
| Markdown rendering | Markwon + `ext-tables` + `ext-tasklist` + `ext-strikethrough` (via `AndroidView` in Compose) | Mature GFM renderer; macOS preview limitations (tables/task lists) solved for free |
| Markdown editing | Compose `BasicTextField` (monospaced, plain text) + formatting toolbar | Replaces the AppKit `NSTextView` bridge; selection APIs accessible in Compose |
| File access | Storage Access Framework (`OpenMultipleDocuments`) + persistable URI permissions + intent filters for Open With / Share / Send | Android equivalent of `NSOpenPanel` / Finder events; share-sheet ingress is the primary Android document path |
| Persistence | DataStore (preferences) | Equivalent of `UserDefaults` session/PDF/Markdown state |
| Printing | `PrintManager` + `PrintDocumentAdapter` | PDF print direct; Markdown printed as laid-out text |
| Build | Gradle (Kotlin DSL), version catalog | Standard |

### Key architecture mapping (macOS → Android)

```text
macOS                          Android
─────────────────────────────────────────────────────────
AppModel (@MainActor)     →    AppViewModel (StateFlow)
DocumentTab               →    DocumentTab (immutable data class, per-tab state)
FileViewerWindowRegistry  →    Not needed: single-window tab model only
NSTextView wrapper        →    BasicTextField + selection-based formatter
PDFView (PDFKit)          →    Pdfium-based PdfViewerView wrapped in AndroidView
UserDefaults session      →    DataStore session snapshot
NSOpenPanel               →    SAF OpenMultipleDocuments + share/view intents
NotificationCenter        →    SharedFlow events / direct ViewModel calls
```

State-boundary rules carried over from the macOS lessons learned:

1. **Per-tab vs global state** — everything document-specific (search text, match index, PDF page/scale, scroll offsets, dirty flags, undo stacks) lives inside `DocumentTab`, never in the ViewModel root.
2. **Compose state vs wrapped View state** — PDF view and Markwon preview are wrapped views; sync page/scale/search *into* them on explicit events, never on every recomposition (avoids the "scrolls back to search hit" bug).
3. **Stale document callbacks** — guard all PDF callbacks against `-1` page indexes and stale document references; verify callback document identity matches the current tab before mutating state.
4. **One writable instance per file** — opening an already-open URI selects its tab instead of duplicating it (data-loss protection).

## 3. Functional requirements

Priority: **P0** = MVP, **P1** = core, **P2** = nice-to-have, **P3** = optional/later.

### 3.1 Document management

| ID | Req | Pri |
|---|---|---|
| DM-1 | Open `.md`, `.markdown`, `.pdf` via in-app file picker (SAF, multi-select) | P0 |
| DM-2 | Register as handler for those types (intent filters) so "Open with" from any file manager works | P0 |
| DM-3 | Receive documents via Android share sheet / `ACTION_SEND` / `ACTION_VIEW` (email, browser, messaging) — the primary real-world ingress on Android | P1 |
| DM-4 | Each opened file becomes a tab; opening an already-open URI selects the existing tab | P0 |
| DM-5 | Close tab with unsaved-changes prompt: Save / Don't Save / Cancel | P0 |
| DM-6 | New unsaved "Untitled" Markdown document; Save routes to Save As (SAF `CreateDocument`) | P0 |
| DM-7 | Persistable URI permissions taken on open so files survive reboot | P0 |
| DM-8 | Recent documents list (URI, display name, last-opened); recents are shown directly on the empty state when no tab is open, and also in the drawer | P1 |
| DM-9 | Session restore: reopen file-backed tabs + selected tab on cold start; skip untitled unsaved docs; skip missing files silently; suppress restore when launched from an external intent | P1 |
| DM-10 | Per-file PDF page/zoom and Markdown scroll positions restored even after tab was closed (separate store from session) | P1 |

### 3.2 Markdown

| ID | Req | Pri |
|---|---|---|
| MD-1 | Two modes on phones: Preview / Source. Split (side-by-side) only on tablets/foldables with `maxWidth ≥ 840dp`; hidden otherwise | P0 |
| MD-2 | Source editor: monospaced, plain text, undo/redo, no autocorrect/smart quotes | P0 |
| MD-3 | Live preview re-render on text change (debounced ~150ms) | P0 |
| MD-4 | GFM preview: headings 1–6, bold/italic/strikethrough, links, inline code, fenced code, bullet/numbered lists, quotes, tables, task lists, horizontal rule | P0 |
| MD-5 | Formatting toolbar with visible "Format" dropdown + icon buttons: Bold, Italic, Heading, Bullet, Numbered list, Quote, Link, Code, Insert Table, Task List. Underline included but labeled **HTML underline** in UI so the non-standard `<u>` output is not a surprise when the file is opened elsewhere | P1 |
| MD-6 | Toggle semantics identical to macOS: wrap selection; if already wrapped, unwrap (expand to surrounding markers); no selection → insert placeholder (e.g. `**bold text**`) | P1 |
| MD-7 | Whole-line commands (heading/list/quote) operate on all lines intersecting the selection | P1 |
| MD-8 | Insert Table: template, or convert comma-separated selected lines into a table; Task List: template or convert selected lines to `- [ ]` items | P1 |
| MD-9 | Search: highlights matches in preview (yellow; current match orange), shows "n of N", prev/next, Enter = next | P0 |
| MD-10 | Navigate panel: heading list of the current document | P1 |
| MD-11 | Clicking a Navigate heading scrolls preview/source to it | P2 |
| MD-12 | Markdown Syntax Guide screen (same content list as macOS help window) | P1 |
| MD-13 | Save / Save As via SAF; dirty dot on tab; UTF-8 read/write | P0 |
| MD-14 | Print Markdown (laid-out source text via `PrintDocumentAdapter`) | P3 |

### 3.3 PDF

| ID | Req | Pri |
|---|---|---|
| PDF-1 | Render via Pdfium, single-page continuous vertical scroll, auto-scale on open | P0 |
| PDF-2 | Pinch zoom + double-tap zoom; fit-width / fit-page / zoom-in / zoom-out controls | P0 |
| PDF-3 | Page navigation: first/prev/next/last + go-to-page field | P0 |
| PDF-4 | Text reflow / reading mode: extract current page (or whole-document) text and present it as comfortably readable, resizable text — avoids zoom-pan misery on phone screens | P1 |
| PDF-5 | Thumbnail page list inside the Navigate panel, tap to jump | P1 |
| PDF-6 | Outline/table-of-contents in the Navigate panel when the PDF provides one; tap to navigate; "No PDF Outline" placeholder otherwise | P1 |
| PDF-7 | Text search: highlight all matches, jump on search-text change and on explicit prev/next/Enter only (never on recomposition/redraw), show "PDF: n of N" | P0 |
| PDF-8 | Guard every page callback: ignore index `-1`, out-of-range, or stale document | P0 |
| PDF-9 | Print PDF | P1 |
| PDF-10 | *Annotation spike gate:* Phase 0 must prove Pdfium can write a highlight into a real PDF and read it back. Results decide the scope of PDF-11+ | P0 (spike) |
| PDF-11 | Selection-based markup: Highlight, Underline, Strikeout, with color picker (highlight 55% alpha; underline/strikeout 85%); erase markup from selection | P2 |
| PDF-12 | Sticky note + free-text box annotations, placed near selection or page center, clamped inside page bounds | P2 |
| PDF-13 | Dirty tracking after annotation/form edits; Save writes back via SAF; Save Copy As (`CreateDocument`) switches tab to the copy; close warning for unsaved changes | P2 |
| PDF-14 | Fillable form support: widget fields editable; edits mark document dirty (debounced interaction-driven detection, never a polling timer) | P2 |
| PDF-15 | Undo/redo for annotations: **snapshot-only** (document-data snapshot per action, cap ~10 per tab). No object-level/snapshot hybrid — the macOS hybrid timeline (follow-up fixes 6–9) is deliberately not ported | P3 |
| PDF-16 | Shape annotations: rectangle, oval, line, arrow, freehand ink with live preview; move/resize/edit/delete/recolor modes with handles | P3 |
| PDF-17 | Notes panel (shown only when the open PDF has annotations): list with filter, tap to jump; export annotation summary as Markdown via SAF | P3 |

### 3.4 App shell / UX

| ID | Req | Pri |
|---|---|---|
| UX-1 | Single `MainActivity`, Compose navigation; top bar: overflow menu (New, Open, Save, Save As, Print), mode control, search field with match counter and prev/next | P0 |
| UX-2 | Scrollable tab row with dirty dot and close button per tab | P0 |
| UX-3 | Empty state (no open tabs) shows recent documents + Open/New actions directly, not hidden in a drawer | P1 |
| UX-4 | Status strip: file name, unsaved indicator, status message, search/PDF page info | P1 |
| UX-5 | Navigation drawer with at most two panels: **Navigate** (merges Contents + Pages; shows Markdown headings or PDF outline/thumbnails per doc type) and **Notes** (only when the open PDF has annotations). Recent lives on the empty state + a drawer section | P1 |
| UX-6 | Hardware/gesture back: closes drawer → clears search → asks before closing dirty tab | P0 |
| UX-7 | Dark/light theme follow system | P1 |
| UX-8 | Minimum usable width respected on small phones; tablet two-pane layout (drawer inline) | P3 |

## 4. Non-functional requirements

- **Local-first**: no network permission. Manifest omits `INTERNET`.
- **Personal use**: pragmatic quality bar — no Play listing assets, no formal accessibility audit, no localization pass; keep 48dp touch targets and content descriptions on icon-only buttons as cheap hygiene.
- **Performance**: PDF page render ≤ 150ms/page on a mid-range device; Markdown re-render debounced; large PDFs (>500 pages) render pages lazily.
- **Memory**: one decoded-page bitmap pool per tab; released on tab close; snapshot undo cap respected.
- **Data safety**: never write a file without explicit Save; duplicate-open protection (DM-4); on SAF write failure, fall back to Save Copy As rather than erroring out.

## 5. Development plan

Core app (Phases 0–4, 7): ~7–8 weeks at one developer. Annotation phases (5–6) are optional add-ons gated on the Phase 0 spike.

### Phase 0 — Project skeleton + PDF annotation-write spike (1 wk)

1. `gradle init` Android project: Kotlin DSL, Compose BOM, Material 3, minSdk 26.
2. Packages: `app`, `core.model`, `core.files`, `feature.markdown`, `feature.pdf`, `feature.shell`.
3. CI (optional for personal use): assembleDebug + lint + unit tests.
4. App icon, theme, manifest with intent filters for `.md`/`.markdown`/`.pdf` (view/send, mime + scheme patterns).
5. **Spike (2 days, gates all annotation scope):** with the chosen Pdfium fork — open a fixture PDF, programmatically add a highlight annotation, write to a new file, reopen, verify the highlight renders. Also probe: free-text annotation write, form-field value write. Record results in `docs/pdf-annotation-spike.md`; adjust PDF-11+ scope (full / reduced / dropped) before Phase 5 is ever scheduled.

**Deliverable:** empty app launches, accepts "Open with" and share-sheet intents, spike verdict documented.

### Phase 1 — Document model + tabs + file open (1 wk)

1. `DocumentKind`, `DocumentTab` (id, document, searchText, searchMatchIndex/Count, pdfPage/Count/Scale, markdownScroll, dirty flags) as immutable data classes.
2. `AppViewModel`: `tabs: StateFlow<List<DocumentTab>>`, `selectedTabId`, open/select/close/new-untitled; duplicate-URI guard.
3. SAF open (multi-select) + persistable permission; share-sheet/`ACTION_SEND`/`ACTION_VIEW` ingress (DM-3); content-resolver read for Markdown (UTF-8) and PDF (`ParcelFileDescriptor` → Pdfium).
4. Compose shell: top bar, `ScrollableTabRow` with dirty dot + close, workspace switch by `DocumentKind`, empty state with recents (UX-3).
5. Back-press handling per UX-6.
6. Unsaved-close dialog (Save / Don't Save / Cancel) wired for Markdown dirty state.

**Tests:** duplicate-open guard, dirty tracking, close-prompt logic, share-intent routing (Robolectric).

**Deliverable:** open/share multiple files → tabs; close protection works.

### Phase 2 — Markdown MVP (1.5 wks)

1. Markwon setup with GFM tables/task-list/strikethrough plugins in an `AndroidView`.
2. Source editor: `BasicTextField`, monospaced, decorations off; expose `TextFieldValue` selection to ViewModel.
3. Mode segmented control per MD-1 (Preview / Source; Split only ≥ 840dp).
4. Debounced preview refresh.
5. Save / Save As / New Untitled via SAF; dirty dot; status message.
6. Session snapshot to DataStore + restore on launch with external-intent suppression (DM-9).

**Tests:** save/load round-trip, session codec, restore-skips-missing-file.

**Deliverable:** daily-usable Markdown notes app.

### Phase 3 — Markdown formatting + search + guide (1.5 wks)

1. `MarkdownFormatter` pure-Kotlin port of the macOS toggle logic: bold/italic/underline(HTML)/heading/bullet/numbered/quote/link/code/table/task-list; UTF-16 index math (the `NSString`-style lesson).
2. Toolbar: Format dropdown + icon buttons; underline labeled "HTML underline"; placeholder insertion with no selection.
3. Search: literal find over source, map to preview spans; highlight all (yellow) + current (orange); counter; prev/next; Enter = next via `IME_ACTION_SEARCH`.
4. Navigate panel: heading list for Markdown (MD-10).
5. Markdown Syntax Guide screen (static Compose content, same topics as macOS).

**Tests:** formatter toggle table (wrap/unwrap/expand, multi-line, CJK text index math), table/task-list converters, search count.

**Deliverable:** source-editor formatting parity with macOS.

### Phase 4 — PDF viewer core (2 wks)

1. Pdfium integration behind a `PdfDocumentHandle` interface (page count, render at scale, page size, text extraction).
2. `PdfViewerView`: continuous vertical scroll, fling, pinch/double-tap zoom, page-change callback; wrapped in `AndroidView`.
3. Guard rails: page callbacks clamped, `-1` ignored, document-identity check before state writes (PDF-8).
4. Toolbar: page nav, go-to-page dialog, zoom in/out, fit width/page.
5. Text reflow / reading mode (PDF-4): extract page text via Pdfium, render as selectable, resizable Compose text; per-page or continuous mode.
6. Per-file page/zoom persistence (saved on natural save points — tab switch/close/pause, not per-scroll-pixel).
7. Navigate panel for PDF: thumbnails (lazy 96dp renders) + outline (PDF-5/6).
8. Search: Pdfium `findAll`, highlight rects overlay; navigation strictly event-driven (request-ID pattern from the macOS fix) so redraws never re-jump; counter "PDF: n of N" (PDF-7).
9. Print PDF (PDF-9).

**Tests:** page-guard unit tests, search count on fixture PDFs, reflow extraction, state persistence.

**Deliverable:** solid phone-friendly PDF reader. **Core app feature-complete here.**

### Phase 5 — (P2, gated) PDF annotations: markup + notes + save (~2 wks)

*Schedule only if the Phase 0 spike passed.*

1. Text selection → line-bound rects.
2. Highlight/Underline/Strikeout with color picker + alpha rules; erase markup from selection (whole-annotation removal, documented limitation).
3. Sticky note + text box via dialog; clamped inside page bounds.
4. Dirty flag → orange dot, close warning, Save via SAF, Save Copy As (PDF-13).
5. Fillable form editing + debounced dirty detection (PDF-14).

**Deliverable:** annotatable PDF viewer with safe save.

### Phase 6 — (P3, optional) Shapes, undo/redo, notes panel (~2 wks)

1. Rectangle/oval/line/arrow/ink with live drag preview; stroke width (1/2/4pt); move/resize/edit/delete/recolor modes + handles (PDF-16).
2. Snapshot-only undo/redo, cap 10 per tab (PDF-15).
3. Notes panel with filters + tap-to-jump; export annotation summary Markdown (PDF-17).

### Phase 7 — Polish & personal release (0.5–1 wk)

1. Dark theme pass; performance profiling on a 500-page PDF and 1MB Markdown file.
2. Heading-jump (MD-11) if time allows; tablet two-pane only if Patrick owns a tablet (UX-8, P3).
3. Signed release APK for sideloading; short `README` with install steps.

## 6. Testing strategy

- **Unit (JUnit + Robolectric):** document model, formatter toggles, session codec, page guards, search counters.
- **Fixtures:** `app/src/test/resources/` — small/large PDF, outlined PDF, fillable-form PDF, annotated PDF (for the spike), GFM Markdown sample, CJK Markdown.
- **Instrumented (Compose UI test):** open→tab→edit→dirty→save flow; close prompt; share-intent open; PDF search navigation; (if built) annotation save round-trip.
- **Manual regression checklist** mirrors HANDOFF §6.1 formatting steps plus a PDF walkthrough per release.

## 7. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Pdfium annotation *write* support partial in free forks | Phase 0 spike gates all annotation scope; fallback options: reduced scope (view-only annotations), commercial PSPDFKit/Nutrient, or drop annotations entirely |
| SAF write-back depends on provider granting persistable write permission; some providers reject in-place writes | Write via `ContentResolver.openOutputStream(uri, "wt")`; on failure fall back to Save Copy As; never promise silent background writes |
| Compose `BasicTextField` selection/IME edge cases | Keep editor surface minimal (plain text); instrumented tests; escape hatch to an `EditText`-based editor behind the same interface |
| Large-PDF memory | Lazy page rendering, bitmap pool, snapshot undo cap |
| Text reflow quality on multi-column/academic PDFs | Extraction order can jumble; ship as a reading-aid toggle, not the default view; note limitation |

## 8. Explicitly out of scope

- AI assistant, providers, conversation panel (per request)
- Multi-window / multi-instance support — tabs only
- Preview-selection formatting (macOS MD-9 equivalent) — fragile first-match mapping, not ported
- Phone Split mode — tablet/foldable only
- Cloud sync, accounts, collaboration
- EPUB/DOCX and other formats
- OCR for scanned PDFs (selection-based markup and reflow need embedded text)
- Play Store release, formal accessibility audit, localization (personal-use app)
- Object-level/snapshot hybrid annotation undo — snapshot-only if built at all
- Support below API 26
