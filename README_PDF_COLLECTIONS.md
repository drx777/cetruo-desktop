# Card Forge - PDF and collection UX

- `A4 Contact Sheet PDF` exports the currently visible cards (folder/filter scope) at their native physical card size.
- A4 plans use centered grid cells and 10 mm page margins.
- Browser previews can switch between original image previews and card previews.
- Nested `.cardforge.sqlite` directories are shown as collections and can be switched to from the collection selector or folder tree.


## PDF rendering

PDF contact sheets render each complete card through the same JavaFX card renderer used by PNG export, then place the resulting full-card PNG into PDFBox. This avoids relying on SVG/Batik to reproduce the JavaFX preview and prevents image-only PDF output.
