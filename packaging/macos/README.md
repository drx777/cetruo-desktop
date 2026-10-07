# macOS application icon and Dock behavior

`Cetruo.icns` is the native macOS application icon used by `jpackage`.nt native macOS application icon file used by `jpackage`; the filename is a legacy implementation detail.

Cetruo Desktop also sets its JavaFX window icon and attempts to set the Dock icon at runtime through the JDK `Taskbar` API. When launched directly from IntelliJ, macOS can still identify the IDE-launched JVM rather than Cetruo Desktop as a separate application. A native `.app` bundle is the reliable way to get a dedicated Cetruo Desktop Dock item and icon.

Use:

```text
scripts/package-macos.sh
scripts/run-macos-app.sh
```

The resulting `build/macos/Cetruo Desktop.app` is a normal macOS application bundle with the Cetruo Desktop icon.
