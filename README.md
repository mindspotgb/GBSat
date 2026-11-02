<div align="center">

# GBSat

**Sega Saturn emulation for Android phones and handhelds**

Vulkan GPU rendering · Full controller navigation · Save states · RetroAchievements

[Download the latest APK](https://github.com/mindspotgb/GBSat/releases/latest) · [Changelog](CHANGELOG.md) · [Privacy policy](PRIVACY.md)

</div>

GBSat is an Android Sega Saturn emulator built around the Ymir emulation core and the shared [GBFrame](https://github.com/mindspotgb/GBFrame) UI and device framework. This repository contains the Android app, the Ymir core revision used by it, Android build support, and required third-party dependencies.

## Features

- Vulkan GPU renderer with enhanced internal resolution; OpenGL ES presentation renderer as a compatibility option.
- Controller-first Compose library and menus, touch controls, configurable bindings, and quick menu.
- Save states, automatic save/load, per-game settings, cheats, and RetroAchievements.
- Game library with custom and downloaded covers, play time, and Scoped Storage folder access.
- Display modes, built-in widescreen hacks, filters, shaders, and performance profiles.
- Audio settings, fast-forward, and on-screen performance overlay.

GBSat does not include BIOS files or game images. Supply your own legally obtained BIOS and game discs.

## Requirements

- Android 9 or newer and a 64-bit ARM device (`arm64-v8a`).
- Your own Sega Saturn BIOS and game disc images.

## Build

Clone the repository with its submodules:

```powershell
git clone --recurse-submodules https://github.com/mindspotgb/GBSat.git
```

Open `apps/android` in Android Studio, or build with the included Gradle wrapper. Native builds require the Android SDK/NDK and CMake versions specified by the project. Vulkan shader generation also requires the DirectX Shader Compiler (`dxc`) with SPIR-V support on `PATH`.

For Windows local builds and signing setup, see [apps/android/README.md](apps/android/README.md) and `tools/build-local.ps1`.

## Project layout

- `apps/android` — Android app, Kotlin/Compose UI, JNI bridge, assets, and Gradle project.
- `libs/gbsat-core` — Ymir Saturn emulation core used by the Android app.
- `libs/gbframe` — shared Android app framework, pinned as a Git submodule.
- `vendor` — required native dependencies, pinned as Git submodules.
- `cmake`, `tools`, `.github/workflows` — native build support and Android CI/release workflow.

## Privacy and license

See the [privacy policy](PRIVACY.md). GBSat is distributed under [GNU GPL version 3](LICENSE); third-party components retain their respective licenses and notices in the app.

GBSat is not affiliated with or endorsed by SEGA. Sega Saturn is a trademark of SEGA.
