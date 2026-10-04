# macOS application icon and Dock behavior

`CardForge.icns` is the native macOS application icon used by `jpackage`.

Card Forge also sets its JavaFX window icon and attempts to set the Dock icon at runtime through the JDK `Taskbar` API. When launched directly from IntelliJ, macOS can still identify the IDE-launched JVM rather than Card Forge as a separate application. A native `.app` bundle is the reliable way to get a dedicated Card Forge Dock item and icon.

Use:

```text
scripts/package-macos.sh
scripts/run-macos-app.sh
```

The resulting `build/macos/Card Forge.app` is a normal macOS application bundle with the Card Forge icon.
