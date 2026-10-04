# Card Forge reliability and layout fixes

This revision fixes several presentation and browser issues:

- Canonical card snapshots are placed in an exact-size export host, so scaled previews no longer appear offset or clipped to a corner.
- PNG, SVG, PDF and in-app card previews use the same complete-card render path.
- PDF export offers scale, page-margin and card-gap controls; 100% preserves the template's physical size. A4 portrait/landscape orientation is chosen per compatible card size to improve packing.
- Export files default to a visible `Card Forge Exports/` directory and recognizable generated card names are excluded from source-image discovery.
- Browser list/grid and original/card preview choices are icon toggles.
- The list browser shows the card name above filename and folder, with explicit light/dark readable colors.
- Removing/replacing images no longer reclaims deleted asset IDs from hash-only matches; persistent IDs come from path records or sidecars.
- Scheme cells show Light/Dark grouping labels and palette swatches.
