# Card Forge Backlog

Current follow-up work for the 0.14.x line.

## UX / browser

## Regression verification

- Verify rendered-card thumbnails in list and grid modes remain current after edits.
- Verify the paged in-app contact sheet matches the live card preview.
- Verify PNG, SVG, and PDF output remain visually consistent with the canonical renderer, including bleed, opacity, and transparent pixels outside the card silhouette/corners.
- Verify file add/remove/move/rename identity behavior under the filesystem watcher.
- Verify list/grid keyboard navigation and selection/scroll behavior.
- Verify initial window sizing and macOS Dock/window icons in the packaged app.
- Verify auto-save on card switch, collection switch, app close, and Cmd/Ctrl+S.

## Refactoring / maintenance

- Continue reducing responsibilities in `Main.kt`.
- Browser list/grid/thumbnail orchestration is now split across selection, tile, and preview-cache coordinators; keep further browser changes behavior-preserving.
- Remaining export execution orchestration is extracted; keep further preview/export changes behavior-preserving.
- Continue using `CollectionCardStore`, `CardUndoManager`, `AppPlatform`, and collection action classes instead of duplicate state in `MainApp`.
- Keep startup profiling available as opt-in diagnostics without adding normal-launch overhead.

## Completed recently

- PNG/SVG/PDF export execution and PDF FX-thread bridging extracted from `Main.kt`.
- Browser original/card preview request de-duplication and cache orchestration extracted from `Main.kt`.
- Browser list/grid cell and tile construction extracted from `Main.kt`.
- Browser list/grid selection, focus, and scroll coordination extracted from `Main.kt`.
- Collection/Layout template UX consolidated: duplicate bulk-template action removed, collection default vs per-card template semantics clarified, and card count retained.
- Regression test foundation for undo, collector numbering, PDF planning, and asset identity reconciliation.
- Permanent pull-request/main verification workflow.
- Canonical export snapshots now explicitly preserve transparent pixels outside the card silhouette.
- Export target/options UI extracted from `Main.kt`.
- Contact-sheet window/paging UI extracted from `Main.kt`.
- Source-image inspector window lifecycle extracted from `Main.kt`.
- Source-image inspector with preview action, image context-menu action, and Cmd/Ctrl+I shortcut; native-resolution view dismisses with Esc.
- Startup performance regression caused by image decoding during browser sorting.
- Apply selected template explicitly to every card in a collection; verified working in-app.
- Collection card/image count.
- Collection-wide set-name application including previously uninitialized images.
- Set-aware collector-number totals and duplicate-number cleanup.
- Recent-catalog chooser.
- Collection bleed/foreground opacity controls.
- File identity reconciliation for removal/move/rename.
