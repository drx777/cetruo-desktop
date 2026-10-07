# Template layout

The template renderer treats template coordinates as absolute design-space coordinates.

Positioned panels and text nodes are unmanaged and explicitly resized/relocated so JavaFX parent layout cannot stretch or move them.

Template JSON fields include:

- `titleBox`, `typeBox`, `descriptionBox`, `statsBox`, `art`: panel/frame rectangles;
- `titleText`, `costText`, `typeText`, `rarityText`, `descriptionHeading`, `descriptionText`, `flavorText`, `footerText`, `statsText`: text rectangles in the same coordinate system.

Text rectangles use their top-left `(x,y)` as the placement anchor and `width`/`height` as the available text area. The renderer applies horizontal alignment from `align` and keeps text inside the rectangle.

This makes it safe to edit positions directly in JSON template files without JavaFX layout changing their intended size or position.
