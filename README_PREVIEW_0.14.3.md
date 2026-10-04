# Card Forge 0.14.3 — Preview Rendering Fix

Card preview thumbnails and the in-app contact sheet now use a canonical full-card snapshot at the template's native dimensions.

The renderer no longer scales the JavaFX card node inside the small off-screen preview scene. Instead, it snapshots the complete card first and then resizes the resulting bitmap. This avoids JavaFX transform-origin clipping that could leave only a small corner of a card visible.

PNG/SVG/PDF export continues to use the same canonical renderer.
