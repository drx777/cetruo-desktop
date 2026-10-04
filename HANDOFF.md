# Card Forge Handoff

Target branch: `main`

Current line: `0.14.x`

## Current status

Card Forge 0.14.4 is integrated on `main`. The startup regression is resolved, recent collection/presentation work is merged, the source-image inspector and preview/export UI extractions are merged, and canonical snapshots explicitly preserve transparency outside the card silhouette.

The current focus is regression hardening before larger browser refactoring or new visual/editor features.

## Recently completed

- SVG/PDF export now shares a vector renderer: generated card geometry/text/template/overlay stay vector; only source artwork remains raster.
- Explicit save now bypasses the 3-second thumbnail debounce and refreshes only the active rendered-card tile immediately.
- Borderless one-card-per-page PDF export with card-sized pages and no surrounding page margin.
- Startup performance fix: browser sorting no longer decodes source JPEGs on the JavaFX application thread.
- Recent-catalog chooser and collection card/image count.
- Collection-level bleed opacity and foreground opacity plus per-card bleed opacity.
- Collection-wide set-name, collector-total normalization, and explicit template application.
- File identity preservation across removal/move/rename with tombstone/hash reconciliation.
- Thumbnail cache invalidation and off-thread preview/color processing improvements.
- Source-image inspector:
  - preview-header action,
  - image context-menu action,
  - Cmd/Ctrl+I shortcut,
  - native-resolution pannable view,
  - Esc dismissal.
- Extracted UI/window concerns:
  - `SourceImageInspector`
  - `ContactSheetWindow`
  - `ExportUi`
- Canonical rendered-card snapshots preserve transparent pixels outside rounded/cut-out card corners.

## Current hardening work

Initial window sizing math and packaged icon/CSS resource presence are now covered deterministically; actual macOS Dock/window icon appearance still requires packaged-app smoke testing.

PDF page planning now has expanded deterministic coverage for clamping, orientation choice, mixed-size grouping, and one-to-one card preservation; visual renderer parity still requires desktop smoke testing.

Collection-wide set-name application is now covered for initialization, metadata preservation, unique collector numbering, affected-set reporting, and idempotence.

Autosave transition gating is now centralized in `AutosaveGuard` and covered for success/failure semantics; desktop verification of the actual UI entry points remains on the smoke-test checklist.

Edited-card thumbnail refreshes are now targeted to the active card after 3 seconds of inactivity; list/grid-wide refreshes are not used for editor changes.

Browser preview cache validity rules are now covered: original previews depend on source size/mtime, while rendered-card previews also depend on the render signature.

Browser selection index/row mapping is now covered independently of JavaFX virtualization; viewport/cell scrolling still requires desktop smoke testing.

Watcher scan-cycle reconciliation is now isolated in `FileIdentityReconciler` and covered for hash-based rename/move recovery versus unrelated remove/add replacements.

Regression coverage now also protects collection-wide template application semantics: every card gets an explicit template, uninitialized cards are initialized, unrelated metadata is preserved, and the collection default is updated.

A regression-test foundation is being added for deterministic non-UI behavior:

- undo/redo snapshot semantics,
- collector-number normalization,
- PDF contact-sheet planning,
- collection database identity reconciliation,
- permanent CI verification on pull requests and `main`.

UI/rendering behavior still requires desktop smoke testing.

## Manual regression checklist

- Window close is blocked if the current card save is cancelled or fails; non-window shutdown has a final best-effort save fallback.
- Rendered-card thumbnails remain current after edits in list and grid modes.
- In-app paged contact sheet matches the live card preview.
- PNG, SVG, and PDF match the canonical renderer:
  - bleed,
  - foreground opacity,
  - overlays,
  - transparent pixels outside the card silhouette/corners.
- List/grid keyboard navigation and selection/scroll behavior are consistent.
- Packaged-app initial sizing and macOS Dock/window icons are correct.
- Auto-save works on card switch, collection switch, application close, and Cmd/Ctrl+S.

## Next structural work

After the hardening pass:

1. Browser orchestration is now split across `BrowserSelectionCoordinator`, `BrowserTileFactory`, and `BrowserPreviewCoordinator`. Prefer stabilizing this boundary before any deeper browser rewrite; the next structural target is remaining preview/export execution orchestration.
2. Export execution now lives in `ExportCoordinator`; the next structural work should focus on remaining preview orchestration only where it meaningfully reduces `MainApp` coupling.
3. Continue routing persistence/undo/collection behavior through the existing helper classes rather than reintroducing state in `MainApp`.

## Important semantics to preserve

### Collection template bulk action

**Apply selected template to all cards** must:

- use the currently selected template,
- persist that template explicitly on every card,
- initialize previously uninitialized cards as needed,
- store the selected template as the collection default.

Do not replace this with merely changing the collection default or clearing per-card overrides.

### Asset identity

A removed/moved/renamed asset keeps its catalog identity only when a newly discovered file matches the detached asset by content hash. A different file that reuses the old filename must get a new identity and must not inherit card metadata.

### Rendering

The canonical JavaFX card tree remains the shared visual source for preview-derived PNG/SVG/PDF/contact-sheet output. Pixels outside the actual card silhouette must stay transparent.

## Startup diagnostics

Normal launches are quiet.

Enable startup profiling with either:

```text
-Dcardforge.profileStartup=true
```

or:

```text
CARDFORGE_PROFILE_STARTUP=1
```

Profiler output is written to stderr and includes timed phases plus JavaFX event-thread stall traces.

Do not reopen the startup-performance investigation unless a regression is observed.
