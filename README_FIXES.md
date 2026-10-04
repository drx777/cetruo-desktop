# Card Forge browser/render fixes

This revision addresses:

- asynchronous, lazy thumbnail loading with an in-memory cache retained across List/Grid mode changes
- explicit image selection on mouse press in both list and grid cells
- transparent ListView selection styling with a subtle card-level selected state
- exact artwork viewport sizing independent of the decorative border
- edge-to-edge pan ranges using explicit minimum/maximum image translations
- robust normalized slider <-> pixel pan conversion
- color scheme application via ComboBox action and an explicit Apply Scheme button
- more robust discovery of the project `schemes/` directory from IntelliJ/local runs
- stylesheet to suppress JavaFX ListView selection backgrounds

The application continues to use JDK 21 / JavaFX 21.0.5 / Kotlin 2.4.20.

## Reliability and export fixes

- Catalog/sidecar discrepancies now prompt for an explicit resolution when a card is selected.
- Card asset registration no longer reuses a live asset's hash for a newly added duplicate image; hash reuse is limited to stale/missing catalog paths so new files keep their own IDs.
- Saves guard against suspicious bulk clearing of populated fields and ask for confirmation before writing.
- A `Backup catalog` command creates a consistent SQLite backup using SQLite `VACUUM INTO`.
- Current-card undo/redo is available from the toolbar and with Cmd/Ctrl+Z and Cmd/Ctrl+Shift+Z. Text fields retain their native text-edit undo behavior.
- PDF contact sheets now rasterize the complete vector card via Batik before placing it in PDFBox, rather than relying on the JavaFX scene snapshot that could capture only part of the card.
- A4 contact-sheet planning groups identical physical card sizes, preserving original dimensions while keeping portrait and landscape cards cleanly arranged on separate page groups.
