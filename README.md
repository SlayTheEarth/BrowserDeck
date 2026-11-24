# BrowserDeck
Experimental browser-game launcher for Android handhelds. Big Ice is the first profile.

v0.2.0: GeckoView (Firefox) is the default engine; Android WebView remains selectable per game via Profile. This is an ARM64 test build for devices such as the AYN Thor. GeckoView observes normal cross-origin isolation requirements; no security headers are removed or spoofed. The native controller bridge is a bundled extension. Diagnostics report SharedArrayBuffer, crossOriginIsolated and shared WebAssembly memory availability.

In game: Start + Select opens the launcher menu, Select cycles mouse modes. Export diagnostics via the menu. No game assets are bundled. Browser data is stored separately for each engine. The build is experimental and device compatibility is unverified.

Build: JDK 17, Android SDK 37, Android Gradle Plugin 9.1.1, Gradle 9.3.1. GeckoView is pinned to 160.0.20261116130455 for repeatable builds. Releases are debug-signed; Android may require uninstalling an earlier build if its signing key differs.
