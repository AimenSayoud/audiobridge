# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.1.0] — 2026-09-24

First public release.

### macOS
- Menu-bar and windowed SwiftUI app; universal binary for Apple Silicon and Intel.
- Two capture methods: ScreenCaptureKit (Screen Recording) and AVAudioEngine on
  a BlackHole device (Microphone), switchable from the window.
- TCP server with token authentication and per-client drop-oldest queues.
- QR pairing that lists USB, LAN, VPN and custom routes.
- Automatic `adb reverse` when a phone is plugged in.
- Optional local monitor playback, launch at login, menu-bar-only mode.
- Live per-client diagnostics: RTT, buffer depth, underruns, loss, drift ppm.

### Android
- Compose app with QR scanner, deep-link pairing and manual entry.
- Parallel route racing and automatic reconnect with backoff.
- Jitter buffer with continuous, glitch-free clock-drift correction.
- Foreground playback service with a Stop action in the notification.

### Shared
- Wire protocol v1, specified in `docs/PROTOCOL.md` and checked by identical
  test vectors in Swift and Kotlin.

[Unreleased]: https://github.com/AimenSayoud/audiobridge/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/AimenSayoud/audiobridge/releases/tag/v0.1.0
