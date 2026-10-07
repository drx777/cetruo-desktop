# Cetruo Desktop — current handoff

Updated 2026-10-07. Repository: https://github.com/drx777/cetruo-desktop.

## Start here

Read this file, `BACKLOG.md`, `README.md`, and `CHANGELOG.md` before continuing development.

Current release line: **0.15.x**. The 0.14.x line is closed and frozen; reopen it only for a concrete regression affecting stability, persistence, rendering/export correctness, or data safety.

## Current state

Cetruo Desktop 0.14.4 is the stable baseline. The next development phase is visual/template work rather than broad architecture churn.

Major completed foundations include:

- SQLite-backed collection persistence with explicit/manual sidecar sharing;
- stable asset identity across move/rename and safe handling of replacement files;
- recent collections, collection counts, collection-wide set/template actions, and set-aware numbering;
- list/grid browser modes with bounded preview caches and background thumbnail work;
- card-level undo/redo, autosave gating, catalog backup, and destructive-save protection;
- JavaFX live/PNG rendering plus vector SVG/PDF rendering;
- A4 contact-sheet and borderless card PDF export;
- startup diagnostics and regression coverage;
- checked-in Gradle Wrapper.

## Rendering/export architecture

Two rendering paths exist intentionally.

### JavaFX raster/live path

`CardRenderer` is the source for:

- live editor preview;
- PNG export;
- browser rendered-card thumbnails;
- in-app contact-sheet previews.

`ExportRenderer` snapshots the JavaFX card tree for raster output.

### Vector SVG/PDF path

`VectorCardSvgRenderer` is the source for standalone SVG and PDF card rendering.

Keep raster and vector paths visually aligned through regression tests and targeted smoke checks. Do not collapse them into one path without a concrete requirement.

## Semantics to preserve

### Collection template bulk action

**Apply selected template to all cards** must persist the selected template on every card, initialize previously uninitialized cards as needed, and store the selected template as the collection default.

### Asset identity

A moved or renamed asset may retain its catalog identity when the detached asset is matched by content hash. A different file reusing an old filename must receive a new identity and must not inherit card metadata.

### Persistence

- The collection SQLite database is authoritative.
- Do not write sidecars automatically.
- Sidecars remain an explicit sharing/export action.
- The canonical catalog filename is `.cetruo.sqlite`; legacy `.cardforge.sqlite` catalogs are migrated on first open. Keep that migration path intact.

### Card initialization

- Initial title comes from the filename without extension.
- Sensible fields may be randomized on first initialization.
- Do not randomize artwork fit mode, overlay choice, or bleed-over.
- Prefer image-derived theme/color when available; otherwise choose randomly.
- Randomize card number if absent.

### Rendering

- Pixels outside the actual card silhouette/corners must remain transparent.
- PNG/live output uses the JavaFX renderer.
- SVG/PDF output uses the vector renderer.

## Current priority — 0.15 visual/template work

Focus on:

1. stronger template/background structure;
2. rarity-dependent frames;
3. material/depth/embossing treatment;
4. flatter title/type rails with restrained depth;
5. improved P/T treatment;
6. restrained jewel/gem treatment;
7. typography and text-fit refinement;
8. description-box depth/layering;
9. disciplined palette work;
10. a cleaner, more systematic template/theme architecture;
11. additional template families without coupling them to existing templates.

Avoid further browser/export refactoring unless a concrete bug or feature exposes a poor boundary.

## Validation policy

GitHub Actions is intentionally not triggered for every pull request. Use local tests during iteration, then run the workflow manually when a branch is otherwise merge-ready. A push to `main` also runs verification.

Primary local check:

```bash
./gradlew test
```

Run the application with:

```bash
./gradlew run
```

## Startup diagnostics

Normal launches are quiet. Enable profiling with either:

```text
-Dcetruo.profileStartup=true
```

or:

```text
CETRUO_PROFILE_STARTUP=1
```

The legacy `cardforge.profileStartup` / `CARDFORGE_PROFILE_STARTUP` names remain accepted as compatibility fallbacks.

## Source map

- `src/main/kotlin/de/cetruo/desktop/Main.kt` — application shell, editor/browser orchestration, collection lifecycle.
- `CardRenderer.kt` / `ExportRenderer.kt` — live/raster rendering.
- `VectorCardSvgRenderer.kt` — SVG/PDF vector rendering.
- `CollectionDatabase.kt` / `CollectionCardStore.kt` — authoritative collection persistence.
- `BrowserSelectionCoordinator.kt`, `BrowserTileFactory.kt`, `BrowserPreviewCoordinator.kt` — browser behavior.
- `ExportCoordinator.kt`, `ExportUi.kt`, `PdfContactSheetExporter.kt` — export planning/execution.
- `templates/`, `schemes/`, `overlays/` — editable visual resources.
- `docs/COLLECTION_LAYOUT.md` — collection/template persistence semantics.
- `docs/TEMPLATE_LAYOUT.md` — template coordinate/layout conventions.
- `docs/SCHEME_CONTRAST.md` — current palette contrast report.

## Naming note

The product/project name and Kotlin namespace are **Cetruo Desktop** / `de.cetruo.desktop`. Remaining `cardforge` strings are compatibility fallbacks only (legacy catalog/tombstone names, profiler keys, and old export-folder detection).
