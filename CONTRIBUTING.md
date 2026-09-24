# Contributing

Thanks for helping improve AudioBridge. Bug reports, fixes, documentation and
testing on more devices are all valuable.

## Reporting bugs

Use the [bug report form](https://github.com/AimenSayoud/audiobridge/issues/new/choose)
and include:

- macOS version, Android version and phone model;
- the capture method (Screen Recording or BlackHole) and the route (USB or Wi-Fi);
- what the Mac's **Activity** card and the phone's stats show.

## Development setup

See [docs/BUILDING.md](docs/BUILDING.md). In short:

```bash
cd macos && ./make-signing-identity.sh && ./build.sh
cd android && ./gradlew :shared:jvmTest :androidApp:assembleDebug
```

## Before opening a pull request

Run the checks CI runs:

```bash
cd macos && ./build.sh && ./AudioBridge.app/Contents/MacOS/AudioBridge --selftest
cd android && ./gradlew :shared:jvmTest :androidApp:assembleDebug
```

If you run a server and a client, also check `python3 tools/probe.py` passes.

## Guidelines

- **Keep the two protocol implementations in step.** Any change to
  [docs/PROTOCOL.md](docs/PROTOCOL.md) needs matching changes in the Swift
  codec and self-test (`macos/Sources/AudioBridgeMac/Protocol`, `SelfTest.swift`),
  the Kotlin codec and tests (`android/shared`), and `tools/probe.py`. Never
  break compatibility within a protocol version; bump `v` in the handshake
  instead.
- **Keep `shared/commonMain` free of platform types.** Android and JVM
  specifics belong in their own source sets.
- **Match the surrounding style.** Swift follows the Swift API Design
  Guidelines; Kotlin follows the official Kotlin style (`kotlin.code.style=official`).
  Comments explain *why*, not *what*.
- **Test on hardware** for anything touching audio. Emulators and the desktop
  sink do not reproduce `AudioTrack` behaviour.
- One logical change per pull request, with a clear description of what
  changed and how you tested it.

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE) and that you follow the [Code of Conduct](CODE_OF_CONDUCT.md).
