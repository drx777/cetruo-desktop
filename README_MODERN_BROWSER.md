# Card Forge 0.8.0

This revision focuses on dependable browser interaction, editable visual schemes, randomization, and additional polished templates.

## Browser
- Single-click selection on both list and thumbnail grid.
- Keyboard navigation uses natural ListView scrolling rather than manually pinning each move to the top.
- List and grid remain virtualized.
- Thumbnails are decoded lazily in a background executor and retained in an in-memory LRU cache.
- Right-click an image for Reveal in Finder / Copy Path.
- Folder tree remains hierarchical.

## Randomization
The **Randomize** toolbar action changes style safely without destroying card copy:
- random color scheme
- random layout template
- random cost (0–9)
- random attack/defense values (0–12)

## Schemes
Each JSON scheme contains a complete visual palette, including `backgroundColor` and `imagePadColor`.

## Templates
Templates are JSON + transparent decorative SVG pairs. The included set now contains several portrait and landscape compositions with intentionally different information hierarchies.
