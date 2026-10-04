# Card Forge

Kotlin + JavaFX desktop app for creating collectible-card layouts from a directory of source images.

## Browser

- Folder tree with nested collections and stable path/asset identity.
- List and thumbnail-grid modes with natural keyboard navigation.
- Original-image or rendered-card previews.
- Original and card previews are generated lazily and retained in bounded in-memory caches.
- Thumbnail decoding uses ImageIO off the JavaFX thread, with the result converted once to JavaFX images.
- Sorting: title/name, last word of title, collector/card number, folder, status, or file name, ascending/descending; the choice is remembered.
- Adding/removing images does not reassign the currently edited card because the active card is tracked by its actual path and asset ID rather than a list index.
- Generated `*.card.png`, `*.card.svg`, and files below `.cardforge/` are excluded from source-image discovery by default.
- New exports are placed in `Card Forge Exports/` by default. Generated card files are excluded from source-image discovery.
- Right click an image for Reveal in Finder / Copy Path.

## Persistence and safety

- Each selected collection gets its own `.cardforge.sqlite`.
- Stable UUID-style asset IDs are independent of filenames.
- Sidecars are never written automatically; **Share sidecar** explicitly creates a portable `image.ext.card.json` file when needed.
- Saves record revisions and activity history.
- Switching cards, collections, and closing the app save the active card first.
- Catalog/sidecar conflicts are resolved interactively.
- A catalog backup command is available from the toolbar.
- Undo/redo is available per card.
- Destructive saves that would blank many populated fields require confirmation.
- Deleting or inserting files cannot cause the edited card's data to be written into its neighbour. Removed assets are detached from their old path; if the same file is moved/renamed while Card Forge is running, content-hash reconciliation preserves its asset ID, while an unrelated replacement at the old filename starts as a new asset.

## Schemes

Editable JSON files live under `schemes/` and include:

- `tone`: `LIGHT`, `DARK`, or legacy `AUTO`.
- `backgroundColor`: card stock/background color.
- `panelColor`, `frameColor`, `accentColor`.
- `overlayColor`: tint used for SVG overlays.
- `textColor`, `darkTextColor`.
- `imagePadColor`.

Light schemes are listed before dark schemes and show a palette preview. `SCHEME_CONTRAST.md` documents the actual text/surface contrast checks.

## Templates and overlays

- JSON geometry + paired SVG base files live under `templates/`.
- SVG decorative overlays live under `overlays/`.
- Overlay placement can be Frames Only or Over Content.
- Templates are selectable per collection, with optional per-card overrides. A collection action clears all per-card overrides so every card follows the collection default.

## Export and contact sheets

- PNG export uses the canonical complete-card renderer.
- SVG export is a self-contained rendered-card SVG.
- A4 contact-sheet PDF keeps cards at physical card size by default, with optional per-export scale, margin, and gap controls; compatible dimensions are grouped and A4 orientation is chosen for better packing.
- The application also has a paged in-app contact-sheet viewer using the same canonical card previews. Browser view/previews use icon toggles instead of mode dropdowns.

## Collection presentation

- Collection-scoped bleed opacity controls only artwork outside the normal image aperture.
- Collection foreground opacity controls title/type/description/P-T surface fills while borders and text remain crisp.
- Description background opacity remains a per-card control and composes with collection foreground opacity.
- Set name can be applied collection-wide, including images that had not been initialized yet; collector-number totals are kept synchronized with set membership and bulk assignment avoids duplicate numeric prefixes.
- Startup offers up to the five most recently opened catalogs.

## Editor UX

- Cmd/Ctrl+S saves.
- Cmd/Ctrl+Z / Shift+Cmd/Ctrl+Z provide card-level undo/redo.
- Double-clicking artwork resets image zoom and position.
- Double-clicking sensible numeric/slider controls resets their value.
- Editor guides can be toggled off for a print/export-style preview.
- Dice controls randomize appropriate fields, including collector numbers with duplicate avoidance.
- Artist has decorative Unicode-pattern generation.
- Randomization uses time with microsecond precision plus additional entropy; layout is not randomized.

## macOS application icon

The runtime sets the JavaFX window icon and attempts to set the Dock icon through the JDK `Taskbar` API. A native `CardForge.icns` is included under `packaging/macos/`. For a dedicated Dock item and reliable native icon, build and launch the packaged `.app` with `scripts/package-macos.sh` followed by `scripts/run-macos-app.sh`.

`scripts/package-macos.sh` documents the intended `jpackage` flow on a machine with the Gradle dependencies installed.

## Running

Use IntelliJ IDEA with Gradle JVM / project SDK set to JDK 21.

Main class: `com.example.cardforge.MainKt`


## Startup diagnostics

Startup profiling is normally silent. To diagnose a regression, launch with
`-Dcardforge.profileStartup=true` or set `CARDFORGE_PROFILE_STARTUP=1`.
The profiler reports timed startup phases and JavaFX event-thread stalls to stderr.
