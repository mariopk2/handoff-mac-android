# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

ClipBridge syncs plain-text clipboard between a Pixel phone and a Mac over the local Wi‑Fi network only (no cloud, no accounts). This directory is the Android half; the Mac half is a single-file Swift menu-bar app in `../mac/` (`main.swift`, built with `../mac/build.sh`). The top-level `../README.md` is the user-facing setup guide (in Bulgarian).

User-facing strings, comments and docs are in **Bulgarian** — keep new ones in Bulgarian to match.

## Commands

Run from this directory (`android/`):

```bash
./gradlew assembleDebug      # build APK -> app/build/outputs/apk/debug/
./gradlew installDebug       # build and install on a connected device (USB debugging)
./gradlew lint               # Android lint
```

There are no tests and no test dependencies. Verify changes by building and exercising the app on a device against a running Mac app.

## Build setup

- Plain **Java 17** (not Kotlin), single `:app` module, AGP 8.7.3, Gradle 8.9, compileSdk/targetSdk 35, minSdk 29.
- **No external dependencies** — Android framework only (no AndroidX, no Material). UI in `MainActivity` is built programmatically (no layout XML); themes are `@android:style/...`. Keep it dependency-free.

## Architecture

Package `bg.mvsoft.clipbridge`:

- **Protocol (shared with `../mac/main.swift`)**: HTTP on port 8765, every request carries an `X-Token` header (the code shown in the Mac menu; 401 on mismatch). `POST /clip` sets the Mac clipboard from the UTF‑8 body, `GET /clip` returns it, `GET /ping` is a health check. The Mac advertises itself via Bonjour as `_clipbridge._tcp`. Changes to the protocol must be made on both sides.
- **`Config`** — `SharedPreferences` store for `host` (`"ip:port"`, port defaulted to 8765) and `token` (trimmed, uppercased).
- **`Net`** — blocking `HttpURLConnection` calls (must run off the main thread). If the saved host fails with an I/O error it re-discovers the Mac via `Discovery`, saves the new host, and retries once; `AuthException` is never retried.
- **`Discovery`** — blocking NSD/mDNS lookup that returns the first IPv4 `ip:port` (uses `getHostAddresses()` on API 34+).
- **`ClipActivity`** — invisible, translucent, `noHistory` activity that does the actual clipboard work. It exists because Android 10+ only allows clipboard reads by the foreground app: in send mode it waits for `onWindowFocusChanged(true)` before reading the clipboard. It also handles `ACTION_SEND text/plain` (Share → ClipBridge). Results are reported via Toast, then it finishes.
- **`SendTile` / `ReceiveTile`** — Quick Settings tiles that delegate to `TileLauncher`, which starts `ClipActivity` with a mode extra (`PendingIntent` variant of `startActivityAndCollapse` on API 34+, `unlockAndRun` when the device is locked).
- **`MainActivity`** — settings screen: enter token, auto-discover Mac, save & ping, and request adding the tiles via `StatusBarManager`.

Transport is cleartext HTTP (`usesCleartextTraffic="true"`), intentionally LAN-only.
