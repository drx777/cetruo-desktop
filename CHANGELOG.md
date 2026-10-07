# Changelog

This changelog records net user-visible and significant system changes between Cetruo Desktop versions. Intermediate implementation attempts, superseded fixes, and planning-only work are intentionally omitted.

## Unreleased

- Renamed the desktop Kotlin/Gradle namespace to `de.cetruo.desktop` and aligned resource/macOS packaging identifiers with Cetruo Desktop.
- Changed the canonical collection catalog filename to `.cetruo.sqlite`, with automatic migration from legacy `.cardforge.sqlite` catalogs and compatibility for existing missing-asset tombstones.
- Migrated application preferences lazily from the old `com.example.cardforge` Java Preferences node.
- Kept legacy startup-profiler keys and old export-directory detection as compatibility fallbacks.

## 0.14.4

- Closed the 0.14.x stabilization line and froze it against further proactive refactoring.
- Consolidated collection persistence around SQLite, with explicit sidecar sharing rather than automatic sidecar writes.
- Preserved card identity across file moves and renames while preventing unrelated replacement files from inheriting metadata.
- Added recent collections, collection counts, collection-wide set/template actions, and set-aware collector numbering.
- Added browser selection, tile, and preview coordinators with bounded preview caches and targeted refresh behavior.
- Added card-level undo/redo, autosave gating, catalog backup, and destructive-save protection.
- Added canonical PNG/live rendering and a separate vector SVG/PDF export path with vector text and artwork/template handling.
- Added borderless one-card-per-page PDF export and A4 contact-sheet export.
- Added startup diagnostics and fixed the startup-performance regression while keeping profiling opt-in.
- Added Gradle Wrapper support and standardized project builds on the checked-in wrapper.

## 0.14.3

- Fixed rendered-card thumbnails and the in-app contact sheet to snapshot the complete card at native template dimensions before resizing, avoiding transform-origin clipping.

## 0.14.2

- Fixed off-screen preview layout so the complete card tree is laid out and positioned before snapshotting.
- Removed an unnecessary PNG encode/decode round trip from preview generation and surfaced preview failures in diagnostics.

## 0.8.0

- Added dependable list/grid browser interaction with lazy background thumbnail loading and cache reuse.
- Added editable color schemes, card randomization, and multiple portrait/landscape template layouts.
