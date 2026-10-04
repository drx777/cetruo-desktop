# Card Forge Handoff

Target branch: `main`

Current line: `0.15.x`

0.14.x status: **closed / frozen**

## Current state

Card Forge 0.14.4 is complete and closed. The 0.14 hardening line should not receive further proactive work.

The remaining manual smoke checks are intentionally deferred while the application is used in normal workflows. Reopen 0.14 only for a concrete regression that materially affects stability, persistence, rendering/export correctness, or data safety.

Major completed work includes:

- collection SQLite persistence with no automatic sidecars,
- randomized sensible initialization with image-derived color selection,
- recent collections and collection counts,
- collection-wide set-name/template actions with preserved semantics,
- set-aware collector numbering,
- file identity preservation across move/rename,
- source-image inspector,
- browser selection/tile/preview coordinator extraction,
- targeted/debounced thumbnail refresh,
- centralized autosave gating,
- export UI and execution extraction,
- borderless one-card-per-page PDF export,
- vector SVG/PDF export,
- regression-test and CI foundation,
- startup-performance regression fix.

## Export/rendering architecture

There are now two rendering paths by design.

### JavaFX raster/live path

The JavaFX `CardRenderer` remains the source for:

- live editor preview,
- PNG export,
- browser rendered-card thumbnails,
- in-app contact-sheet previews.

`ExportRenderer` snapshots the JavaFX card tree for raster output.

### Vector SVG/PDF path

`VectorCardSvgRenderer` is the shared source for:

- standalone SVG export,
- PDF card rendering.

In this path:

- source artwork remains raster,
- template SVG remains vector,
- overlay SVG remains vector,
- generated panels/borders/clips remain vector,
- generated text is converted to vector glyph paths before PDF transcoding,
- PDF pages are assembled as SVG and transcoded through Batik/FOP,
- PDFBox merges the resulting page documents while preserving image resources.

Important vector-export details already fixed and regression-covered:

- artwork uses both `href` and `xlink:href`,
- PDF artwork uses a file URI with a document base URI so FOP can load it,
- generated text does not rely on FOP font substitution,
- outer border is inset by half the stroke width so the full configured thickness remains visible,
- multi-card PDF pages scope SVG IDs to avoid clip/filter collisions.

## Deferred 0.14 verification

The previous manual smoke checklist is no longer a release gate for 0.14. It remains useful as a regression checklist if a real issue is reported in live/PNG/SVG/PDF parity, contact sheets, thumbnails, keyboard/virtualization behavior, autosave, or packaged macOS behavior.

Do not perform additional 0.14 refactoring, hardening, or verification for its own sake.

## Current phase: 0.15 visual/template work

The next planned line should focus on visual quality and template capability rather than architecture churn.

Priorities:

1. stronger background/template structure;
2. rarity-dependent frames;
3. material/depth/embossing treatment;
4. flatter rails with subtle depth;
5. improved P/T treatment;
6. restrained jewel/gem treatment;
7. typography and text-fit refinement;
8. description-box depth/layering;
9. palette discipline: five main colors plus gold and gray;
10. cleaner, more systematic template/theme architecture.

The browser/export refactors are sufficiently separated for now. Avoid more structural work unless a real bug or feature requirement exposes a poor boundary.

## Important semantics to preserve

### Collection template bulk action

**Apply selected template to all cards** must:

- use the currently selected template,
- persist that template explicitly on every card,
- initialize previously uninitialized cards as needed,
- store the selected template as the collection default.

Do not replace this with merely changing the collection default or clearing per-card overrides.

### Asset identity

A removed/moved/renamed asset keeps its catalog identity only when a newly discovered file matches the detached asset by content hash.

A different file reusing an old filename must get a new identity and must not inherit card metadata.

### Persistence

- Collection SQLite DB is authoritative.
- Do not write sidecars automatically.
- Sidecars are explicit/manual only.

### Card initialization

- Uninitialized card title = filename without extension.
- Randomize sensible fields on first initialization.
- Do not randomize artwork fit mode, overlay choice, or bleed-over.
- Prefer image-derived theme/color when available; otherwise choose randomly.
- Randomize card number if absent.

### Rendering

- Pixels outside the actual card silhouette/corners must remain transparent.
- PNG/live output uses the JavaFX renderer.
- SVG/PDF output uses the vector renderer.
- Keep those two paths visually aligned through regression tests and smoke checks.

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

Do not reopen startup-performance work unless a regression is observed.
