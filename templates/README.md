# Card templates

Each template is a pair of files:
- `name.json` — layout geometry and metadata regions in design-space pixels.
- `name.svg` — decorative, transparent base artwork rendered behind the editable content.

The SVG should remain transparent apart from decorative strokes/fills so the card's selected color scheme controls the actual card background.

Included examples:
- Portrait Classic
- Portrait Full Art
- Royal Portrait
- Arcane Portrait
- Landscape Command
- Landscape Minimal
- Relic Landscape
- Split Prism
- Duel Landscape
- Classic Creature

To add a template, add a matching JSON/SVG pair to this directory and use **Reload templates** in Card Forge.


## Rarity variants

A template can optionally override its decorative SVG and/or visual depth metrics per rarity tier without changing the card's selected template. Add a `rarityVariants` object to the template JSON:

```json
{
  "rarityVariants": {
    "RARE": {
      "svgFile": "portrait-rare.svg"
    },
    "MYTHIC": {
      "svgFile": "portrait-mythic.svg",
      "visualStyle": {
        "materialDepth": 4.0,
        "railDepth": 2.0,
        "railStrokeWidth": 1.5,
        "descriptionDepth": 4.0,
        "statsJewelInset": 4.0,
        "statsJewelCut": 10.0,
        "rarityFrames": true
      }
    }
  }
}
```

Supported keys are `COMMON`, `UNCOMMON`, `RARE`, and `MYTHIC`. Card rarity text is mapped to those tiers by the shared visual system, so values such as “Mythic Rare”, “Legendary”, and “Epic” use the `MYTHIC` variant. Missing variant properties fall back to the base template.

Existing templates do not receive rarity-colored structural frames automatically. Set `rarityFrames: true` only when a template intentionally wants generated rarity coloring. For structural differences such as a different outer border, corner profile, cutout, or background shape, prefer a rarity-specific `svgFile`; the template SVG owns that geometry.

The rarity SVG is used consistently by live preview, PNG/thumbnails/contact sheets, standalone SVG, and PDF. Keep variant SVGs transparent outside their intended decorative/background shapes so the card silhouette remains transparent at its corners.
