# Cetruo Desktop

Kotlin + JavaFX desktop application for creating collectible-style cards from source images and managing them as local collections.

## Product decisions

- Desktop-first local application; no account or server required.
- Collections are directory-based and use a local SQLite catalog.
- Templates, schemes, and overlays are editable project resources.
- Live editing and PNG output use JavaFX rendering; SVG/PDF use the vector export path.
- Data safety and stable image/card identity take priority over aggressive automatic migration.

## Requirements

- JDK 21
- macOS, Linux, or Windows environment capable of running JavaFX
- The checked-in Gradle Wrapper

## Run

From the repository root:

```bash
./gradlew run
```

In IntelliJ IDEA, use **Gradle Wrapper** and JDK 21. The main class remains `com.example.cardforge.MainKt`; that package name is currently retained as an internal compatibility/implementation identifier.

## Test

```bash
./gradlew test
```

GitHub Actions is intentionally not run on every pull request. The Verify workflow runs on pushes to `main` and can be started manually when a branch is otherwise merge-ready.

## Collections and persistence

Each collection uses a `.cardforge.sqlite` catalog. The filename is retained for compatibility even though the product name is now Cetruo Desktop.

- Stable asset IDs are independent of filenames.
- Moves/renames can preserve identity through content reconciliation.
- An unrelated replacement file at an old path must not inherit the previous card metadata.
- Sidecars are not written automatically; **Share sidecar** creates one explicitly when portability is needed.
- Saves record revision/activity data.
- Catalog backups are available from the application.
- Suspicious destructive saves are guarded.

See [docs/COLLECTION_LAYOUT.md](docs/COLLECTION_LAYOUT.md) for collection/template semantics.

## Browser

- Hierarchical folder tree with nested collection discovery.
- List and thumbnail-grid modes.
- Original-image and rendered-card previews.
- Lazy background thumbnail decoding with bounded in-memory caches.
- Sorting by title/name, last word, collector number, folder, status, or filename.
- Keyboard navigation and Finder/path actions.
- Generated exports are excluded from source-image discovery.

## Templates, schemes, and overlays

- Template geometry and matching SVG resources live under `templates/`.
- Editable color schemes live under `schemes/`.
- Decorative SVG overlays live under `overlays/`.
- Templates can be selected per collection with per-card overrides.
- Collection-wide template actions preserve the explicit persistence semantics documented in [docs/COLLECTION_LAYOUT.md](docs/COLLECTION_LAYOUT.md).

See [docs/TEMPLATE_LAYOUT.md](docs/TEMPLATE_LAYOUT.md) for the coordinate/layout model and [docs/SCHEME_CONTRAST.md](docs/SCHEME_CONTRAST.md) for the current contrast audit.

## Rendering and export

### JavaFX path

Used for live editor preview, PNG export, rendered browser thumbnails, and in-app contact-sheet previews.

### Vector path

`VectorCardSvgRenderer` drives standalone SVG and PDF card rendering. Artwork remains raster while template/overlay geometry and generated text remain vector where supported.

Available exports include:

- PNG
- SVG
- A4 contact-sheet PDF
- borderless one-card-per-page PDF

New exports default to `Cetruo Desktop Exports/` inside the active collection. The browser also continues to ignore the legacy `Card Forge Exports/` directory so existing collections do not start ingesting old exports.

## Editor

- Cmd/Ctrl+S saves.
- Cmd/Ctrl+Z and Shift+Cmd/Ctrl+Z provide card-level undo/redo outside native text-edit undo.
- Double-click artwork to reset zoom/position.
- Sensible numeric and slider controls support reset.
- Editor guides can be hidden for a cleaner output-style preview.
- Dice controls randomize appropriate fields with duplicate avoidance where applicable.

## macOS packaging

Use:

```bash
scripts/package-macos.sh
scripts/run-macos-app.sh
```

The packaging script uses the checked-in Gradle Wrapper and creates `build/macos/Cetruo Desktop.app`.

The current native icon asset filename, `packaging/macos/CardForge.icns`, is a legacy implementation detail and can be renamed separately when the branding asset itself is intentionally revisited.

## Startup diagnostics

Startup profiling is opt-in:

```text
-Dcardforge.profileStartup=true
```

or:

```text
CARDFORGE_PROFILE_STARTUP=1
```

These names are retained as internal compatibility identifiers.

## Project status

The stable baseline is 0.14.4. The 0.14.x line is closed/frozen; current planned work is the 0.15 visual/template phase.

See:

- [BACKLOG.md](BACKLOG.md) — unresolved work and priorities;
- [CHANGELOG.md](CHANGELOG.md) — release-oriented change history;
- [docs/HANDOFF.md](docs/HANDOFF.md) — current development checkpoint and invariants.
