# SNAKE_

[中文说明](README.md) · [Download Android APK v1.5.0](dist/snake-offline-v1.5.0.apk?raw=true)

A native, offline Android snake game with a dark pixel-arcade interface, lime snake, red pixel fruit and square controls. No ads, accounts, network access or third-party runtime libraries. Requires Android 6.0 or newer.

## Tablets and music

16:10 tablets use a square playfield on the left and a separate score/control panel on the right. Rotation preserves the score and pauses play. Phones retain the portrait layout.

The original 60-second, 128 BPM chiptune **Neon Orchard** plays during countdown and gameplay. It pauses with the game and in the background. A persistent music switch is available in Settings. [Download the WAV](dist/neon-orchard.wav). The score and synthesizer source are in `music/`; regenerate with `compose-music.ps1`.

Version 1.4.0 was checked at 1920×1200 and 1200×1920 on the emulator, including score preservation, audio playback, mute, pause and background behavior.

## Controller and quick start

New runs start with a three-cell snake. Start/resume countdown is configurable: Off, 1s (default), 2s or 3s. Phone directional controls are centered.

Xbox-compatible Android input: D-pad/left stick steers, A starts/resumes, B pauses, Menu starts/pauses, X restarts, Y toggles music and View opens Settings. The stick uses a dead zone and diagonal hysteresis; a disconnected active controller pauses play. Injected emulator button events and pure-Java stick tests passed; physical Bluetooth/USB Xbox controllers have not yet been tested.

Music can also be muted immediately with the main-screen ON/OFF button. The preference survives restarts.

## Play

Press an arrow or swipe the board to steer. Buttons submit input on touch-down, and short swipes are supported. Swipes trigger at 3dp with noise filtering and repeated directions suppressed. Turns execute at grid junctions. Accepted turns briefly accelerate the remaining segment with a 32ms timing budget; actual presentation also depends on frame callbacks. Normal movement speed resumes afterward. Eat fruit for 10 points and an extra body segment; hitting a wall or your body ends the run. Direct U-turns are blocked.

Chill, Classic and Fast keep separate best scores. Arrow keys / WASD work in emulators; Space pauses. The game also pauses when sent to the background.

## Settings

Open the menu button in the top-right corner:

- Language: Chinese or English across the game, dialogs and settings.
- Background: Midnight or Black.
- Refresh / frame rate: 60 Hz or 120 Hz, with the corresponding rendering cap. A compatible display is required for 120 FPS. The game falls back to supported refresh rates and shows the current display Hz.
- Version: shown at the bottom of Settings.

Preferences survive app restarts. Changing a setting preserves the current score and pauses the run. The APK can be installed over earlier signed releases to retain best scores.

## Build on Windows

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\setup-tools.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

The setup script downloads and checks the pinned Java 17 and Android 35 toolchain. Subsequent builds work offline. Keep `.signing/` private and backed up to sign future updates with the same key.

Tests cover 34,729 game-rule assertions, 531 timing/rendering assertions, 326 input assertions and 111 gamepad assertions, plus APK signing and alignment checks. Version 1.3.0 was installed and checked on an Android 15 MuMu emulator: a 12px swipe (3dp at its current density) triggers a turn, and both language and background variants display correctly. A short sample in 120Hz mode recorded 85 rendered frames over approximately 0.70 seconds, with zero reported janky frames. This does not establish sustained performance or measure end-to-end touch latency. Physical high-refresh devices have not yet been tested.
