# PDF Annotation Spike — Results

Date: 2026-07-21
Phase: 0 (gate for all annotation scope, PDF-10 in the plan)
Test: `app/src/androidTest/.../PdfAnnotationSpikeTest.kt` on emulator `fileviewer_test` (API 34, arm64)

## Verdict: PASSED — annotation scope per plan is feasible

**3/3 instrumented tests green.**

## Findings

### Rendering / search / text: `io.legere:pdfiumandroid:1.0.24`

- Renders pages, extracts text, provides `saveAsCopy`.
- **No annotation APIs at all** (verified by inspecting the AAR — no `Annot*` classes, no annotation methods on `PdfPage`).
- Conclusion: use for **rendering, search, and text extraction only**.

### Annotation write-back: `com.tom-roush:pdfbox-android:2.0.27.0`

| Capability | Result | Notes |
|---|---|---|
| Highlight (TextMarkup) write → save → reload | **PASS** | `PDAnnotationTextMarkup` with quad points, color, 55% opacity |
| FreeText write → save → reload | **PASS** | `PDAnnotationFreeText` is **not shipped** in this port; construct at COS level (`COSDictionary` with `/Subtype /FreeText`) wrapped in `PDAnnotationMarkup` — works fine |
| AcroForm text-field fill → save → reload | **PASS** | Requires `acroForm.defaultResources` with a `Helv` font entry for appearance generation |

Additional notes:

- Also present in the port: `PDAnnotationText` (sticky note), `PDAnnotationSquareCircle` (rectangle/oval), `PDAnnotationLine` (line/arrow) — covers PDF-11/12 and most of PDF-16.
- **Not present:** `PDAnnotationInk` — freehand ink would need COS-level construction like FreeText. Acceptable for P3.
- Architecture decision: **Pdfium for render/search, PDFBox-Android for annotation/form write-back.** The two never share a live document; write-back loads the file fresh with PDFBox, applies changes, saves via SAF.

## Fixture gotcha worth remembering

- Instrumented-test assets must be read via `InstrumentationRegistry.getInstrumentation().context` (the test APK), not `targetContext` (the app APK).

## Emulator

- `fileviewer_test` AVD (pixel_6, API 34, google_apis, arm64) now exists for run-testing. Boot: `emulator -avd fileviewer_test -no-window -no-audio -gpu swiftshader_indirect -no-snapshot-save`.
