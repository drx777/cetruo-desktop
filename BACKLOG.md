# Card Forge Backlog

Current follow-up work for the 0.14.x line.

## UX / browser

- Add a quick way to inspect the selected source image at full size:
  - hoverable icon/action near the preview/browser, and/or
  - keyboard shortcut while browsing/editing.
  - It should show the original image without the card frame/template and be easy to dismiss.
- Revisit Collection/Layout UI after functionality is stable:
  - remove the duplicate “Apply selected template to all cards” control,
  - clarify collection default vs per-card override actions,
  - keep collection card count visible without adding clutter.
- Add a lightweight full-image inspector for the selected source image:
  - hoverable icon/action near the preview/browser and/or a keyboard shortcut,
  - show the original source image without the card frame/template,
  - easy to dismiss and usable without disturbing editor state.

## Regression verification

- Verify rendered-card thumbnails in list and grid modes remain current after edits.
- Verify the paged in-app contact sheet matches the live card preview.
- Verify PNG, SVG, and PDF output remain visually consistent with the canonical renderer, including bleed and opacity.
- Verify file add/remove/move/rename identity behavior under the filesystem watcher.
- Verify list/grid keyboard navigation and selection/scroll behavior.
- Verify initial window sizing and macOS Dock/window icons in the packaged app.
- Verify auto-save on card switch, collection switch, app close, and Cmd/Ctrl+S.

## Refactoring / maintenance

- Continue reducing responsibilities in `Main.kt`.
- Extract browser/list-grid/thumbnail orchestration.
- Extract preview/export/contact-sheet orchestration where practical.
- Continue using `CollectionCardStore`, `CardUndoManager`, `AppPlatform`, and collection action classes instead of duplicate state in `MainApp`.
- Keep startup profiling available as opt-in diagnostics without adding normal-launch overhead.

## Completed recently

- Startup performance regression caused by image decoding during browser sorting.
- Apply selected template explicitly to every card in a collection; verified working in-app.
- Collection card/image count.
- Collection-wide set-name application including previously uninitialized images.
- Set-aware collector-number totals and duplicate-number cleanup.
- Recent-catalog chooser.
- Collection bleed/foreground opacity controls.
- File identity reconciliation for removal/move/rename.
