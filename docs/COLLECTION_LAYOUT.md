# Collection layout and schemes

Cetruo Desktop stores the collection-wide default template in the legacy-compatible `.cardforge.sqlite` database (`collection_meta.default_template`).

A card with an empty `templateName` follows the collection default. A card with a non-empty `templateName` uses that template as a per-card override.

Selecting a template in **Card template** automatically enables the per-card override. Uncheck **Override for this card** (or press **Reset card to collection default**) to follow the collection default again.

Changing **Collection default** changes the effective layout only for cards without an override; it does not modify each card snapshot, so per-card history stays clean.

JSON color schemes live under `schemes/`. `backgroundColor` is the actual base card background and is independent of the decorative SVG template.

## Persistence safety

Card selection validates catalog/sidecar disagreements before loading a conflicting card. SQLite asset identity is kept stable through moves/renames while duplicate live files receive new identities. Saves protect against accidental mass-clearing of previously populated card fields.
