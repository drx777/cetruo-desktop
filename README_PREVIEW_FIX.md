# Preview rendering fix — 0.14.2

Card thumbnails and the in-app contact sheet now use the direct canonical JavaFX snapshot instead of encoding the snapshot to PNG and decoding it again.

The off-screen renderer explicitly lays out the card tree and positions the outer card canvas at (0, 0), preventing the partial/empty-card preview seen when StackPane layout had not run before snapshotting.

Preview failures now log the underlying exception to stderr instead of silently producing a blank preview.
