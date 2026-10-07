# Cetruo Desktop backlog

Updated 2026-10-07. Keep this file focused on unresolved work rather than completed implementation history.

## 0.14.x — closed

0.14.4 is the stable baseline. No proactive 0.14 work remains.

Use the previous smoke-test areas only when a concrete regression is observed, especially:

- persistence/data safety;
- autosave and card switching;
- live/PNG/SVG/PDF rendering parity;
- transparent card corners;
- thumbnails/contact sheets;
- browser navigation/virtualization;
- packaged macOS behavior.

Do not reopen 0.14 for general hardening, cleanup, or speculative verification.

## Current priority — 0.15 visual/template phase

- [ ] Strengthen template/background structure.
- [ ] Add rarity-dependent outer frames.
- [ ] Improve material/depth/embossing treatment without overusing shadows.
- [ ] Refine title/type rails toward flatter geometry with restrained depth.
- [ ] Improve P/T treatment and placement.
- [ ] Refine the jewel/gem treatment.
- [ ] Tighten typography and text fitting.
- [ ] Improve description-box depth/layering where appropriate.
- [ ] Establish disciplined palette rules: five main colors plus gold and gray.
- [ ] Make template/theme architecture more systematic where visual requirements expose a real need.
- [ ] Continue adding sports and other template families without coupling them to existing templates.

## Validation

- [ ] Run local `./gradlew test` during active development.
- [ ] Trigger the GitHub Verify workflow manually once a branch is otherwise merge-ready when CI evidence is useful.
- [ ] Re-run CI only after meaningful fixes rather than on every iteration.
- [ ] For visual changes, validate live preview plus affected PNG/SVG/PDF paths rather than assuming one renderer proves the others.

## Later / structural

Only pursue these when they solve a concrete product need:

- [ ] Continue reducing responsibilities in `Main.kt` when a feature or bug exposes a useful boundary.
- [ ] Preserve browser separation across `BrowserSelectionCoordinator`, `BrowserTileFactory`, and `BrowserPreviewCoordinator`.
- [ ] Keep export execution in `ExportCoordinator`.
- [ ] Continue routing persistence/undo/collection behavior through focused helpers such as `CollectionCardStore`, `CardUndoManager`, `AppPlatform`, and collection action/settings classes.
- [ ] Keep startup profiling opt-in and effectively zero-cost during normal launch.

## Naming / compatibility follow-up

- [ ] Decide separately whether internal `com.example.cardforge` packages and `cardforge` resource/property names are worth migrating. Do not mix that migration into visual/template work.
- [ ] Keep `.cardforge.sqlite` compatible unless there is an explicit catalog migration plan.
- [ ] Revisit the legacy `CardForge.icns` filename only together with an intentional branding/icon asset update.
