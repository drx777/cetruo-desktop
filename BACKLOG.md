# Card Forge Backlog

Card Forge 0.14.x is closed and frozen. Keep this file focused on unresolved work rather than completed implementation history.

## 0.14.x — closed

No proactive 0.14 work remains.

The previous manual smoke checks are deferred and should be used only when a concrete regression is observed, especially for:

- data safety or persistence,
- autosave,
- live/PNG/SVG/PDF rendering parity,
- transparent card corners,
- thumbnails/contact sheets,
- browser navigation/virtualization,
- packaged macOS behavior.

Do not reopen 0.14 for general hardening, cleanup, or speculative verification.

## 0.15 — visual/template phase

Primary direction for the next feature line:

- stronger template/background structure,
- more convincing material/depth/embossing,
- rarity-dependent outer frames,
- flatter title/type rails with subtle depth,
- improved P/T treatment,
- restrained jewel/gem treatment,
- tighter typography and text fitting,
- stronger description-box depth and layering,
- final palette discipline: five main colors plus gold and gray,
- more systematic template/theme architecture,
- continue adding sports and other template families without coupling them to existing templates.

## Later / structural

Only pursue these when they solve a concrete product need:

- Continue reducing responsibilities in `Main.kt`.
- Keep browser responsibilities split across:
  - `BrowserSelectionCoordinator`,
  - `BrowserTileFactory`,
  - `BrowserPreviewCoordinator`.
- Keep export execution in `ExportCoordinator`.
- Continue routing persistence/undo/collection behavior through:
  - `CollectionCardStore`,
  - `CardUndoManager`,
  - `AppPlatform`,
  - collection action/settings helpers.
- Keep startup profiling opt-in and zero-cost during normal launch.
