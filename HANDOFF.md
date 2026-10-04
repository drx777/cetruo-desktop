# Card Forge 0.14.4 Handoff

Branch: `feature/0.14.4-integration`

Target: `main`

## Current status

The 0.14.4 integration work is functionally stable enough for review/merge. The app builds and runs locally, startup performance is fast again, and the collection-wide selected-template operation was verified working in-app.

The feature branch is ahead of `main` and contains the integrated 0.14.4 work, refactors, fixes, and documentation updates.

## Important completed work

- Fixed startup stalls caused by decoding source JPEGs while sorting the browser on the JavaFX application thread.
- Added opt-in startup profiling and JavaFX stall diagnostics.
- Added recent-catalog startup chooser with up to five recent collection roots.
- Added collection card/image count.
- Added collection-level bleed opacity and foreground-surface opacity.
- Added per-card artwork bleed opacity.
- Added explicit collection-wide set-name operation, including previously uninitialized images.
- Added set-aware collector-number totals and duplicate-number cleanup.
- Added collection-wide selected-template operation:
  - uses the template currently selected in **Card template**,
  - writes that template explicitly to every card,
  - initializes previously uninitialized cards as needed,
  - also stores the selected template as the collection default,
  - verifies persistence after the bulk operation.
- Preserved card identity across file removal/move/rename using detached/tombstone paths and hash-based reconciliation.
- Prevented unrelated replacement files from inheriting metadata from a removed asset with the same filename.
- Improved card-preview cache invalidation so thumbnails follow current editor state, collection presentation, and collection default template.
- Reused off-thread image decoding for preview/color analysis.
- Subsampled dominant-color analysis for large images.
- Wired extracted helpers into `MainApp`:
  - `AppPlatform`
  - `CardUndoManager`
  - `CollectionCardStore`
  - `CollectionEditorActions`
  - `CollectionSettingsPane`
  - `CollectionBulkActions`
  - `CollectionVisualSettings`
- Removed temporary 0.14.4 integration scripts/workflow.
- Build version is `0.14.4`.

## Known UI issue

There are currently two **Apply selected template to all cards** buttons: one in Collection settings and one beside the Layout/template controls. Both call the same verified bulk implementation. Keep functionality as-is for now; consolidate/rework the UI later.

## Backlog

See `BACKLOG.md` for the current authoritative follow-up list.

Highest-value remaining items:

- Add a quick full-source-image inspector via hoverable icon/action and/or keyboard shortcut.
- Verify rendered-card thumbnails in list/grid after edits.
- Verify paged in-app contact sheet against the live preview.
- Verify PNG/SVG/PDF output consistency, especially bleed and opacity behavior.
- Verify watcher behavior for add/remove/move/rename identity.
- Verify list/grid keyboard navigation and scrolling.
- Verify packaged-app initial sizing and macOS Dock/window icons.
- Verify auto-save on selection change, collection switch, close, and Cmd/Ctrl+S.
- Continue decomposing `Main.kt`, especially browser and preview/export/contact-sheet orchestration.
- Later, clean up the duplicated collection-template UI and clarify collection default vs per-card override controls.

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

## Notes for the next chat

Start by checking the current branch/PR state and `BACKLOG.md`.

Do not re-open the startup-performance investigation unless it regresses; the confirmed bottleneck was image decoding in browser sorting and it is resolved.

When touching the selected-template bulk action, preserve its current semantics: it must persist the selected template explicitly to every card, not merely set a collection default or clear overrides.
