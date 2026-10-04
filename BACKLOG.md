# Card Forge Backlog

Card Forge 0.14.x is functionally wrapped. Keep this file focused on unresolved work rather than completed implementation history.

## 0.14.x — remaining smoke verification

These checks are intentionally manual because they depend on JavaFX rendering, virtualization, packaging, or platform behavior.

- Compare live preview, PNG, SVG, and PDF for:
  - artwork crop/pan/zoom,
  - bleed behavior,
  - foreground opacity,
  - overlay placement/tint/opacity,
  - text wrapping and alignment,
  - full outer-border thickness,
  - transparent pixels outside rounded/cut-out card corners.
- Verify the paged in-app contact sheet matches the live preview.
- Verify rendered-card thumbnails remain current after edits in both list and grid modes.
- Verify list/grid keyboard navigation, scrolling, and JavaFX virtualization behavior.
- Verify autosave on:
  - card switch,
  - collection switch,
  - application close,
  - Cmd/Ctrl+S.
- Verify packaged-app behavior on macOS:
  - initial window sizing,
  - window icon,
  - Dock icon.

If these checks reveal concrete regressions, fix those regressions on 0.14.x. Otherwise consider the line frozen.

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
