# Template layout notes

The template renderer now treats template coordinates as absolute design-space coordinates.
Positioned panels and text nodes are marked unmanaged and explicitly resized/relocated, so JavaFX parent layout cannot stretch or move them.

Template JSON fields:
- `titleBox`, `typeBox`, `descriptionBox`, `statsBox`, `art`: panel/frame rectangles.
- `titleText`, `costText`, `typeText`, `rarityText`, `descriptionHeading`, `descriptionText`, `flavorText`, `footerText`, `statsText`: text rectangles in the same coordinate system.

Text rectangles use their top-left `(x,y)` as the placement anchor and `width`/`height` as the available text area.
The renderer applies horizontal alignment from `align` and keeps the text inside the rectangle.

This makes it safe to edit positions directly in the JSON template files without the JavaFX layout system changing their size.
